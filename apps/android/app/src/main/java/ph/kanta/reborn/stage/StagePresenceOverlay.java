package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.core.Song;

/**
 * Karaoke standby presence for the empty stage.
 *
 * <p>When no song is on the stage the reference empty state is not a blank wallpaper: it is a
 * karaoke machine waiting for a song code, showing the same {@code 00000} code prompt a real
 * karaoke remote shows before a selection, a live on-air style badge, an animated equaliser, and
 * a clear instruction. This is decoration only: it owns no session state and never touches
 * playback, the queue, the clock or scoring.
 *
 * <p>The code prompt and the instruction are real text views rather than painted glyphs, so they
 * reach screen readers and can be asserted by the runtime checks. Only the scrim, badge and
 * equaliser are painted on the canvas underneath them.
 *
 * <p>It is also a truthful evidence surface: {@link #liveFrame()} publishes a monotonically
 * increasing frame counter that only advances while the background video is actually rendering,
 * so an automated check can prove the standby stage is running moving video, not a frozen still.
 */
public final class StagePresenceOverlay extends FrameLayout {

  /** Fixed width of the song-code prompt, matching a hardware karaoke remote. */
  public static final String CODE_PROMPT = "00000";

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextView codeView;
  private final TextView titleView;
  private final TextView subtitleView;
  private final LinearLayout hintBlock;
  private final float density;
  private final int barCount = 9;

  private long liveFrames;
  private Boolean interactive;

  public StagePresenceOverlay(Context context) {
    this(context, null);
  }

