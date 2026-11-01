package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Visual picker backed by the existing scenic assets, CameraX preview and import flow. */
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
    "Bayan Dawn", "Bamboo Mist", "Coral Haze", "Deep Lagoon", "Ember Sunset",
    "Indigo Horizon", "Mango Glow", "Moonlit Bay", "Orchid Night", "Pearl Sky"
  };
  private static final String[] SCENES = {
    "bayanDawn", "bambooMist", "coralHaze", "deepLagoon", "emberSunset",
    "indigoHorizon", "mangoGlow", "moonlitBay", "orchidNight", "pearlSky"
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
    for (int row = 0; row < 4; row++) {
      LinearLayout line = new LinearLayout(context);
      line.setOrientation(HORIZONTAL);
      for (int col = 0; col < 3; col++) {
        int index = row * 3 + col;
        if (index >= LABELS.length) break;
        final String label = LABELS[index];
        final String scene = SCENES[index];
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(VERTICAL);
        tile.setBackground(KantaTheme.backgroundDrawable(
            KantaTheme.SURFACE, 13,
            selectedBackground.equals(label) ? KantaTheme.PRIMARY_CYAN : KantaTheme.BORDER,
            selectedBackground.equals(label) ? 2 : 1, density));
        tile.setPadding(dp(5), dp(5), dp(5), dp(7));
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription(label + " background preview");
        Bitmap preview = loadThumbnail(scene);
        if (preview != null) image.setImageBitmap(preview);
        else image.setBackgroundColor(KantaTheme.SURFACE_ELEVATED);
        tile.addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, dp(92)));
        TextView name = new TextView(context);
        name.setText(selectedBackground.equals(label) ? "\u2713  " + label : label);
        name.setTextColor(selectedBackground.equals(label) ? KantaTheme.PRIMARY_CYAN : KantaTheme.TEXT_PRIMARY);
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
        LayoutParams p = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        p.bottomMargin = dp(8);
        line.addView(tile, p);
      }
      parent.addView(line);
    }
    Button custom = smallButton("＋  Custom background");
    custom.setTag("kanta.visuals.custom-import");
    custom.setOnClickListener(v -> { if (listener != null) listener.onCustomImport(); });
    LayoutParams customParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(48));
    customParams.topMargin = dp(4);
    parent.addView(custom, customParams);
  }

  private Bitmap loadThumbnail(String scene) {
    try (java.io.InputStream stream = context.getAssets().open("scenic/" + scene + ".png")) {
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(stream, null, bounds);
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = Math.max(1, Math.max(bounds.outWidth / 320, bounds.outHeight / 180));
      try (java.io.InputStream image = context.getAssets().open("scenic/" + scene + ".png")) {
        return BitmapFactory.decodeStream(image, null, options);
      }
    } catch (Exception ignored) {
      return null;
    }
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

  public void setSelectedScene(String scene) {
    if (scene == null) return;
    for (int i = 0; i < SCENES.length; i++) {
      if (SCENES[i].equals(scene)) {
        selectedBackground = LABELS[i];
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
