package ph.kanta.reborn.stage;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.core.Song;
import java.util.Locale;

/**
 * Post-song score breakdown overlay.
 *
 * <p>Per {@link replica/design/components.md} ScoreOverlay spec.
 * Shows overall score, tier, and breakdown (pitch, timing, bonus).
 */
public final class ScoreOverlay extends FrameLayout {

  private static final int ANIM_DURATION_MS = 300;

  private final Paint backgroundPaint;
  private final Paint borderPaint;
  private final Paint tierPaint;
  private final Paint scorePaint;
  private final Paint labelPaint;
  private final Paint dividerPaint;
  private final int cornerRadiusPx;
  private final int paddingPx;
  private final int rowGapPx;

  private TextView scoreValueView;
  private TextView tierLabelView;
  private LinearLayout breakdownContainer;
  private TextView closeButton;
  private Runnable onDismiss;

  private boolean isVisible = false;

  public ScoreOverlay(Context context) {
    this(context, null);
  }

  public ScoreOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);
    setClipChildren(false);
    setClipToPadding(false);

    float density = context.getResources().getDisplayMetrics().density;
    cornerRadiusPx = (int) (24 * density);
    paddingPx = (int) (24 * density);
    rowGapPx = (int) (12 * density);

    // Background
    backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    backgroundPaint.setColor(0xFF1B1030); // surface

    // Border
    borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    borderPaint.setStyle(Paint.Style.STROKE);
    borderPaint.setStrokeWidth(1 * density);
    borderPaint.setColor(0xFF1A1A1A); // hairline

    // Tier label
    tierPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    tierPaint.setTextSize(14 * density);
    tierPaint.setFakeBoldText(true);
    tierPaint.setTextAlign(Paint.Align.CENTER);

    // Score value
    scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    scorePaint.setTextSize(48 * density);
    scorePaint.setFakeBoldText(true);
    scorePaint.setColor(0xFFFFFFFF);
    scorePaint.setTextAlign(Paint.Align.CENTER);

    // Labels
    labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    labelPaint.setTextSize(13 * density);
    labelPaint.setColor(0xFFCCCCCC);
    labelPaint.setTextAlign(Paint.Align.LEFT);

    // Divider
    dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    dividerPaint.setStrokeWidth(1 * density);
    dividerPaint.setColor(0xFF1A1A1A);

    initViews(context);
  }

  private void initViews(Context context) {
    // Main container (we'll draw background ourselves, this is for child views)
    LinearLayout container = new LinearLayout(context);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
    container.setGravity(CENTER_HORIZONTAL);
    LayoutParams containerParams = new LayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    containerParams.gravity = android.view.Gravity.CENTER;
    addView(container, containerParams);

    // Score value
    scoreValueView = new TextView(context);
    scoreValueView.setTextSize(48);
    scoreValueView.setTextColor(0xFFFFFFFF);
    scoreValueView.setTypeface(null, android.graphics.Typeface.BOLD);
    scoreValueView.setGravity(android.view.Gravity.CENTER);
    container.addView(scoreValueView);

    // Tier label
    tierLabelView = new TextView(context);
    tierLabelView.setTextSize(14);
    scoreValueView.setTypeface(null, android.graphics.Typeface.BOLD);
    tierLabelView.setGravity(android.view.Gravity.CENTER);
    tierLabelView.setPadding(0, (int)(4 * context.getResources().getDisplayMetrics().density), 0, 0);
    container.addView(tierLabelView);

    // Divider
    View divider = new View(context);
    divider.setLayoutParams(new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, (int)(1 * context.getResources().getDisplayMetrics().density)));
    divider.setBackgroundColor(0xFF1A1A1A);
    container.addView(divider);

    // Breakdown container
    breakdownContainer = new LinearLayout(context);
    breakdownContainer.setOrientation(LinearLayout.VERTICAL);
    breakdownContainer.setPadding(0, rowGapPx, 0, 0);
    container.addView(breakdownContainer);

    // Close button
    closeButton = new TextView(context);
    closeButton.setText("Dismiss");
    closeButton.setTextSize(13);
    closeButton.setTextColor(0xFFCCCCCC);
    closeButton.setPadding(0, (int)(16 * density), 0, 0);
    closeButton.setGravity(android.view.Gravity.CENTER);
    closeButton.setOnClickListener(v -> dismiss());
    container.addView(closeButton);
  }

  /** Shows the score result. */
  public void show(ScoreResult result, Runnable onDismiss) {
    this.onDismiss = onDismiss;
    this.isVisible = true;

    scoreValueView.setText(String.format(Locale.getDefault(), "%d", result.overallScore));
    tierLabelView.setText(result.tier);
    tierLabelView.setTextColor(getTierColor(result.tier));

    // Build breakdown rows
    breakdownContainer.removeAllViews();
    addBreakdownRow("Pitch", result.pitchScore, getTierColor(result.tier));
    addBreakdownRow("Timing", result.timingScore, getTierColor(result.tier));
    addBreakdownRow("Bonus", result.bonusScore, 0xFFFFB03A);

    // Animate in
    setAlpha(0f);
    setScaleX(0.95f);
    setScaleY(0.95f);
    setVisibility(VISIBLE);
    animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(ANIM_DURATION_MS)
        .setListener(null)
        .start();
  }

  private void addBreakdownRow(String label, int value, int color) {
    Context ctx = getContext();
    float density = ctx.getResources().getDisplayMetrics().density;

    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);

    TextView labelView = new TextView(ctx);
    labelView.setText(label);
    labelView.setTextSize(13);
    labelView.setTextColor(0xFFCCCCCC);
    labelView.setLayoutParams(new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    TextView valueView = new TextView(ctx);
    valueView.setText(String.format(Locale.getDefault(), "%d", value));
    valueView.setTextSize(13);
    valueView.setTextColor(color);
    valueView.setTypeface(null, android.graphics.Typeface.BOLD);
    valueView.setGravity(android.view.Gravity.END);
    valueView.setLayoutParams(new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    valueView.setPadding((int)(16 * density), 0, 0, 0);

    row.addView(labelView);
    row.addView(valueView);
    breakdownContainer.addView(row);
  }

  private int getTierColor(String tier) {
    switch (tier) {
      case "Perfect": return 0xFF3DE0A8; // success
      case "Great": return 0xFF4DE3F0; // cyan
      case "Good": return 0xFFFFB03A; // warning
      default: return 0xFFFF5A6E; // destructive
    }
  }

  /** Dismisses the overlay. */
  public void dismiss() {
    if (!isVisible) return;
    isVisible = false;

    animate()
        .alpha(0f)
        .scaleX(0.95f)
        .scaleY(0.95f)
        .setDuration(ANIM_DURATION_MS)
        .setListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            setVisibility(GONE);
            if (onDismiss != null) onDismiss.run();
          }
        })
        .start();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    // Draw rounded background behind children
    int w = getWidth();
    int h = getHeight();
    int cx = w / 2;
    int cy = h / 2;
    int rw = (int) (w * 0.85f);
    int rh = (int) (h * 0.85f);
    int left = cx - rw / 2;
    int top = cy - rh / 2;
    int right = cx + rw / 2;
    int bottom = cy + rh / 2;

    canvas.drawRoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx, backgroundPaint);
    canvas.drawRoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx, borderPaint);
  }
}
