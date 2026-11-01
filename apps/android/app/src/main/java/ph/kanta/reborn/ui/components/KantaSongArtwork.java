package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class KantaSongArtwork extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private int color = 0xFF203459;
  private int radius = 12;
  public KantaSongArtwork(Context context) { this(context, null); }
  public KantaSongArtwork(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(color);
  }
  public void setColor(int color) { this.color = color; paint.setColor(color); invalidate(); }
  public void setRadiusDp(int radiusDp) { this.radius = radiusDp; invalidate(); }
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int w = getWidth(), h = getHeight();
    if (w <= 0 || h <= 0) return;
    float density = getResources().getDisplayMetrics().density;
    rect.set(0, 0, w, h);
    canvas.drawRoundRect(rect, radius*density, radius*density, paint);
  }
}
