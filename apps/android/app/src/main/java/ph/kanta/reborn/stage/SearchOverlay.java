package ph.kanta.reborn.stage;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ph.kanta.reborn.core.Song;
import ph.kanta.reborn.core.ReservationQueue;
import ph.kanta.reborn.core.catalog.SongCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Type-ahead search overlay with reserve action.
 *
 * <p>Per {@link replica/design/components.md} SearchOverlay spec.
 * Bottom sheet on handset, center modal on TV.
 */
public final class SearchOverlay extends FrameLayout {

  private static final int ANIM_DURATION_MS = 250;

  // Karaoke machine style keypad keys
  private static final String[] KEYPAD_KEYS = {
      "1", "2", "3", "4", "5", "6", "7", "8", "9",
      "⌫", "0", "✓"
  };
  private static final int SHEET_HEIGHT_PERCENT = 50;

  private final EditText searchField;
  private final RecyclerView resultsList;
  private final ProgressBar loadingSpinner;
  private final TextView emptyView;
  private final SongAdapter adapter;

  private SongCatalog catalog;
  private ReservationQueue queue;
  private SearchListener listener;
  private boolean isVisible = false;

  private final GridLayout keypadGrid;
  private final TextView[] keypadKeyViews = new TextView[12];
  private KeypadListener keypadListener;

  public SearchOverlay(Context context) {
    this(context, null);
  }

  public SearchOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);
    setClipChildren(false);
    setClipToPadding(false);

    float density = context.getResources().getDisplayMetrics().density;

    LinearLayout container = new LinearLayout(context);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_search_field_bg);
    container.setPadding((int)(20 * density), (int)(20 * density), (int)(20 * density), (int)(20 * density));
    container.setOrientation(LinearLayout.VERTICAL);
    container.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_search_field_bg); // reuse as sheet bg
    container.setPadding((int)(20 * density), (int)(20 * density), (int)(20 * density), (int)(20 * density));
    LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    params.gravity = android.view.Gravity.BOTTOM;
    addView(container, params);

    // Search field
    searchField = new EditText(context);
    searchField.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_search_field_bg);
    searchField.setTextColor(0xFFFFFFFF);
    searchField.setHintTextColor(0xFF999999);
    searchField.setHint("Search songs...");
    searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    searchField.setSingleLine(true);
    searchField.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        performSearch(searchField.getText().toString());
        return true;
      }
      return false;
    });
    searchField.addTextChangedListener(new android.text.TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
        performSearch(s.toString());
      }
      @Override public void afterTextChanged(android.text.Editable s) {}
    });
    LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    fieldParams.bottomMargin = (int)(16 * density);
    container.addView(searchField, fieldParams);

    // Loading spinner
    loadingSpinner = new ProgressBar(context, null, android.R.attr.progressBarStyle);
    loadingSpinner.setIndeterminate(true);
    loadingSpinner.setVisibility(GONE);
    LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    spinnerParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
    container.addView(loadingSpinner, spinnerParams);

    // Results list
    resultsList = new RecyclerView(context);
    resultsList.setLayoutManager(new LinearLayoutManager(context));
    adapter = new SongAdapter();
    resultsList.setAdapter(adapter);
    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0);
    listParams.weight = 1;
    container.addView(resultsList, listParams);

// Keypad row - always visible at bottom of search sheet
keypadGrid = new GridLayout(context);
keypadGrid.setColumnCount(3);
keypadGrid.setRowCount(4);
keypadGrid.setPadding((int)(16 * density), (int)(16 * density), (int)(16 * density), (int)(16 * density));
LayoutParams keypadParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
keypadParams.gravity = android.view.Gravity.BOTTOM;
container.addView(keypadGrid, keypadParams);

