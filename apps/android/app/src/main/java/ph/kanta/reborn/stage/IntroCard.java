package ph.kanta.reborn.stage;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Song metadata card shown before countdown.
 *
 * <p>Per {@link replica/design/components.md} IntroCard spec.
 * Auto-dismisses after 3s or on tap.
 */
public final class IntroCard extends FrameLayout {

  private static final int ANIM_DURATION_MS = 250;
  private static final int AUTO_DISMISS_DELAY_MS = 3000;

  private final Paint backgroundPaint;
  private final Paint borderPaint;
  private final Paint glowPaint;
  private final int cornerRadiusPx;
  private final int paddingPx;

  private TextView titleView;
  private TextView artistView;
  private LinearLayout metadataContainer;

  private Runnable onDismiss;
  private boolean isVisible = false;

  public IntroCard(Context context) {
    this(context, null);
  }

  public IntroCard(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);
    setClipChildren(false);
    setClipToPadding(false);

    float density = context.getResources().getDisplayMetrics().density;
    cornerRadiusPx = (int) (24 * density);
    paddingPx = (int) (24 * density);

    backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    backgroundPaint.setColor(0xE60A0614); // introCardBackground (canvas at 0.9 alpha)

    borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    borderPaint.setStyle(Paint.Style.STROKE);
    borderPaint.setStrokeWidth(1 * density);
    borderPaint.setColor(0xFF1A1A1A); // introCardBorder

    glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    glowPaint.setColor(0x40A24BFF); // glow shadow

    initViews(context);
  }

  private void initViews(Context context) {
    float density = context.getResources().getDisplayMetrics().density;

    LinearLayout container = new LinearLayout(context);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
    container.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
        (int)(context.getResources().getDisplayMetrics().widthPixels * 0.8f),
        FrameLayout.LayoutParams.WRAP_CONTENT);
    params.gravity = android.view.Gravity.CENTER;
    addView(container, params);

    // Title
    titleView = new TextView(context);
    titleView.setTextSize(28);
    titleView.setTextColor(0xFFFFFFFF);
    titleView.setTypeface(null, android.graphics.Typeface.BOLD);
    titleView.setGravity(android.view.Gravity.CENTER);
    container.addView(titleView);

    // Artist
    artistView = new TextView(context);
    artistView.setTextSize(16);
    artistView.setTextColor(0xFFCCCCCC);
    artistView.setGravity(android.view.Gravity.CENTER);
    artistView.setPadding(0, (int)(4 * density), 0, 0);
    container.addView(artistView);

    // Divider
    View divider = new View(context);
    divider.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * density)));
    divider.setBackgroundColor(0xFF1A1A1A);
    divider.setPadding(0, (int)(12 * density), 0, (int)(12 * density));
    container.addView(divider);

    // Metadata container
    metadataContainer = new LinearLayout(context);
    metadataContainer.setOrientation(LinearLayout.HORIZONTAL);
    metadataContainer.setGravity(android.view.Gravity.CENTER);
    container.addView(metadataContainer);

    // Tap to dismiss
    setOnClickListener(v -> dismiss());
  }

  /** Shows the intro card. */
  public void show(IntroCardData data, Runnable onDismiss) {
    this.onDismiss = onDismiss;
    this.isVisible = true;

    titleView.setText(data.title);
    artistView.setText(data.artist);

    metadataContainer.removeAllViews();
    addMetadataItem("Duration", data.duration);
    addMetadataItem("Key", data.originalKey);
    addMetadataItem("Tempo", data.tempo + " BPM");
    addMetadataItem("Genre", data.genre);

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

    // Auto-dismiss
    postDelayed(this::dismiss, AUTO_DISMISS_DELAY_MS);
  }

  private void addMetadataItem(String label, String value) {
    Context ctx = getContext();
    float density = ctx.getResources().getDisplayMetrics().density;

    LinearLayout item = new LinearLayout(ctx);
    item.setOrientation(LinearLayout.VERTICAL);
    item.setGravity(android.view.Gravity.CENTER);
    item.setPadding((int)(16 * density), 0, (int)(16 * density), 0);

    TextView labelView = new TextView(ctx);
    labelView.setText(label);
    labelView.setTextSize(12);
    labelView.setTextColor(0xFF999999);

    TextView valueView = new TextView(ctx);
    valueView.setText(value);
    valueView.setTextSize(13);
    valueView.setTextColor(0xFFCCCCCC);

    item.addView(labelView);
    item.addView(valueView);
    metadataContainer.addView(item);
  }

  /** Dismisses the card. */
  public void dismiss() {
    if (!isVisible) return;
    isVisible = false;
    removeCallbacks(null);

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
    int w = getWidth();
    int h = getHeight();
    int cw = (int)(w * 0.8f);
    int left = (w - cw) / 2;
    int top = (h - h / 3) / 2; // approximate vertical center
    int right = left + cw;
    int bottom = top + getChildAt(0).getHeight();

    // Draw glow
    canvas.drawRoundRect(left - 8, top - 8, right + 8, bottom + 8, cornerRadiusPx, cornerRadiusPx, glowPaint);
    // Draw background
    canvas.drawRoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx, backgroundPaint);
    // Draw border
    canvas.drawRoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx, borderPaint);
  }
}
