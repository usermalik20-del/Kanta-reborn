package ph.kanta.reborn.core;

/** Framework-free timing for starting a countdown against the first sung melody cue. */
public final class MelodyStartPlan {
  public static final class Schedule {
    /** MIDI with a known positive melody cue starts its intro immediately. */
    public final boolean startAudioImmediately;
    /** False for short intros and zero-length countdown settings. */
    public final boolean showCountdown;
    /** Absolute elapsed-realtime at which the countdown should appear. */
    public final long countdownAtMillis;

    Schedule(boolean startAudioImmediately, boolean showCountdown, long countdownAtMillis) {
      this.startAudioImmediately = startAudioImmediately;
      this.showCountdown = showCountdown;
      this.countdownAtMillis = countdownAtMillis;
    }
  }

  /**
   * A long MIDI intro plays first; its countdown begins one countdown-length before the melody.
   * Unknown melody zero retains the legacy countdown-before-audio behavior.
   */
  public static Schedule at(long melodyStartMillis, int countdownSeconds, long now) {
    long melody = Math.max(0, melodyStartMillis);
    long duration = Math.max(0, countdownSeconds) * 1000L;
    if (melody == 0) return new Schedule(false, duration > 0, now);
    if (duration == 0) return new Schedule(true, false, now);
    boolean show = melody > duration;
    return new Schedule(true, show, show ? now + melody - duration : now);
  }

  private MelodyStartPlan() {}
}
