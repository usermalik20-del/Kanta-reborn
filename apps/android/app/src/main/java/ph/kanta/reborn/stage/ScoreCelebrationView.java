package ph.kanta.reborn.stage;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

import ph.kanta.reborn.core.score.ScoreCelebrationPlan;

/**
 * Draws the deterministic celebration for a completed score.
 *
 * <p>All choreography comes from {@link ScoreCelebrationPlan}; this view only turns
 * normalised positions into pixels, so the behaviour is reproducible and unit-tested
 * without a device. The view never consumes touches and never announces itself to
 * accessibility services — it is decoration above the score card.
 */
public final class ScoreCelebrationView extends View {

  private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint balloonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint stringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path starPath = new Path();

  private ScoreCelebrationPlan.Plan plan;
  private float progress;
  private ValueAnimator animator;
  private boolean reducedMotion;

  public ScoreCelebrationView(Context context) {
    this(context, null);
  }

  public ScoreCelebrationView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);
    // Decoration must never intercept a tap meant for the score card or the stage.
    setClickable(false);
    setFocusable(false);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    particlePaint.setStyle(Paint.Style.FILL);
    balloonPaint.setStyle(Paint.Style.FILL);
    stringPaint.setStyle(Paint.Style.STROKE);
    stringPaint.setColor(0x66FFFFFF);
    stringPaint.setStrokeWidth(1f);
    reducedMotion = isReducedMotion(context);
  }

  private static boolean isReducedMotion(Context context) {
    float scale;
    try {
      scale =
          android.provider.Settings.Global.getFloat(
              context.getContentResolver(),
              android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
              1f);
    } catch (RuntimeException failure) {
      return false;
    }
    return scale == 0f;
  }

  /**
   * Starts the celebration for a score.
   *
   * @param overall the overall score, 0..100
   * @param seed a deterministic seed; callers pass a stable value per completion
   */
  public void celebrate(int overall, long seed) {
    cancel();
    plan = ScoreCelebrationPlan.forScore(overall, seed);
    if (!plan.celebrates()) {
      setVisibility(GONE);
      invalidate();
      return;
    }
    setVisibility(VISIBLE);
    if (reducedMotion) {
      // Honour "Reduce Motion": show the resolved end state without animating.
      progress = 1f;
      invalidate();
      return;
    }
    progress = 0f;
    animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(plan.durationMillis);
    animator.addUpdateListener(
        animation -> {
          progress = (float) animation.getAnimatedValue();
          invalidate();
        });
    animator.start();
  }

  /** Stops any running celebration and clears the decoration. */
  public void cancel() {
    if (animator != null) {
      animator.cancel();
      animator = null;
    }
    plan = null;
    progress = 0f;
    setVisibility(GONE);
    invalidate();
  }

  /** True while a celebration is on screen. */
  public boolean isCelebrating() {
    return plan != null && plan.celebrates() && getVisibility() == VISIBLE;
  }

  /** The tier currently being celebrated, or null. */
  public ScoreCelebrationPlan.Tier currentTier() {
    return plan == null ? null : plan.tier;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (plan == null || !plan.celebrates()) return;

    int width = getWidth();
    int height = getHeight();
    if (width <= 0 || height <= 0) return;
    float unit = Math.min(width, height);

    drawBalloons(canvas, width, height, unit);

    int index = 0;
    for (ScoreCelebrationPlan.Particle particle : plan.particles) {
      float x = ScoreCelebrationPlan.positionX(particle, progress) * width;
      float y = ScoreCelebrationPlan.positionY(particle, progress) * height;
      if (y < -unit * 0.1f || y > height + unit * 0.1f) {
        index++;
        continue;
      }
      boolean star = index >= plan.confettiCount;
      particlePaint.setColor(hueColor(particle.hue));
      float radius = Math.max(1f, particle.radius * unit);
      float rotation = ScoreCelebrationPlan.rotationDegrees(particle, progress);

      canvas.save();
      canvas.rotate(rotation, x, y);
      if (star) {
        drawStar(canvas, x, y, radius * 1.8f);
      } else {
        canvas.drawRect(x - radius, y - radius * 0.45f, x + radius, y + radius * 0.45f, particlePaint);
      }
      canvas.restore();
      index++;
    }
  }

  private void drawBalloons(Canvas canvas, int width, int height, float unit) {
    if (plan.balloonCount <= 0) return;
    float travel = progress * (height + unit * 0.6f);
    for (int index = 0; index < plan.balloonCount; index++) {
      float x = width * ((index + 0.5f) / plan.balloonCount);
      float y = height + unit * 0.2f - travel - index * unit * 0.08f;
      float radius = unit * 0.030f;
      int hue = (int) ((268f + index * 24f) % 360f);
      balloonPaint.setColor(hueColor(hue));
      canvas.drawCircle(x, y, radius, balloonPaint);
      canvas.drawLine(x, y + radius, x, y + radius * 2.2f, stringPaint);
    }
  }

  private void drawStar(Canvas canvas, float cx, float cy, float radius) {
    starPath.reset();
    for (int point = 0; point < 10; point++) {
      double angle = Math.PI / 5 * point - Math.PI / 2;
      float r = (point % 2 == 0) ? radius : radius * 0.45f;
      float px = (float) (cx + Math.cos(angle) * r);
      float py = (float) (cy + Math.sin(angle) * r);
      if (point == 0) starPath.moveTo(px, py);
      else starPath.lineTo(px, py);
    }
    starPath.close();
    canvas.drawPath(starPath, particlePaint);
  }

  private static int hueColor(float hue) {
    float normalised = ((hue % 360f) + 360f) % 360f;
    return Color.HSVToColor(new float[] {normalised, 0.72f, 1.0f});
  }

  @Override
  protected void onDetachedFromWindow() {
    cancel();
    super.onDetachedFromWindow();
  }

  @Override
  public String toString() {
    return String.format(
        Locale.ROOT, "ScoreCelebrationView[tier=%s, progress=%.2f]", currentTier(), progress);
  }
}
