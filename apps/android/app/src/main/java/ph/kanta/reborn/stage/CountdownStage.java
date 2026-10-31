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
 * Full-screen fun countdown: "Ready ⏱️" -> 1 -> 2 -> ... -> N -> "Kanta 🎤".
 *
 * <p>The count is source-free presentation only; playback still starts exactly when the owner
 * reports the last cue complete. Reduced-motion shows the same sequence without the pop/orbit.
 */
public final class CountdownStage extends View {

  private static final int RING_STROKE_DP = 10;
  private static final int STEP_DURATION_MS = 800;
  private static final int REDUCED_STEP_MS = 500;
  private static final int ORBIT_DOTS = 12;

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
  private boolean completed = false;

  private int maxSeconds = 4;
  private int currentStep = 0; // 0 = Ready, 1..N = numbers, N+1 = Kanta
  private float stepProgress = 0f;

  private final Runnable reducedStep =
      new Runnable() {
        @Override
        public void run() {
          if (!running) return;
          currentStep++;
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
    this.currentStep = 0;
    this.stepProgress = 0f;
    this.running = true;
    this.completed = false;
    setVisibility(VISIBLE);
    invalidate();
    scheduleStep();
  }

  private void scheduleStep() {
    if (!running) return;
    if (currentStep > maxSeconds + 1) {
      finish();
      return;
    }
    if (currentStep >= 1 && currentStep <= maxSeconds && listener != null) {
      // Contract keeps "remaining" semantics while the visible label counts up 1..N.
      listener.onTick(maxSeconds - currentStep + 1);
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
            currentStep++;
            stepProgress = 0f;
            scheduleStep();
          }
        });
    currentAnimator.start();
  }

  private void finish() {
    running = false;
    setVisibility(GONE);
    if (!completed && listener != null) {
      completed = true;
      listener.onComplete();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int w = getWidth();
    int h = getHeight();
    if (w <= 0 || h <= 0) return;

    float density = getResources().getDisplayMetrics().density;
    float cx = w / 2f;
    float cy = h / 2f;
    float radius = Math.min(w, h) * 0.28f;

    canvas.drawColor(0xF00A0614);

    RectF ring = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

    float total = maxSeconds + 2f;
    float overall = Math.min(1f, (currentStep + stepProgress) / total);
    float pop = 0.62f + 0.38f * stepProgress;
    boolean finalStep = currentStep > maxSeconds;

    glowPaint.setShader(
        new LinearGradient(
            ring.left,
            ring.top,
            ring.right,
            ring.bottom,
            new int[] {0x55A24BFF, 0x3300F0FF},
            null,
            Shader.TileMode.CLAMP));
    canvas.drawCircle(cx, cy, radius * 1.12f, glowPaint);
    glowPaint.setShader(null);

    canvas.drawArc(ring, 0f, 360f, false, ringTrackPaint);

    ringProgressPaint.setShader(
        new LinearGradient(
            ring.left,
            ring.top,
            ring.right,
            ring.bottom,
            new int[] {0xFFFF3D8B, 0xFFA24BFF, 0xFF00F0FF},
            null,
            Shader.TileMode.CLAMP));
    canvas.drawArc(ring, -90f, overall * 360f, false, ringProgressPaint);
    ringProgressPaint.setShader(null);

    drawOrbitDots(canvas, cx, cy, radius, overall, density);
    if (finalStep) drawConfetti(canvas, cx, cy, radius, stepProgress, density);

    String label;
    String sub;
    int color;
    if (currentStep == 0) {
      label = "Ready \u23F1\uFE0F";
      sub = "GET SET";
      color = 0xFFFFD24A;
    } else if (currentStep <= maxSeconds) {
      label = String.valueOf(currentStep);
      sub = "GET READY";
      color = 0xFFFFFFFF;
    } else {
      label = "Kanta \uD83C\uDFA4";
      sub = "SING!";
      color = 0xFF00F0FF;
    }

    boolean glyphLabel = currentStep == 0 || finalStep;
    textPaint.setTypeface(glyphLabel ? labelTypeface : numberTypeface);
    textPaint.setTextSize((glyphLabel ? 58f : 96f) * density);
    textPaint.setColor(color);
    subTextPaint.setColor(finalStep ? 0xFF00F0FF : 0xFFE9FFFF);

    Paint.FontMetrics metrics = textPaint.getFontMetrics();
    float baseline = cy - (metrics.ascent + metrics.descent) / 2f;

    canvas.save();
    canvas.translate(cx, baseline);
    canvas.scale(pop, pop);
    canvas.drawText(label, 0f, 0f, textPaint);
    if (currentStep == 0 || currentStep <= maxSeconds || finalStep) {
      canvas.drawText(sub, 0f, 46f * density, subTextPaint);
    }
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
    completed = false;
    removeCallbacks(reducedStep);
    if (currentAnimator != null) {
      currentAnimator.cancel();
      currentAnimator = null;
    }
    setVisibility(GONE);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    cancel();
  }
}
