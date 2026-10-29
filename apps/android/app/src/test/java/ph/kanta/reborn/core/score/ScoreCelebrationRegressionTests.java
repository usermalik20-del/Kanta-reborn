package ph.kanta.reborn.core.score;

import java.util.List;

/**
 * Domain regression checks for the deterministic score-celebration choreography.
 *
 * <p>No framework dependency: runs on a host JVM inside {@code coreRegression}.
 */
public final class ScoreCelebrationRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static void tierBoundaries() {
    check(
        ScoreCelebrationPlan.tierFor(100) == ScoreCelebrationPlan.Tier.PERFECT,
        "100 is Perfect");
    check(
        ScoreCelebrationPlan.tierFor(95) == ScoreCelebrationPlan.Tier.PERFECT,
        "95 is Perfect");
    check(
        ScoreCelebrationPlan.tierFor(94) == ScoreCelebrationPlan.Tier.GREAT,
        "94 is Great");
    check(
        ScoreCelebrationPlan.tierFor(85) == ScoreCelebrationPlan.Tier.GREAT,
        "85 is Great");
    check(
        ScoreCelebrationPlan.tierFor(84) == ScoreCelebrationPlan.Tier.GOOD,
        "84 is Good");
    check(
        ScoreCelebrationPlan.tierFor(70) == ScoreCelebrationPlan.Tier.GOOD,
        "70 is Good");
    check(
        ScoreCelebrationPlan.tierFor(69) == ScoreCelebrationPlan.Tier.MISS,
        "69 is Miss");
    check(ScoreCelebrationPlan.tierFor(0) == ScoreCelebrationPlan.Tier.MISS, "0 is Miss");
  }

  private static void outOfRangeScoresAreClamped() {
    check(
        ScoreCelebrationPlan.forScore(-25, 1).tier == ScoreCelebrationPlan.Tier.MISS,
        "Negative score clamps to Miss");
    check(
        ScoreCelebrationPlan.forScore(250, 1).tier == ScoreCelebrationPlan.Tier.PERFECT,
        "Score above 100 clamps to Perfect");
    check(
        ScoreCelebrationPlan.forScore(-25, 1).confettiCount == 0,
        "Negative score draws no confetti");
  }

  private static void missNeverCelebrates() {
    ScoreCelebrationPlan.Plan plan = ScoreCelebrationPlan.forScore(40, 7);
    check(!plan.celebrates(), "Miss does not celebrate");
    check(plan.particles.isEmpty(), "Miss has no particles");
    check(plan.durationMillis == 0, "Miss has no duration");
    check(
        plan.confettiCount == 0 && plan.balloonCount == 0 && plan.starCount == 0,
        "Miss schedules no confetti, balloons or stars");
  }

  private static void higherTiersCelebrateMore() {
    ScoreCelebrationPlan.Plan perfect = ScoreCelebrationPlan.forScore(100, 11);
    ScoreCelebrationPlan.Plan great = ScoreCelebrationPlan.forScore(90, 11);
    ScoreCelebrationPlan.Plan good = ScoreCelebrationPlan.forScore(75, 11);
    check(perfect.celebrates() && great.celebrates() && good.celebrates(), "Tiers celebrate");
    check(
        perfect.particles.size() > great.particles.size()
            && great.particles.size() > good.particles.size(),
        "Particle count increases with tier");
    check(
        perfect.durationMillis > great.durationMillis
            && great.durationMillis > good.durationMillis,
        "Duration increases with tier");
    check(
        perfect.balloonCount > great.balloonCount && great.balloonCount > good.balloonCount,
        "Balloon count increases with tier");
  }

  private static void sameSeedIsReproducible() {
    ScoreCelebrationPlan.Plan first = ScoreCelebrationPlan.forScore(97, 4242);
    ScoreCelebrationPlan.Plan second = ScoreCelebrationPlan.forScore(97, 4242);
    check(first.tier == second.tier, "Reproducible tier");
    check(first.particles.size() == second.particles.size(), "Reproducible particle count");
    boolean identical = true;
    for (int index = 0; index < first.particles.size(); index++) {
      ScoreCelebrationPlan.Particle a = first.particles.get(index);
      ScoreCelebrationPlan.Particle b = second.particles.get(index);
      if (a.originX != b.originX
          || a.originY != b.originY
          || a.velocityX != b.velocityX
          || a.velocityY != b.velocityY
          || a.radius != b.radius
          || a.spin != b.spin
          || a.hue != b.hue) {
        identical = false;
        break;
      }
    }
    check(identical, "Same seed yields an identical plan");
  }

  private static void differentSeedChangesChoreography() {
    ScoreCelebrationPlan.Plan first = ScoreCelebrationPlan.forScore(97, 1);
    ScoreCelebrationPlan.Plan second = ScoreCelebrationPlan.forScore(97, 2);
    check(
        first.particles.size() == second.particles.size(),
        "Seed does not change the particle count");
    boolean differs = false;
    for (int index = 0; index < first.particles.size(); index++) {
      if (first.particles.get(index).originX != second.particles.get(index).originX) {
        differs = true;
        break;
      }
    }
    check(differs, "Different seeds yield different placement");
  }

  private static void planStaysWithinBounds() {
    for (long seed = 0; seed < 25; seed++) {
      for (int score = 0; score <= 100; score += 5) {
        ScoreCelebrationPlan.Plan plan = ScoreCelebrationPlan.forScore(score, seed);
        check(
            plan.particles.size() <= ScoreCelebrationPlan.MAX_PARTICLES,
            "Particle budget respected at score " + score + " seed " + seed);
        check(plan.durationMillis >= 0, "Non-negative duration");
        for (ScoreCelebrationPlan.Particle particle : plan.particles) {
          check(
              particle.radius > 0f && particle.radius < 0.05f,
              "Particle radius bounded at score " + score);
          check(
              particle.hue >= 0f && particle.hue < 360f,
              "Hue within one turn at score " + score);
          check(
              particle.originX >= 0f && particle.originX <= 1f,
              "Origin X inside the stage at score " + score);
          check(
              particle.originY >= 0f && particle.originY <= 1f,
              "Origin Y inside the stage at score " + score);
          if (plan.tier != ScoreCelebrationPlan.Tier.MISS && particle.originY == 0f) {
            check(
                particle.velocityY > 0f,
                "Falling confetti never starts moving upward at score " + score);
          }
        }
      }
    }
  }

  private static void starBurstStartsAtTheCardCentre() {
    ScoreCelebrationPlan.Plan plan = ScoreCelebrationPlan.forScore(100, 9);
    int starsSeen = 0;
    for (int index = plan.confettiCount; index < plan.particles.size(); index++) {
      ScoreCelebrationPlan.Particle star = plan.particles.get(index);
      check(
          star.originX == 0.5f && star.originY == 0.5f,
          "Star burst originates at the score card centre");
      starsSeen++;
    }
    check(starsSeen == plan.starCount, "Star count matches the planned burst");
  }

  private static void motionIsDeterministicAndBounded() {
    ScoreCelebrationPlan.Plan plan = ScoreCelebrationPlan.forScore(100, 3);
    ScoreCelebrationPlan.Particle particle = plan.particles.get(0);
    check(
        ScoreCelebrationPlan.positionX(particle, 0f) == particle.originX,
        "Motion starts at the origin in X");
    check(
        ScoreCelebrationPlan.positionY(particle, 0f) == particle.originY,
        "Motion starts at the origin in Y");
    check(
        ScoreCelebrationPlan.positionX(particle, 0.5f)
            == ScoreCelebrationPlan.positionX(particle, 0.5f),
        "Motion is deterministic");
    check(
        ScoreCelebrationPlan.rotationDegrees(particle, 0f) == 0f,
        "Rotation starts at zero");
    check(
        Math.abs(ScoreCelebrationPlan.rotationDegrees(particle, 1f)) <= 360f,
        "Rotation stays within one turn");
    // Gravity must dominate a constant upward drift over the full sweep.
    float end = ScoreCelebrationPlan.positionY(particle, 1f);
    float linear = particle.originY + particle.velocityY;
    check(end > linear, "Gravity bends the path downward");
  }

  private static void particlesAreImmutable() {
    ScoreCelebrationPlan.Plan plan = ScoreCelebrationPlan.forScore(90, 5);
    boolean rejected = false;
    try {
      plan.particles.add(plan.particles.get(0));
    } catch (UnsupportedOperationException expected) {
      rejected = true;
    }
    check(rejected, "Particle list is unmodifiable");
  }

  /** Runs every score-celebration regression check. */
  public static int run() {
    checks = 0;
    tierBoundaries();
    outOfRangeScoresAreClamped();
    missNeverCelebrates();
    higherTiersCelebrateMore();
    sameSeedIsReproducible();
    differentSeedChangesChoreography();
    planStaysWithinBounds();
    starBurstStartsAtTheCardCentre();
    motionIsDeterministicAndBounded();
    particlesAreImmutable();
    return checks;
  }
}
