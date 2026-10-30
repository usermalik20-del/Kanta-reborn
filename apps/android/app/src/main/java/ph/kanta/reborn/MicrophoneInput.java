package ph.kanta.reborn;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import ph.kanta.reborn.core.DryPitchDetector;

/** Ephemeral dry capture; the independent optional output receives a copy of each PCM block. */
final class MicrophoneInput {
  static final class Reading {
    final double rms;
    final DryPitchDetector.Pitch pitch;
    final long measuredAt;

    Reading(double rms, DryPitchDetector.Pitch pitch, long measuredAt) {
      this.rms = rms;
      this.pitch = pitch;
      this.measuredAt = measuredAt;
    }
  }

  private static final int RATE = 48000;
  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService capture =
      new java.util.concurrent.ThreadPoolExecutor(
          1,
          1,
          0L,
          java.util.concurrent.TimeUnit.MILLISECONDS,
          new java.util.concurrent.ArrayBlockingQueue<>(1),
          new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
  private final ExecutorService analysis = Executors.newSingleThreadExecutor();
  private final Consumer<String> notice;
  private final Runnable changed;

  interface DrySink {
    void offer(short[] input, int count);
  }

  private final DrySink sink;
  private volatile Run active, captureOwner;
  private final AtomicBoolean analysisBusy = new AtomicBoolean();
  private final DryPitchDetector detector = new DryPitchDetector();

  private static final class Run {
    volatile boolean cancelled;
    volatile boolean capturing;
    volatile Reading reading;
    volatile SpicePitchEstimator spice;
    final float[] analysisFrame = new float[DryPitchDetector.FRAME_SIZE];
  }

  MicrophoneInput(Context context, Runnable changed, Consumer<String> notice, DrySink sink) {
    this.context = context;
    this.changed = changed;
    this.notice = notice;
    this.sink = sink;
  }

  boolean enabled() {
    return active != null;
  }

  boolean resourcesReleased() {
    return active == null && captureOwner == null;
  }

  boolean capturing() {
    Run run = active;
    return run != null && run.capturing;
  }

  /** The calibrated pitch estimate in Hz, or 0 when SPICE has not produced one. */
  float calibratedPitchHz() {
    Run run = active;
    if (run == null || run.spice == null) return 0f;
    return run.spice.pitchHz();
  }

  /** When the calibrated estimate was measured, in milliseconds; 0 when unknown. */
  long calibratedMeasuredAt() {
    Run run = active;
    if (run == null || run.spice == null) return 0L;
    return run.spice.measuredAt();
  }

  Reading reading() {
    Run run = active;
    Reading value = run == null ? null : run.reading;
    return value != null && SystemClock.elapsedRealtime() - value.measuredAt <= 750 ? value : null;
  }

  void start() {
    if (enabled()
        || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return;
    Run run = new Run();
    active = run;
    capture.execute(() -> capture(run));
    changed.run();
  }

  void stop() {
    Run run = active;
    active = null;
    if (run != null) run.cancelled = true;
  }

  private boolean current(Run run) {
    return active == run && !run.cancelled;
  }

  private void fail(Run run, String message) {
    main.post(
        () -> {
          if (!current(run)) return;
          stop();
          notice.accept(message);
          changed.run();
        });
  }

  private void capture(Run run) {
    captureOwner = run;
    AudioRecord input = null;
    short[] block = new short[480];
    float[] frame = new float[DryPitchDetector.FRAME_SIZE];
    try {
      if (!current(run)) return;
      AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      int source =
          "true".equals(audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED))
              ? MediaRecorder.AudioSource.UNPROCESSED
              : MediaRecorder.AudioSource.VOICE_RECOGNITION;
      int minimum =
          AudioRecord.getMinBufferSize(
              RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
      if (minimum <= 0 || minimum > 1024 * 1024) throw new IllegalStateException();
      AudioRecord.Builder builder =
          new AudioRecord.Builder()
              .setAudioSource(source)
              .setAudioFormat(
                  new AudioFormat.Builder()
                      .setSampleRate(RATE)
                      .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                      .build())
              .setBufferSizeInBytes(Math.max(minimum, 4800));
      if (Build.VERSION.SDK_INT >= 30) builder.setPrivacySensitive(true);
      if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
          != PackageManager.PERMISSION_GRANTED) throw new SecurityException();
      input = builder.build();
      if (input.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException();
      if (!current(run)) return;
      input.startRecording();
      if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
        throw new IllegalStateException();
      // SPICE is an optional DSP adapter. If the model cannot be loaded the pitch features
      // keep using the frame detector, so this must never stop the capture.
      run.spice = SpicePitchEstimator.create(context);
      long lastData = SystemClock.elapsedRealtime(), lastAnalysis = 0, lastConfig = 0;
      int filled = 0;
      while (current(run)) {
        int count = input.read(block, 0, block.length, AudioRecord.READ_NON_BLOCKING);
        long now = SystemClock.elapsedRealtime();
        if (count < 0) throw new IllegalStateException();
        if (now - lastConfig >= 200 && Build.VERSION.SDK_INT >= 29) {
          AudioRecordingConfiguration configuration = input.getActiveRecordingConfiguration();
          if (configuration != null && configuration.isClientSilenced()) {
            fail(
                run, "Microphone is unavailable or muted by Android. Turn it on again when ready.");
            return;
          }
          lastConfig = now;
        }
        if (count == 0) {
          if (now - lastData > 5000) throw new IllegalStateException();
          SystemClock.sleep(4);
          continue;
        }
        lastData = now;
        if (!current(run)) break;
        run.capturing = true;
        sink.offer(block, count);
        SpicePitchEstimator estimator = run.spice;
        if (estimator != null) estimator.offer(block, count);
        for (int i = 0; i < count; i++) {
          frame[filled++] = block[i] / 32768f;
          if (filled == frame.length) {
            if (now - lastAnalysis >= 125 && analysisBusy.compareAndSet(false, true)) {
              System.arraycopy(frame, 0, run.analysisFrame, 0, frame.length);
              analyze(run, now);
              lastAnalysis = now;
            }
            filled = 0;
          }
        }
      }
    } catch (RuntimeException failure) {
      if (current(run))
        fail(run, "Microphone could not start or was interrupted. Nothing was saved.");
    } finally {
      if (input != null) {
        try {
          if (input.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) input.stop();
        } catch (RuntimeException ignored) {
          // A revoked permission or dead audio server may already have stopped capture.
        }
        try {
          input.release();
        } catch (RuntimeException ignored) {
          // The platform may already have retired the native capture object.
        }
      }
      java.util.Arrays.fill(block, (short) 0);
      java.util.Arrays.fill(frame, 0);
      captureOwner = null;
    }
  }

  private void analyze(Run run, long measuredAt) {
    analysis.execute(
        () -> {
          try {
            if (!current(run)) return;
            double energy = 0;
            for (float value : run.analysisFrame) energy += value * value;
            SpicePitchEstimator estimator = run.spice;
            if (estimator != null) estimator.poll();
            DryPitchDetector.Pitch pitch = detector.detect(run.analysisFrame, RATE);
            Reading value =
                new Reading(Math.sqrt(energy / run.analysisFrame.length), pitch, measuredAt);
            if (current(run)) run.reading = value;
          } finally {
            java.util.Arrays.fill(run.analysisFrame, 0);
            analysisBusy.set(false);
          }
        });
  }
}
