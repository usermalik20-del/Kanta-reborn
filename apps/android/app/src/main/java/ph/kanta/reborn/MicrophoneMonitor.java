package ph.kanta.reborn;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import ph.kanta.reborn.core.ChromaticPitchCorrector;
import ph.kanta.reborn.core.MonitorBuffer;
import ph.kanta.reborn.core.MonitorRouteGuard;
import ph.kanta.reborn.core.VocalMonitorEffects;

/** Optional voice output. Actual routing, not device availability, controls audibility. */
@android.annotation.SuppressLint("InlinedApi")
final class MicrophoneMonitor {
  private static final int RATE = 48000;
  /** Bluetooth link settling bound: SCO negotiation takes one to three seconds. */
  private static final long COMMUNICATION_READY_MS = 6000;
  /** Wired outputs stall briefly; a Bluetooth link can stall while it reconfigures. */
  private static final long WRITE_STALL_MS = 500, COMMUNICATION_WRITE_STALL_MS = 2500;
  private final AudioManager audio;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Consumer<String> notice;
  private final Runnable changed;
  private final java.util.function.Supplier<MicrophoneInput.Reading> readings;
  private final ExecutorService worker =
      new ThreadPoolExecutor(
          1,
          1,
          0L,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(1),
          new ThreadPoolExecutor.DiscardOldestPolicy());
  private volatile Run active, outputOwner;
  private volatile float gain = .1f;
  private volatile VocalMonitorEffects.Mode mode = VocalMonitorEffects.Mode.DRY;

  private static final class Run {
    final AudioDeviceInfo device;
    final MonitorBuffer buffer = new MonitorBuffer();
    final MonitorRouteGuard guard;
    final boolean communication;
    volatile boolean communicationRouted;
    volatile boolean cancelled, audible, ready;
    volatile CorrectionChoice correction = CorrectionChoice.OFF;
    AudioTrack track;

    Run(AudioDeviceInfo device) {
      this.device = device;
      communication = MicrophoneMonitor.communication(device);
      // A Bluetooth activation needs the longer readiness bound; wired keeps the 1.5 s one.
      guard =
          new MonitorRouteGuard(
              device.getId(),
              SystemClock.elapsedRealtime(),
              communication ? COMMUNICATION_READY_MS : MonitorRouteGuard.READY_MS);
    }
  }

  private static final class CorrectionChoice {
    static final CorrectionChoice OFF = new CorrectionChoice(false, 0);
    final boolean enabled;
    final long epoch;

    CorrectionChoice(boolean enabled, long epoch) {
      this.enabled = enabled;
      this.epoch = epoch;
    }
  }

  MicrophoneMonitor(
      Context context,
      Runnable changed,
      Consumer<String> notice,
      java.util.function.Supplier<MicrophoneInput.Reading> readings) {
    audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    this.changed = changed;
    this.notice = notice;
    this.readings = readings;
  }

  /**
   * Accepted monitor outputs: headset-class and line-out sinks only.
   *
   * <p>Speaker-class routes (built-in speaker, Bluetooth A2DP speakers, HDMI/ARC) stay rejected:
   * the microphone hears them, so monitoring there is a feedback loop. Bluetooth SCO and BLE
   * headsets are accepted because the microphone belongs to the same worn device.
   */
  static boolean supported(AudioDeviceInfo device) {
    if (device == null) return false;
    switch (device.getType()) {
      case AudioDeviceInfo.TYPE_WIRED_HEADSET:
      case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
      case AudioDeviceInfo.TYPE_USB_DEVICE:
      case AudioDeviceInfo.TYPE_USB_HEADSET:
      case AudioDeviceInfo.TYPE_LINE_ANALOG:
      case AudioDeviceInfo.TYPE_AUX_LINE:
        return device.isSink();
      case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
      case AudioDeviceInfo.TYPE_BLE_HEADSET:
        return true;
      default:
        return false;
    }
  }

  /**
   * Bluetooth headsets route through the platform communication path. A plain media track would
   * keep playing from the built-in speaker, so the selection is not enough on its own.
   */
  static boolean communication(AudioDeviceInfo device) {
    return device != null
        && (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            || device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET);
  }

  List<AudioDeviceInfo> outputs() {
    ArrayList<AudioDeviceInfo> devices = new ArrayList<>();
    for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
      // Communication routing replaced the SCO broadcasts in API 31, so older platforms are
      // not offered a Bluetooth output they cannot route.
      if (supported(device) && (Build.VERSION.SDK_INT >= 31 || !communication(device)))
        devices.add(device);
    return devices;
  }

  boolean enabled() {
    return active != null;
  }

  boolean audible() {
    Run run = active;
    return run != null && run.audible;
  }

  boolean resourcesReleased() {
    return active == null && outputOwner == null;
  }

  boolean correctionAvailable() {
    Run run = active;
    return current(run) && run.ready;
  }

  boolean correctionEnabled() {
    Run run = active;
    return current(run) && run.correction.enabled;
  }

