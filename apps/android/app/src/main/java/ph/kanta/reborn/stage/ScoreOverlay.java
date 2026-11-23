package ph.kanta.reborn.stage;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import ph.kanta.reborn.core.Song;
import ph.kanta.reborn.core.score.ScoreResultTier;
import ph.kanta.reborn.core.score.ScoreRevealPlan;
import java.util.Locale;

/**
 * Post-song score breakdown overlay.
 *
 * <p>Per {@link replica/design/components.md} ScoreOverlay spec.
 * Shows overall score, tier, and breakdown (pitch, timing, bonus).
 */
public final class ScoreOverlay extends FrameLayout {

  private static final int ANIM_DURATION_MS = 300;

  /** Extra time after the celebration where the card stays up before fading out on its own. */
  private static final long AUTO_DISMISS_BUFFER_MS = 900;

  private final int rowGapPx;

  private TextView scoreValueView;
  private TextView tierLabelView;
  private LinearLayout breakdownContainer;
  private TextView closeButton;
  private Runnable onDismiss;

  private boolean isVisible = false;
  private ValueAnimator counter;
  private boolean reducedMotion;
  private final android.os.Handler autoDismissHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private Runnable autoDismiss;

  public ScoreOverlay(Context context) {
    this(context, null);
  }

  public ScoreOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
    setClipChildren(false);
    setClipToPadding(false);

    float density = context.getResources().getDisplayMetrics().density;
    rowGapPx = (int) (12 * density);