  public StagePresenceOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);
    density = context.getResources().getDisplayMetrics().density;

    codeView = new TextView(context);
    codeView.setText(CODE_PROMPT);
    codeView.setTag("kanta.stage.presence.code");
    codeView.setTypeface(Typeface.create("monospace", Typeface.BOLD));
    codeView.setTextSize(56f);
    codeView.setTextColor(0xFF29D9FF);
    codeView.setLetterSpacing(0.16f);
    codeView.setShadowLayer(18f, 0f, 0f, 0x8829D9FF);
    codeView.setGravity(Gravity.CENTER);
    addView(codeView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

    hintBlock = new LinearLayout(context);
    hintBlock.setOrientation(LinearLayout.VERTICAL);
    hintBlock.setGravity(Gravity.CENTER);
    titleView = new TextView(context);
    titleView.setText("Enter a song code to sing");
    titleView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    titleView.setTextSize(19f);
    titleView.setTextColor(0xFFFFFFFF);
    titleView.setGravity(Gravity.CENTER);
    subtitleView = new TextView(context);
    subtitleView.setText("or find a song in Sing");
    subtitleView.setTextSize(13f);
    subtitleView.setTextColor(0xCCB9D8E8);
    subtitleView.setGravity(Gravity.CENTER);
    hintBlock.addView(titleView);
    hintBlock.addView(subtitleView);
    addView(hintBlock, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

    badgeTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    badgeTextPaint.setTextAlign(Paint.Align.LEFT);
    badgeTextPaint.setColor(0xFFFFFFFF);
    badgeTextPaint.setTextSize(12f * density);
    badgePaint.setColor(0xE6E23A55);
    barPaint.setStyle(Paint.Style.FILL);
  }

  /**
   * Publishes the count of background-video frames the standby stage has rendered. Called only
   * while the moving background is actually being drawn, so a frozen or absent video cannot
   * advance it. Never resets within a session.
   */
  public void recordLiveFrame() {
    liveFrames++;
  }

  /** Monotonic count of rendered standby video frames; used by the runtime evidence check. */
  public long liveFrame() {
    return liveFrames;
  }

  /** The exact code prompt this standby stage renders. */
  public String promptText() {
    return CODE_PROMPT;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  /** Keeps the decorative standby surface out of the accessibility tree when not actionable. */
  public void setInteractive(boolean interactive) {
    if (this.interactive != null && this.interactive == interactive) return;
    this.interactive = interactive;
    // The decoration is not itself a control, but the code prompt and the instruction stay
    // reachable, so a screen reader still announces how to start a song.
    int mode = interactive
        ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        : View.IMPORTANT_FOR_ACCESSIBILITY_NO;
    setImportantForAccessibility(mode);
  }

  /** Binds the standby presence to the truthful empty-playback state. */
  public void bind(Song current) {
    setVisibility(current == null ? VISIBLE : GONE);
    invalidate();
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int w = right - left;
    int h = bottom - top;
    layoutCentered(codeView, w, (int) (h * 0.36f));
    layoutCentered(hintBlock, w, (int) (h * 0.62f));
  }

  private void layoutCentered(View child, int width, int centerY) {
    int cw = child.getMeasuredWidth();
    int ch = child.getMeasuredHeight();
    int childLeft = (width - cw) / 2;
    int childTop = centerY - ch / 2;
    child.layout(childLeft, childTop, childLeft + cw, childTop + ch);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int w = getWidth();
    int h = getHeight();
    if (w <= 0 || h <= 0) return;
    float cx = w / 2f;
    float scale = Math.min(1f, h / (300f * density));
    if (scale < 0.72f) scale = 0.72f;

    // Scrim: a soft vertical gradient (transparent at the top, deep at the bottom) so the
    // video keeps reading as one continuous stage instead of a panel with a hard seam.
    paint.setShader(new LinearGradient(0f, h * 0.10f, 0f, h,
        new int[]{0x00000000, 0x55000000, 0xAA000000, 0xCC000000},
        new float[]{0f, 0.32f, 0.72f, 1f}, Shader.TileMode.CLAMP));
    canvas.drawRect(0, h * 0.10f, w, h, paint);
    paint.setShader(null);

    // Live on-air badge, top-left.
    float badgeY = h * 0.075f;
    float badgeH = 26f * scale * density;
    // Measured width: a fixed width clipped "KARAOKE READY" on narrow stages and at
    // non-default font scales.
    float badgeW = badgeTextPaint.measureText("KARAOKE READY") + 46f * scale * density;
    float badgeX = 18f * density;
    paint.setColor(0x55000000);
    canvas.drawRoundRect(badgeX + 6f, badgeY + 6f, badgeX + badgeW + 6f, badgeY + badgeH + 6f,
        badgeH / 2f, badgeH / 2f, paint);
    badgePaint.setAlpha(pulseAlpha());
    canvas.drawRoundRect(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH,
        badgeH / 2f, badgeH / 2f, badgePaint);
    paint.setColor(0xFFFFFFFF);
    canvas.drawCircle(badgeX + 18f * scale * density, badgeY + badgeH / 2f,
        4f * scale * density, paint);
    canvas.drawText("KARAOKE READY", badgeX + 32f * scale * density,
        badgeY + badgeH / 2f + 4f * scale * density, badgeTextPaint);

    // Animated equaliser between the code prompt and the instruction.
    float barH = 26f * scale * density;
    float barW = 5f * scale * density;
    float gap = 7f * scale * density;
    float totalW = barCount * barW + (barCount - 1) * gap;
    float bx = cx - totalW / 2f;
    float barsTop = h * 0.485f;
    long phase = liveFrames / 3;
    for (int i = 0; i < barCount; i++) {
      float t = (float) Math.sin(phase * 0.22f + i * 0.7f);
      float height = barH * (0.28f + 0.72f * Math.abs(t));
      barPaint.setColor(0xCC29D9FF);
      canvas.drawRoundRect(bx, barsTop + (barH - height), bx + barW, barsTop + barH,
          barW / 2f, barW / 2f, barPaint);
      bx += barW + gap;
    }
  }

  private int pulseAlpha() {
    float t = (float) Math.sin(liveFrames * 0.06f);
    return (int) (180 + 60 * Math.abs(t));
  }
}
