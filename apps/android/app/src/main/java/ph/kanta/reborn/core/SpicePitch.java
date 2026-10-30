package ph.kanta.reborn.core;

import java.util.Arrays;

/**
 * Pure DSP for the SPICE pitch estimator.
 *
 * <p>SPICE (Google Research) is a self-supervised pitch-extraction model. It consumes mono
 * audio at 16 kHz and returns, for every 32 ms of input, a normalised pitch value and an
 * uncertainty. The normalised pitch is not a frequency: it still needs the model's published
 * calibration, {@code semitones = 24.37 + 62.51 * pitch}, before it means anything.
 *
 * <p>The capture runs at 48 kHz, so the audio has to be resampled before the model sees it.
 * This class owns that resampling with a 33-tap Hann-windowed sinc low-pass at 4.5 kHz,
 * decimated by three. The low-pass is not decoration: without it, content above the 8 kHz
 * output Nyquist folds back down and the model reads a fundamental that is not there.
 *
 * <p>Everything here is framework-free, so the host-JVM coreRegression task can check it
 * against golden vectors computed independently from the same design.
 */
public final class SpicePitch {

  /** The rate the model expects. */
  public static final int MODEL_RATE = 16000;

  /** The model window: 1.024 s of 16 kHz audio. */
  public static final int WINDOW_SAMPLES = 16384;

  /** The capture rate the resampler is designed for. */
  public static final int CAPTURE_RATE = 48000;

  /** Published model calibration: {@code semitones = OFFSET + SLOPE * modelPitch}. */
  public static final float SEMITONE_OFFSET = 24.37f;

  /** Published model calibration slope. */
  public static final float SEMITONE_SLOPE = 62.51f;

  /** Capture samples per model sample. */
  public static final int DECIMATION = CAPTURE_RATE / MODEL_RATE;

  /** Low-pass length. Odd, so the window has a true centre tap. */
  private static final int TAPS = 33;

  /** Distance from the window centre to its edge. */
  private static final int HALF = TAPS / 2;

  /**
   * 33-tap Hann-windowed sinc low-pass at 4.5 kHz, designed for the 48 kHz capture rate and
   * normalised to unit DC gain. Coefficients were generated from
   * {@code 2*fc*sinc(2*fc*(n-16)) * hann(33)} with {@code fc = 4500/48000} and then normalised.
   */
  private static final double[] LOWPASS = {
    0.0,
    0.00011368886589436291,
    0.0008024628402645814,
    0.00203116395272001,
    0.0027570792296065204,
    0.0012591641589503443,
    -0.0037738424639610652,
    -0.01187920002258025,
    -0.01996853420595968,
    -0.02267698144406405,
    -0.014087935118028508,
    0.009695975709287647,
    0.048208306100738924,
    0.0956507931138595,
    0.1419709204952493,
    0.17579743824142322,
    0.18819900109319826,
    0.17579743824142322,
    0.1419709204952493,
    0.0956507931138595,
    0.048208306100738924,
    0.009695975709287647,
    -0.014087935118028508,
    -0.02267698144406405,
    -0.01996853420595968,
    -0.01187920002258025,
    -0.0037738424639610652,
    0.0012591641589503443,
    0.0027570792296065204,
    0.00203116395272001,
    0.0008024628402645814,
    0.00011368886589436291,
    0.0
  };

  private SpicePitch() {}

  /** Converts a normalised model pitch in [0,1] to semitones above MIDI note 0. */
  public static float semitones(float modelPitch) {
    return SEMITONE_OFFSET + SEMITONE_SLOPE * modelPitch;
  }

  /** Converts semitones above MIDI note 0 to Hz. A4 (69 semitones) is 440 Hz. */
  public static float hz(float semitones) {
    return (float) (440.0 * Math.pow(2.0, (semitones - 69.0) / 12.0));
  }

