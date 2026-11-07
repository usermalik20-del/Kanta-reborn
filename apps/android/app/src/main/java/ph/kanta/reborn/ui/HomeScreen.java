package ph.kanta.reborn.ui;

import ph.kanta.reborn.R;
import ph.kanta.reborn.ui.components.InlineKeyboard;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
    void onSearchQueryChanged(String query);
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

    addView(header);

    // Persistent search with inline keyboard
    LinearLayout searchRow = new LinearLayout(context);
    searchRow.setOrientation(HORIZONTAL);
    searchRow.setGravity(Gravity.CENTER_VERTICAL);
    searchRow.setPadding(dp(16), 0, dp(16), 0);

    EditText searchInput = new EditText(context);
    searchInput.setSingleLine(true);
    searchInput.setShowSoftInputOnFocus(false);
    searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
        | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    searchInput.setTextColor(KantaTheme.TEXT_PRIMARY);
    searchInput.setHintTextColor(KantaTheme.TEXT_MUTED);
    searchInput.setText("Search songs, artists, or enter code");
    searchInput.setTextSize(14);
    searchInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    searchInput.setPadding(dp(14), 0, dp(10), 0);
    Drawable searchIcon = context.getDrawable(R.drawable.kanta_search_color).mutate();
    searchInput.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null);
    searchInput.setCompoundDrawablePadding(dp(10));
    searchInput.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE_ELEVATED, 14, KantaTheme.BORDER, 1, density));
    searchInput.setMinHeight(dp(54));
    searchRow.addView(searchInput, new LayoutParams(0, dp(54), 1));

    // Keyboard toggle (ABC/123)
    Button keyboardToggle = new Button(context);
    keyboardToggle.setAllCaps(false);
    keyboardToggle.setText("ABC");
    keyboardToggle.setTextSize(11);
    keyboardToggle.setTypeface(null, Typeface.BOLD);
    keyboardToggle.setTextColor(KantaTheme.TEXT_PRIMARY);
    keyboardToggle.setPadding(dp(10), dp(4), dp(10), dp(4));
    keyboardToggle.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE, 12, KantaTheme.BORDER, 1, density));
    keyboardToggle.setMinWidth(0);
    keyboardToggle.setMinimumWidth(0);
    LayoutParams toggleParams = new LayoutParams(dp(64), dp(52));
    toggleParams.leftMargin = dp(6);
    searchRow.addView(keyboardToggle, toggleParams);

    // Code entry shortcut
    ImageView code = new ImageView(context);
    code.setImageResource(R.drawable.kanta_code_color);
    code.setContentDescription("Enter song code");
    code.setPadding(dp(10), dp(10), dp(10), dp(10));
    LayoutParams codeParams = new LayoutParams(dp(48), dp(48));
    codeParams.leftMargin = dp(6);
    searchRow.addView(code, codeParams);

    addView(searchRow);

    // Inline keyboard
    InlineKeyboard keyboard = new InlineKeyboard(context, new InlineKeyboard.Listener() {
      @Override public void onText(String value) {
        int start = Math.max(0, searchInput.getSelectionStart());
        int end = Math.max(start, searchInput.getSelectionEnd());
        searchInput.getText().replace(start, end, value);
        searchInput.setSelection(start + value.length());
        searchInput.requestFocus();
      }
      @Override public void onBackspace() {
        int start = Math.max(0, searchInput.getSelectionStart());
        int end = Math.max(start, searchInput.getSelectionEnd());
        if (start != end) searchInput.getText().delete(start, end);
        else if (start > 0) searchInput.getText().delete(start - 1, start);
        searchInput.requestFocus();
      }
      @Override public void onSubmit() { searchInput.requestFocus(); }
    });
    keyboard.setTag("kanta.home.keyboard");
    addView(keyboard, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    // Toggle between letters and numbers
    keyboardToggle.setOnClickListener(v -> {
      keyboard.toggle();
      boolean letters = keyboard.isLetters();
      keyboardToggle.setText(letters ? "123" : "ABC");
      keyboardToggle.setContentDescription(letters
          ? "Switch keyboard to numbers"
          : "Switch keyboard to letters");
      searchInput.requestFocus();
    });

    // Live search as you type
    searchInput.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {
        if (listener != null) listener.onSearchQueryChanged(s.toString());
      }
      public void afterTextChanged(android.text.Editable e) {}
    });
    searchInput.requestFocus();
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
}
