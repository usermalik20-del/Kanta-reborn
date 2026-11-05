package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.R;
import ph.kanta.reborn.ui.KantaTheme;

/** Shared branded app bar for non-home destinations. */
public final class KantaScreenHeader extends LinearLayout {
  private final float density;
  private final TextView titleView;
  private final LinearLayout actions;

  public KantaScreenHeader(
      Context context, String title, String backDescription, String backTag,
      View.OnClickListener onBack) {
    super(context);
    density = context.getResources().getDisplayMetrics().density;
    setOrientation(HORIZONTAL);
    setGravity(android.view.Gravity.CENTER_VERTICAL);
    setPadding(dp(16), dp(8), dp(16), dp(8));

    ImageButton back = new ImageButton(context);
    back.setImageResource(R.drawable.kanta_back_color);
    back.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
    back.setContentDescription(backDescription);
    back.setTag(backTag);
    back.setPadding(dp(10), dp(10), dp(10), dp(10));
    StateListDrawable backBackground = new StateListDrawable();
    backBackground.addState(new int[]{android.R.attr.state_pressed}, KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE, 16, KantaTheme.PRIMARY_CYAN, 1, density));
    backBackground.addState(new int[]{}, KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE_ELEVATED, 16, KantaTheme.BORDER, 1, density));
    back.setBackground(backBackground);
    back.setOnClickListener(onBack);
    addView(back, new LayoutParams(dp(48), dp(48)));

    titleView = KantaTheme.titleText(context, title, 20);
    titleView.setSingleLine(true);
    titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
    titleParams.leftMargin = dp(12);
    addView(titleView, titleParams);

    actions = new LinearLayout(context);
    actions.setOrientation(HORIZONTAL);
    actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
    addView(actions, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
  }

  public void setTitle(String title) {
    titleView.setText(title);
  }

  public Button addAction(String label, String tag, View.OnClickListener onClick) {
    Button action = new Button(getContext());
    action.setText(label);
    action.setTag(tag);
    action.setAllCaps(false);
    action.setTextSize(13);
    action.setTypeface(null, Typeface.BOLD);
    action.setTextColor(KantaTheme.PRIMARY_CYAN);
    action.setMinWidth(dp(64));
    action.setMinimumHeight(dp(40));
    action.setPadding(dp(12), 0, dp(12), 0);
    action.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE, 12, KantaTheme.BORDER, 1, density));
    action.setOnClickListener(onClick);
    LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(44));
    params.leftMargin = dp(8);
    actions.addView(action, params);
    return action;
  }

  private int dp(int value) {
    return (int) (value * density + 0.5f);
  }
}
