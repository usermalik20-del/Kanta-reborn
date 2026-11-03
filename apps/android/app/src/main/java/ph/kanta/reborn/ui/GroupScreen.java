package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Goal-oriented landing page for one primary KANTA user group. */
public final class GroupScreen extends LinearLayout {
  public interface Listener { void onAction(String action); void onBack(); }
  public static final class Item {
    public final String action, title, detail, icon;
    public Item(String action, String title, String detail, String icon) {
      this.action = action; this.title = title; this.detail = detail; this.icon = icon;
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
    LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(12), dp(12), dp(12), dp(4));
    Button back = new Button(context); back.setText("←"); back.setAllCaps(false); back.setTextSize(18);
    back.setTextColor(KantaTheme.TEXT_PRIMARY); back.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE, 18, KantaTheme.BORDER, 1, density));
    back.setContentDescription("Back to Home"); back.setOnClickListener(v -> { if (listener != null) listener.onBack(); });
    header.addView(back, new LayoutParams(dp(64), dp(48)));
    TextView heading = KantaTheme.titleText(context, title, 24); heading.setTypeface(null, Typeface.BOLD);
    heading.setPadding(dp(12), 0, 0, 0); header.addView(heading, new LayoutParams(0, -2, 1)); addView(header);
    TextView description = KantaTheme.bodyText(context, purpose, 14); description.setTextColor(KantaTheme.TEXT_SECONDARY);
    description.setPadding(dp(24), dp(2), dp(24), dp(14)); addView(description);
    LinearLayout grid = new LinearLayout(context); grid.setOrientation(VERTICAL); grid.setPadding(dp(12), 0, dp(12), dp(16));
    for (int index = 0; index < items.length; index += 2) {
      LinearLayout row = new LinearLayout(context);
      for (int column = 0; column < 2; column++) { int itemIndex = index + column; if (itemIndex >= items.length) break;
        Button card = itemButton(items[itemIndex]); LayoutParams params = new LayoutParams(0, dp(92), 1);
        params.setMargins(dp(4), dp(4), dp(4), dp(4)); row.addView(card, params); }
      grid.addView(row, new LayoutParams(-1, -2));
    }
    addView(grid);
  }
  private Button itemButton(Item item) {
    Button card = new Button(context); card.setAllCaps(false); card.setText(item.icon + "  " + item.title + "\n" + item.detail);
    card.setTextSize(13); card.setGravity(Gravity.CENTER); card.setTextColor(KantaTheme.TEXT_PRIMARY);
    card.setTypeface(null, Typeface.BOLD); card.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE_ELEVATED, 16, KantaTheme.BORDER, 1, density)); card.setPadding(dp(6), dp(8), dp(6), dp(8));
    card.setContentDescription(item.title + ". " + item.detail); card.setTag("kanta.group." + item.action);
    card.setOnClickListener(v -> { if (listener != null) listener.onAction(item.action); }); return card;
  }
}
