package ph.kanta.reborn.core.score;

import ph.kanta.reborn.core.PartySounds;

/**
 * The choreography of the score reveal: a drum roll, then the card, then a cue.
 *
 * <p>Framework-free and free of clocks, so the host-JVM {@code coreRegression} task can assert
 * the timing and the cue choice without a device. The stage only has to run this plan.
 */
public final class ScoreRevealPlan {

  /** How long the drum roll plays before the score card appears. */
  public static final int DRUMROLL_MILLIS = 1900;
  /** How long the score counts up from the floor to its final value. */
  public static final int COUNT_UP_MILLIS = 900;
  /** How long the band label takes to arrive after the counter stops. */
  public static final int LABEL_MILLIS = 420;

  /** The effect cue that plays before any score is shown. */
  public static int drumrollSlot() {
    return PartySounds.DRUM_ROLL;
  }

  /**
   * The effect cue that lands with the score card.
   *
   * @return a party-effect slot, or {@code -1} when this band earns no cue
   */
  public static int revealSlot(ScoreResultTier tier) {
    if (tier == null) return -1;
    switch (tier) {
      case EXCELLENT_SINGER:
        return PartySounds.TA_DA;
      case GREAT_SINGER:
      case GOOD_SINGER:
      case RISING_STAR:
        return PartySounds.APPLAUSE;
      default:
        return -1;
    }
  }

  /** Total on-screen time for a reveal of this band. */
  public static int totalMillis(ScoreResultTier tier) {
    return DRUMROLL_MILLIS + COUNT_UP_MILLIS + LABEL_MILLIS;
  }

  /** Eased score value at normalised progress, so the count-up decelerates into its result. */
  public static int value(int from, int to, float progress) {
    float clamped = Math.max(0f, Math.min(1f, Float.isFinite(progress) ? progress : 0f));
    float remaining = 1f - clamped;
    float eased = 1f - remaining * remaining * remaining;
    return from + Math.round((to - from) * eased);
  }

  /**
   * The values a counter shows over {@code ticks} steps. Starts at {@code from}, ends exactly at
   * {@code to}, and never moves backwards, which is what the regression test asserts.
   */
  public static int[] counts(int from, int to, int ticks) {
    if (ticks < 1) throw new IllegalArgumentException("Need at least one tick");
    int[] values = new int[ticks];
    for (int index = 0; index < ticks; index++) {
      float progress = ticks == 1 ? 1f : index / (float) (ticks - 1);
      int value = value(from, to, progress);
      if (index > 0 && value < values[index - 1]) value = values[index - 1];
      if (index == ticks - 1) value = to;
      values[index] = value;
    }
    return values;
  }

  private ScoreRevealPlan() {}
}
