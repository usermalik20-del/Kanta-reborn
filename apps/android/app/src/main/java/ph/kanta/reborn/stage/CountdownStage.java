package ph.kanta.reborn.stage;

import ph.kanta.reborn.stage.StagePresenter.CountdownListener;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Compact countdown: "Ready ⏱️" -> 1 -> 2 -> ... -> N -> "Kanta 🎤".
 *
 * <p>Completion requests playback; the final cue stays visible until the owner dismisses it on
 * start acknowledgement, failure, or cancellation.
 */
public final class CountdownStage extends View {

  public static final int BADGE_SIZE_DP = 152;
  private static final int RING_STROKE_DP = 3;
  private static final int STEP_DURATION_MS = 800;
  private static final int REDUCED_STEP_MS = 500;
  private static final int ORBIT_DOTS = 12;

  private final RectF ring = new RectF();
  private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ringTrackPaint;
  private final Paint ringProgressPaint;
  private final Paint textPaint;
  private final Paint subTextPaint;
  private final Paint glowPaint;
  private final Paint dotPaint;
  private final int ringStrokePx;
  private final Typeface numberTypeface = Typeface.create("sans-serif-black", Typeface.BOLD);
  private final Typeface labelTypeface = Typeface.DEFAULT;

  private ValueAnimator currentAnimator;
  private CountdownListener listener;
  private boolean reducedMotion = false;
  private boolean running = false;
  /** The vocal-countdown visual can be switched off from the lyrics editor; the
   *  countdown itself (animator and listener) always keeps running. */
  private boolean visualsEnabled = true;

  public void setVisualsEnabled(boolean enabled) {
    if (visualsEnabled == enabled) return;
    visualsEnabled = enabled;
    invalidate();
  }

  private int maxSeconds = 4;
  private int currentStep = 0; // counts down: maxSeconds..1, then 0 = "Go"
  private float stepProgress = 0f;

  private final Runnable reducedStep =
      new Runnable() {
        @Override
        public void run() {
          if (!running) return;
          currentStep--; // count down
          stepProgress = 0f;
          scheduleStep();
        }
      };

  public CountdownStage(Context context) {
    this(context, null);
  }

  public CountdownStage(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);

    float density = context.getResources().getDisplayMetrics().density;
    ringStrokePx = (int) (RING_STROKE_DP * density);

    ringTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    ringTrackPaint.setStyle(Paint.Style.STROKE);
    ringTrackPaint.setStrokeWidth(ringStrokePx);
    ringTrackPaint.setColor(0x4D1A1A1A);
    ringTrackPaint.setStrokeCap(Paint.Cap.ROUND);

    ringProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    ringProgressPaint.setStyle(Paint.Style.STROKE);
    ringProgressPaint.setStrokeWidth(ringStrokePx);
    ringProgressPaint.setStrokeCap(Paint.Cap.ROUND);

    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setTextAlign(Paint.Align.CENTER);
    textPaint.setFakeBoldText(true);

    subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    subTextPaint.setTextSize(20 * density);
    subTextPaint.setColor(0xFFE9FFFF);
    subTextPaint.setTextAlign(Paint.Align.CENTER);

    glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    glowPaint.setStyle(Paint.Style.FILL);

    dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    dotPaint.setStyle(Paint.Style.FILL);

