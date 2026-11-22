package ph.kanta.reborn.core;

import java.util.UUID;

/** One playback identity, full target coverage, and constant-size observation aggregation. */
public final class MelodyScoreSession {
  public static final class Estimate {
    public final double overall, pitchAccuracy, coverage;
    public final int matchedSlots;
    public final boolean reportable;

    /** Neutral breakdown for a completed song with no usable singing estimate. */
    public static Estimate noSinging() {
      return new Estimate(50, 50, 0);
    }

    Estimate(double pitchAccuracy, double coverage, int matchedSlots) {
      this.pitchAccuracy = pitchAccuracy;
      this.coverage = coverage;
      this.matchedSlots = matchedSlots;
      overall = pitchAccuracy * .85 + coverage * .15;
      reportable = matchedSlots >= 20 && coverage >= 10 && Double.isFinite(overall);
    }
  }

  public static final class Completion {
    public final UUID playbackId;
    public final Estimate estimate;

    Completion(UUID playbackId, Estimate estimate) {
      this.playbackId = playbackId;
      this.estimate = estimate;
    }
  }

  private final UUID playbackId;
  private final MelodyTarget target;
  private final int key, interval;
  private final long expectedSlots;
  private boolean closed;
  private long lastTime = -1, lastSlot = -1;
  private int lastWindow = -1, matched;
  private double sum, bestConfidence, bestAccuracy;

  public MelodyScoreSession(UUID playbackId, MelodyTarget target, int key, double tempo) {
    if (playbackId == null
        || target == null
        || !target.available()
        || key < -12
        || key > 12
        || !Double.isFinite(tempo)
        || tempo < .5
        || tempo > 2) throw new IllegalArgumentException();
    this.playbackId = playbackId;
    this.target = target;
    this.key = key;
    interval = (int) Math.round(200 * tempo);
    long expected = 0;
    for (MelodyTarget.Window window : target.windows)
      expected += (window.end - window.start - 1) / interval + 1;
    expectedSlots = expected;
  }

  /** Arrival/frame timing is an estimate; physical input delay requires user calibration. */
  public static long align(
      long frameCenter, long now, long sourcePosition, double tempo, int latency) {
    if (frameCenter < 0
        || now < frameCenter
        || sourcePosition < 0
        || sourcePosition > 21600000
        || !Double.isFinite(tempo)
        || tempo < .5
        || tempo > 2
        || latency < -500
        || latency > 1500) return -1;
    long age = now - frameCenter;
    if (age < 0 || age > 2000) return -1;
    double position = sourcePosition - (age + latency) * tempo;
    return Double.isFinite(position) && position >= 0 && position <= 21600000
        ? (long) position
        : -1;
  }

  public synchronized boolean observe(
      UUID identity, long position, double frequency, double confidence, double rms) {
    if (closed
        || !playbackId.equals(identity)
        || position < 0
        || position > 21600000
        || position < lastTime
        || !Double.isFinite(frequency)
        || frequency < 70
        || frequency > 1000
        || !Double.isFinite(confidence)
        || confidence < .8
        || confidence > 1
        || !Double.isFinite(rms)
        || rms < .008
        || rms > 1) return false;
    int low = 0, high = target.windows.size() - 1, index = -1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      if (target.windows.get(middle).start <= position) {
        index = middle;
        low = middle + 1;
      } else high = middle - 1;
    }
    lastTime = position;
    if (index < 0) return false;
    MelodyTarget.Window window = target.windows.get(index);
    if (position >= window.end) return false;
    long slot = (position - window.start) / interval;
    double observedNote = 69 + 12 * Math.log(frequency / 440) / Math.log(2);
    double accuracy = Math.max(0, 1 - Math.abs(observedNote - (window.note + key)));
    if (index == lastWindow && slot == lastSlot) {
      if (confidence <= bestConfidence) return false;
      sum += accuracy - bestAccuracy;
    } else {
      matched++;
      sum += accuracy;
    }
    bestConfidence = confidence;
    bestAccuracy = accuracy;
    lastWindow = index;
    lastSlot = slot;
    return true;
  }

  public synchronized Estimate estimate() {
    double pitch = matched == 0 ? 0 : Math.max(0, Math.min(100, sum / matched * 100));
    double coverage = expectedSlots == 0 ? 0 : Math.min(100, matched * 100.0 / expectedSlots);
    return new Estimate(pitch, coverage, matched);
  }

  public synchronized Completion finish(UUID identity) {
    if (closed || !playbackId.equals(identity)) return null;
    closed = true;
    return new Completion(playbackId, estimate());
  }

  public synchronized void cancel() {
    closed = true;
  }
}
