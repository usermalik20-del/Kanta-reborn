package ph.kanta.reborn.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.ui.components.KantaScreenHeader;

/** Goal-oriented landing page for one primary KANTA user group. */
// Programmatic-only view: callers supply runtime dependencies; not inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
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
  private KantaScreenHeader header;
  private TextView description;
  private LinearLayout grid;
  private LinearLayout[] cards;
  private int columns;
  private boolean compact;
  private boolean fillViewport;

  public void setFillViewport() {
    fillViewport = true;
    ((LayoutParams) grid.getLayoutParams()).weight = 1;
    description.setTextSize(16);
    arrangeCards(compact, columns);
    requestLayout();
  }

  public GroupScreen(Context context, String title, String purpose, Item[] items, Listener listener) {
    super(context); this.context = context; this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener; setOrientation(VERTICAL); setBackgroundColor(KantaTheme.BG_MAIN);
    build(title, purpose, items);
  }
  private int dp(int value) { return (int) (value * density + 0.5f); }
  private void build(String title, String purpose, Item[] items) {
    header = new KantaScreenHeader(context, title, "Back", "kanta.group.back",
        v -> { if (listener != null) listener.onBack(); });
    addView(header);
    description = KantaTheme.bodyText(context, purpose, 14);
    description.setTextColor(KantaTheme.TEXT_SECONDARY);
    addView(description);
    grid = new LinearLayout(context);
    grid.setOrientation(VERTICAL);
    addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    cards = new LinearLayout[items.length];
    for (int i = 0; i < items.length; i++) cards[i] = itemButton(items[i]);
    arrangeCards(false, 2);
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    boolean shortLandscape = CompactLandscape.applies(this, heightMeasureSpec);
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int count = !shortLandscape ? 2 : width >= dp(656) ? 4 : width >= dp(456) ? 3
        : width >= dp(208) ? 2 : 1;
    if (fillViewport) {
      float fontScale = getResources().getConfiguration().fontScale;
      count = Math.min(count, Math.max(1, (int) (width / (dp(shortLandscape ? 164 : 146) * fontScale))));
    }
    if (shortLandscape != compact || count != columns) arrangeCards(shortLandscape, count);
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  private void arrangeCards(boolean shortLandscape, int count) {
    compact = shortLandscape;
    columns = count;
    header.setPadding(dp(compact ? 8 : 16), dp(compact ? 0 : 8),
        dp(compact ? 8 : 16), dp(compact ? 0 : 8));
    description.setPadding(dp(compact ? 10 : 24), dp(2), dp(compact ? 10 : 24), dp(compact ? 4 : 14));
    grid.setPadding(0, 0, 0, dp(compact ? 4 : 16));
    for (int i = 0; i < grid.getChildCount(); i++) ((LinearLayout) grid.getChildAt(i)).removeAllViews();
    grid.removeAllViews();
    int gap = dp(compact ? 2 : 4);
    for (int first = 0; first < cards.length; first += columns) {
      LinearLayout row = new LinearLayout(context);
      for (int col = 0; col < columns && first + col < cards.length; col++) {
        LinearLayout card = cards[first + col];
        card.setOrientation(compact ? HORIZONTAL : VERTICAL);
        card.setGravity(compact ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        card.setMinimumHeight(dp(compact ? 48 : 116));
        card.setPadding(dp(8), dp(compact ? 3 : 8), dp(8), dp(compact ? 3 : 8));
        View icon = card.getChildAt(0);
        int iconSize = dp(compact ? 24 : 36);
        LayoutParams iconParams = new LayoutParams(iconSize, iconSize);
        if (compact) iconParams.setMarginEnd(dp(6));
        icon.setLayoutParams(iconParams);
        LinearLayout labels = (LinearLayout) card.getChildAt(1);
        labels.setLayoutParams(new LayoutParams(compact ? 0 : LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT, compact ? 1 : 0));
        TextView label = (TextView) labels.getChildAt(0);
        TextView detail = (TextView) labels.getChildAt(1);
        label.setGravity(compact ? Gravity.START : Gravity.CENTER);
        detail.setGravity(compact ? Gravity.START : Gravity.CENTER);
        label.setTextSize(fillViewport ? 19 : 14);
        detail.setTextSize(fillViewport ? 14 : 11);
        label.setMaxLines(fillViewport ? Integer.MAX_VALUE : compact ? 2 : 1);
        detail.setMaxLines(fillViewport ? Integer.MAX_VALUE : 1);
        ((LayoutParams) label.getLayoutParams()).topMargin = dp(compact ? 0 : 5);
        LayoutParams params = new LayoutParams(0,
            fillViewport ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(0, gap, 0, gap);
        if (col > 0) params.setMarginStart(gap * 2);
        row.addView(card, params);
      }
      if (compact && !fillViewport && row.getChildCount() < columns) row.setWeightSum(columns);
      grid.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
          fillViewport ? 1 : 0));
    }
  }
  private LinearLayout itemButton(Item item) {
    LinearLayout card = new LinearLayout(context);
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
    card.addView(icon);
    LinearLayout labels = new LinearLayout(context);
    labels.setOrientation(VERTICAL);
    card.addView(labels);

    TextView title = KantaTheme.titleText(context, item.title, 14);
    title.setTypeface(null, Typeface.BOLD);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LayoutParams titleParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    labels.addView(title, titleParams);

    TextView detail = KantaTheme.bodyText(context, item.detail, 11);
    detail.setTextColor(KantaTheme.TEXT_SECONDARY);
    detail.setMaxLines(1);
    detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
    labels.addView(detail, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    return card;
  }
}

/** Shared breakpoint for utility screens, which normally live inside a vertical ScrollView. */
final class CompactLandscape {
  private CompactLandscape() {}

  static boolean applies(View screen, int heightMeasureSpec) {
    Configuration config = screen.getResources().getConfiguration();
    if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) return false;
    float density = screen.getResources().getDisplayMetrics().density;
    int heightHint = View.MeasureSpec.getSize(heightMeasureSpec);
    // ScrollView measures content without a vertical limit, but normally passes its
    // viewport size as the UNSPECIFIED size hint. Fall back to the window configuration
    // when a host gives no hint, and account for app chrome in short landscape windows.
    return (heightHint > 0 && heightHint <= Math.round(320 * density))
        || (config.screenHeightDp > 0 && config.screenHeightDp <= 480);
  }
}
