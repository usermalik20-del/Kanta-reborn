package ph.kanta.reborn.core.score;

import ph.kanta.reborn.core.PartySounds;

/**
 * Domain regression checks for the score-reveal choreography.
 *
 * <p>Framework-free: runs on a host JVM inside {@code coreRegression}, so the reveal timing and
 * the per-band sound cue are asserted without a device, an emulator or a clock.
 */
public final class ScoreRevealRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static void drumrollPrecedesEveryReveal() {
    check(
        ScoreRevealPlan.drumrollSlot() == PartySounds.DRUM_ROLL,
        "The reveal opens on the drum roll, not some other pad");
    check(ScoreRevealPlan.DRUMROLL_MILLIS > 0, "A drum roll takes measurable time");
    check(
        ScoreRevealPlan.DRUMROLL_MILLIS > ScoreRevealPlan.COUNT_UP_MILLIS,
        "The drum roll is the longest beat of the reveal");
  }

  private static void eachBandGetsItsOwnCue() {
    check(
        ScoreRevealPlan.revealSlot(ScoreResultTier.EXCELLENT_SINGER) == PartySounds.TA_DA,
        "A perfect performance lands on the fanfare");
    check(
        ScoreRevealPlan.revealSlot(ScoreResultTier.GREAT_SINGER) == PartySounds.APPLAUSE,
        "A great performance lands on applause");
    check(
        ScoreRevealPlan.revealSlot(ScoreResultTier.GOOD_SINGER) == PartySounds.APPLAUSE,
        "A good performance lands on applause");
    check(
        ScoreRevealPlan.revealSlot(ScoreResultTier.RISING_STAR) == PartySounds.APPLAUSE,
        "A rising star lands on applause");
    check(
        ScoreRevealPlan.revealSlot(ScoreResultTier.KEEP_PRACTICING) == -1,
        "A struggling singer is not mocked with a celebration cue");
    check(ScoreRevealPlan.revealSlot(null) == -1, "An absent band earns no cue");
  }

  private static void totalIsTheSumOfItsStages() {
    for (ScoreResultTier tier : ScoreResultTier.values()) {
      check(
          ScoreRevealPlan.totalMillis(tier)
              == ScoreRevealPlan.DRUMROLL_MILLIS
                  + ScoreRevealPlan.COUNT_UP_MILLIS
                  + ScoreRevealPlan.LABEL_MILLIS,
          "Reveal length is the drum roll, the count-up and the label for " + tier);
    }
  }

  private static void counterEndsExactlyOnTheScore() {
    int[] values = ScoreRevealPlan.counts(50, 100, 12);
    check(values.length == 12, "The counter produces one value per tick");
    check(values[0] == 50, "The counter starts on the floor");
    check(values[11] == 100, "The counter ends exactly on the score");
    for (int index = 1; index < values.length; index++) {
      check(
          values[index] >= values[index - 1],
          "The counter never counts backwards at tick " + index);
    }
    int[] single = ScoreRevealPlan.counts(50, 90, 1);
    check(single.length == 1 && single[0] == 90, "A one-tick reveal shows the final score");
  }

  private static void countUpDecelerates() {
    // Cubic ease-out: the first half of the time covers most of the distance.
    int from = 50;
    int to = 100;
    int half = ScoreRevealPlan.value(from, to, .5f);
    check(half > (from + to) / 2, "The count-up front-loads its progress");
    check(ScoreRevealPlan.value(from, to, 0f) == from, "Progress zero is the floor");
    check(ScoreRevealPlan.value(from, to, 1f) == to, "Progress one is the score");
  }

  private static void progressIsClampedAndFinite() {
    check(ScoreRevealPlan.value(50, 100, -5f) == 50, "Negative progress clamps to the floor");
    check(ScoreRevealPlan.value(50, 100, 5f) == 100, "Progress above one clamps to the score");
    check(
        ScoreRevealPlan.value(50, 100, Float.NaN) == 50,
        "An undefined progress cannot corrupt the counter");
    check(
        ScoreRevealPlan.value(50, 100, Float.POSITIVE_INFINITY) == 50,
        "A non-finite progress falls back to the floor rather than jumping to the score");
    check(
        ScoreRevealPlan.value(50, 100, Float.NEGATIVE_INFINITY) == 50,
        "Negative infinity is treated as zero progress");
    boolean rejected = false;
    try {
      ScoreRevealPlan.counts(50, 100, 0);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    check(rejected, "A reveal with no ticks is refused");
  }

  public static int run() {
    int before = checks;
    drumrollPrecedesEveryReveal();
    eachBandGetsItsOwnCue();
    totalIsTheSumOfItsStages();
    counterEndsExactlyOnTheScore();
    countUpDecelerates();
    progressIsClampedAndFinite();
    return checks - before;
  }
}