  /**
   * Model confidence in [0,1]. The model reports uncertainty, so confidence is its complement.
   */
  public static float confidence(float uncertainty) {
    return 1.0f - uncertainty;
  }

  /**
   * Picks one pitch for a model window.
   *
   * <p>The model pads its input for the constant-Q transform, which leaves the first and last
   * frames less accurate than the rest; they are skipped when there are enough frames to
   * spare. Unconfident frames are dropped, and the median of the remaining semitone values is
   * used, because a median does not let one octave error drag the result away.
   *
   * @param pitches model pitch values, one per 32 ms frame
   * @param uncertainties model uncertainty values, one per frame
   * @param minConfidence frames below this confidence are ignored
   * @return the pitch in Hz, or 0 when no frame was confident enough
   */
  public static float selectPitch(float[] pitches, float[] uncertainties, float minConfidence) {
    if (pitches == null || uncertainties == null) return 0f;
    int count = Math.min(pitches.length, uncertainties.length);
    if (count == 0) return 0f;
    int first = count >= 3 ? 1 : 0;
    int last = count >= 3 ? count - 1 : count;
    float[] voiced = new float[last - first];
    int kept = 0;
    for (int i = first; i < last; i++) {
      if (confidence(uncertainties[i]) < minConfidence) continue;
      voiced[kept++] = semitones(pitches[i]);
    }
    if (kept == 0) return 0f;
    Arrays.sort(voiced, 0, kept);
    float median =
        (kept & 1) == 1 ? voiced[kept / 2] : (voiced[kept / 2 - 1] + voiced[kept / 2]) / 2f;
    return hz(median);
  }

  /**
   * Streaming 48 kHz to 16 kHz resampler.
   *
   * <p>Capture blocks are handed in as they arrive; matching 16 kHz samples are written into a
   * caller-supplied buffer. The filter is centred, so it needs half a window of history
   * before the first output and half a window of lookahead after the last one; {@link #flush}
   * supplies the lookahead with zeros so the tail is not silently dropped.
   */
  public static final class Resampler {

    private final double[] ring = new double[TAPS];
    private long received;
    private long nextCentre;

    /** Feeds captured PCM and writes matching 16 kHz samples into {@code out}. */
    public int accept(short[] pcm, int count, float[] out) {
      int written = 0;
      for (int i = 0; i < count; i++) {
        ring[(int) (received % TAPS)] = pcm[i] / 32768.0;
        received++;
        long centre = received - 1 - HALF;
        if (centre < nextCentre) continue;
        out[written++] = window(centre, received);
        nextCentre += DECIMATION;
      }
      return written;
    }

    /**
     * Emits the outputs whose filter window runs past the end of the captured audio, using
     * zeros for the missing tail. Without this the last few output samples are lost.
     *
     * <p>The outputs are written from {@code offset}, so a caller that has already filled part
     * of its buffer keeps what is there.
     */
    public int flush(float[] out, int offset) {
      int written = 0;
      // Only a complete decimation group counts: output j needs the capture samples
      // [3j, 3j+3). A trailing partial group is not a sample the model would have produced.
      while (nextCentre + DECIMATION <= received) {
        out[offset + written++] = window(nextCentre, received);
        nextCentre += DECIMATION;
      }
      return written;
    }

    /** Discards all buffered audio. */
    public void reset() {
      Arrays.fill(ring, 0.0);
      received = 0;
      nextCentre = 0;
    }

    /** Total capture samples consumed so far. */
    public long received() {
      return received;
    }

    /**
     * Evaluates the low-pass centred on {@code centre}. Samples before the start or past the
     * end of the captured audio count as zero.
     */
    private float window(long centre, long total) {
      double sum = 0.0;
      long base = centre - HALF;
      for (int k = 0; k < TAPS; k++) {
        long index = base + k;
        double sample = 0.0;
        if (index >= 0 && index < total) sample = ring[(int) (index % TAPS)];
        sum += LOWPASS[k] * sample;
      }
      return (float) sum;
    }
  }
}
