package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.view.View;
import ph.kanta.reborn.ui.KantaTheme;

/** Compact in-app keyboard that never opens or resizes around the system IME. */
// Programmatic-only view: callers supply runtime dependencies; not inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class InlineKeyboard extends LinearLayout {
  public interface Listener { void onText(String value); void onBackspace(); void onSubmit(); }
  private final Listener listener;
  private final GridLayout grid;
  private boolean letters;
  private final float density;
  /** DEL removes one character; the label is also the stable action token. */
  private static final String BACKSPACE = "DEL";
  private String submitLabel = "OK";
  private static final String[] NUMBERS = {"1","2","3","4","5","6","7","8","9",BACKSPACE,"0","OK"};
  private static final String[] LETTERS = {
      "q","w","e","r","t","y","u","i","o","p",
      "a","s","d","f","g","h","j","k","l",BACKSPACE,
      "z","x","c","v","b","n","m","'","-","OK",
      "123","space",".","/"};

  public InlineKeyboard(Context context, Listener listener) {
    super(context); this.listener = listener; density = getResources().getDisplayMetrics().density;
    setOrientation(VERTICAL); setPadding(dp(6), dp(4), dp(6), dp(4));
    grid = new GridLayout(context); addView(grid, new LayoutParams(-1, dp(196))); refresh();
  }
  private int dp(int value) { return (int)(value * density + .5f); }
  public void setSubmitLabel(String label) { submitLabel = label; refresh(); }
  public void toggle() { letters = !letters; refresh(); }
  public boolean isLetters() { return letters; }
  private void refresh() {
    grid.removeAllViews(); String[] keys = letters ? LETTERS : NUMBERS;
    grid.setColumnCount(letters ? 10 : 3); grid.setRowCount(letters ? 4 : 4);
    for (String value : keys) {
      Button key = new Button(getContext()); key.setText("OK".equals(value) ? submitLabel : value); key.setAllCaps(false); key.setTextSize(letters && value.length() == 1 ? 12 : 11);
      if (BACKSPACE.equals(value)) {
        key.setContentDescription("Delete one character");
        key.setTag("kanta.keyboard.delete");
      } else if ("OK".equals(value)) {
        key.setContentDescription("RES".equals(submitLabel) ? "Reserve song code or find a song" : "Submit search");
        key.setTag("kanta.keyboard.submit");
      }
      key.setTypeface(null, Typeface.BOLD); key.setTextColor(KantaTheme.TEXT_PRIMARY); key.setGravity(Gravity.CENTER);
      key.setPadding(0, 0, 0, 0); key.setMinHeight(0); key.setMinimumHeight(0);
      key.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE_ELEVATED, 8, KantaTheme.BORDER, 1, density));
      key.setOnClickListener(v -> click(value));
      GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = 0; params.height = 0;
      params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
      params.setMargins(dp(2), dp(2), dp(2), dp(2)); grid.addView(key, params);
    }
  }
  private void click(String value) {
    // Dispatch by the action token, not by the configurable submit label.
    if (BACKSPACE.equals(value)) listener.onBackspace();
    else if ("OK".equals(value)) listener.onSubmit();
    else if ("123".equals(value)) { letters = false; refresh(); }
    else if ("space".equals(value)) listener.onText(" ");
    else listener.onText(value);
  }
}
