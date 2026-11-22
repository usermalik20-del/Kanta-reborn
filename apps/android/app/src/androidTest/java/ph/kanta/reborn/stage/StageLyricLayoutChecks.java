package ph.kanta.reborn.stage;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.TextPaint;

/** Real StaticLayout and Canvas checks, run by the Android runtime suite. */
public final class StageLyricLayoutChecks {
  private static final int GOLD = 0xFFFFD700;

  public static int run() {
    TextPaint active = style(62, Typeface.create("sans-serif", Typeface.BOLD));
    TextPaint preview = style(40, Typeface.create("serif", Typeface.ITALIC));
    StageLyricLayout roomy = StageLyricLayout.fit("Sing", active, "with me", preview,
        360, 220, 12, 18);
    check(roomy.current.getPaint().getTextSize() == 62, "Roomy pane reduced the active size");
    check(roomy.next != null && roomy.next.getPaint().getTextSize() < 62,
        "Roomy pane did not retain a smaller preview");
    check(roomy.height() <= 220 && roomy.gap == 12, "Cue pair exceeded its height budget");

    // This pane cannot fit even the minimum preview; the active-only fit must win.
    StageLyricLayout alone = StageLyricLayout.fit("Sing", active, "", preview, 360, 48, 12, 18);
    StageLyricLayout shortPane = StageLyricLayout.fit("Sing", active, "with me", preview,
        360, 48, 12, 18);
    check(shortPane.next == null && shortPane.gap == 0 && shortPane.height() <= 48,
        "Short pane failed to drop the preview and its gap");
    check(shortPane.current.getPaint().getTextSize() == alone.current.getPaint().getTextSize(),
        "Dropped preview still reduced the active size");

    String cue = "Sing like nobody is listening, then sing a little louder";
    StageLyricLayout wrapped = StageLyricLayout.fit(cue, active, "", preview, 230, 280, 8, 16);
    StaticLayout layout = wrapped.current;
    check(layout.getLineCount() > 2 && wrapped.height() <= 280,
        "Long cue did not wrap and fit the measured pane");
    check(cue.contentEquals(layout.getText()) && layout.getLineEnd(layout.getLineCount() - 1)
        == cue.length(), "Fitting changed the active cue or its timing offsets");
    for (int i = 0; i < layout.getLineCount(); i++) {
      check(layout.getEllipsisCount(i) == 0 && layout.getLineMax(i) <= 230.5f,
          "A wrapped row was truncated or did not fit");
    }

    Bitmap bitmap = Bitmap.createBitmap(layout.getWidth(), layout.getHeight(), Bitmap.Config.ARGB_8888);
    try {
      Canvas canvas = new Canvas(bitmap);
      int boundary = layout.getLineStart(1);
      int split = layout.getLineTop(1);
      wrapped.draw(canvas, 0, 0, boundary, GOLD);
      int finished = goldInRows(bitmap, 0, split);
      check(finished > 0 && goldInRows(bitmap, split, bitmap.getHeight()) == 0,
          "Wrap boundary lost the completed row or highlighted unsung text");

      bitmap.eraseColor(Color.TRANSPARENT);
      wrapped.draw(canvas, 0, 0, boundary + 1.5f, GOLD);
      check(goldInRows(bitmap, 0, split) == finished
          && goldInRows(bitmap, split, layout.getLineTop(2)) > 0,
          "Fractional timing did not advance onto the next wrapped row");
      check(layout.getPaint().getColor() == Color.WHITE, "Highlight changed the base paint color");
    } finally {
      bitmap.recycle();
    }
    return 10 + layout.getLineCount();
  }

  private static TextPaint style(float size, Typeface typeface) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.WHITE);
    paint.setTypeface(typeface);
    paint.setTextSize(size);
    return paint;
  }

  private static int goldInRows(Bitmap bitmap, int top, int bottom) {
    int count = 0;
    for (int y = top; y < bottom; y++) {
      for (int x = 0; x < bitmap.getWidth(); x++) {
        int pixel = bitmap.getPixel(x, y);
        if (Color.alpha(pixel) > 180 && Color.red(pixel) > 200 && Color.blue(pixel) < 100) count++;
      }
    }
    return count;
  }

  private static void check(boolean condition, String error) {
    if (!condition) throw new AssertionError(error);
  }
}
