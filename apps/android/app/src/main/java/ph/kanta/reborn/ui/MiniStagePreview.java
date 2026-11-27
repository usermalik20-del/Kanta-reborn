package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A small, faithful, scaled-down preview of the real KANTA Stage.
 *
 * <p>It lets the visual editors (Lyrics, Interlude, background video, background photo) show
 * what the audience will see while the user edits. It owns no session state, does not touch
 * playback, does no I/O and never loads anything asynchronously: every setter just repaints.
 */
public final class MiniStagePreview extends FrameLayout {
  public static final int MODE_LYRICS = 0;
  public static final int MODE_INTERLUDE = 1;
  public static final int MODE_PHOTO = 2;
  public static final int MODE_VIDEO = 3;

  private final float density;
  private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint wordmarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint primaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint secondaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect source = new Rect();
  private final RectF target = new RectF();

  private int mode = MODE_LYRICS;
  private String primary = "";
  private String secondary = "";
  private String chip = "";
  private String placeholder = "Background photo";
  private Bitmap background;
  private Drawable artwork;
  private Typeface baseFace = Typeface.DEFAULT_BOLD;
  private float scale = 1f;
  private LyricStyle style = new LyricStyle();
  private int interludeColor = 0xFFFFFFFF;
  private float imageScale = 1f;
  private String imagePlacement = "above";
  private boolean interludeWatermark = true;

  /** Style snapshot pushed by the editor; every field mirrors the real StageView. */
  public static final class LyricStyle {
    public int activeColor = 0xFFFFFFFF;
    public float upcomingAlpha = 0.8f;
    public boolean allCaps = false;
    public boolean bold = true;
    public int outline = 1;
    public String alignment = "center";
    public boolean showUpcoming = true;
    public boolean showTitle = false;
    public String title = "";
    public boolean showWatermark = true;
  }

  public MiniStagePreview(Context context) {
    super(context);
    density = context.getResources().getDisplayMetrics().density;
    setWillNotDraw(false);
    setBackgroundColor(KantaTheme.BG_MAIN);
    setTag("kanta.preview.stage");
    setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    framePaint.setStyle(Paint.Style.STROKE);
    framePaint.setColor(KantaTheme.BORDER);
    framePaint.setStrokeWidth(1.2f * density);
    scrimPaint.setColor(0x99000000);
    wordmarkPaint.setColor(0xCC76E4FF);
    wordmarkPaint.setTextAlign(Paint.Align.LEFT);
    wordmarkPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    titlePaint.setColor(KantaTheme.TEXT_SECONDARY);
    titlePaint.setTextAlign(Paint.Align.RIGHT);
    primaryPaint.setColor(KantaTheme.TEXT_PRIMARY);
    primaryPaint.setTextAlign(Paint.Align.CENTER);
    primaryPaint.setTypeface(Typeface.DEFAULT_BOLD);
    secondaryPaint.setColor(0xFFB9D8E8);
    secondaryPaint.setTextAlign(Paint.Align.CENTER);
    placeholderPaint.setColor(0x66A7B1C7);
    placeholderPaint.setTextAlign(Paint.Align.CENTER);
    placeholderPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
  }

  public void setMode(int value) { mode = value; invalidate(); }
  public void setBackground(Bitmap bitmap) { background = bitmap; invalidate(); }
  public void setArtwork(Drawable drawable) { artwork = drawable; invalidate(); }
  public void setPlaceholder(String text) {
    placeholder = text == null ? "" : text;
    invalidate();
  }
  public void setChip(String text) { chip = text == null ? "" : text; invalidate(); }
  public void setLyricLines(String line1, String line2, Typeface typeface, float size) {
    primary = line1 == null ? "" : line1;
    secondary = line2 == null ? "" : line2;
    // null means the KANTA default: reset instead of keeping the previous face.
    baseFace = typeface == null ? Typeface.DEFAULT_BOLD : typeface;
    scale = size <= 0 ? 1f : size;
    mode = MODE_LYRICS;
    invalidate();
  }
  public void applyLyricStyle(LyricStyle value) {
    style = value == null ? new LyricStyle() : value;
    invalidate();
  }
  public void setInterlude(String text, Drawable image, Typeface typeface, int color,
      String animation, float scale, String placement, boolean showWatermark) {
    primary = text == null ? "" : text;
    artwork = image;
    // null means the KANTA default: reset instead of keeping the previous face.
    baseFace = typeface == null ? Typeface.DEFAULT_BOLD : typeface;
    interludeColor = color;
    imageScale = scale <= 0 ? 1f : scale;
    imagePlacement = placement == null ? "above" : placement;
    interludeWatermark = showWatermark;
    mode = MODE_INTERLUDE;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int w = getWidth(), h = getHeight();
    if (w <= 0 || h <= 0) return;
    drawBackground(canvas, w, h);
    float pad = 12 * density;
    if (mode == MODE_LYRICS && style.showTitle && !style.title.isEmpty()) {
      titlePaint.setTextSize(11 * density);
      titlePaint.setTextAlign(Paint.Align.CENTER);
      titlePaint.setColor(0xCCFFFFFF);
      canvas.drawText(style.title, w / 2f, pad + 9 * density, titlePaint);
      titlePaint.setColor(KantaTheme.TEXT_SECONDARY);
      titlePaint.setTextAlign(Paint.Align.RIGHT);
    }
    if (!chip.isEmpty()) {
      titlePaint.setTextSize(11 * density);
      canvas.drawText(chip, w - pad, pad + 9 * density, titlePaint);
    }
    boolean showWordmark = mode == MODE_INTERLUDE ? interludeWatermark : style.showWatermark;
    if (showWordmark) {
      wordmarkPaint.setTextSize(10 * density);
      canvas.drawText("KANTA", pad, h - pad, wordmarkPaint);
    }
    if (mode == MODE_INTERLUDE) {
      if ("background".equals(imagePlacement)) drawArtworkFull(canvas, w, h);
      else drawArtwork(canvas, w, h);
      applyStyleTo(primaryPaint, interludeColor, true);
      drawLine(canvas, primary, primaryPaint, w, h * 0.82f, 15 * density * scale, false);
      return;
    }
    boolean left = "left".equals(style.alignment);
    applyStyleTo(primaryPaint, style.activeColor, !left);
    applyStyleTo(secondaryPaint,
        ((int) Math.round(style.upcomingAlpha * 255) << 24) | 0xFFB9D8E8, !left);
    float ty = "bottom".equals(style.alignment) ? h * 0.66f : h * 0.45f;
    drawLine(canvas, primary, primaryPaint, w, ty, 15 * density * scale, left);
    if (style.showUpcoming && !secondary.isEmpty()) {
      drawLine(canvas, secondary, secondaryPaint, w, h * 0.60f, 13 * density * scale, left);
    }
  }

