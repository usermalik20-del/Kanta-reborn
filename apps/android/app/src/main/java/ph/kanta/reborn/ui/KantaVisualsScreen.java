package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.R;

/** Visual picker wired to bundled KANTA artwork, CameraStage and the existing media import owner. */
public final class KantaVisualsScreen extends LinearLayout {
  public interface Listener {
    void onBack();
    void onTabSelected(int index, String label);
    void onBackgroundSelected(String name);
    void onCameraToggle(boolean enabled);
    void onEffectSelected(String name);
    void onCustomImport();
  }

  private final Context context;
  private final float density;
  private final Listener listener;
  private int selectedTab;
  private String selectedBackground = "Default";
  private boolean cameraEnabled, cameraStreaming;
  private Button cameraToggle;
  private TextView cameraStatus;

  private static final String[] LABELS = {
    "Default", "Stage Lights", "Galaxy", "Sunset",
    "Ocean", "City", "Neon", "Particles"
  };
  private static final String[] SCENES = {
    "default", "stageLights", "galaxy", "sunset", "ocean", "city", "neon", "particles"
  };
  private static final int[] ARTWORK = {
    R.drawable.kanta_stage_phone,
    R.drawable.kanta_visual_stage_lights,
    R.drawable.kanta_visual_galaxy,
    R.drawable.kanta_visual_sunset,
    R.drawable.kanta_visual_ocean,
    R.drawable.kanta_visual_city,
    R.drawable.kanta_visual_neon,
    R.drawable.kanta_visual_particles
  };

  public KantaVisualsScreen(Context context, Listener listener) {
    super(context);
    this.context = context;
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    setOrientation(VERTICAL);
    setBackgroundColor(KantaTheme.BG_MAIN);
    build();
  }

  private int dp(int value) { return (int) (value * density); }

  private void build() {
    LinearLayout top = new LinearLayout(context);
    top.setOrientation(HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(16), dp(12), dp(16), dp(8));
    ph.kanta.reborn.ui.components.KantaIconButton back =
        new ph.kanta.reborn.ui.components.KantaIconButton(context);
    back.setText("\u2190");
    back.setTextSize(18);
    back.setTag("kanta.screen.visuals.back");
    back.setContentDescription("Back to Home");
    back.setOnClickListener(v -> { if (listener != null) listener.onBack(); });
    top.addView(back);
    top.addView(KantaTheme.titleText(context, "Visuals", 20),
        new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    addView(top);

    LinearLayout tabs = new LinearLayout(context);
    tabs.setOrientation(HORIZONTAL);
    tabs.setPadding(dp(16), dp(4), dp(16), dp(12));
    String[] tabNames = {"Background", "Camera", "Effects"};
    for (int i = 0; i < tabNames.length; i++) {
      final int index = i;
      Button tab = smallButton(tabNames[i]);
      tab.setTag("kanta.visuals.tab." + tabNames[i].toLowerCase(java.util.Locale.ROOT));
      tab.setTextColor(selectedTab == i ? KantaTheme.TEXT_PRIMARY : KantaTheme.TEXT_SECONDARY);
      tab.setBackground(KantaTheme.backgroundDrawable(
          selectedTab == i ? KantaTheme.PRIMARY_PURPLE : KantaTheme.SURFACE,
          12, selectedTab == i ? KantaTheme.PRIMARY_CYAN : KantaTheme.BORDER, 1, density));
      tab.setOnClickListener(v -> {
        selectedTab = index;
        if (listener != null) listener.onTabSelected(index, tabNames[index]);
        refresh();
      });
      LayoutParams p = new LayoutParams(0, dp(44), 1);
      p.leftMargin = dp(3);
      p.rightMargin = dp(3);
      tabs.addView(tab, p);
    }
    addView(tabs);

    android.widget.ScrollView scroll = new android.widget.ScrollView(context);
    scroll.setFillViewport(false);
    LinearLayout content = new LinearLayout(context);
    content.setOrientation(VERTICAL);
    content.setPadding(dp(16), 0, dp(16), dp(24));
    scroll.addView(content);
    addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

    if (selectedTab == 0) buildBackgroundGrid(content);
    else if (selectedTab == 1) buildCameraPanel(content);
    else buildEffectsPanel(content);
  }

  private void buildBackgroundGrid(LinearLayout parent) {
    parent.addView(KantaTheme.titleText(context, "Choose your stage", 15));
    for (int row = 0; row < 3; row++) {
      LinearLayout line = new LinearLayout(context);
      line.setOrientation(HORIZONTAL);
      for (int col = 0; col < 3; col++) {
        int index = row * 3 + col;
        if (index > LABELS.length) break;
        if (index == LABELS.length) {
          line.addView(customTile(), tileParams());
          continue;
        }
        final String label = LABELS[index];
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(VERTICAL);
        boolean selected = selectedBackground.equals(label);
        tile.setBackground(KantaTheme.backgroundDrawable(
            KantaTheme.SURFACE, 13,
            selected ? KantaTheme.PRIMARY_CYAN : KantaTheme.BORDER,
            selected ? 2 : 1, density));
        tile.setPadding(dp(5), dp(5), dp(5), dp(7));
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageResource(ARTWORK[index]);
        image.setContentDescription(label + " background preview");
        tile.addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, dp(92)));
        TextView name = new TextView(context);
        name.setText(selected ? "\u2713  " + label : label);
        name.setTextColor(selected ? KantaTheme.PRIMARY_CYAN : KantaTheme.TEXT_PRIMARY);
        name.setTextSize(12);
        name.setTypeface(null, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(6), 0, 0);
        tile.addView(name);
        tile.setTag("kanta.visuals.background." + label.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
        tile.setOnClickListener(v -> {
          selectedBackground = label;
          if (listener != null) listener.onBackgroundSelected(label);
          refresh();
        });
        line.addView(tile, tileParams());
      }
      parent.addView(line);
    }
  }