    reducedMotion = isReducedMotion(context);
    initViews(context);
  }

  private void initViews(Context context) {
    float density = context.getResources().getDisplayMetrics().density;
    // No card: the number floats over the stage with a soft shadow, karaoke style.
    LinearLayout container = new LinearLayout(context);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
    container.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
    FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
    containerParams.gravity = android.view.Gravity.CENTER;
    addView(container, containerParams);

    // Score value — the hero of the card, big enough to read from the back row.
    scoreValueView = new TextView(context);
    scoreValueView.setTextSize(96);
    scoreValueView.setTextColor(0xFFFFFFFF);
    scoreValueView.setTypeface(null, android.graphics.Typeface.BOLD);
    scoreValueView.setGravity(android.view.Gravity.CENTER);
    scoreValueView.setShadowLayer(12 * density, 0f, 6 * density, 0xCC000000);
    scoreValueView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null);
    container.addView(scoreValueView);

    // Tier label
    tierLabelView = new TextView(context);
    tierLabelView.setTextSize(18);
    tierLabelView.setTypeface(null, android.graphics.Typeface.BOLD);
    tierLabelView.setGravity(android.view.Gravity.CENTER);
    tierLabelView.setPadding(0, (int)(4 * context.getResources().getDisplayMetrics().density), 0, 0);
    container.addView(tierLabelView);

    // Divider
    View divider = new View(context);
    divider.setLayoutParams(new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, (int)(1 * context.getResources().getDisplayMetrics().density)));
    divider.setBackgroundColor(0x66FFFFFF);
    container.addView(divider);

    // Breakdown container
    breakdownContainer = new LinearLayout(context);
    breakdownContainer.setOrientation(LinearLayout.VERTICAL);
    breakdownContainer.setPadding(0, rowGapPx, 0, 0);
    container.addView(breakdownContainer);

    // Close button
    closeButton = new TextView(context);
    closeButton.setText(ph.kanta.reborn.R.string.label_dismiss);
    closeButton.setTextSize(13);
    closeButton.setTextColor(0xFFCCCCCC);
    closeButton.setPadding(0, (int)(16 * context.getResources().getDisplayMetrics().density), 0, 0);
    closeButton.setGravity(android.view.Gravity.CENTER);
    closeButton.setOnClickListener(v -> dismiss());
    container.addView(closeButton);
  }

  /**
   * Shows the score result: the number counts up, then the band lands under it.
   *
   * <p>Karaoke behaviour: after the celebration runs, the card fades out on its own;
   * the Dismiss tap remains as an early exit.
   *
   * @param celebrationMillis how long the celebration for this score runs
   */
  public void show(StagePresenter.ScoreResult result, long celebrationMillis, Runnable onDismiss) {
    this.onDismiss = onDismiss;
    this.isVisible = true;
    cancelAutoDismiss();
    autoDismiss = this::dismiss;
    long minVisible =
        Math.max(celebrationMillis, (long) ScoreRevealPlan.COUNT_UP_MILLIS + ScoreRevealPlan.LABEL_MILLIS);
    autoDismissHandler.postDelayed(autoDismiss, minVisible + AUTO_DISMISS_BUFFER_MS);

    final int target = result.overallScore;
    final int floor = ScoreResultTier.FLOOR;
    final int tierColor = getTierColor(result.tier);
    tierLabelView.setText(result.tier.label());
    tierLabelView.setTextColor(tierColor);

    // Build breakdown rows. Pitch and timing stay the raw measurement, so the floor placed on
    // the headline number never inflates what the microphone actually heard.
    breakdownContainer.removeAllViews();
    addBreakdownRow("Pitch", result.pitchScore, tierColor);
    addBreakdownRow("Timing", result.timingScore, tierColor);
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

    cancelCounter();
    if (reducedMotion) {
      // Honour "Reduce Motion": show the finished state without the count-up.
      scoreValueView.setText(String.format(Locale.getDefault(), "%d", target));
      tierLabelView.setAlpha(1f);
      return;
    }
    scoreValueView.setText(String.format(Locale.getDefault(), "%d", floor));
    tierLabelView.setAlpha(0f);
    counter = ValueAnimator.ofFloat(0f, 1f);
    counter.setDuration(ScoreRevealPlan.COUNT_UP_MILLIS);
    counter.addUpdateListener(
        animation -> {
          int value = ScoreRevealPlan.value(floor, target, (float) animation.getAnimatedValue());
          scoreValueView.setText(String.format(Locale.getDefault(), "%d", value));
        });
    counter.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            counter = null;
            scoreValueView.setText(String.format(Locale.getDefault(), "%d", target));
            tierLabelView
                .animate()
                .alpha(1f)
                .setDuration(ScoreRevealPlan.LABEL_MILLIS)
                .setListener(null)
                .start();
          }
        });
    counter.start();
  }

  private void cancelCounter() {
    if (counter != null) {
      counter.cancel();
      counter = null;
    }
  }

  private void cancelAutoDismiss() {
    if (autoDismiss != null) {
      autoDismissHandler.removeCallbacks(autoDismiss);
      autoDismiss = null;
    }
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

  private void addBreakdownRow(String label, int value, int color) {
    Context ctx = getContext();
    float density = ctx.getResources().getDisplayMetrics().density;

    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);

    TextView labelView = new TextView(ctx);
    labelView.setText(label);
    labelView.setTextSize(16);
    labelView.setTextColor(0xFFE8E8E8);
    labelView.setShadowLayer(8 * density, 0f, 4 * density, 0xCC000000);
    labelView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    labelView.setLayoutParams(new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    TextView valueView = new TextView(ctx);
    valueView.setText(String.format(Locale.getDefault(), "%d", value));
    valueView.setTextSize(16);
    valueView.setTextColor(color);
    valueView.setTypeface(null, android.graphics.Typeface.BOLD);
    valueView.setShadowLayer(8 * density, 0f, 4 * density, 0xCC000000);
    valueView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    valueView.setGravity(android.view.Gravity.END);
    valueView.setLayoutParams(new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    valueView.setPadding((int)(16 * density), 0, 0, 0);

    row.addView(labelView);
    row.addView(valueView);
    breakdownContainer.addView(row);
  }

  private int getTierColor(ScoreResultTier tier) {
    if (tier == null) return 0xFFCCCCCC;
    switch (tier) {
      case EXCELLENT_SINGER: return 0xFFF6CE77; // brand gold
      case GREAT_SINGER: return 0xFF4DE3F0; // cyan
      case GOOD_SINGER: return 0xFF3DE0A8; // success
      case RISING_STAR: return 0xFFFFB03A; // warning
      default: return 0xFFFF5A6E; // destructive
    }
  }

  /** Dismisses the overlay. */
  public void dismiss() {
    if (!isVisible) return;
    isVisible = false;
    cancelAutoDismiss();
    cancelCounter();

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

}