  private void applyStyleTo(Paint p, int color, boolean centered) {
    p.setTypeface(style.bold ? Typeface.create(baseFace, Typeface.BOLD) : baseFace);
    p.setColor(color);
    switch (style.outline) {
      case 0: p.setShadowLayer(0, 0, 0, 0); break;
      case 2: p.setShadowLayer(5 * density, 0, 5 * density, 0xFF000000); break;
      default: p.setShadowLayer(2 * density, 0, 2 * density, 0xFF000000);
    }
    p.setTextAlign(centered ? Paint.Align.CENTER : Paint.Align.LEFT);
  }

  private void drawLine(Canvas c, String text, Paint p, int w, float y, float size,
      boolean left) {
    if (text.isEmpty()) return;
    if (style.allCaps) text = text.toUpperCase(java.util.Locale.ROOT);
    p.setTextSize(size);
    float max = w - 24 * density;
    if (p.measureText(text) > max) {
      while (!text.isEmpty() && p.measureText(text + "...") > max) {
        text = text.substring(0, text.length() - 1);
      }
      text += "...";
    }
    c.drawText(text, left ? 12 * density : w / 2f, y, p);
  }

  private void drawBackground(Canvas canvas, int w, int h) {
    if (background != null) {
      int bw = background.getWidth(), bh = background.getHeight();
      if (bw > 0 && bh > 0) {
        float s = Math.max((float) w / bw, (float) h / bh);
        int fw = Math.round(bw * s), fh = Math.round(bh * s);
        source.set(0, 0, bw, bh);
        target.set((w - fw) / 2f, (h - fh) / 2f, (w + fw) / 2f, (h + fh) / 2f);
        canvas.drawBitmap(background, source, target, null);
      }
      canvas.drawRect(0, 0, w, h, scrimPaint);
    } else if (!placeholder.isEmpty()) {
      placeholderPaint.setTextSize(13 * density);
      canvas.drawText(placeholder, w / 2f, h / 2f, placeholderPaint);
    }
  }

  private void drawArtwork(Canvas canvas, int w, int h) {
    if (artwork == null) return;
    int bw = artwork.getIntrinsicWidth(), bh = artwork.getIntrinsicHeight();
    if (bw <= 0 || bh <= 0) return;
    int maxW = Math.round(w * 0.7f * imageScale), maxH = Math.round(h * 0.45f * imageScale);
    float s = Math.min((float) maxW / bw, (float) maxH / bh);
    int fw = Math.round(bw * s), fh = Math.round(bh * s);
    int top = Math.round(h * 0.20f);
    artwork.setBounds((w - fw) / 2, top, (w + fw) / 2, top + fh);
    artwork.draw(canvas);
  }

  private void drawArtworkFull(Canvas canvas, int w, int h) {
    if (artwork == null) return;
    int bw = artwork.getIntrinsicWidth(), bh = artwork.getIntrinsicHeight();
    if (bw <= 0 || bh <= 0) return;
    float s = Math.max((float) w / bw, (float) h / bh) * imageScale;
    int fw = Math.round(bw * s), fh = Math.round(bh * s);
    artwork.setBounds((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2);
    artwork.draw(canvas);
    canvas.drawRect(0, 0, w, h, scrimPaint);
  }
}
