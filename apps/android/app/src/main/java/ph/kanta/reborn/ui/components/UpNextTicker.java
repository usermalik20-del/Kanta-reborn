package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.List;
import ph.kanta.reborn.core.ReservationQueue;

/** Read-only queue hint. Native marquee owns scrolling; the controller owns the FIFO. */
public final class UpNextTicker extends FrameLayout {
  private static final int MAX_ITEMS = 8;
  private final TextView text;
  private boolean visualActive = true;

  public UpNextTicker(Context context) {
    super(context);
    setTag("kanta.stage.up-next");
    setClickable(false);
    setFocusable(false);
    setBackgroundColor(0xCC070C17);
    text = new TextView(context);
    text.setTag("kanta.stage.up-next.text");
    text.setSingleLine(true);
    text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
    text.setMarqueeRepeatLimit(-1);
    text.setFocusable(false);
    text.setClickable(false);
    text.setGravity(Gravity.CENTER_VERTICAL);
    text.setTextColor(0xFFF3F6FF);
    text.setTextSize(14);
    float density = getResources().getDisplayMetrics().density;
    text.setPadding((int)(12 * density), (int)(6 * density),
        (int)(12 * density), (int)(6 * density));
    addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    setVisibility(GONE);
  }

  public void updateFromSnapshot(List<ReservationQueue.Entry> entries) {
    StringBuilder label = new StringBuilder();
    for (int i = 0; i < Math.min(MAX_ITEMS, entries.size()); i++) {
      ReservationQueue.Entry entry = entries.get(i);
      label.append(i == 0 ? "Up next · " : "   •   ");
      label.append(i + 1).append(". ").append(bounded(entry.song.title, 60));
      if (!entry.singer.isEmpty()) label.append(" — ").append(bounded(entry.singer, 30));
    }
    if (entries.size() > MAX_ITEMS)
      label.append("   •   +").append(entries.size() - MAX_ITEMS).append(" more");
    String next = label.toString();
    // Clock/session publications must not reset the marquee or accessibility text.
    if (!TextUtils.equals(text.getText(), next)) text.setText(next);
    int visibility = entries.isEmpty() ? GONE : VISIBLE;
    if (getVisibility() != visibility) setVisibility(visibility);
    updateScrolling();
  }

  private static String bounded(String value, int max) {
    String clean = value.replace('\n', ' ').replace('\r', ' ');
    int count = clean.codePointCount(0, clean.length());
    return count <= max ? clean : clean.substring(0, clean.offsetByCodePoints(0, max)) + "…";
  }

  public void setVisualActive(boolean active) {
    visualActive = active;
    updateScrolling();
  }

  private void updateScrolling() {
    if (text != null)
      text.setSelected(visualActive && isAttachedToWindow() && isShown()
          && getWindowVisibility() == VISIBLE);
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    updateScrolling();
  }

  @Override protected void onDetachedFromWindow() {
    text.setSelected(false);
    super.onDetachedFromWindow();
  }

  @Override protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    updateScrolling();
  }

  @Override protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    updateScrolling();
  }
}
