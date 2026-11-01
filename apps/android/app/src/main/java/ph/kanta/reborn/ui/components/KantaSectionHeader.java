package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KantaSectionHeader extends LinearLayout {
  private final TextView label;
  private final float density;
  public KantaSectionHeader(Context context) { this(context, null); }
  public KantaSectionHeader(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    this.density = context.getResources().getDisplayMetrics().density;
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(4*density));
    label = new TextView(context);
    label.setText("Section");
    label.setTextSize(13);
    label.setTypeface(null, Typeface.BOLD);
    label.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    label.setAllCaps(true);
    label.setLetterSpacing(0.08f);
    addView(label);
  }
  public void setText(String text) { label.setText(text); }
  public void setColor(int color) { label.setTextColor(color); }
}
