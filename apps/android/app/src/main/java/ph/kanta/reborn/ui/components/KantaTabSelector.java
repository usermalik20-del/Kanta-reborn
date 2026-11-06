package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KantaTabSelector extends LinearLayout {
  private final float density;
  private OnTabSelectedListener listener;
  private int selectedIndex = 0;
  public interface OnTabSelectedListener { void onTabSelected(int index, String label); }
  public KantaTabSelector(Context context) { this(context, null); }
  public KantaTabSelector(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    this.density = context.getResources().getDisplayMetrics().density;
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
  }
  public void setTabs(String[] tabs, int initiallySelected, OnTabSelectedListener listener) {
    removeAllViews();
    this.selectedIndex = initiallySelected;
    this.listener = listener;
    for (int i = 0; i < tabs.length; i++) {
      final int index = i;
      TextView tab = new TextView(getContext());
      tab.setText(tabs[i]);
      tab.setTextSize(13);
      tab.setTypeface(null, Typeface.BOLD);
      tab.setGravity(Gravity.CENTER);
      boolean selected = (i == initiallySelected);
      applyStyle(tab, selected);
      tab.setPadding((int)(8*density), 0, (int)(8*density), 0);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, (int)(42*density), 1);
      p.leftMargin = (int)(3*density); p.rightMargin = (int)(3*density);
      tab.setLayoutParams(p);
      tab.setContentDescription(tabs[i]);
      tab.setOnClickListener(v -> {
        selectedIndex = index;
        refresh();
        if (listener != null) listener.onTabSelected(index, tabs[index]);
      });
      addView(tab);
    }
  }
  private void refresh() {
    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      TextView tab = (TextView) getChildAt(i);
      boolean selected = (i == selectedIndex);
      applyStyle(tab, selected);
    }
  }
  private void applyStyle(TextView tab, boolean selected) {
    tab.setTextColor(selected
        ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN
        : ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    StateListDrawable backgrounds = new StateListDrawable();
    backgrounds.addState(new int[]{android.R.attr.state_pressed},
        ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
            ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 12,
            ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN, 1, density));
    backgrounds.addState(new int[]{},
        ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
            selected ? ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED
                : ph.kanta.reborn.ui.KantaTheme.BG_ELEVATED,
            12,
            selected ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN
                : ph.kanta.reborn.ui.KantaTheme.BORDER,
            1, density));
    tab.setBackground(backgrounds);
  }
}
