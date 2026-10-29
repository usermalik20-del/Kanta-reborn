package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import ph.kanta.reborn.core.connect.QrEncoder;

/**
 * Draws a {@link QrEncoder.Symbol} so a phone can scan it.
 *
 * <p>Rendering choices are about scanability, not looks: a full four-module quiet zone,
 * integer module scaling so no module straddles a pixel boundary, and maximum contrast.
 * A blurry or tight QR is the most common reason a pairing scan fails.
 *
 * <p>The content description deliberately does not repeat the encoded link. The link
 * carries the single-session pairing code, and a screen reader announcing it would leak
 * the secret to anyone in earshot.
 */
public final class QrCodeView extends View {

  /** Quiet zone in modules. Four is the minimum the specification allows. */
  private static final int QUIET_ZONE_MODULES = 4;

  private final Paint darkPaint = new Paint();
  private final Paint lightPaint = new Paint();

  private QrEncoder.Symbol symbol;
  private int lastQuietPixels = -1;

  public QrCodeView(Context context) {
    this(context, null);
  }

  public QrCodeView(Context context, AttributeSet attrs) {
    super(context, attrs);
    darkPaint.setColor(Color.BLACK);
    darkPaint.setStyle(Paint.Style.FILL);
    lightPaint.setColor(Color.WHITE);
    lightPaint.setStyle(Paint.Style.FILL);
    setContentDescription("Kanta-Connect pairing QR code");
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  /** Sets the symbol to draw, or null to clear. */
  public void setSymbol(QrEncoder.Symbol value) {
    this.symbol = value;
    invalidate();
  }

  /** The symbol currently drawn, or null. */
  public QrEncoder.Symbol symbol() {
    return symbol;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // Always square: a stretched QR is still decodable but wastes contrast and space.
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);
    int mode = MeasureSpec.getMode(widthMeasureSpec);
    int size;
    if (mode == MeasureSpec.EXACTLY && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
      size = Math.min(width, height);
    } else {
      size = Math.min(
          width > 0 ? width : getSuggestedMinimumWidth(),
          height > 0 ? height : getSuggestedMinimumHeight());
      if (size <= 0) size = 256;
    }
    setMeasuredDimension(size, size);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.drawColor(Color.WHITE);
    if (symbol == null || symbol.size <= 0) return;

    int width = getWidth();
    int height = getHeight();
    if (width <= 0 || height <= 0) return;

    int totalModules = symbol.size + QUIET_ZONE_MODULES * 2;
    // Floor to an integer module size so every module lands on whole pixels.
    int modulePixels = Math.max(1, Math.min(width, height) / totalModules);
    int drawn = modulePixels * totalModules;
    float left = (width - drawn) / 2f;
    float top = (height - drawn) / 2f;
    lastQuietPixels = modulePixels * QUIET_ZONE_MODULES;

    float originX = left + lastQuietPixels;
    float originY = top + lastQuietPixels;
    for (int row = 0; row < symbol.size; row++) {
      float y = originY + row * modulePixels;
      for (int col = 0; col < symbol.size; col++) {
        if (!symbol.dark(row, col)) continue;
        float x = originX + col * modulePixels;
        canvas.drawRect(
            x, y, x + modulePixels, y + modulePixels, darkPaint);
      }
    }
  }

  @Override
  public String toString() {
    return "QrCodeView[modules="
        + (symbol == null ? 0 : symbol.size)
        + ", quietPixels="
        + lastQuietPixels
        + "]";
  }
}
