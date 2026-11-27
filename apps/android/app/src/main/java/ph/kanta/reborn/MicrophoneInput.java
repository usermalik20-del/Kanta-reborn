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
import ph.kanta.reborn.core.SpectrumAnalyzer;

/** Ephemeral dry capture; the independent optional output receives a copy of each PCM block. */
final class MicrophoneInput {
  static final class Reading {
    final double rms;
    final DryPitchDetector.Pitch pitch;
    final SpectrumAnalyzer.Spectrum spectrum; // Immutable band power; never raw PCM
    final long measuredAt;

    Reading(double rms, DryPitchDetector.Pitch pitch, SpectrumAnalyzer.Spectrum spectrum, long measuredAt) {
      this.rms = rms;
      this.pitch = pitch;
      this.spectrum = spectrum;
      this.measuredAt = measuredAt;
    }
  }

  private static final int RATE = 48000;
  private volatile int gainDb;
  private volatile int gateDb = ph.kanta.reborn.core.MicrophoneInputDsp.GATE_OFF;
  // Foreground-only selection, never persisted. Cleared on stop/failure/background.
  private android.media.AudioDeviceInfo preferred;

  int gainDb() { return gainDb; }
  int gateDb() { return gateDb; }
  void parameters(int gain, int gate) {
    gainDb = ph.kanta.reborn.core.MicrophoneInputDsp.gainDb(gain);
    gateDb = ph.kanta.reborn.core.MicrophoneInputDsp.gateDb(gate);
  }

