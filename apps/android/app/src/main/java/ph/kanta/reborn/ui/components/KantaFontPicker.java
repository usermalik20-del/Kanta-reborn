package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import ph.kanta.reborn.R;
import ph.kanta.reborn.font.FontStore;
import ph.kanta.reborn.ui.KantaTheme;

/**
 * Font choice list whose built-in rows render the short sample in the actual
 * typeface, so each option demonstrates its own design. Custom (SAF) entries
 * are listed by name; they are confirmed through the two-step sample dialog.
 *
 * <p>Programmatic-only view: the font dialog constructs it; it is never inflated from XML.
 */
@android.annotation.SuppressLint("ViewConstructor")
public final class KantaFontPicker extends ScrollView {
  public interface OnFontPicked {
    /** uri is null for the KANTA default; entry is null for the default too. */
    void onPicked(KantaFontPicker picker, Uri uri, FontStore.FontEntry entry);
  }

  private static final String SAMPLE = "Sing more, together.";

  private final FontStore fonts;
  private final OnFontPicked picked;
  private final LinearLayout list;
  private final java.util.ArrayList<Row> rows = new java.util.ArrayList<>();
  private final float density;
  private String selected = "";
  private final Typeface defaultFace = Typeface.create("sans-serif", Typeface.BOLD);

  public KantaFontPicker(Context context, FontStore fonts,
      List<FontStore.FontEntry> entries, boolean bundled, String saved, OnFontPicked picked) {
    super(context);
    this.fonts = fonts;
    this.picked = picked;
    this.selected = saved == null ? "" : saved;
    this.density = context.getResources().getDisplayMetrics().density;
    setTag("kanta.font.picker");
    setPadding(dp(12), dp(8), dp(12), dp(12));
    list = new LinearLayout(context);
    list.setOrientation(LinearLayout.VERTICAL);
    addView(list, new LayoutParams(-1, -2));

    Row defaultRow = addRow(null, context.getString(R.string.visuals_lyrics_font_default),
        bundled, picked);
    defaultRow.sample(defaultFace);

    for (FontStore.FontEntry entry : entries) {
      Row row = addRow(entry, entry.name, bundled, picked);
      if (bundled) {
        // Bundled faces are small OFL assets and are cached by FontStore after
        // the first decode, so every row renders in its real design. The
        // callback only ever mutates this instance's own row, so a stale
        // arrival after the dialog closed is harmless (the row is detached).
        fonts.preview(entry.uri, face -> row.sample(face));
      }
    }
  }

  /** Move the selection highlight after a successful or failed selection. */
  public void setSelected(String saved) {
    this.selected = saved == null ? "" : saved;
    if (!isAttachedToWindow()) return;
    for (Row row : rows) {
      row.applySelected(row.uriString.equals(this.selected));
    }
  }

  private int dp(int value) { return Math.round(value * density); }

  private Row addRow(FontStore.FontEntry entry, String name, boolean bundled, OnFontPicked picked) {
    Context context = getContext();
    String uriString = entry == null ? "" : entry.uri.toString();
    Row row = new Row(uriString, name);
    LinearLayout view = new LinearLayout(context);
    view.setOrientation(LinearLayout.VERTICAL);
    view.setPadding(dp(14), dp(10), dp(14), dp(10));
    view.setMinimumHeight(dp(56));
    android.util.TypedValue selectable = new android.util.TypedValue();
    if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
        && selectable.resourceId != 0) {
      view.setForeground(context.getDrawable(selectable.resourceId));
    }
    view.setFocusable(true);
    view.setOnClickListener(v -> picked.onPicked(this,
        entry == null ? null : entry.uri, entry));
    view.setTag("kanta.font.picker.row." + name.toLowerCase(java.util.Locale.ROOT)
        .replace(' ', '-').replace('&', 'a'));

    TextView sample = new TextView(context);
    String plainText = bundled || entry == null ? SAMPLE : name;
    sample.setText(plainText);
    sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, bundled || entry == null ? 18 : 14);
    sample.setTextColor(KantaTheme.TEXT_PRIMARY);
    sample.setTypeface(entry == null ? defaultFace : Typeface.DEFAULT_BOLD);
    sample.setMaxLines(2);
    sample.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    TextView nameView = null;
    if (bundled || entry == null) {
      nameView = new TextView(context);
      nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
      nameView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
      nameView.setTextColor(KantaTheme.TEXT_SECONDARY);
      nameView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    view.addView(sample, new LinearLayout.LayoutParams(-1, -2));
    if (nameView != null) {
      LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
      nameParams.setMargins(0, dp(2), 0, 0);
      view.addView(nameView, nameParams);
    }
    row.view = view;
    row.sampleView = sample;
    row.nameView = nameView;
    row.plainText = plainText;
    rows.add(row);
    row.applySelected(uriString.equals(selected));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.setMargins(0, dp(4), 0, dp(4));
    list.addView(view, params);
    return row;
  }

  private static final class Row {
    final String uriString;
    final String name;
    String plainText;
    LinearLayout view;
    TextView sampleView;
    TextView nameView;
    boolean failed;

    Row(String uriString, String name) {
      this.uriString = uriString;
      this.name = name;
    }

    void sample(Typeface face) {
      if (view == null) return;
      if (face == null) {
        failed = true;
        sampleView.setText(R.string.lyric_font_load_failed);
        sampleView.setTextColor(KantaTheme.TEXT_MUTED);
        view.setEnabled(false);
        view.setContentDescription(name + ", unavailable");
        return;
      }
      sampleView.setTypeface(face);
      applyContentDescription(false);
    }

    void applyContentDescription(boolean selected) {
      if (view == null) return;
      view.setContentDescription(name + (failed ? ", unavailable" : "")
          + (selected ? ", selected" : ""));
    }

    void applySelected(boolean selected) {
      if (view == null || failed) return;
      applyContentDescription(selected);
      view.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE, 12,
          selected ? KantaTheme.PRIMARY_CYAN : KantaTheme.BORDER, selected ? 2 : 1,
          view.getResources().getDisplayMetrics().density));
      if (nameView != null) {
        nameView.setTextColor(selected ? KantaTheme.PRIMARY_CYAN : KantaTheme.TEXT_SECONDARY);
        nameView.setText(selected ? "✓ " + name : name);
      } else {
        sampleView.setTextColor(selected ? KantaTheme.PRIMARY_CYAN : KantaTheme.TEXT_PRIMARY);
        sampleView.setText(selected ? "✓ " + plainText : plainText);
      }
    }
  }
}
