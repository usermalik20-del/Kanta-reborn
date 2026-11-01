package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class KantaTheme {
  public static final int BG_MAIN = 0xFF050B18;
  public static final int BG_ELEVATED = 0xFF081426;
  public static final int SURFACE = 0xFF0D1930;
  public static final int SURFACE_ELEVATED = 0xFF12203A;
  public static final int BORDER = 0xFF203459;
  public static final int PRIMARY_CYAN = 0xFF29D9FF;
  public static final int PRIMARY_BLUE = 0xFF2589FF;
  public static final int PRIMARY_PURPLE = 0xFF7657FF;
  public static final int ACCENT_MAGENTA = 0xFFD946EF;
  public static final int TEXT_PRIMARY = 0xFFF7F9FF;
  public static final int TEXT_SECONDARY = 0xFFA7B1C7;
  public static final int TEXT_MUTED = 0xFF6F7990;
  public static final int SUCCESS = 0xFF36E394;
  public static final int WARNING = 0xFFF5C451;
  public static final int DANGER = 0xFFFF5277;
  private KantaTheme() {}
  public static Drawable stageBackground(Context context, boolean wide) {
    int artwork = wide ? R.drawable.kanta_stage_tv : R.drawable.kanta_stage_phone;
    Drawable image = context.getDrawable(artwork).mutate();
    return new LayerDrawable(new Drawable[] {image, new ColorDrawable(0x55050B18)});
  }
  public static GradientDrawable backgroundDrawable(int color, int radiusDp, int strokeColor, int strokeWidthDp, float density) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radiusDp * density);
    if (strokeColor != 0 && strokeWidthDp > 0) d.setStroke((int)(strokeWidthDp * density), strokeColor);
    return d;
  }
  public static GradientDrawable gradientDrawable(int startColor, int endColor, int radiusDp, float density) {
    GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{startColor, endColor});
    d.setCornerRadius(radiusDp * density);
    return d;
  }
  public static Button primaryButton(Context context, String label, android.view.View.OnClickListener action) {
    float density = context.getResources().getDisplayMetrics().density;
    Button b = new Button(context);
    b.setText(label); b.setTextColor(TEXT_PRIMARY); b.setTextSize(14);
    b.setTypeface(null, Typeface.BOLD); b.setGravity(Gravity.CENTER);
    b.setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(12*density));
    GradientDrawable normal = gradientDrawable(PRIMARY_CYAN, PRIMARY_BLUE, 12, density);
    GradientDrawable pressed = backgroundDrawable(PRIMARY_BLUE, 12, PRIMARY_CYAN, 2, density);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[]{android.R.attr.state_pressed}, pressed);
    states.addState(new int[]{}, normal);
    b.setBackground(states);
    if (action != null) b.setOnClickListener(action);
    return b;
  }
  public static Button secondaryButton(Context context, String label, android.view.View.OnClickListener action) {
    float density = context.getResources().getDisplayMetrics().density;
    Button b = new Button(context);
    b.setText(label); b.setTextColor(TEXT_PRIMARY); b.setTextSize(14);
    b.setGravity(Gravity.CENTER);
    b.setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(12*density));
    GradientDrawable normal = backgroundDrawable(SURFACE_ELEVATED, 12, BORDER, 1, density);
    GradientDrawable pressed = backgroundDrawable(SURFACE, 12, PRIMARY_CYAN, 1, density);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[]{android.R.attr.state_pressed}, pressed);
    states.addState(new int[]{}, normal);
    b.setBackground(states);
    if (action != null) b.setOnClickListener(action);
    return b;
  }
  public static TextView titleText(Context context, String text, float sizeSp) {
    TextView tv = new TextView(context);
    tv.setText(text); tv.setTextSize(sizeSp); tv.setTextColor(TEXT_PRIMARY);
    tv.setTypeface(null, Typeface.BOLD);
    return tv;
  }
  public static TextView bodyText(Context context, String text, float sizeSp) {
    TextView tv = new TextView(context);
    tv.setText(text); tv.setTextSize(sizeSp); tv.setTextColor(TEXT_SECONDARY);
    return tv;
  }
}