    reducedMotion = isReducedMotionEnabled(context);
  }

  private boolean isReducedMotionEnabled(Context context) {
    float scale =
        android.provider.Settings.Global.getFloat(
            context.getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f);
    return scale == 0f;
  }

  /** Starts the countdown. A non-positive request falls back to the KANTA default of four. */
  public void start(int countdownSeconds, CountdownListener listener) {
    cancel();
    this.listener = listener;
    this.maxSeconds =
        countdownSeconds <= 0 ? 4 : Math.min(9, Math.max(1, countdownSeconds));
    this.currentStep = this.maxSeconds; // start counting down from maxSeconds
    this.stepProgress = 0f;
    this.running = true;
    setVisibility(VISIBLE);
    invalidate();
    scheduleStep();
  }

  private void scheduleStep() {
    if (!running) return;
    if (currentStep < 0) {
      finish();
      return;
    }
    if (currentStep >= 1 && currentStep <= maxSeconds && listener != null) {
      // Contract keeps "remaining" semantics: countdown ticks report remaining seconds.
      listener.onTick(currentStep);
    }
    if (reducedMotion) {
      invalidate();
      postDelayed(reducedStep, REDUCED_STEP_MS);
      return;
    }

    if (currentAnimator != null) {
      currentAnimator.cancel();
      currentAnimator = null;
    }
    currentAnimator = ValueAnimator.ofFloat(0f, 1f);
    currentAnimator.setDuration(STEP_DURATION_MS);
    currentAnimator.setInterpolator(new DecelerateInterpolator());
    currentAnimator.addUpdateListener(
        animation -> {
          stepProgress = (float) animation.getAnimatedValue();
          invalidate();
        });
    currentAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            if (!running) return;
            currentAnimator = null;
            currentStep--; // count down
            stepProgress = 0f;
            scheduleStep();
          }
        });
    currentAnimator.start();
  }

  private void finish() {
    running = false;
    currentStep = -1; // final "Go" step
    stepProgress = 1f;
    invalidate();
    CountdownListener finished = listener;
    listener = null;
    if (finished != null) finished.onComplete();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w <= 0 || h <= 0) return;
    float cx = w / 2f, cy = h / 2f;
    float density = getResources().getDisplayMetrics().density;
    float radius = Math.min(46f * density, Math.min(w, h) * 0.30f);
    badgePaint.setColor(0xF00A0614);
    ring.set(cx - radius, cy - radius, cx + radius, cy + radius);
    glowPaint.setShader(
        new LinearGradient(
            ring.left,
            ring.top,
            ring.right,
            ring.bottom,
            new int[] {0x55A24BFF, 0x3300F0FF},
            null,
            Shader.TileMode.CLAMP));
    ringProgressPaint.setShader(
        new LinearGradient(
            ring.left,
            ring.top,
            ring.right,
            ring.bottom,
            new int[] {0xFFFF3D8B, 0xFFA24BFF, 0xFF00F0FF},
            null,
            Shader.TileMode.CLAMP));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (!visualsEnabled) return;

    int w = getWidth();
    int h = getHeight();
    if (w <= 0 || h <= 0) return;

    float cx = w / 2f;
    float cy = h / 2f;
    float radius = ring.width() / 2f;
    float scale = radius / 46f;
    float total = maxSeconds + 1f;
    // overall goes from 0 to 1 as we count down from maxSeconds to 0
    float overall = 1f - Math.max(0f, Math.min(1f, (currentStep + 1f - stepProgress) / total));
    float pop = reducedMotion ? 1f : 0.62f + 0.38f * stepProgress;
    boolean finalStep = currentStep < 0;

    canvas.drawCircle(cx, cy, radius * 1.49f, badgePaint);
    canvas.drawCircle(cx, cy, radius * 1.12f, glowPaint);

    canvas.drawArc(ring, 0f, 360f, false, ringTrackPaint);

    canvas.drawArc(ring, -90f, overall * 360f, false, ringProgressPaint);

    if (!reducedMotion) {
      drawOrbitDots(canvas, cx, cy, radius, overall, scale);
      if (finalStep && running) drawConfetti(canvas, cx, cy, radius, stepProgress, scale);
    }

    String label;
    String sub;
    int color;
    if (currentStep > 0) {
      label = String.valueOf(currentStep);
      sub = "GET READY";
      color = 0xFFFFFFFF;
    } else if (currentStep == 0) {
      label = "Go";
      sub = "SING!";
      color = 0xFF00F0FF;
    } else {
      label = "Kanta \uD83C\uDFA4";
      sub = "";
      color = 0xFF00F0FF;
    }

    boolean glyphLabel = currentStep <= 0;
    textPaint.setTypeface(glyphLabel ? labelTypeface : numberTypeface);
    textPaint.setTextSize((glyphLabel ? 20f : 37f) * scale);
    textPaint.setColor(color);
    subTextPaint.setTextSize(9f * scale);
    subTextPaint.setColor(finalStep ? 0xFF00F0FF : 0xFFE9FFFF);

    Paint.FontMetrics metrics = textPaint.getFontMetrics();
    float baseline = cy - (metrics.ascent + metrics.descent) / 2f;

    canvas.save();
    canvas.translate(cx, baseline);
    canvas.scale(pop, pop);
    canvas.drawText(label, 0f, 0f, textPaint);
    canvas.drawText(sub, 0f, 19f * scale, subTextPaint);
    canvas.restore();
  }

  private void drawOrbitDots(
      Canvas canvas, float cx, float cy, float radius, float overall, float density) {
    dotPaint.setColor(0x99FFFFFF);
    for (int i = 0; i < ORBIT_DOTS; i++) {
      double angle = overall * Math.PI * 2 + i * (Math.PI * 2 / ORBIT_DOTS);
      float sx = cx + (float) Math.cos(angle) * radius * 1.35f;
      float sy = cy + (float) Math.sin(angle) * radius * 1.35f;
      float r = (2.5f + 2.5f * stepProgress) * density;
      canvas.drawCircle(sx, sy, r, dotPaint);
    }
  }

  private void drawConfetti(
      Canvas canvas, float cx, float cy, float radius, float progress, float density) {
    int[] colors = {0xFFFF3D8B, 0xFF00F0FF, 0xFFFFD24A, 0xFFA24BFF};
    float eased = (float) Math.sin(progress * Math.PI / 2f);
    for (int i = 0; i < 24; i++) {
      double angle = i * (Math.PI * 2 / 24);
      float distance = radius * (0.35f + 1.15f * eased);
      float x = cx + (float) Math.cos(angle) * distance;
      float y = cy + (float) Math.sin(angle) * distance;
      int alpha = (int) (235 * (1f - progress));
      int color = colors[i % colors.length];
      dotPaint.setColor((alpha << 24) | (color & 0x00FFFFFF));
      canvas.drawCircle(x, y, (2.5f + 2.5f * (1f - progress)) * density, dotPaint);
    }
  }

  /** Cancels any running animation. */
  public void cancel() {
    running = false;
    listener = null;
    removeCallbacks(reducedStep);
    if (currentAnimator != null) {
      currentAnimator.cancel();
      currentAnimator = null;
    }
    setVisibility(GONE);
  }

  /** Hides the final cue when the count finished but the owner has not dismissed it yet. */
  public void dismissFinalCue() {
    if (!running && getVisibility() == VISIBLE) setVisibility(GONE);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    cancel();
  }
}