  boolean correction(boolean enabled) {
    Run run = active;
    if (run == null) return false;
    synchronized (run) {
      if (!current(run) || enabled && !run.ready) return false;
      run.correction =
          enabled
              ? new CorrectionChoice(true, SystemClock.elapsedRealtime())
              : CorrectionChoice.OFF;
    }
    changed.run();
    return true;
  }

  float gain() {
    return gain;
  }

  VocalMonitorEffects.Mode mode() {
    return mode;
  }

  void gain(float value) {
    if (Float.isFinite(value)) gain = Math.max(0, Math.min(.35f, value));
  }

  void mode(VocalMonitorEffects.Mode value) {
    if (value != null) mode = value;
  }

  boolean start(AudioDeviceInfo selected) {
    if (enabled() || !supported(selected)) return false;
    if (communication(selected) && Build.VERSION.SDK_INT < 31) return false;
    boolean connected = outputs().stream().anyMatch(d -> d.getId() == selected.getId());
    if (!connected) return false;
    gain = .1f;
    mode = VocalMonitorEffects.Mode.DRY;
    Run run = new Run(selected);
    active = run;
    worker.execute(() -> output(run));
    changed.run();
    return true;
  }

  void offer(short[] input, int count) {
    Run run = active;
    if (current(run)) run.buffer.offer(input, count);
  }

  void stop() {
    Run run = active;
    active = null;
    if (run != null) retire(run);
  }

  private boolean current(Run run) {
    return run != null && active == run && !run.cancelled;
  }

  private void retire(Run run) {
    run.cancelled = true;
    run.audible = false;
    run.ready = false;
    run.correction = CorrectionChoice.OFF;
    run.guard.close();
    synchronized (run) {
      if (run.track != null) {
        try {
          run.track.setVolume(0);
          run.track.pause();
          run.track.flush();
        } catch (RuntimeException ignored) {
        }
      }
    }
    run.buffer.clear();
  }

  private void fail(Run run, String message) {
    if (!current(run)) return;
    retire(run);
    main.post(
        () -> {
          if (active != run) return;
          active = null;
          notice.accept(message);
          changed.run();
        });
  }

  private int[] routed(AudioTrack track) {
    if (Build.VERSION.SDK_INT >= 36) {
      List<AudioDeviceInfo> devices = track.getRoutedDevices();
      int[] ids = new int[devices.size()];
      for (int i = 0; i < ids.length; i++)
        ids[i] = supported(devices.get(i)) ? devices.get(i).getId() : -1;
      return ids;
    }
    AudioDeviceInfo device = track.getRoutedDevice();
    return device == null ? new int[0] : new int[] {supported(device) ? device.getId() : -1};
  }

