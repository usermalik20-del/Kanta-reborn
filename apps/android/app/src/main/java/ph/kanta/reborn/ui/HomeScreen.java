package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HomeScreen extends LinearLayout {
  public interface Listener {
    void onHome();
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
    setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.BG_MAIN);
    build();
  }
  private void build() {
    LinearLayout header = new LinearLayout(context);
    header.setOrientation(HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(8*density));
    TextView brand = new TextView(context);
    brand.setText("KANTA");
    brand.setTextSize(26);
    brand.setTypeface(null, Typeface.BOLD);
    brand.setTextColor(ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN);
    header.addView(brand);
    TextView tagline = new TextView(context);
    tagline.setText("SING MORE. TOGETHER.");
    tagline.setTextSize(12);
    tagline.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    header.addView(tagline);
    TextView settings = new TextView(context);
    settings.setText("\u2699");
    settings.setTextSize(16);
    settings.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
    settings.setGravity(Gravity.END);
    settings.setContentDescription("Settings");
    settings.setOnClickListener(v -> { if (listener != null) listener.onSettings(); });
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    sp.weight = 1;
    sp.gravity = Gravity.END;
    settings.setLayoutParams(sp);
    header.addView(settings);
    addView(header);
    LinearLayout hero = new LinearLayout(context);
    hero.setOrientation(VERTICAL);
    hero.setPadding((int)(16*density), 0, (int)(16*density), 0);
    TextView evening = new TextView(context);
    evening.setText("Good evening");
    evening.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    evening.setTypeface(null, Typeface.BOLD);
    evening.setTextSize(18);
    TextView together = new TextView(context);
    together.setText("Music brings people together.");
    together.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    together.setAlpha(0.9f);
    together.setTextSize(16);
    hero.addView(evening);
    hero.addView(together);
    addView(hero);
    LinearLayout searchRow = new LinearLayout(context);
    searchRow.setOrientation(HORIZONTAL);
    searchRow.setPadding((int)(16*density), 0, (int)(16*density), 0);
    TextView searchIcon = new TextView(context);
    searchIcon.setText("\uD83D\uDD0E");
    searchIcon.setTextSize(20);
    searchIcon.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    iconParams.gravity = Gravity.CENTER_VERTICAL;
    searchIcon.setLayoutParams(iconParams);
    searchRow.addView(searchIcon);
    Button searchField = new Button(context);
    searchField.setText("Search songs, artists, or enter code");
    searchField.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    searchField.setTextSize(14);
    searchField.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    LinearLayout.LayoutParams sfParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    sfParams.weight = 1;
    sfParams.leftMargin = (int)(8*density);
    sfParams.bottomMargin = (int)(4*density);
    searchField.setLayoutParams(sfParams);
    GradientDrawable searchBg = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 10,
        ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density);
    StateListDrawable searchStates = new StateListDrawable();
    searchStates.addState(new int[]{android.R.attr.state_focused}, ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE, 10,
        ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN, 1, density));
    searchStates.addState(new int[]{}, searchBg);
    searchField.setBackground(searchStates);
    searchField.setOnClickListener(v -> { if (listener != null) listener.onSearch(); });
    searchRow.addView(searchField);
    Button scan = new Button(context);
    scan.setText("SCAN");
    scan.setTextSize(12);
    scan.setTextColor(ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN);
    scan.setTypeface(null, Typeface.BOLD);
    scan.setPadding((int)(12*density), 0, (int)(12*density), 0);
    scan.setBackground(null);
    LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    scanParams.leftMargin = (int)(4*density);
    scan.setLayoutParams(scanParams);
    searchRow.addView(scan);
    addView(searchRow);
    LinearLayout grid = new LinearLayout(context);
    grid.setOrientation(VERTICAL);
    grid.setPadding((int)(16*density), 0, (int)(16*density), 0);
    String[][] features = {
        {"Search", "\uD83D\uDD0E", "onSearch"},
        {"Queue", "\uD83D\uDC51", "onQueue"},
        {"YouTube", "\uD83C\uDF99", "onYouTube"},
        {"Visuals", "\uD83C\uDFAB", "onVisuals"},
        {"Mic & Voice", "\uD83D\uDCF4", "onMicVoice"},
        {"Pitch & Tempo", "\u266B", "onPitchTempo"},
    };
    for (int row = 0; row < 2; row++) {
      LinearLayout rowLayout = new LinearLayout(context);
      rowLayout.setOrientation(HORIZONTAL);
      for (int col = 0; col < 3; col++) {
        String[] feature = features[row * 3 + col];
        Button card = featureCard(context, feature[0], feature[1], feature[2]);
        LinearLayout.LayoutParams cardParams = new LayoutParams(0, (int)(96*density), 1);
        cardParams.setMargins((int)(4*density), (int)(5*density), (int)(4*density), (int)(5*density));
        rowLayout.addView(card, cardParams);
      }
      grid.addView(rowLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
    addView(grid);
    TextView quickLabel = new TextView(context);
    quickLabel.setText("Quick Play");
    quickLabel.setTextSize(14);
    quickLabel.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    quickLabel.setPadding((int)(16*density), (int)(12*density), 0, 0);
    addView(quickLabel);
    LinearLayout quickRow = new LinearLayout(context);
    quickRow.setOrientation(HORIZONTAL);
    quickRow.setPadding((int)(16*density), 0, (int)(16*density), 0);
    String[][] quick = {
        {"My Set", "\u2606", "onMySet"},
        {"Favorites", "\u2764", "onFavorites"},
        {"Recent", "\uD83D\uDD03", "onRecent"},
    };
    for (String[] q : quick) {
      Button card = featureCard(context, q[0], q[1], q[2]);
      quickRow.addView(card, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    }
    addView(quickRow);
    LinearLayout bottom = new LinearLayout(context);
    bottom.setOrientation(HORIZONTAL);
    bottom.setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.BG_ELEVATED);
    bottom.setPadding(0, (int)(8*density), 0, (int)(16*density));
    String[][] nav = {
        {"Home", "\uD83C\uDFA0", "onHome", "true"},
        {"My Set", "\u2606", "onMySet", "false"},
        {"Scenic", "\uD83C\uDFAB", "onScenic", "false"},
        {"Realm", "\uD83D\uDDFE\uFE0F", "onRealm", "false"},
        {"More", "\u22EE", "onMore", "false"},
    };
    for (String[] n : nav) {
      Button navBtn = new Button(context);
      navBtn.setText(n[0] + "\n" + n[1]);
      navBtn.setTextSize(12);
      navBtn.setTypeface(null, Typeface.BOLD);
      navBtn.setGravity(Gravity.CENTER);
      navBtn.setTextColor(n[3].equals("true") ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
      navBtn.setBackground(null);
      LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
      navParams.bottomMargin = (int)(6*density);
      navBtn.setLayoutParams(navParams);
      switch (n[2]) {
        case "onHome": navBtn.setOnClickListener(v -> { if (listener != null) listener.onHome(); }); break;
        case "onMySet": navBtn.setOnClickListener(v -> { if (listener != null) listener.onMySet(); }); break;
        case "onScenic": navBtn.setOnClickListener(v -> { if (listener != null) listener.onScenic(); }); break;
        case "onRealm": navBtn.setOnClickListener(v -> { if (listener != null) listener.onRealm(); }); break;
        case "onMore": navBtn.setOnClickListener(v -> { if (listener != null) listener.onMore(); }); break;
      }
      bottom.addView(navBtn);
    }
    addView(bottom);
  }
  private Button featureCard(Context context, String label, String icon, String action) {
    Button card = new Button(context);
    card.setText(icon + "\n" + label);
    card.setTextSize(12);
    card.setTypeface(null, Typeface.BOLD);
    card.setGravity(Gravity.CENTER);
    card.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    card.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 16,
        ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
    card.setPadding(0, (int)(10*density), 0, (int)(10*density));
    card.setOnClickListener(v -> {
      if (listener == null) return;
      switch (action) {
        case "onSearch": listener.onSearch(); break;
        case "onQueue": listener.onQueue(); break;
        case "onYouTube": listener.onYouTube(); break;
        case "onVisuals": listener.onVisuals(); break;
        case "onMicVoice": listener.onMicVoice(); break;
        case "onPitchTempo": listener.onPitchTempo(); break;
        case "onMySet": listener.onMySet(); break;
        case "onFavorites": listener.onFavorites(); break;
        case "onRecent": listener.onRecent(); break;
      }
    });
    return card;
  }
}
