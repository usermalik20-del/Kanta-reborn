package ph.kanta.reborn;

import android.content.Context;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.tensorflow.lite.Interpreter;
import ph.kanta.reborn.core.SpicePitch;

/**
 * SPICE pitch estimation, backed by the TensorFlow Lite model in {@code assets/spice.tflite}.
 *
 * <p>The model takes mono audio at 16 kHz and returns, per 32 ms frame, a normalised pitch
 * and an uncertainty. This class owns the awkward parts around it: resampling the 48 kHz
 * capture down to 16 kHz, keeping the most recent one-second window, running the
 * interpreter, and turning the model's normalised output back into a frequency.
 *
 * <p>This is an optional DSP adapter, as the architecture contract describes. It never opens
 * a capture of its own - the microphone stays owned by {@link MicrophoneInput} - and if the
 * model cannot be loaded it simply is not created, so the pitch features fall back to the
 * existing frame detector.
 *
 * <p>Threading: {@link #offer} is called from the capture thread and only touches the sample
 * ring; {@link #poll} is called from the analysis thread and does the inference. The two
 * communicate through a single published window, so neither blocks the other.
 */
final class SpicePitchEstimator {

  private static final String MODEL_ASSET = "spice.tflite";

  /** Frames below this model confidence are treated as unvoiced. */
  private static final float MIN_CONFIDENCE = 0.5f;

  /** Minimum gap between two inferences. The model is heavy, so it is not run every frame. */
  private static final long MIN_INTERVAL_MS = 250;

  private final Interpreter interpreter;
  private final SpicePitch.Resampler resampler = new SpicePitch.Resampler();
  private final float[] ring = new float[SpicePitch.WINDOW_SAMPLES];
  private final float[] scratch = new float[SpicePitch.WINDOW_SAMPLES / SpicePitch.DECIMATION + 8];
  private final AtomicReference<float[]> published = new AtomicReference<>();
  private final ByteBuffer pitchBuffer;
  private final ByteBuffer uncertaintyBuffer;

  private final int capacity;
  private int writeIndex;
  private long filled;
  private long lastRunAt;
  private int frameCount;

  private volatile float pitchHz;
  private volatile long measuredAt;

  private SpicePitchEstimator(Interpreter interpreter, int capacity) {
    this.interpreter = interpreter;
    this.capacity = capacity;
    this.pitchBuffer = ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.nativeOrder());
    this.uncertaintyBuffer = ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.nativeOrder());
    this.frameCount = capacity;
  }

  /**
   * Loads the model and prepares the estimator.
   *
   * @return the estimator, or null when the model is unavailable. A null result is not an
   *     error: the caller keeps using the existing pitch detection.
   */
  static SpicePitchEstimator create(Context context) {
    try {
      ByteBuffer model = readAsset(context, MODEL_ASSET);
      Interpreter interpreter = new Interpreter(model);
      SpicePitchEstimator estimator =
          new SpicePitchEstimator(interpreter, SpicePitch.WINDOW_SAMPLES / 512 + 4);
      estimator.probe();
      return estimator;
    } catch (IOException | RuntimeException unavailable) {
      return null;
    }
  }

  /** Reads an asset into a direct buffer the interpreter can map. */
  private static ByteBuffer readAsset(Context context, String name) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = context.getAssets().open(name)) {
      byte[] chunk = new byte[65536];
      int read;
      while ((read = in.read(chunk)) > 0) bytes.write(chunk, 0, read);
    }
    byte[] model = bytes.toByteArray();
    ByteBuffer buffer = ByteBuffer.allocateDirect(model.length).order(ByteOrder.nativeOrder());
    buffer.put(model).rewind();
    return buffer;
  }

  /**
   * Runs the model once on silence to learn how many frames it produces for a window of this
   * length, rather than assuming the count from the model card.
   */
  private void probe() {
    runModel(new float[SpicePitch.WINDOW_SAMPLES]);
  }

  /** Feeds captured audio. Called from the capture thread; never blocks on inference. */
  void offer(short[] pcm, int count) {
    int written = resampler.accept(pcm, count, scratch);
    if (written == 0) return;
    for (int i = 0; i < written; i++) {
      ring[writeIndex] = scratch[i];
      writeIndex = (writeIndex + 1) % SpicePitch.WINDOW_SAMPLES;
    }
    filled += written;
    if (writeIndex == 0 && filled >= SpicePitch.WINDOW_SAMPLES) {
      published.set(Arrays.copyOf(ring, SpicePitch.WINDOW_SAMPLES));
    }
  }

  /**
   * Runs the model over the newest completed window when one is ready.
   *
   * @return true when an inference ran
   */
  boolean poll() {
    long now = SystemClock.elapsedRealtime();
    if (now - lastRunAt < MIN_INTERVAL_MS) return false;
    float[] window = published.getAndSet(null);
    if (window == null) return false;
    lastRunAt = now;
    float hz = runModel(window);
    if (hz > 0f) {
      pitchHz = hz;
      measuredAt = now;
    }
    return true;
  }

  /** The most recent calibrated pitch in Hz, or 0 when the model has not produced one. */
  float pitchHz() {
    return pitchHz;
  }

  /** When that pitch was measured, in milliseconds; 0 when unknown. */
  long measuredAt() {
    return measuredAt;
  }

  /**
   * Runs the interpreter over one window.
   *
   * @return the selected pitch in Hz, or 0 when no frame was confident enough
   */
  private float runModel(float[] window) {
    interpreter.resizeInput(0, new int[] {window.length});
    ByteBuffer input =
        ByteBuffer.allocateDirect(window.length * 4).order(ByteOrder.nativeOrder());
    input.put(window).rewind();
    pitchBuffer.clear();
    uncertaintyBuffer.clear();
    interpreter.run(input, new ByteBuffer[] {pitchBuffer, uncertaintyBuffer});

    int count = reportedFrameCount();
    if (count <= 0 || count > capacity) count = frameCount > 0 ? frameCount : capacity;
    frameCount = count;
    pitchBuffer.rewind();
    uncertaintyBuffer.rewind();
    float[] pitches = new float[count];
    float[] uncertainties = new float[count];
    pitchBuffer.get(pitches);
    uncertaintyBuffer.get(uncertainties);
    return SpicePitch.selectPitch(pitches, uncertainties, MIN_CONFIDENCE);
  }

  /** How many frames the model actually produced for the last run. */
  private int reportedFrameCount() {
    try {
      int[] shape = interpreter.getOutputTensor(0).shape();
      if (shape.length == 0) return 0;
      return shape[shape.length - 1];
    } catch (RuntimeException unavailable) {
      return 0;
    }
  }
}
