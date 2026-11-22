package ph.kanta.reborn.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import ph.kanta.reborn.R;

/** The inset KANTA surfaces used by Menu and its grouped destinations, not by Stage. */
public final class MenuSurface {
  public static final int SECONDARY = KantaTheme.TEXT_SECONDARY;
  private static final int ACCENT = KantaTheme.PRIMARY_CYAN;
  private static final int RIPPLE = 0x4029D9FF;

  private MenuSurface() {}

  public static GradientDrawable background() {
    return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
        new int[]{KantaTheme.BG_ELEVATED, KantaTheme.BG_MAIN, KantaTheme.SURFACE});
  }

  public static LinearLayout header(Context context, String title, String backLabel, String backTag,
      Runnable onBack) {
    LinearLayout header = column(context);
    header.setPadding(dp(context, 18), dp(context, 8), dp(context, 18), dp(context, 16));
    header.addView(backButton(context, backLabel, backTag, onBack),
        new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.topMargin = dp(context, 10);
    header.addView(heading(context, title, 34, 0), params);
    return header;
  }

  public static ImageButton backButton(Context context, String label, String tag, Runnable onBack) {
    ImageButton back = iconAction(context, R.drawable.kanta_menu_back, label);
    back.setTag(tag);
    back.setOnClickListener(v -> onBack.run());
    return back;
  }

  public static LinearLayout section(LinearLayout parent, String title) {
    return section(parent, title, 0);
  }

  public static LinearLayout section(LinearLayout parent, String title, int iconResource) {
    Context context = parent.getContext();
    LinearLayout card = column(context);
    card.setBackground(shape(context, KantaTheme.SURFACE, 22, KantaTheme.BORDER));
    int padding = dp(context, 16);
    card.setPadding(padding, padding, padding, padding);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.setMargins(dp(context, 14), 0, dp(context, 14), dp(context, 16));
    parent.addView(card, params);
    TextView heading = heading(context, title, 19, iconResource);
    heading.setPadding(0, 0, 0, dp(context, 4));
    card.addView(heading);
    return card;
  }

  public static TextView heading(Context context, String title, int sp, int iconResource) {
    TextView heading = label(context, title, sp, KantaTheme.TEXT_PRIMARY, true);
    setLeadingIcon(heading, iconResource);
    heading.setCompoundDrawableTintList(ColorStateList.valueOf(ACCENT));
    ViewCompat.setAccessibilityHeading(heading, true);
    return heading;
  }

  public static TextView detail(Context context, String text) {
    TextView detail = label(context, text, 14, SECONDARY, false);
    detail.setPadding(0, dp(context, 4), 0, dp(context, 8));
    return detail;
  }

  public static View row(Context context, String title, String detail, int iconResource, String tag,
      Runnable action) {
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(context, 56));
    row.setPaddingRelative(dp(context, 6), dp(context, 8), dp(context, 6), dp(context, 8));
    row.setBackground(ripple(surface(context, 12, Color.TRANSPARENT, 0),
        shape(context, Color.WHITE, 12, 0)));
    row.setTag(tag);
    row.setContentDescription(title + ". " + detail);
    row.setFocusable(true);
    row.setOnClickListener(v -> action.run());
    ImageView icon = icon(context, iconResource, 7);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36));
    iconParams.setMarginEnd(dp(context, 12));
    row.addView(icon, iconParams);
    LinearLayout labels = column(context);
    labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    labels.addView(label(context, title, 16, Color.WHITE, true));
    labels.addView(label(context, detail, 13, SECONDARY, false));
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    ImageView chevron = new ImageView(context);
    chevron.setImageResource(R.drawable.kanta_submenu_next);
    chevron.setColorFilter(SECONDARY);
    chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(chevron, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));
    return row;
  }

  public static ImageView icon(Context context, int resource, int inset) {
    ImageView icon = new ImageView(context);
    icon.setImageResource(resource);
    icon.setColorFilter(ACCENT);
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    int padding = dp(context, inset);
    icon.setPadding(padding, padding, padding, padding);
    icon.setBackground(shape(context, KantaTheme.SURFACE_ELEVATED, 10, 0));
    return icon;
  }

  /** Optional compound glyph: the existing label remains the only spoken label. */
  public static void setLeadingIcon(TextView view, int iconResource) {
    Context context = view.getContext();
    Drawable glyph = glyph(context, iconResource);
    view.setCompoundDrawablesRelative(glyph, null, null, null);
    view.setCompoundDrawablePadding(dp(context, 8));
  }

  public static void actionStyle(Button button, boolean primary) {
    Context context = button.getContext();
    int radius = primary ? 26 : 12;
    GradientDrawable normal = primary ? primarySurface(context, radius, 0)
        : shape(context, KantaTheme.SURFACE_ELEVATED, radius, KantaTheme.BORDER);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[]{-android.R.attr.state_enabled},
        shape(context, KantaTheme.SURFACE_ELEVATED, radius, KantaTheme.BORDER));
    states.addState(new int[]{android.R.attr.state_focused}, primary
        ? primarySurface(context, radius, KantaTheme.TEXT_PRIMARY)
        : focusShape(context, KantaTheme.SURFACE_ELEVATED, radius));
    states.addState(new int[]{android.R.attr.state_selected},
        shape(context, KantaTheme.SURFACE, radius, ACCENT));
    states.addState(new int[]{}, normal);
    button.setBackgroundTintList(null);
    button.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x33050B18 : RIPPLE), states, null));
    button.setTextColor(actionColors(primary ? KantaTheme.BG_MAIN : KantaTheme.TEXT_PRIMARY,
        primary ? KantaTheme.BG_MAIN : ACCENT));
    button.setCompoundDrawableTintList(actionColors(primary ? KantaTheme.BG_MAIN : ACCENT,
        primary ? KantaTheme.BG_MAIN : ACCENT));
    button.setMinHeight(dp(context, 48));
    button.setMinimumHeight(dp(context, 48));
    button.setFocusable(true);
    if (primary) button.setGravity(Gravity.CENTER);
  }

  /** Existing icon-only controls (e.g. PDF page and zoom); callers supply the spoken label. */
  public static ImageButton iconAction(Context context, int iconResource, String description) {
    ImageButton button = new ImageButton(context);
    button.setImageDrawable(glyph(context, iconResource));
    button.setImageTintList(actionColors(ACCENT, ACCENT));
    button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    button.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
    button.setBackground(ripple(surface(context, 12, KantaTheme.SURFACE_ELEVATED, KantaTheme.BORDER), null));
    button.setMinimumHeight(dp(context, 48));
    button.setContentDescription(description);
    button.setFocusable(true);
    return button;
  }

  /** Selected artwork keeps its border; the brighter focus ring is a separate state. */
  static void tileStyle(View tile, boolean selected) {
    Context context = tile.getContext();
    tile.setBackground(ripple(surface(context, 13, selected ? KantaTheme.SURFACE_ELEVATED : KantaTheme.SURFACE,
        selected ? ACCENT : KantaTheme.BORDER), null));
  }

  private static Drawable glyph(Context context, int iconResource) {
    if (iconResource == 0) return null;
    Drawable glyph = context.getDrawable(iconResource).mutate();
    int size = dp(context, 20);
    glyph.setBounds(0, 0, size, size);
    return glyph;
  }

  private static ColorStateList actionColors(int normal, int selected) {
    return new ColorStateList(new int[][]{{-android.R.attr.state_enabled},
        {android.R.attr.state_selected}, {}}, new int[]{SECONDARY, selected, normal});
  }

  private static GradientDrawable primarySurface(Context context, int radius, int stroke) {
    GradientDrawable drawable = KantaTheme.gradientDrawable(ACCENT, KantaTheme.PRIMARY_BLUE, radius,
        context.getResources().getDisplayMetrics().density);
    if (stroke != 0) drawable.setStroke(dp(context, 2), stroke);
    return drawable;
  }

  private static GradientDrawable focusShape(Context context, int color, int radius) {
    return KantaTheme.backgroundDrawable(color, radius, KantaTheme.TEXT_PRIMARY, 2,
        context.getResources().getDisplayMetrics().density);
  }

  private static StateListDrawable surface(Context context, int radius, int fill, int stroke) {
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[]{-android.R.attr.state_enabled},
        shape(context, KantaTheme.SURFACE, radius, KantaTheme.BORDER));
    states.addState(new int[]{android.R.attr.state_focused},
        focusShape(context, fill == Color.TRANSPARENT ? KantaTheme.SURFACE_ELEVATED : fill, radius));
    states.addState(new int[]{}, shape(context, fill, radius, stroke));
    return states;
  }

  public static TextView label(Context context, String text, int sp, int color, boolean bold) {
    TextView label = new TextView(context);
    label.setText(text);
    label.setTextSize(sp);
    label.setTextColor(color);
    if (bold) label.setTypeface(null, Typeface.BOLD);
    return label;
  }

  private static LinearLayout column(Context context) {
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  private static GradientDrawable shape(Context context, int color, int radius, int stroke) {
    return KantaTheme.backgroundDrawable(color, radius, stroke, stroke == 0 ? 0 : 1,
        context.getResources().getDisplayMetrics().density);
  }

  private static RippleDrawable ripple(Drawable content, Drawable mask) {
    return new RippleDrawable(ColorStateList.valueOf(RIPPLE), content, mask);
  }

  private static int dp(Context context, int dp) {
    return Math.round(dp * context.getResources().getDisplayMetrics().density);
  }
}
