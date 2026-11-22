package ph.kanta.reborn.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

public final class PdfPageViewChecks {
  private PdfPageViewChecks() {}

  public static int run(Context context) {
    Bitmap page = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
    Bitmap target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
    try {
      page.eraseColor(Color.RED);
      int[] delta = {0};
      FrameLayout parent = new FrameLayout(context);
      PdfPageView view = new PdfPageView(context, page, PdfPageBounds.FIT_PAGE,
          ignored -> {}, step -> delta[0] = step);
      parent.addView(view, new FrameLayout.LayoutParams(400, 400));
      int spec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY);
      parent.measure(spec, spec);
      parent.layout(0, 0, 400, 400);
      view.draw(new Canvas(target));
      if (target.getPixel(200, 200) != Color.RED || target.getPixel(5, 200) != Color.TRANSPARENT)
        throw new AssertionError("PDF bitmap is not visible at its fitted bounds");
      if (!view.canZoomIn()) throw new AssertionError("Fitted PDF cannot zoom in");
      view.scaleBy(2f);
      view.draw(new Canvas(target));
      if (target.getPixel(5, 200) != Color.RED)
        throw new AssertionError("Zoomed PDF did not fill the viewport");
      long down = android.os.SystemClock.uptimeMillis();
      event(view, down, down, MotionEvent.ACTION_DOWN, 360);
      event(view, down, down + 40, MotionEvent.ACTION_MOVE, 200);
      event(view, down, down + 80, MotionEvent.ACTION_UP, 40);
      if (delta[0] != 1) throw new AssertionError("Left swipe did not advance the PDF");
      return 4;
    } finally {
      target.recycle();
      page.recycle();
    }
  }

  private static void event(View view, long down, long time, int action, float x) {
    MotionEvent event = MotionEvent.obtain(down, time, action, x, 200, 0);
    try {
      view.onTouchEvent(event);
    } finally {
      event.recycle();
    }
  }
}
