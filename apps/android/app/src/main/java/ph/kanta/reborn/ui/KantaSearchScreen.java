package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import ph.kanta.reborn.ui.components.KantaIconButton;
import ph.kanta.reborn.ui.components.KantaMediaRow;
import ph.kanta.reborn.ui.components.KantaSearchBar;
import ph.kanta.reborn.ui.components.KantaTabSelector;
import ph.kanta.reborn.ui.components.InlineKeyboard;

public class KantaSearchScreen extends LinearLayout {
  public interface Listener {
    void onBack();
    void onSearchTextChanged(String query);
    void onTabSelected(int index, String label);
    void onSing(String title, String artist);
    void onSourceAll();
    void onSourceKanta();
    void onSourceYouTube();
    void onSourceLocal();
  }
  private final Context context;
  private final float density;
  private final Listener listener;
  private KantaSearchBar searchBar;
  private KantaTabSelector tabSelector;
  private LinearLayout resultsContainer;
  private TextView emptyView;
  private InlineKeyboard keyboard;
  private Button keyboardToggle;
  public KantaSearchScreen(Context context, Listener listener) {
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
    back.setText("\u2190"); back.setTextSize(18); back.setTag("kanta.screen.search.back");
    back.setOnClickListener(v -> { if (listener != null) listener.onBack(); });
    TextView title = new TextView(context);
    title.setText("Search");
    title.setTextSize(18);
    title.setTypeface(null, Typeface.BOLD);
    title.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    tp.weight = 1;
    title.setLayoutParams(tp);
    top.addView(back); top.addView(title);
    addView(top);
    tabSelector = new KantaTabSelector(context);
    tabSelector.setTabs(new String[]{"All", "KANTA", "YouTube", "Local"}, 0,
        (index, label) -> {
          if (listener != null) listener.onTabSelected(index, label);
          switch (index) {
            case 0: if (listener != null) listener.onSourceAll(); break;
            case 1: if (listener != null) listener.onSourceKanta(); break;
            case 2: if (listener != null) listener.onSourceYouTube(); break;
            case 3: if (listener != null) listener.onSourceLocal(); break;
          }
        });
    LinearLayout.LayoutParams tsParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    tsParams.leftMargin = (int)(16*density); tsParams.rightMargin = (int)(16*density);
    addView(tabSelector, tsParams);
    searchBar = new KantaSearchBar(context);
    searchBar.setEmbeddedKeyboardMode(true);
    LinearLayout inputRow = new LinearLayout(context);
    inputRow.setOrientation(HORIZONTAL);
    inputRow.setGravity(Gravity.CENTER_VERTICAL);
    inputRow.setPadding((int)(16*density), (int)(12*density), (int)(16*density), (int)(4*density));
    inputRow.addView(searchBar, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    keyboardToggle = new Button(context);
    keyboardToggle.setText("123");
    keyboardToggle.setAllCaps(false);
    keyboardToggle.setTextSize(12);
    keyboardToggle.setTextColor(KantaTheme.PRIMARY_CYAN);
    keyboardToggle.setContentDescription("Open KANTA keypad");
    keyboardToggle.setOnClickListener(v -> {
      if (keyboard.getVisibility() == VISIBLE) {
        keyboard.setVisibility(GONE);
        keyboardToggle.setText("123");
        keyboardToggle.setContentDescription("Open KANTA keypad");
      } else {
        keyboard.setVisibility(VISIBLE);
        keyboardToggle.setText("⌨");
        keyboardToggle.setContentDescription("Switch KANTA keypad layout");
      }
    });
    inputRow.addView(keyboardToggle, new LinearLayout.LayoutParams((int)(52*density), (int)(52*density)));
    addView(inputRow);
    keyboard = new InlineKeyboard(context, new InlineKeyboard.Listener() {
      @Override public void onText(String value) {
        searchBar.getEditText().append(value);
      }
      @Override public void onBackspace() {
        android.widget.EditText input = searchBar.getEditText();
        int end = input.getSelectionStart();
        if (end > 0) input.getText().delete(end - 1, end);
      }
      @Override public void onSubmit() {
        if (listener != null) listener.onSearchTextChanged(searchBar.getText());
      }
    });
    keyboard.setVisibility(GONE);
    addView(keyboard, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, (int)(204*density)));
    searchBar.addTextChangedListener(new android.text.TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (listener != null) listener.onSearchTextChanged(s.toString());
      }
      @Override public void afterTextChanged(android.text.Editable s) {}
    });
    searchBar.setOnEditorActionListener((v, actionId, event) -> {
      if (listener != null) listener.onSearchTextChanged(searchBar.getText());
      return true;
    });
    resultsContainer = new LinearLayout(context);
    resultsContainer.setOrientation(VERTICAL);
    resultsContainer.setPadding((int)(16*density), 0, (int)(16*density), 0);
    LinearLayout.LayoutParams rcParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0);
    rcParams.weight = 1;
    addView(resultsContainer, rcParams);
    emptyView = new TextView(context);
    emptyView.setText("No songs found");
    emptyView.setTextSize(15);
    emptyView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
    emptyView.setGravity(Gravity.CENTER);
    emptyView.setVisibility(GONE);
    LinearLayout.LayoutParams evParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0);
    evParams.weight = 1;
    addView(emptyView, evParams);
  }
  public void setQuery(String query) {
    if (searchBar != null) searchBar.getEditText().setText(query);
  }
  public void showResults(String[][] results) {
    resultsContainer.removeAllViews();
    emptyView.setVisibility(GONE);
    if (results == null || results.length == 0) {
      emptyView.setVisibility(VISIBLE);
      return;
    }
    for (String[] r : results) {
      KantaMediaRow row = new KantaMediaRow(context);
      final String title = r[0];
      final String artist = r[1];
      final String color = r.length > 2 ? r[2] : "#203459";
      int artwork = 0;
      if (r.length > 3) {
        try { artwork = Integer.parseInt(r[3]); } catch (NumberFormatException ignored) {}
      }
      final int artworkResource = artwork;
      if (artworkResource != 0) row.bind(artworkResource, title, artist, () -> {
        if (listener != null) listener.onSing(title, artist);
      });
      else row.bind(color, title, artist, () -> {
        if (listener != null) listener.onSing(title, artist);
      });
      resultsContainer.addView(row);
    }
  }
  public void showEmpty() {
    resultsContainer.removeAllViews();
    emptyView.setVisibility(VISIBLE);
  }
  public void showLoading() {
    resultsContainer.removeAllViews();
    emptyView.setVisibility(GONE);
  }
}
