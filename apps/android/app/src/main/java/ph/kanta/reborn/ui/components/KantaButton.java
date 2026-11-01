package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;

public class KantaButton extends Button {
  public enum Variant { PRIMARY, SECONDARY, OUTLINE, TEXT }
  public KantaButton(Context context) { this(context, null); }
  public KantaButton(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    applyStyle(Variant.PRIMARY);
  }
  public void applyStyle(Variant variant) {
    Context context = getContext();
    float density = context.getResources().getDisplayMetrics().density;
    int cyan = ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN;
    int blue = ph.kanta.reborn.ui.KantaTheme.PRIMARY_BLUE;
    int surface = ph.kanta.reborn.ui.KantaTheme.SURFACE;
    int surfaceElevated = ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED;
    int border = ph.kanta.reborn.ui.KantaTheme.BORDER;
    int textPrimary = ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY;
    int textSecondary = ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY;
    setTypeface(null, Typeface.BOLD);
    setTextSize(14);
    setGravity(Gravity.CENTER);
    setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(12*density));
    GradientDrawable normal, pressed;
    StateListDrawable states = new StateListDrawable();
    switch (variant) {
      case PRIMARY:
        setTextColor(textPrimary);
        normal = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{cyan, blue});
        normal.setCornerRadius(12*density);
        pressed = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(blue, 12, cyan, 2, density);
        break;
      case SECONDARY:
        setTextColor(textPrimary);
        normal = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(surfaceElevated, 12, border, 1, density);
        pressed = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(surface, 12, cyan, 1, density);
        break;
      case OUTLINE:
        setTextColor(cyan);
        normal = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(surfaceElevated, 12, cyan, 1, density);
        pressed = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(cyan, 12, blue, 1, density);
        break;
      case TEXT:
        setTextColor(textSecondary);
        normal = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(surfaceElevated, 0, 0, 0, density);
        pressed = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(surface, 0, 0, 0, density);
        break;
      default:
        normal = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(surfaceElevated, 12, border, 1, density);
        pressed = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(surface, 12, cyan, 1, density);
        break;
    }
    states.addState(new int[]{android.R.attr.state_pressed}, pressed);
    states.addState(new int[]{}, normal);
    setBackground(states);
  }
  public void setDisabled(boolean disabled) {
    setEnabled(!disabled);
    setAlpha(disabled ? 0.45f : 1.0f);
  }
}