  java.util.List<android.media.AudioDeviceInfo> inputs() {
    java.util.List<android.media.AudioDeviceInfo> result = new java.util.ArrayList<>();
    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    try {
      for (android.media.AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_INPUTS))
        if (device.isSource()) result.add(device);
    } catch (RuntimeException unavailable) {
      // Android can deny access to some device metadata; automatic selection remains available.
    }
    return result;
  }

  boolean selectInput(android.media.AudioDeviceInfo device) {
    if (enabled()) return false;
    preferred = null;
    if (device == null) return true;
    for (android.media.AudioDeviceInfo available : inputs()) {
      if (available.getId() == device.getId() && available.getType() == device.getType()) {
        preferred = available;
        return true;
      }
    }
    return false;
  }

  static String deviceLabel(android.media.AudioDeviceInfo device) {
    if (device == null) return "Automatic";
    String name = String.valueOf(device.getProductName()).replaceAll("[\\p{Cntrl}]", " ").trim();
    if (name.isEmpty()) name = "Microphone";
    String kind;
    switch (device.getType()) {
      case android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC: kind = "Phone mic"; break;
      case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET: kind = "Wired headset mic"; break;
      case android.media.AudioDeviceInfo.TYPE_USB_DEVICE:
      case android.media.AudioDeviceInfo.TYPE_USB_HEADSET:
      case android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY: kind = "USB mic"; break;
      case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO: kind = "Bluetooth call mic"; break;
      default: kind = "Microphone";
    }
    return kind + " · " + name.substring(0, Math.min(60, name.length())) + " (" + device.getId() + ")";
  }

  String inputStatus() {
    Run run = active;
    if (run != null) return run.routeLabel;
    return "Selected: " + deviceLabel(preferred);
  }

  double inputRms() {
    Run run = active;
    return run != null && SystemClock.elapsedRealtime() - run.levelAt <= 750 ? run.level : 0;
  }

  boolean clipping() {
    Run run = active;
    return run != null && SystemClock.elapsedRealtime() < run.clipUntil;
  }
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
  private final SpectrumAnalyzer spectrumAnalyzer = new SpectrumAnalyzer();

  private static final class Run {
    volatile boolean cancelled;
    volatile boolean capturing;
    volatile Reading reading;
    volatile SpicePitchEstimator spice;
    volatile double level;
    volatile long levelAt, clipUntil;
    volatile String routeLabel = "Checking microphone route…";
    final android.media.AudioDeviceInfo preferred;
    final ph.kanta.reborn.core.MicrophoneInputDsp dsp =
        new ph.kanta.reborn.core.MicrophoneInputDsp(RATE);
    final float[] analysisFrame = new float[DryPitchDetector.FRAME_SIZE];

    Run(android.media.AudioDeviceInfo preferred) { this.preferred = preferred; }
  }

  MicrophoneInput(Context context, Runnable changed, Consumer<String> notice, DrySink sink) {
    this.context = context;
    this.changed = changed;
    this.notice = notice;
    this.sink = sink;
    // This service shares SessionController's application lifetime. Invalidate selections even
    // while capture is off, so a disconnected device ID cannot silently be reused on reconnect.
    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    audio.registerAudioDeviceCallback(new android.media.AudioDeviceCallback() {
      @Override public void onAudioDevicesRemoved(android.media.AudioDeviceInfo[] removed) {
        for (android.media.AudioDeviceInfo device : removed) {
          if (!device.isSource()) continue;
          Run run = active;
          if (preferred != null && preferred.getId() == device.getId()) {
            preferred = null;
            if (run != null) stop();
            notice.accept("Selected microphone disconnected. Choose an input again.");
            changed.run();
            return;
          }
        }
      }
    }, main);
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
    Run run = new Run(preferred);
    active = run;
    capture.execute(() -> capture(run));
    changed.run();
  }

  void stop() {
    Run run = active;
    active = null;
    preferred = null;
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
      if (run.preferred != null && !input.setPreferredDevice(run.preferred)) {
        fail(run, "Android could not select that microphone. Choose an input and try again.");
        return;
      }
      input.startRecording();
      if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
        throw new IllegalStateException();
      // Optional native DSP may be missing on a device. Keep dry capture and frame analysis.
      try {
        run.spice = SpicePitchEstimator.create(context);
      } catch (LinkageError unavailable) {
        run.spice = null;
      }
      long lastData = SystemClock.elapsedRealtime(), lastAnalysis = 0, lastConfig = 0;
      ph.kanta.reborn.core.InputRouteGuard route = new ph.kanta.reborn.core.InputRouteGuard(
          run.preferred == null ? 0 : run.preferred.getId(), lastData);
      int filled = 0;
      while (current(run)) {
        int count = input.read(block, 0, block.length, AudioRecord.READ_NON_BLOCKING);
        long now = SystemClock.elapsedRealtime();
        if (count < 0) throw new IllegalStateException();
        android.media.AudioDeviceInfo actual = input.getRoutedDevice();
        ph.kanta.reborn.core.InputRouteGuard.State routeState =
            route.observe(actual == null ? 0 : actual.getId(), now);
        if (routeState == ph.kanta.reborn.core.InputRouteGuard.State.REJECTED) {
          run.capturing = false;
          run.reading = null;
          fail(run, "Microphone route changed or could not be verified. Choose the input again.");
          return;
        }
        if (routeState == ph.kanta.reborn.core.InputRouteGuard.State.WAITING) {
          SystemClock.sleep(4);
          continue; // Discard unverified PCM; never feed the monitor or scoring.
        }
        if (!run.capturing) run.routeLabel = "Using: " + deviceLabel(actual);
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
        run.dsp.applyGain(block, count, gainDb);
        run.level = run.dsp.rms();
        run.levelAt = now;
        if (run.dsp.clipped()) run.clipUntil = now + 1000;
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
        // Dry analysis/scoring and SPICE have copied the gained, ungated input above.
        // Only the independent monitor receives the smoothly gated signal.
        run.dsp.applyMonitorGate(block, count, gateDb);
        if (current(run)) sink.offer(block, count);
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
            if (estimator != null) {
              try {
                estimator.poll();
              } catch (RuntimeException | LinkageError unavailable) {
                run.spice = null;
              }
            }
            DryPitchDetector.Pitch pitch = detector.detect(run.analysisFrame, RATE);
            SpectrumAnalyzer.Spectrum spectrum = spectrumAnalyzer.analyze(run.analysisFrame);
            Reading value =
                new Reading(Math.sqrt(energy / run.analysisFrame.length), pitch, spectrum, measuredAt);
            if (current(run)) run.reading = value;
          } finally {
            java.util.Arrays.fill(run.analysisFrame, 0);
            analysisBusy.set(false);
          }
        });
  }
}
