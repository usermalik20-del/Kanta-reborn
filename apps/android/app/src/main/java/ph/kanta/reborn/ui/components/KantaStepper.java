package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KantaStepper extends LinearLayout {
  private final TextView valueView;
  private final float density;
  private OnChangeListener listener;
  private String currentValue = "0";
  public interface OnChangeListener { void onDecrement(); void onIncrement(); }
  public KantaStepper(Context context) { this(context, null); }
  public KantaStepper(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    this.density = context.getResources().getDisplayMetrics().density;
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER);
    // Touch target >= 48dp per accessibility guidelines
    int buttonSizeDp = 48;
    int buttonSize = (int) (buttonSizeDp * density);
    int valueMarginDp = 12;
    int valueMargin = (int) (valueMarginDp * density);

    KantaIconButton decrement = new KantaIconButton(context);
    decrement.setText("\u2212");
    decrement.setTextSize(20);
    decrement.setOnClickListener(v -> { if (listener != null) listener.onDecrement(); });
    decrement.setTag("kanta.stepper.decrement");
    valueView = new TextView(context);
    valueView.setText("0");
    valueView.setTextSize(20);
    valueView.setTypeface(null, Typeface.BOLD);
    valueView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    valueView.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    vp.leftMargin = valueMargin; vp.rightMargin = valueMargin;
    valueView.setLayoutParams(vp);
    KantaIconButton increment = new KantaIconButton(context);
    increment.setTag("kanta.stepper.increment");
    increment.setText("+");
    increment.setTextSize(20);
    increment.setOnClickListener(v -> { if (listener != null) listener.onIncrement(); });
    addView(decrement, new LayoutParams(buttonSize, buttonSize));
    addView(valueView);
    addView(increment, new LayoutParams(buttonSize, buttonSize));
  }
  public void setValue(String value) { currentValue = value; valueView.setText(value); }
  public String getValue() { return currentValue; }
  public void setOnChangeListener(OnChangeListener listener) { this.listener = listener; }
}