  private LayoutParams tileParams() {
    LayoutParams p = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
    p.leftMargin = dp(4);
    p.rightMargin = dp(4);
    p.bottomMargin = dp(8);
    return p;
  }

  private LinearLayout customTile() {
    LinearLayout tile = new LinearLayout(context);
    tile.setOrientation(VERTICAL);
    tile.setGravity(Gravity.CENTER);
    tile.setPadding(dp(5), dp(5), dp(5), dp(7));
    tile.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE, 13, KantaTheme.BORDER, 1, density));
    ImageView icon = new ImageView(context);
    icon.setImageResource(R.drawable.kanta_song_code_outline);
    icon.setColorFilter(KantaTheme.TEXT_PRIMARY);
    icon.setContentDescription("Import custom background");
    icon.setPadding(dp(28), dp(18), dp(28), dp(18));
    tile.addView(icon, new LayoutParams(LayoutParams.MATCH_PARENT, dp(92)));
    TextView label = new TextView(context);
    label.setText("Custom");
    label.setTextColor(KantaTheme.TEXT_PRIMARY);
    label.setTextSize(12);
    label.setTypeface(null, Typeface.BOLD);
    label.setGravity(Gravity.CENTER);
    tile.addView(label);
    tile.setTag("kanta.visuals.custom-import");
    tile.setOnClickListener(v -> { if (listener != null) listener.onCustomImport(); });
    return tile;
  }

  private void buildCameraPanel(LinearLayout parent) {
    LinearLayout card = panelCard();
    card.addView(KantaTheme.titleText(context, "Camera Background", 16));
    TextView description = bodyText(
        "Optional live front-camera preview. Nothing is recorded. Camera permission is requested only when enabled.");
    card.addView(description);
    cameraStatus = bodyText(cameraEnabled
        ? cameraStreaming ? "Camera preview is live" : "Camera is connecting…"
        : "Camera is off");
    cameraStatus.setTag("kanta.visuals.camera.status");
    card.addView(cameraStatus);
    cameraToggle = smallButton(cameraEnabled ? "Turn off camera" : "Enable camera");
    cameraToggle.setTag("kanta.visuals.camera.toggle");
    cameraToggle.setOnClickListener(v -> {
      if (listener != null) listener.onCameraToggle(!cameraEnabled);
    });
    card.addView(cameraToggle);
    parent.addView(card);
  }

  private void buildEffectsPanel(LinearLayout parent) {
    LinearLayout card = panelCard();
    card.addView(KantaTheme.titleText(context, "Stage effects", 16));
    TextView explanation = bodyText(
        "Stage post-processing effects are not implemented yet. The working vocal monitor effects are available under Mic & Voice; no inactive visual controls are shown as enabled.");
    card.addView(explanation);
    parent.addView(card);
  }

  private LinearLayout panelCard() {
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(VERTICAL);
    card.setPadding(dp(16), dp(16), dp(16), dp(16));
    card.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE_ELEVATED, 16, KantaTheme.BORDER, 1, density));
    return card;
  }

  private TextView bodyText(String value) {
    TextView text = new TextView(context);
    text.setText(value);
    text.setTextSize(13);
    text.setTextColor(KantaTheme.TEXT_SECONDARY);
    text.setPadding(0, dp(8), 0, dp(8));
    text.setLineSpacing(dp(2), 1f);
    return text;
  }

  private Button smallButton(String value) {
    Button button = new Button(context);
    button.setText(value);
    button.setAllCaps(false);
    button.setTextSize(12);
    button.setTypeface(null, Typeface.BOLD);
    button.setMinHeight(dp(44));
    button.setTextColor(KantaTheme.TEXT_PRIMARY);
    button.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE, 11, KantaTheme.BORDER, 1, density));
    return button;
  }

  private void refresh() {
    removeAllViews();
    build();
  }

  public void setSelectedTab(int tab) {
    selectedTab = Math.max(0, Math.min(2, tab));
    refresh();
  }

  public void setSelectedScene(String scene) {
    if (scene == null) return;
    if ("bayanDawn".equals(scene) || "default".equals(scene)) {
      selectedBackground = "Default";
      refresh();
      return;
    }
    for (int i = 0; i < SCENES.length; i++) {
      if (SCENES[i].equals(scene)) {
        selectedBackground = LABELS[i];
        refresh();
        return;
      }
    }
  }

  public void setCameraState(boolean enabled, boolean streaming) {
    cameraEnabled = enabled;
    cameraStreaming = streaming;
    if (cameraStatus != null) cameraStatus.setText(
        !enabled ? "Camera is off" : streaming ? "Camera preview is live" : "Camera is connecting…");
    if (cameraToggle != null) cameraToggle.setText(enabled ? "Turn off camera" : "Enable camera");
  }
}
