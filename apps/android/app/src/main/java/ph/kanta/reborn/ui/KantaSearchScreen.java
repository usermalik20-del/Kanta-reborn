package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.ui.components.KantaIconButton;
import ph.kanta.reborn.ui.components.KantaMediaRow;
import ph.kanta.reborn.ui.components.KantaSearchBar;
import ph.kanta.reborn.ui.components.KantaTabSelector;

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
    back.setText("\u2190"); back.setTextSize(18);
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
    LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    sbParams.leftMargin = (int)(16*density); sbParams.rightMargin = (int)(16*density);
    sbParams.topMargin = (int)(12*density); sbParams.bottomMargin = (int)(16*density);
    addView(searchBar, sbParams);
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
      row.bind(color, title, artist, () -> {
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
