package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.font.FontStore;
import ph.kanta.reborn.settings.SettingsStore;

import java.util.List;

/**
 * Inline Menu page for selecting lyric fonts with sample text preview.
 * Replaces the AlertDialog-based picker.
 */
public class LyricFontPage extends LinearLayout {
  private final Context context;
  private final FontStore fonts;
  private final SettingsStore settings;
  private final MenuSurface parent;

  public LyricFontPage(Context context, MenuSurface parent) {
    super(context);
    this.context = context;
    this.parent = parent;
    this.fonts = FontStore.getInstance(context);
    this.settings = new SettingsStore(context);
    setOrientation(VERTICAL);
    setPadding(dp(16), dp(16), dp(16), dp(16));
    buildPage();
  }

  private void buildPage() {
    // Header
    TextView header = text("Lyric Font", 20, KantaTheme.TEXT_PRIMARY);
    header.setPadding(0, 0, 0, dp(16));
    addView(header);

    // Default font option
    addFontOption("KANTA default", null, settings.getLyricFont().isEmpty());

    // Bundled fonts
    List<FontStore.FontEntry> bundled = fonts.bundledFonts();
    for (FontEntry entry : bundled) {
      addFontOption(entry.name, entry.uri, settings.getLyricFont().equals(entry.uri.toString()));
    }

    // Custom fonts button
    Button customBtn = button("Custom fonts", v -> parent.openPage("CustomFonts"));
    addView(customBtn);
  }

  private void addFontOption(String name, android.net.Uri uri, boolean selected) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(8), 0, dp(8));

    TextView sample = text("Sing more, together.", 16, KantaTheme.TEXT_SECONDARY);
    if (uri != null) {
      Typeface face = fonts.load(uri);
      if (face != null) sample.setTypeface(face);
    }
    sample.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

    Button useBtn = button(selected ? "✓ Used" : "Use", v -> {
      fonts.select(uri, success -> {
        if (success) {
          // Update UI to show selected state
          // In a real impl, we'd refresh the page
        }
      });
    });

    row.addView(sample);
    row.addView(useBtn);
    addView(row);
  }

  private TextView text(String s, int size, int color) {
    TextView v = new TextView(context);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    return v;
  }

  private Button button(String label, View.OnClickListener onClick) {
    Button b = new Button(context);
    b.setText(label);
    b.setOnClickListener(onClick);
    return b;
  }

  private int dp(int px) {
    return (int) (px * context.getResources().getDisplayMetrics().density);
  }
}