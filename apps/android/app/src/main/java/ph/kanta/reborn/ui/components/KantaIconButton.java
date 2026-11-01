package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;

public class KantaIconButton extends Button {
  public KantaIconButton(Context context) { this(context, null); }
  public KantaIconButton(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    applyStyle();
  }
  public void applyStyle() {
    Context context = getContext();
    float density = context.getResources().getDisplayMetrics().density;
    int surface = ph.kanta.reborn.ui.KantaTheme.SURFACE;
    int surfaceElevated = ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED;
    int border = ph.kanta.reborn.ui.KantaTheme.BORDER;
    int textPrimary = ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY;
    int cyan = ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN;
    setTypeface(null, Typeface.BOLD);
    setTextSize(16);
    setGravity(Gravity.CENTER);
    GradientDrawable normal = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(surfaceElevated, 9999, border, 1, density);
    GradientDrawable pressed = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(surface, 9999, cyan, 1, density);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[]{android.R.attr.state_pressed}, pressed);
    states.addState(new int[]{}, normal);
    setBackground(states);
    setTextColor(textPrimary);
    setPadding(0, 0, 0, 0);
  }
  public void setActive(boolean active) {
    setTextColor(active ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
  }
  public void setDisabled(boolean disabled) {
    setEnabled(!disabled);
    setAlpha(disabled ? 0.45f : 1.0f);
  }
}
