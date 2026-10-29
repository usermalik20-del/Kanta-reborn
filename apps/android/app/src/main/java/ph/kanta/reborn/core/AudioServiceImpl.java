package ph.kanta.reborn.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Turns the shared pitch estimate into {@link PitchData} for the pitch-based features.
 *
 * <p>This is a reader, not a capturer. It samples the single microphone owner through a
 * {@link PitchSource} on a worker and publishes {@link PitchData} to its listeners, so the
 * Pitch Meter Game and the Learning Assistant both observe the same estimate without either
 * of them touching {@code AudioRecord}.
 *
 * <p>Framework-free: it depends only on the JDK and on {@link PitchSource}, so
 * {@code coreRegression} can drive it with a fake source.
 */
public class AudioServiceImpl implements AudioService {

  /** Sampling period. 20 Hz is smooth enough for a meter and cheap enough to ignore. */
  private static final long POLL_PERIOD_MS = 50;

  private final PitchSource source;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final List<AudioListener> listeners = new CopyOnWriteArrayList<>();

  private volatile PitchData latestPitchData;
  private volatile boolean listening;
  private volatile float targetPitchHz = 440f;

  public AudioServiceImpl(PitchSource source) {
    if (source == null) throw new IllegalArgumentException("pitch source is required");
    this.source = source;
  }

  /** Sets the note the singer is being compared against. */
  public void setTargetPitch(float targetPitchHz) {
    this.targetPitchHz = targetPitchHz;
  }

  @Override
  public void startListening() {
    if (listening) return;
    listening = true;
    executor.execute(
        () -> {
          try {
            notifyListeningStarted();
            while (listening) {
              float detected = source.currentPitchHz();
              if (detected > 0) {
                PitchData data =
                    new PitchData(
                        detected,
                        targetPitchHz,
                        accuracy(detected, targetPitchHz),
                        source.measuredAtMs());
                latestPitchData = data;
                for (AudioListener listener : listeners) listener.onPitchDetected(data);
              }
              Thread.sleep(POLL_PERIOD_MS);
            }
          } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
          } catch (RuntimeException failure) {
            for (AudioListener listener : listeners) {
              listener.onError(String.valueOf(failure.getMessage()));
            }
          } finally {
            listening = false;
            notifyListeningStopped();
          }
        });
  }

  @Override
  public void stopListening() {
    listening = false;
  }

  @Override
  public boolean isListening() {
    return listening;
  }

  @Override
  public void addListener(AudioListener listener) {
    if (listener != null && !listeners.contains(listener)) listeners.add(listener);
  }

  @Override
  public void removeListener(AudioListener listener) {
    listeners.remove(listener);
  }

  @Override
  public PitchData getLatestPitchData() {
    return latestPitchData;
  }

  /** Stops listening and releases the worker. The service cannot be restarted after this. */
  public void shutdown() {
    stopListening();
    executor.shutdown();
  }

  /** Maps a cents deviation onto a 0..1 score, matching the scoring bands used elsewhere. */
  private static float accuracy(float detectedHz, float targetHz) {
    if (detectedHz <= 0 || targetHz <= 0) return 0f;
    int cents = Math.abs(Math.round(1200f * (float) Math.log(detectedHz / targetHz)));
    if (cents <= 5) return 1.0f;
    if (cents <= 20) return 0.8f;
    if (cents <= 50) return 0.5f;
    if (cents <= 100) return 0.2f;
    return 0f;
  }

  private void notifyListeningStarted() {
    for (AudioListener listener : listeners) listener.onListeningStarted();
  }

  private void notifyListeningStopped() {
    for (AudioListener listener : listeners) listener.onListeningStopped();
  }
}
