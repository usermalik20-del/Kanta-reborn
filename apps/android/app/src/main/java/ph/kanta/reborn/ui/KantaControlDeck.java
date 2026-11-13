package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Hardware-inspired projection of KANTA's existing controls. */
// Programmatic-only view: callers supply runtime dependencies; not inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class KantaControlDeck extends FrameLayout {
  public interface Listener {
    void onAction(String action);
    void onVolumeDelta(float delta);
  }

  private static final int PANEL = 0xFF050D1D;
  private static final int SURFACE = 0xFF0D1930;
  private static final int BORDER = 0xFF24508A;
  private static final int CYAN = 0xFF29D9FF;
  private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float density;
  private final Listener listener;
  private final Map<View, Slot> slots = new LinkedHashMap<>();
  private final VolumeDial dial;

  public KantaControlDeck(Context context, Listener listener) {
    super(context);
    this.listener = listener;
    density = getResources().getDisplayMetrics().density;
    setWillNotDraw(false);
    setBackground(panelBackground());
    setPadding(dp(6), dp(8), dp(6), dp(8));
    setTag("kanta.control-deck");
    setContentDescription("KANTA performance control deck");

    addControl("Search", "search", .22f, .06f, .13f, .18f, false);
    addControl("Queue", "queue", .20f, .27f, .14f, .18f, false);
    addControl("Visuals", "visuals", .22f, .48f, .13f, .18f, false);
    addControl("Controls", "controls", .65f, .06f, .13f, .18f, false);
    addControl("Pitch", "pitch", .66f, .27f, .14f, .18f, false);
    addControl("Coach", "coach", .65f, .48f, .13f, .18f, false);

    addControl("Mic", "microphone", .02f, .06f, .16f, .15f, true);
    addControl("Monitor", "monitor", .02f, .25f, .16f, .15f, true);
    addControl("Play / Pause", "play", .02f, .47f, .17f, .14f, false);
    addControl("Restart", "restart", .02f, .65f, .17f, .13f, false);
    addControl("Next", "next", .02f, .82f, .17f, .12f, false);

    addControl("Camera", "camera", .82f, .06f, .16f, .15f, true);
    addControl("Full screen", "fullscreen", .82f, .25f, .16f, .15f, true);
    addControl("Key −", "key_down", .81f, .47f, .08f, .13f, false);
    addControl("Key +", "key_up", .90f, .47f, .08f, .13f, false);
    addControl("Tempo −", "tempo_down", .81f, .64f, .08f, .13f, false);
    addControl("Tempo +", "tempo_up", .90f, .64f, .08f, .13f, false);
    addControl("Stop", "stop", .82f, .82f, .16f, .12f, false);

    dial = new VolumeDial(context);
    dial.setTag("kanta.control-deck.volume");
    addView(dial);
    slots.put(dial, new Slot(.34f, .07f, .32f, .56f));

    addControl("Stage", "now_playing", .32f, .69f, .17f, .13f, false);
    addControl("My Set", "my_set", .51f, .69f, .17f, .13f, false);
    addControl("YouTube", "youtube", .32f, .85f, .17f, .11f, false);
    addControl("Help", "help", .51f, .85f, .17f, .11f, false);
  }

  public void setVolume(float volume) { dial.setVolume(volume); }

  private void addControl(String label, String action, float x, float y, float w, float h,
      boolean circular) {
    Button button = new Button(getContext());
    button.setText(label);
    button.setTextSize(circular ? 10 : 11);
    button.setTextColor(0xFFF6F8FF);
    button.setTypeface(null, Typeface.BOLD);
    button.setGravity(Gravity.CENTER);
    button.setAllCaps(false);
    button.setMinWidth(0);
    button.setMinimumWidth(0);
    button.setMinHeight(0);
    button.setMinimumHeight(0);
    button.setPadding(dp(3), dp(2), dp(3), dp(2));
    button.setBackground(controlBackground(circular));
    button.setTag("kanta.control-deck." + action);
    button.setContentDescription(label);
    button.setOnClickListener(v -> { if (listener != null) listener.onAction(action); });
    addView(button);
    slots.put(button, new Slot(x, y, w, h));
  }

  private void changeVolume(float delta) {
    if (listener != null) listener.onVolumeDelta(delta);
  }

  @Override protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec);
    int desired = Math.max(dp(500), Math.round(width * .78f));
    int height = resolveSize(desired, heightSpec);
    setMeasuredDimension(width, height);
    int innerW = Math.max(1, width - getPaddingLeft() - getPaddingRight());
    int innerH = Math.max(1, height - getPaddingTop() - getPaddingBottom());
    for (Map.Entry<View, Slot> entry : slots.entrySet()) {
      Slot slot = entry.getValue();
      entry.getKey().measure(
          MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(innerW * slot.w)), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(innerH * slot.h)), MeasureSpec.EXACTLY));
    }
  }

  @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int innerW = right - left - getPaddingLeft() - getPaddingRight();
    int innerH = bottom - top - getPaddingTop() - getPaddingBottom();
    for (Map.Entry<View, Slot> entry : slots.entrySet()) {
      Slot slot = entry.getValue();
      int x = getPaddingLeft() + Math.round(innerW * slot.x);
      int y = getPaddingTop() + Math.round(innerH * slot.y);
      View view = entry.getKey();
      view.layout(x, y, x + view.getMeasuredWidth(), y + view.getMeasuredHeight());
    }
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    line.setColor(0x6629D9FF);
    line.setStyle(Paint.Style.STROKE);
    line.setStrokeWidth(dp(1));
    float cx = getWidth() * .50f;
    float cy = getHeight() * .35f;
    canvas.drawCircle(cx, cy, getWidth() * .205f, line);
    canvas.drawCircle(cx, cy, getWidth() * .225f, line);
  }

  private GradientDrawable panelBackground() {
    GradientDrawable drawable = new GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF071225, PANEL});
    drawable.setCornerRadius(dp(20));
    drawable.setStroke(dp(1), BORDER);
    return drawable;
  }

  private GradientDrawable controlBackground(boolean circular) {
    GradientDrawable drawable = new GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF142847, SURFACE});
    drawable.setCornerRadius(circular ? dp(80) : dp(16));
    drawable.setStroke(dp(1), CYAN);
    return drawable;
  }

  private int dp(int value) { return Math.round(value * density); }

  private static final class Slot {
    final float x, y, w, h;
    Slot(float x, float y, float w, float h) {
      this.x = x; this.y = y; this.w = w; this.h = h;
    }
  }

  private final class VolumeDial extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader fillShader;
    private float volume = 1f;
    private float downY;

    VolumeDial(Context context) {
      super(context);
      setFocusable(true);
      setClickable(true);
      updateDescription();
    }

    void setVolume(float value) {
      volume = Math.max(0f, Math.min(1f, value));
      updateDescription();
      invalidate();
    }

    private void updateDescription() {
      setContentDescription(String.format(Locale.ROOT,
          "Master volume %d percent. Swipe up or down to adjust; tap to increase.",
          Math.round(volume * 100f)));
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      float radius = Math.min(w, h) / 2f - dp(3);
      fillShader = radius > 0 ? new RadialGradient(w / 2f, h / 2f, radius,
          new int[] {0xFF111B34, 0xFF0A1730, 0xFF020815},
          new float[] {0f, .72f, 1f}, Shader.TileMode.CLAMP) : null;
    }

    @Override protected void onDraw(Canvas canvas) {
      float cx = getWidth() / 2f;
      float cy = getHeight() / 2f;
      float radius = Math.min(cx, cy) - dp(3);
      paint.setShader(fillShader);
      canvas.drawCircle(cx, cy, radius, paint);
      paint.setShader(null);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(5));
      paint.setColor(0xFF24508A);
      canvas.drawCircle(cx, cy, radius - dp(4), paint);
      paint.setColor(CYAN);
      paint.setStrokeWidth(dp(4));
      canvas.drawArc(dp(10), dp(10), getWidth() - dp(10), getHeight() - dp(10),
          135f, 270f * volume, false, paint);
      paint.setStyle(Paint.Style.FILL);
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTypeface(Typeface.DEFAULT_BOLD);
      paint.setColor(0xFFAFB8D0);
      paint.setTextSize(dp(11));
      canvas.drawText("MASTER VOLUME", cx, cy - dp(12), paint);
      paint.setColor(0xFFF6F8FF);
      paint.setTextSize(dp(30));
      canvas.drawText(String.valueOf(Math.round(volume * 100f)), cx, cy + dp(23), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        downY = event.getY();
        return true;
      }
      if (event.getAction() == MotionEvent.ACTION_UP) {
        float delta = downY - event.getY();
        if (Math.abs(delta) > dp(12)) changeVolume(delta > 0 ? .05f : -.05f);
        else performClick();
        return true;
      }
      return true;
    }

    @Override public boolean performClick() {
      super.performClick();
      changeVolume(.05f);
      return true;
    }
  }
}
