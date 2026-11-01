package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
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
      tab.setTextColor(selected ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      p.leftMargin = (int)(4*density); p.rightMargin = (int)(4*density); p.bottomMargin = (int)(4*density);
      tab.setLayoutParams(p);
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
      tab.setTextColor(selected ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    }
  }
}
