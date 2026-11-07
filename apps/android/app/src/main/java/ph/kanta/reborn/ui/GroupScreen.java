package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.ui.components.KantaScreenHeader;

/** Goal-oriented landing page for one primary KANTA user group. */
public final class GroupScreen extends LinearLayout {
  public interface Listener { void onAction(String action); void onBack(); }
  public static final class Item {
    public final String action, title, detail;
    public final int iconResource;
    public Item(String action, String title, String detail, int iconResource) {
      this.action = action; this.title = title; this.detail = detail; this.iconResource = iconResource;
    }
  }
  private final Context context; private final float density; private final Listener listener;
  public GroupScreen(Context context, String title, String purpose, Item[] items, Listener listener) {
    super(context); this.context = context; this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener; setOrientation(VERTICAL); setBackgroundColor(KantaTheme.BG_MAIN);
    build(title, purpose, items);
  }
  private int dp(int value) { return (int) (value * density + 0.5f); }
  private void build(String title, String purpose, Item[] items) {
    addView(new KantaScreenHeader(context, title, "Back to Home", "kanta.group.back",
        v -> { if (listener != null) listener.onBack(); }));
    TextView description = KantaTheme.bodyText(context, purpose, 14); description.setTextColor(KantaTheme.TEXT_SECONDARY);
    description.setPadding(dp(24), dp(2), dp(24), dp(14)); addView(description);
    LinearLayout grid = new LinearLayout(context); grid.setOrientation(VERTICAL); grid.setPadding(dp(12), 0, dp(12), dp(16));
    for (int index = 0; index < items.length; index += 2) {
      LinearLayout row = new LinearLayout(context);
      for (int column = 0; column < 2; column++) { int itemIndex = index + column; if (itemIndex >= items.length) break;
        LinearLayout card = itemButton(items[itemIndex]); LayoutParams params = new LayoutParams(0, dp(116), 1);
        params.setMargins(dp(4), dp(4), dp(4), dp(4)); row.addView(card, params); }
      grid.addView(row, new LayoutParams(-1, -2));
    }
    addView(grid);
  }
  private LinearLayout itemButton(Item item) {
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(VERTICAL);
    card.setGravity(Gravity.CENTER);
    card.setPadding(dp(8), dp(8), dp(8), dp(8));
    StateListDrawable backgrounds = new StateListDrawable();
    backgrounds.addState(new int[]{android.R.attr.state_pressed}, KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE, 16, KantaTheme.PRIMARY_CYAN, 1, density));
    backgrounds.addState(new int[]{}, KantaTheme.edgeLitCard(density));
    card.setBackground(backgrounds);
    card.setClickable(true);
    card.setFocusable(true);
    card.setContentDescription(item.title + ". " + item.detail); card.setTag("kanta.group." + item.action);
    card.setOnClickListener(v -> { if (listener != null) listener.onAction(item.action); });

    ImageView icon = new ImageView(context);
    icon.setImageResource(item.iconResource);
    icon.setContentDescription(null);
    card.addView(icon, new LayoutParams(dp(36), dp(36)));

    TextView title = KantaTheme.titleText(context, item.title, 14);
    title.setTypeface(null, Typeface.BOLD);
    title.setGravity(Gravity.CENTER);
    title.setMaxLines(1);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LayoutParams titleParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    titleParams.topMargin = dp(5);
    card.addView(title, titleParams);

    TextView detail = KantaTheme.bodyText(context, item.detail, 11);
    detail.setTextColor(KantaTheme.TEXT_SECONDARY);
    detail.setGravity(Gravity.CENTER);
    detail.setMaxLines(1);
    detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
    card.addView(detail, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    return card;
  }
}
