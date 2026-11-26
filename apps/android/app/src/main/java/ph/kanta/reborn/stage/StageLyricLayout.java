package ph.kanta.reborn.stage;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;

/** Fits the visible karaoke cues without changing their text or character offsets. */
public final class StageLyricLayout {
  public final StaticLayout current;
  public final StaticLayout next;
  public final int gap;

  private StageLyricLayout(StaticLayout current, StaticLayout next, int gap) {
    this.current = current;
    this.next = next;
    this.gap = next == null ? 0 : gap;
  }

  public int height() {
    return current.getHeight() + (next == null ? 0 : gap + next.getHeight());
  }

  public static float preferredSize(Resources resources, int viewportWidth, boolean largeStage) {
    float widthDp = viewportWidth / resources.getDisplayMetrics().density;
    float sp = largeStage ? Math.max(62f, Math.min(96f, widthDp / 11f))
        : Math.max(42f, Math.min(68f, widthDp / 9f));
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.getDisplayMetrics());
  }

  public static StageLyricLayout fit(String text, TextPaint style, String preview,
      TextPaint previewStyle, int width, int height, int gap, float minimumPreviewSize) {
    return fit(text, style, preview, previewStyle, width, height, gap, minimumPreviewSize,
        Layout.Alignment.ALIGN_CENTER);
  }

  public static StageLyricLayout fit(String text, TextPaint style, String preview,
      TextPaint previewStyle, int width, int height, int gap, float minimumPreviewSize,
      Layout.Alignment alignment) {
    StaticLayout full = fitLine(text, style, style.getTextSize(), width, height, alignment);
    if (preview.isEmpty()) return new StageLyricLayout(full, null, 0);

    // Try the preview without reducing the active cue first. If that fails, reserve
    // only the space the preview actually needs at its readable minimum.
    int previewBudget = height / 3;
    float ratio = previewStyle.getTextSize() / style.getTextSize();
    float fullSize = full.getPaint().getTextSize();
    if (fullSize * ratio < minimumPreviewSize) return new StageLyricLayout(full, null, 0);
    StaticLayout minimum = create(preview, previewStyle, width, minimumPreviewSize, alignment);
    if (!fits(minimum, width, previewBudget)) return new StageLyricLayout(full, null, 0);

    StaticLayout current = full;
    if (full.getHeight() + gap + minimum.getHeight() > height) {
      current = fitLine(text, style, fullSize, width, height - gap - minimum.getHeight(), alignment);
      float size = current.getPaint().getTextSize();
      if (current.getHeight() + gap + minimum.getHeight() > height
          || size < fullSize * .82f || size * ratio < minimumPreviewSize) {
        return new StageLyricLayout(full, null, 0);
      }
    }
    int remaining = Math.min(previewBudget, height - current.getHeight() - gap);
    StaticLayout next = fitLine(preview, previewStyle, current.getPaint().getTextSize() * ratio,
        width, remaining, alignment);
    if (next.getPaint().getTextSize() < minimumPreviewSize) next = minimum;
    return new StageLyricLayout(current, next, gap);
  }

  private static StaticLayout fitLine(
      String text, TextPaint paint, float preferred, int width, int height,
      Layout.Alignment alignment) {
    StaticLayout best = create(text, paint, width, preferred, alignment);
    if (fits(best, width, height)) return best;

    // StaticLayout wraps at natural break opportunities. No maxLines/ellipsis: a timed
    // cue must remain complete. Search actual glyph metrics, including custom fonts.
    float low = Math.min(1f, preferred), high = preferred;
    best = create(text, paint, width, low, alignment);
    for (int i = 0; i < 9 && high - low > .25f; i++) {
      float size = (low + high) / 2f;
      StaticLayout candidate = create(text, paint, width, size, alignment);
      if (fits(candidate, width, height)) {
        low = size;
        best = candidate;
      } else {
        high = size;
      }
    }
    return best;
  }

  private static boolean fits(StaticLayout layout, int width, int height) {
    if (layout.getHeight() > height) return false;
    for (int i = 0; i < layout.getLineCount(); i++) {
      if (layout.getLineMax(i) > width + .5f) return false;
    }
    return true;
  }

  private static StaticLayout create(String text, TextPaint prototype, int width, float size,
      Layout.Alignment alignment) {
    TextPaint paint = new TextPaint(prototype);
    paint.setTextSize(size);
    StaticLayout.Builder builder = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
        .setAlignment(alignment)
        .setIncludePad(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setUseLineSpacingFromFallbacks(true);
    return builder.build();
  }

  /** Draw the pair at a shared left/top origin, using the same glyphs for the timed prefix. */
  public void draw(Canvas canvas, float x, float y, float characters, int highlightColor) {
    canvas.save();
    canvas.translate(x, y);
    current.draw(canvas);
    if (characters > 0f) drawHighlight(canvas, characters, highlightColor);
    if (next != null) {
      canvas.translate(0, current.getHeight() + gap);
      next.draw(canvas);
    }
    canvas.restore();
  }

  private final Path selection = new Path();
  private final Path glyph = new Path();
  private final Path lineBounds = new Path();
  private final RectF glyphBounds = new RectF();

  private void drawHighlight(Canvas canvas, float characters, int color) {
    int line = 0;
    // Paint completed rows as a band. At a wrap the end offset belongs to the
    // next row, so treating it as the old row's horizontal position is incorrect.
    while (line < current.getLineCount() && characters >= current.getLineVisibleEnd(line)) line++;
    TextPaint paint = current.getPaint();
    int originalColor = paint.getColor();
    paint.setColor(color);
    if (line > 0) {
      canvas.save();
      if (line < current.getLineCount()) {
        canvas.clipRect(-current.getWidth(), 0, 2f * current.getWidth(), current.getLineTop(line));
      }
      current.draw(canvas);
      canvas.restore();
    }
    if (line < current.getLineCount() && characters > current.getLineStart(line)) {
      CharSequence text = current.getText();
      int whole = (int) characters;
      if (whole > 0 && Character.isLowSurrogate(text.charAt(whole))
          && Character.isHighSurrogate(text.charAt(whole - 1))) whole--;
      current.getSelectionPath(current.getLineStart(line), whole, selection);
      if (characters > whole) {
        int end = whole + Character.charCount(Character.codePointAt(text, whole));
        current.getSelectionPath(whole, end, glyph);
        if (end == current.getLineEnd(line) && line + 1 < current.getLineCount()) {
          // Drop the extra selection rectangles Android adds for a cross-row selection.
          lineBounds.rewind();
          lineBounds.addRect(current.getLineLeft(line), current.getLineTop(line),
              current.getLineRight(line), current.getLineBottom(line), Path.Direction.CW);
          glyph.op(lineBounds, Path.Op.INTERSECT);
        }
        glyph.computeBounds(glyphBounds, true);
        float advance = glyphBounds.width() * (characters - whole) / (end - whole);
        if (advance > 0f) {
          boolean rtl = current.isRtlCharAt(whole);
          float left = rtl ? glyphBounds.right - advance : glyphBounds.left;
          selection.addRect(left, glyphBounds.top, left + advance, glyphBounds.bottom,
              Path.Direction.CW);
        }
      }
      canvas.save();
      canvas.clipPath(selection);
      current.draw(canvas);
      canvas.restore();
    }
    paint.setColor(originalColor);
  }
}
