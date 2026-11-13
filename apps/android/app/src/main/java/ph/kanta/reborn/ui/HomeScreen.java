package ph.kanta.reborn.ui;

import ph.kanta.reborn.R;
import ph.kanta.reborn.ui.components.InlineKeyboard;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

// Programmatic-only view: callers supply runtime dependencies; not inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public class HomeScreen extends LinearLayout {
  public interface Listener {
    void onHome();
    void onSing();
    void onPerformance();
    void onExperience();
    void onSettings();
    void onSearch();
    /** Editing invalidates a pending submission without navigating away from Home. */
    void onSearchQueryChanged(String query);
    void onSearchSubmitted(String query);
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
  private LinearLayout reservedSongs;
  private TextView queueHeading;
  private EditText searchInput;
  private java.util.List<String> visibleReservations = java.util.Collections.emptyList();
  private int reservationCount = -1;

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
    // The KANTA brand block lives in the app header (MainActivity) so the dashboard
    // does not stack a second logo and tagline above the search bar.

    // Persistent search with inline keyboard
    LinearLayout searchRow = new LinearLayout(context);
    searchRow.setOrientation(HORIZONTAL);
    searchRow.setGravity(Gravity.CENTER_VERTICAL);
    searchRow.setPadding(dp(16), 0, dp(16), 0);

    searchInput = new EditText(context);
    searchInput.setTag("kanta.home.search");
    searchInput.setSingleLine(true);
    searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
        | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    searchInput.setShowSoftInputOnFocus(false);
    searchInput.setTextColor(KantaTheme.TEXT_PRIMARY);
    searchInput.setHintTextColor(KantaTheme.TEXT_MUTED);
    searchInput.setHint("Search songs, artists, or enter code");
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
    keyboardToggle.setTag("kanta.home.keyboard-toggle");
    keyboardToggle.setContentDescription("Switch keyboard to letters");
    keyboardToggle.setAllCaps(false);
    keyboardToggle.setText(ph.kanta.reborn.R.string.label_letters);
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

    addView(searchRow);

    // Use spare height for the queue, leaving the keypad at the bottom. WRAP_CONTENT
    // preserves the queue's minimum content height when the outer ScrollView is short.
    LinearLayout queuePanel = new LinearLayout(context);
    queuePanel.setOrientation(VERTICAL);
    queuePanel.setMinimumHeight(dp(112));
    queuePanel.setPadding(dp(14), dp(6), dp(14), dp(10));
    queuePanel.setTag("kanta.home.reservations");
    queuePanel.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE, 14, KantaTheme.BORDER, 1, density));
    LinearLayout queueHeader = new LinearLayout(context);
    queueHeader.setGravity(Gravity.CENTER_VERTICAL);
    queueHeading = new TextView(context);
    queueHeading.setTextSize(15);
    queueHeading.setTypeface(null, Typeface.BOLD);
    queueHeading.setTextColor(KantaTheme.TEXT_PRIMARY);
    queueHeader.addView(queueHeading, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    Button viewQueue = new Button(context);
    viewQueue.setText(ph.kanta.reborn.R.string.label_view_all);
    viewQueue.setAllCaps(false);
    viewQueue.setTextSize(12);
    viewQueue.setTextColor(KantaTheme.PRIMARY_CYAN);
    viewQueue.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    viewQueue.setContentDescription("View all reserved songs");
    viewQueue.setTag("kanta.home.queue");
    viewQueue.setOnClickListener(v -> { if (listener != null) listener.onQueue(); });
    queueHeader.addView(viewQueue, new LayoutParams(dp(88), dp(48)));
    queuePanel.addView(queueHeader);
    reservedSongs = new LinearLayout(context);
    reservedSongs.setOrientation(VERTICAL);
    queuePanel.addView(reservedSongs);
    LayoutParams queueParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1);
    queueParams.setMargins(dp(16), dp(10), dp(16), dp(10));
    addView(queuePanel, queueParams);
    setReservedSongs(java.util.Collections.emptyList(), 0);

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
      @Override public void onSubmit() {
        // RES commits the whole entry at once instead of
        // navigating on the first digit.
        searchInput.requestFocus();
        if (listener != null) listener.onSearchSubmitted(searchInput.getText().toString());
      }
    });
    keyboard.setSubmitLabel("RES");
    keyboard.setTag("kanta.home.keyboard");
    addView(keyboard, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    // Toggle between letters and numbers
    keyboardToggle.setOnClickListener(v -> {
      keyboard.toggle();
      boolean letters = keyboard.isLetters();
      keyboardToggle.setText(letters ? "Numbers" : "Letters");
      keyboardToggle.setContentDescription(letters
          ? "Switch keyboard to numbers"
          : "Switch keyboard to letters");
      searchInput.requestFocus();
    });

    // Typing stays on Home. The entry is committed by OK (or by tapping a result), so a
    // five-digit karaoke code can be typed without being navigated away mid-code.
    searchInput.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(android.text.Editable e) {
        if (listener != null) listener.onSearchQueryChanged(e.toString());
      }
    });
    searchInput.setOnEditorActionListener((v, actionId, event) -> {
      if (listener != null) listener.onSearchSubmitted(searchInput.getText().toString());
      return true;
    });
    searchInput.requestFocus();
  }

  public String getSearchText() { return searchInput.getText().toString(); }
  public void clearSearchText() { searchInput.setText(""); }

  /** Read-only, bounded projection; reservation identities and ordering remain controller-owned. */
  public void setReservedSongs(java.util.List<String> songs, int total) {
    if (reservationCount == total && visibleReservations.equals(songs)) return;
    visibleReservations = new java.util.ArrayList<>(songs);
    reservationCount = total;
    queueHeading.setText(queueHeading.getContext().getString(ph.kanta.reborn.R.string.reserved_song_count, total));
    reservedSongs.removeAllViews();
    if (songs.isEmpty()) {
      TextView empty = reservationText("No songs reserved yet. Find a song to add it here.");
      empty.setTag("kanta.home.queue.empty");
      reservedSongs.addView(empty);
    } else {
      for (int i = 0; i < Math.min(3, songs.size()); i++) {
        TextView row = reservationText((i + 1) + ". " + songs.get(i));
        row.setSingleLine(true);
        row.setEllipsize(android.text.TextUtils.TruncateAt.END);
        reservedSongs.addView(row);
      }
    }
  }

  private TextView reservationText(String value) {
    TextView row = new TextView(context);
    row.setText(value);
    row.setTextSize(13);
    row.setTextColor(KantaTheme.TEXT_SECONDARY);
    row.setPadding(0, dp(3), 0, dp(3));
    return row;
  }

  private Button groupCard(String title, String detail, String icon, int action) {
    Button card = new Button(context);
    card.setAllCaps(false);
    card.setText(card.getContext().getString(ph.kanta.reborn.R.string.home_collection_card, icon, title, detail));
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
