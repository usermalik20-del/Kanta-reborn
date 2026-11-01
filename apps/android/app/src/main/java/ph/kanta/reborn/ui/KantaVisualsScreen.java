package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.ui.components.KantaIconButton;
import ph.kanta.reborn.ui.components.KantaTabSelector;

public class KantaVisualsScreen extends LinearLayout {
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
  private int selectedTab = 0;
  private String selectedBackground = "Default";
  public KantaVisualsScreen(Context context, Listener listener) {
    super(context);
    this.context = context;
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    setOrientation(VERTICAL);
    setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.BG_MAIN);
    build();
  }
  private void build() {
    LinearLayout top = new LinearLayout(context);
    top.setOrientation(HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(8*density));
    KantaIconButton back = new KantaIconButton(context);
    back.setText("\u2190"); back.setTextSize(18);
    back.setOnClickListener(v -> { if (listener != null) listener.onBack(); });
    TextView title = new TextView(context);
    title.setText("Visuals");
    title.setTextSize(18);
    title.setTypeface(null, Typeface.BOLD);
    title.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    tp.weight = 1;
    title.setLayoutParams(tp);
    top.addView(back); top.addView(title);
    addView(top);
    KantaTabSelector tabs = new KantaTabSelector(context);
    tabs.setTabs(new String[]{"Background", "Camera", "Effects"}, 0,
        (index, label) -> {
          selectedTab = index;
          if (listener != null) listener.onTabSelected(index, label);
        });
    LinearLayout.LayoutParams tsParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    tsParams.leftMargin = (int)(16*density); tsParams.rightMargin = (int)(16*density);
    addView(tabs, tsParams);
    LinearLayout content = new LinearLayout(context);
    content.setOrientation(VERTICAL);
    content.setPadding((int)(16*density), 0, (int)(16*density), (int)(24*density));
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0);
    cp.weight = 1;
    addView(content, cp);
    if (selectedTab == 0) {
      buildBackgroundGrid(content);
    } else if (selectedTab == 1) {
      buildCameraPanel(content);
    } else {
      buildEffectsPanel(content);
    }
  }
  private void buildBackgroundGrid(LinearLayout parent) {
    TextView label = new TextView(context);
    label.setText("Background");
    label.setTextSize(12);
    label.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    label.setTypeface(null, Typeface.BOLD);
    label.setPadding(0, 0, 0, (int)(8*density));
    parent.addView(label);
    LinearLayout grid = new LinearLayout(context);
    grid.setOrientation(VERTICAL);
    String[] backgrounds = {
        "Default", "Stage Lights", "Galaxy", "Sunset", "Ocean",
        "City", "Neon", "Particles", "Custom"
    };
    int[] colors = {
        0xFF203459, 0xFF4A2A6A, 0xFF1A1A3A, 0xFF6A2A2A, 0xFF2A4A6A,
        0xFF3A3A3A, 0xFF4A1A6A, 0xFF1A3A4A, 0xFF7657FF
    };
    for (int row = 0; row < 3; row++) {
      LinearLayout rowLayout = new LinearLayout(context);
      rowLayout.setOrientation(HORIZONTAL);
      for (int col = 0; col < 3; col++) {
        int idx = row * 3 + col;
        if (idx >= backgrounds.length) break;
        final String name = backgrounds[idx];
        final int color = colors[idx];
        boolean selected = name.equals(selectedBackground);
        Button thumb = new Button(context);
        thumb.setText(name);
        thumb.setTextSize(11);
        thumb.setTypeface(null, Typeface.BOLD);
        thumb.setGravity(Gravity.CENTER);
        thumb.setTextColor(selected ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
        thumb.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
            color, 12, selected ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.BORDER, selected ? 2 : 1, density));
        thumb.setPadding(0, (int)(8*density), 0, (int)(8*density));
        LinearLayout.LayoutParams thp = new LinearLayout.LayoutParams(0, (int)(72*density), 1);
        thp.leftMargin = (int)(4*density); thp.rightMargin = (int)(4*density);
        thumb.setLayoutParams(thp);
        thumb.setOnClickListener(v -> {
          selectedBackground = name;
          if (listener != null) listener.onBackgroundSelected(name);
          refresh();
        });
        rowLayout.addView(thumb);
      }
      grid.addView(rowLayout);
    }
    parent.addView(grid);
  }
  private void buildCameraPanel(LinearLayout parent) {
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(VERTICAL);
    card.setPadding((int)(16*density),(int)(16*density),(int)(16*density),(int)(16*density));
    card.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 16,
        ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
    TextView label = new TextView(context);
    label.setText("Camera Background");
    label.setTextSize(16);
    label.setTypeface(null, Typeface.BOLD);
    label.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    card.addView(label);
    TextView desc = new TextView(context);
    desc.setText("Uses the existing camera preview as stage background.");
    desc.setTextSize(13);
    desc.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    desc.setPadding(0, (int)(8*density), 0, (int)(16*density));
    card.addView(desc);
    Button toggle = new Button(context);
    toggle.setText("Toggle Camera");
    toggle.setTextSize(14);
    toggle.setTypeface(null, Typeface.BOLD);
    toggle.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    toggle.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 12,
        ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
    toggle.setPadding(0, (int)(12*density), 0, (int)(12*density));
    toggle.setOnClickListener(v -> { if (listener != null) listener.onCameraToggle(true); });
    card.addView(toggle);
    parent.addView(card);
  }
  private void buildEffectsPanel(LinearLayout parent) {
    TextView label = new TextView(context);
    label.setText("Effects");
    label.setTextSize(12);
    label.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    label.setTypeface(null, Typeface.BOLD);
    label.setPadding(0, 0, 0, (int)(8*density));
    parent.addView(label);
    LinearLayout grid = new LinearLayout(context);
    grid.setOrientation(VERTICAL);
    String[] effects = {"None", "Glow", "Blur", "Vignette", "Scanlines", "Chroma"};
    for (int row = 0; row < 2; row++) {
      LinearLayout rowLayout = new LinearLayout(context);
      rowLayout.setOrientation(HORIZONTAL);
      for (int col = 0; col < 3; col++) {
        int idx = row * 3 + col;
        if (idx >= effects.length) break;
        final String name = effects[idx];
        Button btn = new Button(context);
        btn.setText(name);
        btn.setTextSize(12);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
        btn.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
            ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 12,
            ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
        btn.setPadding(0, (int)(10*density), 0, (int)(10*density));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, (int)(56*density), 1);
        tp.leftMargin = (int)(4*density); tp.rightMargin = (int)(4*density);
        btn.setLayoutParams(tp);
        final String effName = name;
        btn.setOnClickListener(v -> { if (listener != null) listener.onEffectSelected(effName); });
        rowLayout.addView(btn);
      }
      grid.addView(rowLayout);
    }
    parent.addView(grid);
  }
  private void refresh() {
    removeAllViews();
    build();
  }
}
