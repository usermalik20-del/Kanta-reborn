package ph.kanta.reborn;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import ph.kanta.reborn.ui.KantaTheme;
import ph.kanta.reborn.ui.MenuSurface;

/** Native dialog surfaces shared by the Menu's backup and companion setup flows. */
final class MenuDialogStyle {
  private MenuDialogStyle() {}

  static AlertDialog.Builder dialog(Context context, int title, LinearLayout body) {
    return dialog(context, title, 0, body);
  }

  static AlertDialog.Builder dialog(Context context, int title, int icon, LinearLayout body) {
    ScrollView scroll = new ScrollView(context);
    scroll.addView(body);
    return dialogView(context, context.getString(title), icon, scroll);
  }

  static AlertDialog.Builder dialogView(Context context, String title, int icon, android.view.View view) {
    TextView heading = MenuSurface.heading(context, title, 30, icon);
    heading.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 12));
    return new AlertDialog.Builder(context).setCustomTitle(heading).setView(view);
  }

  static void styleShownDialog(AlertDialog dialog) {
    Context context = dialog.getContext();
    Window window = dialog.getWindow();
    if (window != null) {
      window.setBackgroundDrawable(shape(context, KantaTheme.BG_ELEVATED, 24, KantaTheme.BORDER));
      int widthDp = context.getResources().getConfiguration().screenWidthDp;
      if (widthDp > 0) {
        window.setLayout(dp(context, Math.min(560, Math.max(1, widthDp - 32))),
            ViewGroup.LayoutParams.WRAP_CONTENT);
      }
    }
    for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
        AlertDialog.BUTTON_NEUTRAL}) {
      Button button = dialog.getButton(which);
      if (button == null) continue;
      button.setAllCaps(false);
      MenuSurface.actionStyle(button, false);
    }
  }

  static LinearLayout body(Context context) {
    LinearLayout body = column(context);
    int inset = dp(context, 14);
    body.setPadding(inset, 0, inset, 0);
    return body;
  }

  static LinearLayout column(Context context) {
    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    return column;
  }

  static LinearLayout card(LinearLayout parent, CharSequence title) {
    return card(parent, title, 0);
  }

  static LinearLayout card(LinearLayout parent, CharSequence title, int icon) {
    Context context = parent.getContext();
    LinearLayout card = column(context);
    card.setBackground(shape(context, KantaTheme.SURFACE, 22, KantaTheme.BORDER));
    int inset = dp(context, 14);
    card.setPadding(inset, inset, inset, inset);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.bottomMargin = dp(context, 12);
    parent.addView(card, params);
    if (title != null) {
      TextView heading = MenuSurface.heading(context, title.toString(), 20, icon);
      heading.setPadding(0, 0, 0, dp(context, 6));
      card.addView(heading);
    }
    return card;
  }

  static TextView label(Context context, String text) {
    TextView label = KantaTheme.bodyText(context, text, 15);
    int vertical = dp(context, 6);
    label.setPadding(0, vertical, 0, vertical);
    return label;
  }

  static Button button(Context context, String label, boolean primary, Runnable action) {
    return button(context, label, 0, primary, action);
  }

  static Button button(Context context, String label, int icon, boolean primary, Runnable action) {
    Button button = new Button(context);
    button.setText(label);
    button.setAllCaps(false);
    button.setTextSize(15);
    MenuSurface.setLeadingIcon(button, icon);
    MenuSurface.actionStyle(button, primary);
    if (icon != 0 && !primary) button.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
    button.setPaddingRelative(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
    button.setOnClickListener(view -> action.run());
    return button;
  }

  static void styleInput(EditText input) {
    Context context = input.getContext();
    input.setTextColor(KantaTheme.TEXT_PRIMARY);
    input.setHintTextColor(KantaTheme.TEXT_SECONDARY);
    input.setTextSize(16);
    input.setMinimumHeight(dp(context, 48));
    StateListDrawable surface = new StateListDrawable();
    surface.addState(new int[]{android.R.attr.state_focused},
        KantaTheme.backgroundDrawable(KantaTheme.BG_MAIN, 12, KantaTheme.TEXT_PRIMARY, 2,
            context.getResources().getDisplayMetrics().density));
    surface.addState(new int[]{}, shape(context, KantaTheme.BG_MAIN, 12, KantaTheme.BORDER));
    input.setBackgroundTintList(null);
    input.setBackground(surface);
    input.setPaddingRelative(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
  }

  static void addControl(LinearLayout parent, android.view.View control) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.topMargin = dp(parent.getContext(), 8);
    parent.addView(control, params);
  }

  private static Drawable shape(Context context, int color, int radius, int border) {
    return KantaTheme.backgroundDrawable(color, radius, border, 1,
        context.getResources().getDisplayMetrics().density);
  }

  static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