  private void output(Run run) {
    outputOwner = run;
    float[] block = new float[480];
    VocalMonitorEffects effects = new VocalMonitorEffects();
    ChromaticPitchCorrector corrector = new ChromaticPitchCorrector();
    CorrectionChoice priorChoice = CorrectionChoice.OFF;
    AudioDeviceCallback deviceChanges =
        new AudioDeviceCallback() {
          @Override
          public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            for (AudioDeviceInfo device : removed)
              if (device.getId() == run.device.getId()) {
                fail(
                    run,
                    "Monitoring stopped: the selected output disconnected. Reconnect and enable it"
                        + " again.");
                return;
              }
          }
        };
    AudioRouting.OnRoutingChangedListener routing =
        router -> {
          if (!current(run)) return;
          synchronized (run) {
            if (run.track != null
                && run.guard.verify(routed(run.track), SystemClock.elapsedRealtime())
                    == MonitorRouteGuard.Decision.STOP)
              fail(
                  run,
                  "Monitoring stopped because the audio route changed. Enable it again when"
                      + " ready.");
          }
        };
    boolean callbackRegistered = false;
    AudioTrack track = null;
    try {
      if (!current(run)) return;
      // A Bluetooth headset only becomes an output once the platform is asked for the
      // communication route; a plain media track would keep playing from the built-in speaker.
      boolean communicationRouted = false;
      if (run.communication && Build.VERSION.SDK_INT >= 31) {
        try {
          audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
          communicationRouted = audio.setCommunicationDevice(run.device);
        } catch (RuntimeException refused) {
          communicationRouted = false;
        }
        if (!communicationRouted) {
          try {
            audio.setMode(AudioManager.MODE_NORMAL);
          } catch (RuntimeException ignored) {
          }
        }
        run.communicationRouted = communicationRouted;
      }
      int minimum =
          AudioTrack.getMinBufferSize(
              RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
      long bufferLimit = communicationRouted ? (long) RATE * 2 : RATE / 5 * 4;
      if (minimum <= 0 || minimum > bufferLimit) throw new IllegalStateException();
      track =
          new AudioTrack.Builder()
              .setAudioAttributes(
                  new AudioAttributes.Builder()
                      .setUsage(
                          communicationRouted
                              ? AudioAttributes.USAGE_VOICE_COMMUNICATION
                              : AudioAttributes.USAGE_MEDIA)
                      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                      .build())
              .setAudioFormat(
                  new AudioFormat.Builder()
                      .setSampleRate(RATE)
                      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                      .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                      .build())
              .setTransferMode(AudioTrack.MODE_STREAM)
              .setBufferSizeInBytes(Math.max(minimum, 3840))
              .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
              .build();
      synchronized (run) {
        run.track = track;
      }
      if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException();
      if (!current(run)) return;
      if (track.setVolume(0) != AudioTrack.SUCCESS) throw new IllegalStateException();
      if (!track.setPreferredDevice(run.device)) throw new IllegalStateException();
      track.addOnRoutingChangedListener(routing, main);
      audio.registerAudioDeviceCallback(deviceChanges, main);
      callbackRegistered = true;
      // Only zeroes are queued before actual routing has been verified.
      int warmupFrames = track.getBufferSizeInFrames();
      if (warmupFrames <= 0 || warmupFrames > (communicationRouted ? RATE / 2 : RATE / 5))
        throw new IllegalStateException();
      float[] warmup = new float[warmupFrames];
      track.write(warmup, 0, warmup.length, AudioTrack.WRITE_NON_BLOCKING);
      track.play();
      boolean ready = false;
      while (current(run)) {
        MonitorRouteGuard.Decision decision =
            run.guard.verify(routed(track), SystemClock.elapsedRealtime());
        if (decision == MonitorRouteGuard.Decision.STOP) {
          fail(
              run,
              "Monitoring stopped: the selected wired, USB or Bluetooth output could not be"
                  + " verified.");
          return;
        }
        if (decision == MonitorRouteGuard.Decision.ALLOW) {
          ready = true;
          break;
        }
        SystemClock.sleep(4);
      }
      if (!ready || !current(run)) return;
      run.buffer.clear();
      run.ready = true;
      main.post(
          () -> {
            if (current(run)) changed.run();
          });
      long correctionFloor = SystemClock.elapsedRealtime();
      int offset = block.length;
      long lastWrite = SystemClock.elapsedRealtime(), discontinuity = run.buffer.discontinuities();
      boolean hasInput = false;
      while (current(run)) {
        synchronized (run) {
          if (!current(run)) break;
          if (run.guard.verify(routed(track), SystemClock.elapsedRealtime())
              != MonitorRouteGuard.Decision.ALLOW) {
            fail(run, "Monitoring stopped because the selected output changed.");
            break;
          }
          if (offset == block.length) {
            hasInput = run.buffer.drain(block);
            long currentDiscontinuity = run.buffer.discontinuities();
            CorrectionChoice choice = run.correction;
            if (!hasInput || currentDiscontinuity != discontinuity || choice != priorChoice) {
              effects.reset();
              corrector.reset();
              correctionFloor = SystemClock.elapsedRealtime();
            }
            discontinuity = currentDiscontinuity;
            priorChoice = choice;
            if (choice.enabled) {
              MicrophoneInput.Reading reading = readings.get();
              long now = SystemClock.elapsedRealtime();
              if (reading != null
                  && reading.pitch != null
                  && reading.measuredAt - 86 >= Math.max(choice.epoch, correctionFloor))
                corrector.update(
                    reading.pitch.frequencyHz,
                    reading.pitch.confidence,
                    reading.pitch.rms,
                    reading.measuredAt,
                    now);
              else corrector.update(0, 0, 0, 0, now);
              corrector.process(block);
            }
            effects.mode(mode);
            effects.process(block);
            offset = 0;
          }
          float volume = hasInput ? gain : 0;
          if (track.setVolume(volume) != AudioTrack.SUCCESS) throw new IllegalStateException();
          run.audible = volume > 0;
          int written =
              track.write(block, offset, block.length - offset, AudioTrack.WRITE_NON_BLOCKING);
          if (written < 0) throw new IllegalStateException();
          offset += written;
          if (written > 0) lastWrite = SystemClock.elapsedRealtime();
          else if (SystemClock.elapsedRealtime() - lastWrite
              > (communicationRouted ? COMMUNICATION_WRITE_STALL_MS : WRITE_STALL_MS))
            throw new IllegalStateException();
        }
        SystemClock.sleep(2);
      }
    } catch (RuntimeException failure) {
      fail(
          run,
          "Monitoring stopped because the output is unavailable. Microphone input is unchanged.");
    } finally {
      retire(run);
      if (callbackRegistered) {
        try {
          audio.unregisterAudioDeviceCallback(deviceChanges);
        } catch (RuntimeException ignored) {
        }
      }
      if (track != null) {
        synchronized (run) {
          try {
            track.setVolume(0);
            track.removeOnRoutingChangedListener(routing);
            track.pause();
            track.flush();
          } catch (RuntimeException ignored) {
          }
          try {
            track.release();
          } catch (RuntimeException ignored) {
          }
          run.track = null;
        }
      }
      effects.reset();
      corrector.reset();
      java.util.Arrays.fill(block, 0);
      if (run.communicationRouted) {
        run.communicationRouted = false;
        try {
          audio.clearCommunicationDevice();
        } catch (RuntimeException ignored) {
        }
        try {
          audio.setMode(AudioManager.MODE_NORMAL);
        } catch (RuntimeException ignored) {
        }
      }
      outputOwner = null;
    }
  }
}
