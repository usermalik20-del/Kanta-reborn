package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.BundledBgvCatalog;
import ph.kanta.reborn.R;
import ph.kanta.reborn.ui.components.KantaScreenHeader;

/** Stage Visuals uses bundled artwork and video frames, with media actions owned by the host. */
// Programmatic-only view: callers supply runtime dependencies; not inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class KantaVisualsScreen extends LinearLayout {
  public static final int BACKGROUND = 0;
  // Keep the legacy camera index; old Effects states normalize to a supported tab on entry.
  public static final int VIDEOS = 2;

  public interface Listener {
    void onBack();
    void onTabSelected(int tab);
    void onBackgroundSelected(String id, int drawableResource);
    void onVideoSelected(String id);
    void addControls(int tab, LinearLayout target);
  }

  private static final class Still {
    final String id, label;
    final int thumbnail;

    Still(String id, String label, int thumbnail) {
      this.id = id;
      this.label = label;
      this.thumbnail = thumbnail;
    }
  }

  private static final Still[] BACKGROUNDS = {
    new Still("default", "Default", R.drawable.kanta_stage_phone),
    new Still("stageLights", "Stage Lights", R.drawable.kanta_visual_stage_lights),
    new Still("galaxy", "Galaxy", R.drawable.kanta_visual_galaxy),
    new Still("sunset", "Sunset", R.drawable.kanta_visual_sunset),
    new Still("ocean", "Ocean", R.drawable.kanta_visual_ocean),
    new Still("city", "City", R.drawable.kanta_visual_city),
    new Still("neon", "Neon", R.drawable.kanta_visual_neon),
    new Still("particles", "Particles", R.drawable.kanta_visual_particles)
  };
  private static final Still[] SCENIC_BACKGROUNDS = {
    new Still("bayanDawn", "Bayan Dawn", R.drawable.kanta_scene_bayandawn),
    new Still("bambooMist", "Bamboo Mist", R.drawable.kanta_scene_bamboomist),
    new Still("coralHaze", "Coral Haze", R.drawable.kanta_scene_coralhaze),
    new Still("deepLagoon", "Deep Lagoon", R.drawable.kanta_scene_deeplagoon),
    new Still("emberSunset", "Ember Sunset", R.drawable.kanta_scene_embersunset),
    new Still("indigoHorizon", "Indigo Horizon", R.drawable.kanta_scene_indigohorizon),
    new Still("mangoGlow", "Mango Glow", R.drawable.kanta_scene_mangoglow),
    new Still("moonlitBay", "Moonlit Bay", R.drawable.kanta_scene_moonlitbay),
    new Still("orchidNight", "Orchid Night", R.drawable.kanta_scene_orchidnight),
    new Still("pearlSky", "Pearl Sky", R.drawable.kanta_scene_pearlsky)
  };

  private final Context context;
  private final float density;
  private final Listener listener;
  private final int selectedTab;
  private final String selectedScene, selectedVideo;
  private final boolean canChoose, menuSurface;

  public KantaVisualsScreen(Context context, Listener listener, int selectedTab,
      String selectedScene, String selectedVideo, boolean canChoose) {
    this(context, listener, selectedTab, selectedScene, selectedVideo, canChoose, false);
  }

  public KantaVisualsScreen(Context context, Listener listener, int selectedTab,
      String selectedScene, String selectedVideo, boolean canChoose, boolean menuSurface) {
    super(context);
    this.context = context;
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    this.selectedTab = selectedTab;
    this.selectedScene = selectedScene;
    this.selectedVideo = selectedVideo;
    this.canChoose = canChoose;
    this.menuSurface = menuSurface;
    setOrientation(VERTICAL);
    if (!menuSurface) setBackgroundColor(KantaTheme.BG_MAIN);
    build(menuSurface);
  }

  private int dp(int value) { return Math.round(value * density); }

  private void build(boolean menuSurface) {
    String title = context.getString(R.string.menu_stage_visual);
    if (menuSurface) {
      addView(MenuSurface.header(context, title, context.getString(R.string.menu_back),
          "kanta.screen.visuals.back", listener::onBack));
    } else {
      addView(new KantaScreenHeader(context, title, context.getString(R.string.menu_back), "kanta.screen.visuals.back",
          v -> listener.onBack()));
    }

    TextView stage = KantaTheme.titleText(context, context.getString(R.string.menu_stage), 19);
    stage.setPaddingRelative(dp(16), 0, dp(16), 0);
    androidx.core.view.ViewCompat.setAccessibilityHeading(stage, true);
    addView(stage);

    LinearLayout tabs = new LinearLayout(context);
    tabs.setPadding(dp(12), dp(4), dp(12), dp(12));
    addTab(tabs, BACKGROUND, context.getString(R.string.menu_background_photos), "background", R.drawable.kanta_submenu_photo);
    addTab(tabs, VIDEOS, context.getString(R.string.menu_background_videos), "videos", R.drawable.kanta_submenu_video);
    addView(tabs);

    LinearLayout content = new LinearLayout(context);
    content.setOrientation(VERTICAL);
    content.setPadding(dp(16), 0, dp(16), dp(24));
    // MainActivity's outer ScrollView handles the whole destination, including short screens.
    addView(content);
    if (selectedTab == VIDEOS) {
      content.addView(KantaTheme.titleText(context, "Realm · Built-in videos", 16));
      content.addView(KantaTheme.bodyText(context, "Choose a silent video for your stage.", 13));
      addVideoGrid(content);
    } else if (selectedTab == BACKGROUND) {
      content.addView(KantaTheme.titleText(context, "Still backgrounds", 16));
      addStillGrid(content, BACKGROUNDS, false);
    }
    listener.addControls(selectedTab, content);
    if (selectedTab == BACKGROUND) {
      content.addView(KantaTheme.titleText(context, "Scenic stills", 16));
      addStillGrid(content, SCENIC_BACKGROUNDS, true);
    }
  }

  private void addTab(LinearLayout tabs, int index, String label, String tag, int icon) {
    Button tab = KantaTheme.secondaryButton(context, label, v -> listener.onTabSelected(index));
    boolean selected = selectedTab == index;
    tab.setTag("kanta.visuals.tab." + tag);
    tab.setAllCaps(false);
    tab.setSelected(selected);
    tab.setTextSize(12);
    tab.setPadding(dp(3), dp(8), dp(3), dp(8));
    tab.setMinWidth(0);
    tab.setMinimumWidth(0);
    if (menuSurface) {
      MenuSurface.setLeadingIcon(tab, icon);
      MenuSurface.actionStyle(tab, false);
    } else if (selected) {
      tab.setTextColor(KantaTheme.PRIMARY_CYAN);
    }
    LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
    params.setMargins(dp(3), 0, dp(3), 0);
    tab.setMinHeight(dp(48));
    tabs.addView(tab, params);
  }

  private void addStillGrid(LinearLayout parent, Still[] backgrounds, boolean legacy) {
    LinearLayout line = null;
    for (int i = 0; i < backgrounds.length; i++) {
      Still still = backgrounds[i];
      if (i % 3 == 0) line = addGridRow(parent);
      String tag = "kanta.visuals.background."
          + still.label.toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
      line.addView(thumbnail(still.label, still.thumbnail, tag, still.id.equals(selectedScene),
          () -> listener.onBackgroundSelected(still.id, legacy ? 0 : still.thumbnail)), tileParams());
    }
  }

  private void addVideoGrid(LinearLayout parent) {
    LinearLayout line = null;
    int index = 0;
    for (BundledBgvCatalog.Clip clip : BundledBgvCatalog.BUILT_IN) {
      if (index++ % 3 == 0) line = addGridRow(parent);
      line.addView(thumbnail(clip.title, clip.thumbnail, "kanta.realm.video." + clip.id,
          (clip.id + ".video").equals(selectedVideo), () -> listener.onVideoSelected(clip.id)), tileParams());
    }
  }

  private LinearLayout addGridRow(LinearLayout parent) {
    LinearLayout row = new LinearLayout(context);
    // The explicit weight sum keeps a partial final row aligned to the rows above it.
    row.setWeightSum(3);
    parent.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    return row;
  }

  private LayoutParams tileParams() {
    LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
    params.setMargins(dp(4), dp(4), dp(4), dp(6));
    return params;
  }

  private LinearLayout thumbnail(String label, int artwork, String tag, boolean selected, Runnable action) {
    LinearLayout tile = new LinearLayout(context);
    tile.setOrientation(VERTICAL);
    tile.setPadding(dp(5), dp(5), dp(5), dp(7));
    tile.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE, 13,
        selected ? KantaTheme.PRIMARY_CYAN : KantaTheme.BORDER, selected ? 2 : 1, density));
    android.util.TypedValue selectable = new android.util.TypedValue();
    if (menuSurface) {
      MenuSurface.tileStyle(tile, selected);
    } else if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
        && selectable.resourceId != 0) {
      tile.setForeground(context.getDrawable(selectable.resourceId));
    }

    ImageView image = new ImageView(context);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    image.setImageResource(artwork);
    image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    tile.addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, dp(78)));
    TextView name = KantaTheme.titleText(context, selected && !menuSurface ? "✓  " + label : label, 12);
    if (menuSurface && selected) {
      MenuSurface.setLeadingIcon(name, R.drawable.kanta_submenu_check);
      name.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(KantaTheme.PRIMARY_CYAN));
    }
    name.setTypeface(null, Typeface.BOLD);
    name.setTextColor(selected ? KantaTheme.PRIMARY_CYAN : KantaTheme.TEXT_PRIMARY);
    name.setGravity(Gravity.CENTER);
    name.setMinHeight(dp(40));
    name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    tile.addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    tile.setTag(tag);
    tile.setContentDescription(label);
    tile.setSelected(selected);
    tile.setOnClickListener(v -> action.run());
    tile.setEnabled(canChoose);
    tile.setFocusable(true);
    return tile;
  }
}
