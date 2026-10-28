package ph.kanta.reborn.stage;

import ph.kanta.reborn.stage.StagePresenter.CountdownListener;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

/**
 * Full-screen countdown (3-2-1-GO) with animated ring.
 *
 * <p>Per {@link replica/design/components.md} CountdownStage spec.
 */
public final class CountdownStage extends View {

  private static final int RING_STROKE_DP = 8;
  private static final int ANIM_DURATION_MS = 250; // per number
  private static final int GO_FLASH_DURATION_MS = 300;

  private final Paint ringPaint;
  private final Paint ringTrackPaint;
  private final Paint textPaint;
  private final Paint goFlashPaint;
  private final int ringStrokePx;
  private final int centerX;
  private final int centerY;
  private final int radius;

  private ValueAnimator currentAnimator;
  private int currentNumber = 3;
  private float ringProgress = 0f;
  private boolean isGoFlash = false;
  private float goFlashScale = 1f;
  private CountdownListener listener;
  private boolean reducedMotion = false;

  public CountdownStage(Context context) {
    this(context, null);
  }

  public CountdownStage(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);

    float density = context.getResources().getDisplayMetrics().density;
    ringStrokePx = (int) (RING_STROKE_DP * density);

    // Ring track
    ringTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    ringTrackPaint.setStyle(Paint.Style.STROKE);
    ringTrackPaint.setStrokeWidth(ringStrokePx);
    ringTrackPaint.setColor(0x4D1A1A1A); // hairline at 0.3 alpha
    ringTrackPaint.setStrokeCap(Paint.Cap.ROUND);

    // Ring progress
    ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(ringStrokePx);
    ringPaint.setColor(0xFFA24BFF); // karaoke_countdown_ring
    ringPaint.setStrokeCap(Paint.Cap.ROUND);

    // Countdown number text
    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setTextSize(72 * density); // 72sp per design tokens
    textPaint.setColor(0xFFFFFFFF); // karaoke_countdown_number
    textPaint.setFakeBoldText(true);
    textPaint.setTextAlign(Paint.Align.CENTER);
    textPaint.setTextScaleX(1.0f);

    // GO flash
    goFlashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    goFlashPaint.setTextSize(72 * density);
    goFlashPaint.setColor(0xFF3DE0A8); // live color
    goFlashPaint.setFakeBoldText(true);
    goFlashPaint.setTextAlign(Paint.Align.CENTER);

    centerX = 0; // will be set in onSizeChanged
    centerY = 0;
    radius = 0;

    reducedMotion = isReducedMotionEnabled(context);
  }

  private boolean isReducedMotionEnabled(Context context) {
    float scale = android.provider.Settings.Global.getFloat(
        context.getContentResolver(),
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
    return scale == 0f;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    // Center and radius will be computed in onDraw
  }

  /** Starts the countdown. */
  public void start(int countdownSeconds, CountdownListener listener) {
    this.listener = listener;
    this.currentNumber = countdownSeconds;
    this.ringProgress = 0f;
    this.isGoFlash = false;
    this.goFlashScale = 1f;
    setVisibility(VISIBLE);
    animateNumber();
  }

  private void animateNumber() {
    if (currentNumber <= 0) {
      // Show "GO"
      showGoFlash();
      return;
    }

    if (listener != null) {
      listener.onTick(currentNumber);
    }

    if (reducedMotion) {
      // Skip animation, just tick
      currentNumber--;
      animateNumber();
      return;
    }

    currentAnimator = ValueAnimator.ofFloat(0f, 1f);
    currentAnimator.setDuration(ANIM_DURATION_MS);
    currentAnimator.setInterpolator(new DecelerateInterpolator());
    currentAnimator.addUpdateListener(animation -> {
      ringProgress = (float) animation.getAnimatedValue();
      invalidate();
    });
    currentAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        ringProgress = 1f;
        currentNumber--;
        animateNumber();
      }
    });
    currentAnimator.start();
  }

  private void showGoFlash() {
    isGoFlash = true;
    if (listener != null) {
      listener.onComplete();
    }

    if (reducedMotion) {
      // Just show GO briefly
      postDelayed(() -> {
        setVisibility(GONE);
        if (listener != null) listener.onComplete();
      }, 500);
      invalidate();
      return;
    }

    ValueAnimator flash = ValueAnimator.ofFloat(1f, 1.2f, 1f);
    flash.setDuration(GO_FLASH_DURATION_MS);
    flash.addUpdateListener(animation -> {
      goFlashScale = (float) animation.getAnimatedValue();
      invalidate();
    });
    flash.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        setVisibility(GONE);
      }
    });
    flash.start();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int w = getWidth();
    int h = getHeight();
    int cx = w / 2;
    int cy = h / 2;
    int r = Math.min(w, h) / 3;

    // Draw semi-transparent background
    canvas.drawColor(0xEB0A0614); // canvas at 0.92 alpha

    if (isGoFlash) {
      // Draw GO with flash scale
      canvas.save();
      canvas.translate(cx, cy);
      canvas.scale(goFlashScale, goFlashScale);
      Rect bounds = new Rect();
      goFlashPaint.getTextBounds("GO", 0, 2, bounds);
      canvas.drawText("GO", 0, -bounds.top, goFlashPaint);
      canvas.restore();
    } else if (currentNumber > 0) {
      // Draw ring track
      canvas.drawCircle(cx, cy, r, ringTrackPaint);

      // Draw ring progress (0 to 360 degrees)
      float sweepAngle = ringProgress * 360f;
      canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -90, sweepAngle, false, ringPaint);

      // Draw number
      String text = String.valueOf(currentNumber);
      Rect bounds = new Rect();
      textPaint.getTextBounds(text, 0, text.length(), bounds);
      canvas.drawText(text, cx, cy - bounds.top, textPaint);
    }
  }

  /** Cancels any running animation. */
  public void cancel() {
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
