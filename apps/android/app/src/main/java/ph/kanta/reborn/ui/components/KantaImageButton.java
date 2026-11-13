package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.widget.ImageButton;

import ph.kanta.reborn.ui.KantaTheme;

/** Accessible image action with the shared KANTA surface and pressed states. */
// Programmatic-only view: callers supply runtime dependencies; not inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class KantaImageButton extends ImageButton {
  private final float density;

  public KantaImageButton(Context context, int iconResource, String description, boolean prominent) {
    super(context);
    density = context.getResources().getDisplayMetrics().density;
    setScaleType(ScaleType.CENTER_INSIDE);
    setIcon(iconResource, description);
    int padding = dp(prominent ? 20 : 10);
    setPadding(padding, padding, padding, padding);

    StateListDrawable backgrounds = new StateListDrawable();
    backgrounds.addState(new int[]{android.R.attr.state_pressed}, KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE,
        9999,
        KantaTheme.PRIMARY_CYAN,
        prominent ? 2 : 1,
        density));
    backgrounds.addState(new int[]{}, KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE_ELEVATED,
        9999,
        prominent ? KantaTheme.PRIMARY_CYAN : KantaTheme.BORDER,
        prominent ? 2 : 1,
        density));
    setBackground(backgrounds);
  }

  public void setIcon(int iconResource, String description) {
    setImageResource(iconResource);
    setContentDescription(description);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    setAlpha(enabled ? 1f : 0.45f);
  }

  private int dp(int value) {
    return (int) (value * density + 0.5f);
  }
}
