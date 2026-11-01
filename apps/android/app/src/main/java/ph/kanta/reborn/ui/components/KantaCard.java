package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class KantaCard extends FrameLayout {
  public KantaCard(Context context) { this(context, null); }
  public KantaCard(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    float density = context.getResources().getDisplayMetrics().density;
    setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 16,
        ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
    setPadding((int)(16*density),(int)(16*density),(int)(16*density),(int)(16*density));
    setClipToOutline(false);
  }
  public void setContent(LinearLayout content) {
    removeAllViews();
    addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
  }
}
