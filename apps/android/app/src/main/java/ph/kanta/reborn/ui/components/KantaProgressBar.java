package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class KantaProgressBar extends View {
  private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private int trackColor = 0xFF203459;
  private int progressStart = 0xFF29D9FF;
  private int progressEnd = 0xFF2589FF;
  private float progress = 0f;
  public KantaProgressBar(Context context) { this(context, null); }
  public KantaProgressBar(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    trackPaint.setStyle(Paint.Style.FILL);
    trackPaint.setColor(trackColor);
    progressPaint.setStyle(Paint.Style.FILL);
  }
  public void setProgress(float p) { progress = Math.max(0f, Math.min(1f, p)); invalidate(); }
  public void setTrackColor(int color) { trackColor = color; trackPaint.setColor(color); invalidate(); }
  public void setProgressColors(int start, int end) { progressStart = start; progressEnd = end; invalidate(); }
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int w = getWidth(), h = getHeight();
    if (w <= 0 || h <= 0) return;
    int radius = h / 2;
    rect.set(0, 0, w, h);
    canvas.drawRoundRect(rect, radius, radius, trackPaint);
    if (progress > 0f) {
      int fillW = (int)(w * progress);
      rect.set(0, 0, fillW, h);
      progressPaint.setColor(progressStart);
      canvas.drawRoundRect(rect, radius, radius, progressPaint);
    }
  }
}
