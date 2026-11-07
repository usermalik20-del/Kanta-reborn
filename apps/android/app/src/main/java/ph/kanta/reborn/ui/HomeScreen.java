package ph.kanta.reborn.ui;

import ph.kanta.reborn.R;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HomeScreen extends LinearLayout {
  public interface Listener {
    void onHome();
    void onSing();
    void onPerformance();
    void onExperience();
    void onSettings();
    void onSearch();
    void onQueue();
    void onYouTube();
    void onVisuals();
    void onMicVoice();
    void onPitchTempo();
    void onMySet();
    void onFavorites();
    void onRecent();
    void onMore();
    void onScenic();
    void onRealm();
  }

  private final Context context;
  private final float density;
  private final Listener listener;

  public HomeScreen(Context context, Listener listener) {
    super(context);
    this.context = context;
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    setOrientation(VERTICAL);
    setBackgroundColor(KantaTheme.BG_MAIN);
    build();
  }

  private int dp(float value) {
    return (int) (value * density + 0.5f);
  }

  private void build() {
    LinearLayout header = new LinearLayout(context);
    header.setOrientation(HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(18), dp(12), dp(18), dp(10));

    ImageView mark = new ImageView(context);
    mark.setImageResource(R.drawable.kanta_mark);
    mark.setContentDescription("KANTA");
    header.addView(mark, new LayoutParams(dp(42), dp(42)));

    LinearLayout brandBlock = new LinearLayout(context);
    brandBlock.setOrientation(VERTICAL);
    brandBlock.setPadding(dp(9), 0, 0, 0);
    TextView brand = KantaTheme.titleText(context, "KANTA", 25);
    brand.setTextColor(KantaTheme.TEXT_PRIMARY);
    TextView tagline = KantaTheme.bodyText(context, "SING MORE. TOGETHER.", 9);
    tagline.setLetterSpacing(0.16f);
    brandBlock.addView(brand);
    brandBlock.addView(tagline);
    header.addView(brandBlock);

    View spacer = new View(context);
    header.addView(spacer, new LayoutParams(0, 1, 1));

    ImageView settings = new ImageView(context);
    settings.setImageResource(R.drawable.kanta_settings_color);
    settings.setContentDescription("Settings");
    settings.setPadding(dp(10), dp(10), dp(10), dp(10));
    settings.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE, 14, KantaTheme.BORDER, 1, density));
    settings.setOnClickListener(v -> { if (listener != null) listener.onSettings(); });
    header.addView(settings, new LayoutParams(dp(44), dp(44)));
    addView(header);

    LinearLayout searchRow = new LinearLayout(context);
    searchRow.setOrientation(HORIZONTAL);
    searchRow.setGravity(Gravity.CENTER_VERTICAL);
    searchRow.setPadding(dp(16), 0, dp(16), 0);

    Button searchField = new Button(context);
    searchField.setAllCaps(false);
    searchField.setText("Search songs, artists, or enter code");
    searchField.setTextColor(KantaTheme.TEXT_SECONDARY);
    searchField.setTextSize(13);
    searchField.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    searchField.setPadding(dp(14), 0, dp(10), 0);
    Drawable searchIcon = context.getDrawable(R.drawable.kanta_search_color).mutate();
    searchField.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null);
    searchField.setCompoundDrawablePadding(dp(10));
    searchField.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE_ELEVATED, 14, KantaTheme.BORDER, 1, density));
    searchField.setMinHeight(dp(54));
    searchField.setOnClickListener(v -> { if (listener != null) listener.onSing(); });
    searchRow.addView(searchField, new LayoutParams(0, dp(54), 1));

    ImageView code = new ImageView(context);
    code.setImageResource(R.drawable.kanta_code_color);
    code.setContentDescription("Enter song code");
    code.setPadding(dp(10), dp(10), dp(10), dp(10));
    LayoutParams codeParams = new LayoutParams(dp(48), dp(48));
    codeParams.leftMargin = dp(6);
    searchRow.addView(code, codeParams);
    addView(searchRow);



    addView(sectionLabel("Continue Singing"));
    LinearLayout quickRow = new LinearLayout(context);
    quickRow.setOrientation(HORIZONTAL);
    quickRow.setPadding(dp(12), 0, dp(12), dp(12));
    LinearLayout mySet = collectionCard("My Set", "Your collection", R.drawable.kanta_collection_my_set, 6);
    LinearLayout favorites = collectionCard("Favorites", "Saved songs", R.drawable.kanta_collection_favorites, 7);
    LinearLayout recent = collectionCard("Recent", "Recently played", R.drawable.kanta_collection_recent, 8);
    for (LinearLayout card : new LinearLayout[]{mySet, favorites, recent}) {
      LinearLayout.LayoutParams params = new LayoutParams(0, dp(96), 1);
      params.setMargins(dp(4), 0, dp(4), 0);
      quickRow.addView(card, params);
    }
    addView(quickRow);

    View bottomSpacer = new View(context);
    addView(bottomSpacer, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

    LinearLayout bottom = new LinearLayout(context);
    bottom.setOrientation(HORIZONTAL);
    bottom.setGravity(Gravity.CENTER);
    bottom.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.BG_ELEVATED, 18, KantaTheme.BORDER, 1, density));
    bottom.setPadding(dp(4), dp(5), dp(4), dp(5));
    String[] labels = {"Home", "Sing", "Performance", "Experience", "More"};
    // Coherent KANTA set: home / mic+sparkle / play+waveform / stage sparkle / 2x2 grid.
    int[] icons = {
      R.drawable.kanta_home_color,
      R.drawable.kanta_sing_color,
      R.drawable.kanta_performance_color,
      R.drawable.kanta_experience_color,
      R.drawable.kanta_more_color
    };
    for (int i = 0; i < labels.length; i++) {
      final int destination = i;
      Button nav = new Button(context);
      nav.setTag("kanta.home.nav." + labels[i]);
      nav.setAllCaps(false);
      nav.setText(labels[i]);
      nav.setTextSize(10);
      nav.setTypeface(null, Typeface.BOLD);
      nav.setGravity(Gravity.CENTER);
      nav.setTextColor(i == 0 ? KantaTheme.PRIMARY_CYAN : KantaTheme.TEXT_MUTED);
      Drawable icon = context.getDrawable(icons[i]).mutate();
      icon.setAlpha(i == 0 ? 255 : 96);
      nav.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
      nav.setCompoundDrawablePadding(dp(3));
      nav.setPadding(0, dp(5), 0, dp(5));
      nav.setBackground(i == 0
          ? KantaTheme.backgroundDrawable(KantaTheme.SURFACE_ELEVATED, 12, KantaTheme.BORDER, 1, density)
          : null);
      nav.setContentDescription(labels[i]);
      nav.setOnClickListener(v -> dispatchNavigation(destination));
      bottom.addView(nav, new LayoutParams(0, dp(58), 1));
    }
    LayoutParams bottomParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    bottomParams.setMargins(dp(12), dp(2), dp(12), dp(10));
    addView(bottom, bottomParams);
  }

  private Button groupCard(String title, String detail, String icon, int action) {
    Button card = new Button(context);
    card.setAllCaps(false);
    card.setText(icon + "\n" + title + "\n" + detail);
    card.setTextSize(10);
    card.setGravity(Gravity.CENTER);
    card.setTextColor(KantaTheme.TEXT_PRIMARY);
    card.setTypeface(null, Typeface.BOLD);
    card.setPadding(dp(2), dp(2), dp(2), dp(2));
    card.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE_ELEVATED, 13, KantaTheme.BORDER, 1, density));
    card.setContentDescription(title + ". " + detail);
    card.setOnClickListener(v -> {
      if (listener == null) return;
      if (action == 0) listener.onSing();
      else if (action == 1) listener.onPerformance();
      else listener.onExperience();
    });
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(68), 1);
    params.setMargins(dp(4), 0, dp(4), 0);
    card.setLayoutParams(params);
    return card;
  }

  private LinearLayout collectionCard(String title, String detail, int artwork, int action) {
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(VERTICAL);
    card.setPadding(dp(6), dp(6), dp(6), dp(6));
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE_ELEVATED, 13, KantaTheme.BORDER, 1, density));
    card.setTag("kanta.home.collection." + title.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
    ImageView image = new ImageView(context);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    image.setImageResource(artwork);
    image.setContentDescription(title + " collection artwork");
    card.addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
    TextView name = KantaTheme.titleText(context, title, 12);
    name.setPadding(dp(2), dp(5), dp(2), 0);
    card.addView(name);
    TextView subtitle = KantaTheme.bodyText(context, detail, 10);
    subtitle.setPadding(dp(2), 0, dp(2), 0);
    card.addView(subtitle);
    card.setClickable(true);
    card.setOnClickListener(v -> dispatchFeature(action));
    return card;
  }

  private TextView sectionLabel(String value) {
    TextView label = KantaTheme.titleText(context, value, 14);
    label.setTextColor(KantaTheme.TEXT_PRIMARY);
    label.setPadding(dp(18), dp(15), dp(18), dp(7));
    return label;
  }

  private Button featureCard(String label, int iconResource, int action) {
    Button card = new Button(context);
    card.setTag("kanta.home.feature." + actionTag(action));
    card.setAllCaps(false);
    card.setText(label);
    card.setTextSize(12);
    card.setTypeface(null, Typeface.BOLD);
    card.setGravity(Gravity.CENTER);
    card.setTextColor(KantaTheme.TEXT_PRIMARY);
    Drawable icon = context.getDrawable(iconResource).mutate();
    card.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
    card.setCompoundDrawablePadding(dp(8));
    card.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE_ELEVATED, 16, KantaTheme.BORDER, 1, density));
    card.setPadding(dp(4), dp(10), dp(4), dp(10));
    card.setMinHeight(dp(48));
    card.setOnClickListener(v -> dispatchFeature(action));
    return card;
  }

  private String actionTag(int action) {
    switch (action) {
      case 0: return "onSearch";
      case 1: return "onQueue";
      case 2: return "onYouTube";
      case 3: return "onVisuals";
      case 4: return "onMicVoice";
      case 5: return "onPitchTempo";
      case 6: return "onMySet";
      case 7: return "onFavorites";
      default: return "onRecent";
    }
  }

  private void dispatchFeature(int action) {
    if (listener == null) return;
    switch (action) {
      case 0: listener.onSearch(); break;
      case 1: listener.onQueue(); break;
      case 2: listener.onYouTube(); break;
      case 3: listener.onVisuals(); break;
      case 4: listener.onMicVoice(); break;
      case 5: listener.onPitchTempo(); break;
      case 6: listener.onMySet(); break;
      case 7: listener.onFavorites(); break;
      case 8: listener.onRecent(); break;
      default: break;
    }
  }

  private void dispatchNavigation(int destination) {
    if (listener == null) return;
    switch (destination) {
      case 0: listener.onHome(); break;
      case 1: listener.onSing(); break;
      case 2: listener.onPerformance(); break;
      case 3: listener.onExperience(); break;
      case 4: listener.onMore(); break;
      default: break;
    }
  }
}
