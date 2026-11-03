package ph.kanta.reborn.core.score;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Deterministic celebration choreography for a completed score.
 *
 * <p>Framework-free on purpose: the plan is computed here and exercised by the host-JVM
 * {@code coreRegression} task, while the Android view only draws it. Nothing here reads a
 * clock or a view, so the same {@code (overall, seed)} always produces the same plan and
 * the choreography is reproducible in a test.
 *
 * <p>Bands come from {@link ScoreResultTier}, the single result ladder shared with the score
 * card and the reveal plan.
 */
public final class ScoreCelebrationPlan {

  /** Downward acceleration applied by {@link #positionY}, in normalised units per t^2. */
  public static final float GRAVITY = 0.9f;

  /** Hard ceiling so a plan can never ask a view for unbounded work. */
  public static final int MAX_PARTICLES = 160;

  /** One confetti or star particle in normalised stage space (0..1). */
  public static final class Particle {
    public final float originX;
    public final float originY;
    public final float velocityX;
    public final float velocityY;
    public final float radius;
    public final float spin;
    public final float hue;

    Particle(
        float originX,
        float originY,
        float velocityX,
        float velocityY,
        float radius,
        float spin,
        float hue) {
      this.originX = originX;
      this.originY = originY;
      this.velocityX = velocityX;
      this.velocityY = velocityY;
      this.radius = radius;
      this.spin = spin;
      this.hue = hue;
    }
  }

  /** The complete plan for one completed score. */
  public static final class Plan {
    public final ScoreResultTier tier;
    public final int confettiCount;
    public final int balloonCount;
    public final int starCount;
    public final int durationMillis;
    public final List<Particle> particles;

    Plan(
        ScoreResultTier tier,
        int confettiCount,
        int balloonCount,
        int starCount,
        int durationMillis,
        List<Particle> particles) {
      this.tier = tier;
      this.confettiCount = confettiCount;
      this.balloonCount = balloonCount;
      this.starCount = starCount;
      this.durationMillis = durationMillis;
      this.particles = Collections.unmodifiableList(particles);
    }

    /** True when this plan draws anything at all. */
    public boolean celebrates() {
      return !particles.isEmpty();
    }
  }

  private ScoreCelebrationPlan() {}

  /** Maps a presented score (50..100) onto a result band. */
  public static ScoreResultTier tierFor(int overall) {
    return ScoreResultTier.fromScore(overall);
  }

  /**
   * Builds the celebration plan for a score.
   *
   * @param overall the presented score, clamped to 0..100
   * @param seed a deterministic seed; the same seed yields the same plan
   */
  public static Plan forScore(int overall, long seed) {
    int clamped = Math.max(0, Math.min(100, overall));
    ScoreResultTier tier = tierFor(clamped);
    int confetti = confettiFor(tier);
    int balloons = balloonsFor(tier);
    int stars = starsFor(tier);
    int duration = durationFor(tier);

    List<Particle> particles = new ArrayList<>(confetti + stars);
    Random random = new Random(seed);
    float baseHue = baseHueFor(tier);
    for (int index = 0; index < confetti + stars; index++) {
      boolean star = index >= confetti;
      // Confetti falls from the top edge; stars burst from the score card centre.
      float originX = star ? 0.5f : random.nextFloat();
      float originY = star ? 0.5f : 0f;
      float velocityX = star ? (random.nextFloat() - 0.5f) * 1.6f : (random.nextFloat() - 0.5f) * 0.5f;
      float velocityY = star ? (random.nextFloat() - 0.5f) * 1.6f : 0.4f + random.nextFloat() * 0.6f;
      float radius = star ? 0.010f + random.nextFloat() * 0.014f : 0.004f + random.nextFloat() * 0.010f;
      float spin = (random.nextFloat() - 0.5f) * 2f;
      float hue = (baseHue + random.nextFloat() * 40f) % 360f;
      particles.add(new Particle(originX, originY, velocityX, velocityY, radius, spin, hue));
    }

    return new Plan(tier, confetti, balloons, stars, duration, particles);
  }

  /** Horizontal position of a particle at normalised progress {@code t} (0..1). */
  public static float positionX(Particle particle, float t) {
    return particle.originX + particle.velocityX * t;
  }

  /** Vertical position of a particle at normalised progress {@code t} (0..1). */
  public static float positionY(Particle particle, float t) {
    return particle.originY + particle.velocityY * t + 0.5f * GRAVITY * t * t;
  }

  /** Rotation in degrees of a particle at normalised progress {@code t} (0..1). */
  public static float rotationDegrees(Particle particle, float t) {
    return particle.spin * t * 360f;
  }

  private static int confettiFor(ScoreResultTier tier) {
    switch (tier) {
      case EXCELLENT_SINGER:
        return 120;
      case GREAT_SINGER:
        return 72;
      case GOOD_SINGER:
        return 36;
      case RISING_STAR:
        return 24;
      default:
        return 0;
    }
  }

  private static int balloonsFor(ScoreResultTier tier) {
    switch (tier) {
      case EXCELLENT_SINGER:
        return 6;
      case GREAT_SINGER:
        return 3;
      case GOOD_SINGER:
        return 1;
      default:
        return 0;
    }
  }

  private static int starsFor(ScoreResultTier tier) {
    switch (tier) {
      case EXCELLENT_SINGER:
        return 5;
      case GREAT_SINGER:
        return 3;
      case GOOD_SINGER:
        return 1;
      case RISING_STAR:
        return 2;
      default:
        return 0;
    }
  }

  private static int durationFor(ScoreResultTier tier) {
    switch (tier) {
      case EXCELLENT_SINGER:
        return 4200;
      case GREAT_SINGER:
        return 3200;
      case GOOD_SINGER:
        return 2200;
      case RISING_STAR:
        return 1600;
      default:
        return 0;
    }
  }

  private static float baseHueFor(ScoreResultTier tier) {
    switch (tier) {
      case EXCELLENT_SINGER:
        return 42f; // gold
      case GREAT_SINGER:
        return 186f; // cyan
      case GOOD_SINGER:
        return 268f; // violet
      case RISING_STAR:
        return 210f; // blue
      default:
        return 0f;
    }
  }
}