String[] KEYS = KEYPAD_KEYS;
for (int i = 0; i < KEYS.length; i++) {
  TextView key = new TextView(context);
  key.setText(KEYS[i]);
  key.setTextSize(18);
  key.setTextColor(0xFFFFFFFF);
  key.setTypeface(null, android.graphics.Typeface.BOLD);
  key.setGravity(android.view.Gravity.CENTER);
  key.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_keypad_key_bg);
  key.setMinWidth((int)(48 * density));
  key.setMinHeight((int)(48 * density));
  key.setTag(i);
  key.setOnClickListener(v -> {
    int idx = (Integer) v.getTag();
    String key = KEYS[idx];
    if (key.equals("⌫")) {
      if (keypadListener != null) keypadListener.onBackspace();
    } else if (key.equals("✓")) {
      if (keypadListener != null) keypadListener.onSubmit();
      performSearch(searchField.getText().toString());
    } else {
      // Insert digit into search field
      int selStart = searchField.getSelectionStart();
      if (selStart < 0) selStart = 0;
      searchField.getText().insert(selStart, key);
    }
  });

  GridLayout.LayoutParams keyParams = new GridLayout.LayoutParams();
  keyParams.width = 0;
  keyParams.height = 0;
  keyParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
  keyParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
  keyParams.setMargins((int)(6 * density), (int)(6 * density), (int)(6 * density), (int)(6 * density));
  keypadGrid.addView(key, keyParams);

  // Special styling for backspace and submit
  if (i == 8) { // Backspace
    key.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_keypad_key_backspace_bg);
  } else if (i == 11) { // Submit
    key.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_keypad_key_submit_bg);
  }
  keypadKeyViews[i] = key;
}

    // Empty view
    emptyView = new TextView(context);
    emptyView.setText("No songs found");

  /** Listener for keypad events from the search overlay. */
  public interface KeypadListener {
    void onBackspace();
    void onSubmit();
  }

  /** Set the keypad listener to receive backspace/submit events. */
  public void setKeypadListener(KeypadListener listener) {
    this.keypadListener = listener;
    if (keypadKeyViews != null) {
      for (TextView kv : keypadKeyViews) {
        kv.setEnabled(listener != null);
      }
    }
  }
    emptyView.setTextColor(0xFF999999);
    emptyView.setGravity(android.view.Gravity.CENTER);
    emptyView.setVisibility(GONE);
    LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0);
    emptyParams.weight = 1;
    container.addView(emptyView, emptyParams);
  }

  /** Sets the song catalog and queue. */
  public void setCatalog(SongCatalog catalog, ReservationQueue queue) {
    this.catalog = catalog;
    this.queue = queue;
  }

  /** Sets the search listener. */
  public void setListener(SearchListener listener) {
    this.listener = listener;
  }

  /** Shows the overlay. */
  public void show() {
    if (isVisible) return;
    isVisible = true;
    setTranslationY(getHeight());
    setVisibility(VISIBLE);
    animate().translationY(0).setDuration(ANIM_DURATION_MS).start();
    searchField.requestFocus();
    showKeyboard();
  }

  /** Hides the overlay. */
  public void hide() {
    if (!isVisible) return;
    isVisible = false;
    hideKeyboard();
    animate().translationY(getHeight()).setDuration(ANIM_DURATION_MS)
        .withEndAction(() -> setVisibility(GONE))
        .start();
  }

  private void performSearch(String query) {
    if (catalog == null) return;

    loadingSpinner.setVisibility(VISIBLE);
    resultsList.setVisibility(GONE);
    emptyView.setVisibility(GONE);

    // Debounce: simple post-delayed
    removeCallbacks(searchRunnable);
    searchRunnable = () -> {
      loadingSpinner.setVisibility(GONE);
      List<Song> results = catalog.search(query, 50);
      adapter.setSongs(results);
      if (results.isEmpty()) {
        emptyView.setVisibility(VISIBLE);
      } else {
        resultsList.setVisibility(VISIBLE);
      }
    };
    postDelayed(searchRunnable, 150);
  }

  private Runnable searchRunnable = () -> {};

  private void showKeyboard() {
    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.showSoftInput(searchField, 0);
  }

  private void hideKeyboard() {
    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
  }

  /** Adapter for song results. */
  private final class SongAdapter extends RecyclerView.Adapter<SongViewHolder> {
    private List<Song> songs = new ArrayList<>();

    void setSongs(List<Song> songs) {
      this.songs = songs != null ? songs : new ArrayList<>();
      notifyDataSetChanged();
    }

    @Override
    public SongViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
      View view = new SongItemView(getContext());
      return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SongViewHolder holder, int position) {
      holder.bind(songs.get(position));
    }

    @Override
    public int getItemCount() {
      return songs.size();
    }
  }

  /** ViewHolder for song items. */
  private final class SongViewHolder extends RecyclerView.ViewHolder {
    private final SongItemView itemView;

    SongViewHolder(View view) {
      super(view);
      this.itemView = (SongItemView) view;
    }

    void bind(Song song) {
      itemView.bind(song, queue != null && queue.snapshot().stream()
          .anyMatch(e -> e.song.id.equals(song.id)),
          (v) -> {
            if (listener != null) listener.onReserve(song);
          });
    }
  }

  /** Custom view for a song item. */
  private static final class SongItemView extends LinearLayout {
    private final TextView titleView;
    private final TextView artistView;
    private final TextView metaView;
    private final TextView reserveButton;

    SongItemView(Context context) {
      super(context);
      setOrientation(VERTICAL);
      setPadding((int)(16 * getResources().getDisplayMetrics().density),
          (int)(12 * getResources().getDisplayMetrics().density),
          (int)(16 * getResources().getDisplayMetrics().density),
          (int)(12 * getResources().getDisplayMetrics().density));

      titleView = new TextView(context);
      titleView.setTextSize(15);
      titleView.setTextColor(0xFFFFFFFF);
      titleView.setTypeface(null, android.graphics.Typeface.BOLD);

      artistView = new TextView(context);
      artistView.setTextSize(13);
      artistView.setTextColor(0xFFCCCCCC);

      metaView = new TextView(context);
      metaView.setTextSize(12);
      metaView.setTextColor(0xFF999999);

      reserveButton = new TextView(context);
      reserveButton.setText("Reserve");
      reserveButton.setTextSize(13);
      reserveButton.setTextColor(0xFFFFFFFF);
      reserveButton.setTypeface(null, android.graphics.Typeface.BOLD);
      reserveButton.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_reserve_btn_bg);
      reserveButton.setGravity(android.view.Gravity.CENTER);
      reserveButton.setPadding((int)(20 * getResources().getDisplayMetrics().density),
          (int)(8 * getResources().getDisplayMetrics().density),
          (int)(20 * getResources().getDisplayMetrics().density),
          (int)(8 * getResources().getDisplayMetrics().density));
      LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      btnParams.gravity = android.view.Gravity.END;
      btnParams.topMargin = (int)(8 * getResources().getDisplayMetrics().density);

      addView(titleView);
      addView(artistView);
      addView(metaView);
      addView(reserveButton, btnParams);
    }

    void bind(Song song, boolean alreadyQueued, OnClickListener reserveListener) {
      titleView.setText(song.title);
      artistView.setText(song.artist.isEmpty() ? "Unknown Artist" : song.artist);
      metaView.setText(String.format(Locale.getDefault(), "Code: %s", song.code));

      if (alreadyQueued) {
        reserveButton.setText("Queued");
        reserveButton.setEnabled(false);
        reserveButton.setBackgroundColor(0xFF241540); // surfaceHigh
        reserveButton.setTextColor(0xFF999999);
        reserveButton.setOnClickListener(null);
      } else {
        reserveButton.setText("Reserve");
        reserveButton.setEnabled(true);
        reserveButton.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_reserve_btn_bg);
        reserveButton.setTextColor(0xFFFFFFFF);
        reserveButton.setOnClickListener(reserveListener);
      }
    }
  }

  /** Search listener interface. */
  public interface SearchListener {
    void onReserve(Song song);
  }
}
