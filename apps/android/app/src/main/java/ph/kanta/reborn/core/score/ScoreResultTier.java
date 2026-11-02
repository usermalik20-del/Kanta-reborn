package ph.kanta.reborn.core.score;

/**
 * The five result bands a finished song can land in.
 *
 * <p>One ladder for the whole app: the label on the score card, the choreography the
 * celebration view plays and the sound cue the reveal queues all read this type, so a band can
 * never mean two different things on two surfaces.
 *
 * <p>The presented score is floored at {@link #FLOOR}: a singer who scores badly still sees a
 * number rather than a zero. The honest underlying pitch and coverage figures stay available
 * separately in the score breakdown, so the floor changes presentation, not measurement.
 */
public enum ScoreResultTier {
  EXCELLENT_SINGER("Excellent Singer", 100),
  GREAT_SINGER("Great Singer", 90),
  GOOD_SINGER("Good Singer", 80),
  RISING_STAR("Rising Star", 65),
  KEEP_PRACTICING("Keep Practicing", 50);

  /** Lowest score a singer can be shown. */
  public static final int FLOOR = 50;
  /** Highest score, reached only by a perfect performance. */
  public static final int CEILING = 100;

  private final String label;
  private final int minimum;

  ScoreResultTier(String label, int minimum) {
    this.label = label;
    this.minimum = minimum;
  }

  /** The user-facing band name. */
  public String label() {
    return label;
  }

  /** Lowest presented score in this band. */
  public int minimum() {
    return minimum;
  }

  /** Whether this band earns confetti. A struggling performance gets a cue, not a party. */
  public boolean celebrates() {
    return this != KEEP_PRACTICING;
  }

  /** Maps a raw 0..100 estimate onto the presented scale, floored at {@link #FLOOR}. */
  public static int present(double rawOverall) {
    double finite = Double.isFinite(rawOverall) ? rawOverall : 0;
    int rounded = (int) Math.round(Math.max(0, Math.min(100, finite)));
    return Math.max(FLOOR, rounded);
  }

  /** The band a presented score belongs to. */
  public static ScoreResultTier fromScore(int presented) {
    int clamped = Math.max(0, Math.min(100, presented));
    for (ScoreResultTier tier : values()) {
      if (clamped >= tier.minimum) return tier;
    }
    return KEEP_PRACTICING;
  }
}
