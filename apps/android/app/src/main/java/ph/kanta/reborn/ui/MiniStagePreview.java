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
  private Typeface face = Typeface.DEFAULT_BOLD;
  private float scale = 1f;

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
    if (typeface != null) face = typeface;
    scale = size <= 0 ? 1f : size;
    mode = MODE_LYRICS;
    invalidate();
  }
  public void setInterlude(String text, Drawable image, Typeface typeface) {
    primary = text == null ? "" : text;
    artwork = image;
    if (typeface != null) face = typeface;
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
    if (!chip.isEmpty()) {
      titlePaint.setTextSize(11 * density);
      canvas.drawText(chip, w - pad, pad + 9 * density, titlePaint);
    }
    wordmarkPaint.setTextSize(10 * density);
    canvas.drawText("KANTA", pad, h - pad, wordmarkPaint);
    if (mode == MODE_INTERLUDE) drawArtwork(canvas, w, h);
    float ty = (mode == MODE_INTERLUDE) ? h * 0.82f : h * 0.45f;
    drawLine(canvas, primary, primaryPaint, w, ty, 15 * density * scale);
    if (!secondary.isEmpty()) {
      drawLine(canvas, secondary, secondaryPaint, w, h * 0.60f, 13 * density * scale);
    }
  }

  private void drawLine(Canvas c, String text, Paint p, int w, float y, float size) {
    if (text.isEmpty()) return;
    p.setTextSize(size);
    float max = w - 24 * density;
    if (p.measureText(text) > max) {
      while (!text.isEmpty() && p.measureText(text + "...") > max) {
        text = text.substring(0, text.length() - 1);
      }
      text += "...";
    }
    c.drawText(text, w / 2f, y, p);
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
    int maxW = Math.round(w * 0.7f), maxH = Math.round(h * 0.45f);
    float s = Math.min((float) maxW / bw, (float) maxH / bh);
    int fw = Math.round(bw * s), fh = Math.round(bh * s);
    int top = Math.round(h * 0.20f);
    artwork.setBounds((w - fw) / 2, top, (w + fw) / 2, top + fh);
    artwork.draw(canvas);
  }
}
