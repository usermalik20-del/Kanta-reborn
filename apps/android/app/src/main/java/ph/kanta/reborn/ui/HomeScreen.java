package ph.kanta.reborn.ui;

import ph.kanta.reborn.R;
import ph.kanta.reborn.ui.components.InlineKeyboard;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
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
    default void onPauseResumeSong() {}
    default void onNextSong() {}
    default void onStopSong() {}
    default void onKeyStep(int amount) {}
    default void onTempoStep(int amount) {}
    default void onPitchTempo() {}
    default void onKeySound(String key) {}
    void onQueue();
    void onVisuals();
    void onMicVoice();
    void onMySet();
    void onFavorites();
    void onRecent();
    void onMore();
    void onScenic();
    void onRealm();
  }

  private static final int LANDSCAPE_KEYBOARD_MAX_WIDTH_DP = 500;
  private static final long SONG_LOOKUP_TIMEOUT_MS = 8_000L;

  private final Context context;
  private final float density;
  private final Listener listener;
  private final Handler lookupHandler = new Handler(Looper.getMainLooper());
  private final Runnable expireSongLookup = this::dismissSongLookup;
  private LinearLayout searchRow, queuePanel, reservedSongs;
  private android.widget.ScrollView matchesScroll;
  private Button clearSearch, pauseResume;
  private boolean liveMatchesVisible, lookupDocked, restoringSearchText, fullscreenLandscape;
  private long songLookupDeadline, songLookupGeneration;
  private String previousSearchText = "";
  private InlineKeyboard keyboard;
  private java.util.function.Consumer<String> keyFeedback = ignored -> {};
  private boolean compact;
  private TextView queueHeading;
  private Button viewQueue;
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
    searchRow = new LinearLayout(context);
    searchRow.setOrientation(HORIZONTAL);
    searchRow.setGravity(Gravity.CENTER_VERTICAL);

    searchInput = new EditText(context);
    searchInput.setTag("kanta.home.search");
    searchInput.setSingleLine(true);
    searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
        | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    searchInput.setShowSoftInputOnFocus(false);
    searchInput.setFilters(new android.text.InputFilter[] {
        (source, start, end, dest, dstart, dend) -> {
          String next = dest.subSequence(0, dstart).toString() + source.subSequence(start, end)
              + dest.subSequence(dend, dest.length());
          // Reject an overlong numeric replacement; truncating a pasted code could play another song.
          return ph.kanta.reborn.core.SongCodes.isOverlongNumericQuery(next)
              ? dest.subSequence(dstart, dend) : null;
        }
    });
    searchInput.setTextColor(KantaTheme.TEXT_PRIMARY);
    searchInput.setHintTextColor(KantaTheme.TEXT_MUTED);
    searchInput.setHint("Search songs, artists, or enter code");
    searchInput.setTextSize(16);
    searchInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    searchInput.setPaddingRelative(dp(14), 0, dp(10), 0);
    Drawable searchIcon = context.getDrawable(R.drawable.kanta_search_color).mutate();
    searchInput.setCompoundDrawablesRelativeWithIntrinsicBounds(searchIcon, null, null, null);
    searchInput.setCompoundDrawablePadding(dp(10));
    searchInput.setBackground(null);
    searchRow.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE_ELEVATED, 14, KantaTheme.BORDER, 1, density));
    searchInput.setMinHeight(dp(54));
    searchRow.addView(searchInput, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    clearSearch = stageAction(R.string.stage_clear, R.string.stage_clear_search, "cancel", true, () -> {
      clearSearchText();
      searchInput.requestFocus();
    });
    clearSearch.setVisibility(GONE);
    searchRow.addView(clearSearch, new LayoutParams(-2, -2));

    LinearLayout actions = new LinearLayout(context);
    actions.setTag("kanta.stage.transport");
    pauseResume = addSongAction(actions, R.string.stage_play, R.string.stage_play, "pause-resume",
        () -> { if (listener != null) listener.onPauseResumeSong(); });
    pauseResume.setEnabled(false);
    addSongAction(actions, R.string.stage_reserve, R.string.stage_reserve_match, "submit", this::submitEntry);
    addSongAction(actions, R.string.stage_next, R.string.stage_next_song, "next", () -> {
      if (listener != null) listener.onNextSong();
    });
    addSongAction(actions, R.string.stage_stop, R.string.stage_stop_song, "stop", () -> {
      if (listener != null) listener.onStopSong();
    });
    // Intrinsic widths prevent labels from being squeezed in split-screen or at large font sizes.
    android.widget.HorizontalScrollView actionScroll = new android.widget.HorizontalScrollView(context);
    actionScroll.setFillViewport(true);
    actionScroll.addView(actions, new android.widget.FrameLayout.LayoutParams(-2, -2));
    addView(actionScroll, new LayoutParams(-1, -2));
    addView(searchRow, new LayoutParams(-1, -2));

    // Use spare height for the queue, leaving the keypad at the bottom. WRAP_CONTENT
    // preserves the queue's minimum content height when the outer ScrollView is short.
    queuePanel = new LinearLayout(context);
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
    viewQueue = new Button(context);
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
    reservedSongs.setBackgroundColor(0);
    reservedSongs.setOrientation(VERTICAL);
    matchesScroll = new android.widget.ScrollView(context);
    matchesScroll.setBackgroundColor(0);
    matchesScroll.setVerticalScrollBarEnabled(false);
    matchesScroll.setVerticalScrollBarEnabled(true);
    matchesScroll.addView(reservedSongs, new android.widget.ScrollView.LayoutParams(-1, -2));
    queuePanel.addView(matchesScroll, new LayoutParams(-1, -2));
    LayoutParams queueParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    queueParams.setMargins(0, dp(10), 0, dp(10));
    addView(queuePanel, queueParams);
    setReservedSongs(java.util.Collections.emptyList(), 0);

    // Inline keyboard
    keyboard = new InlineKeyboard(context, new InlineKeyboard.Listener() {
      @Override public void onText(String value) {
        if (listener != null && value.length() == 1 && Character.isDigit(value.charAt(0))) {
          listener.onKeySound(value);
        }
        int start = Math.max(0, searchInput.getSelectionStart());
        int end = Math.max(start, searchInput.getSelectionEnd());
        int retainedLength = searchInput.length() - (end - start);
        searchInput.getText().replace(start, end, value);
        searchInput.setSelection(start + searchInput.length() - retainedLength);
        searchInput.requestFocus();
      }
      @Override public void onBackspace() {
        if (listener != null) listener.onKeySound("delete");
        int start = Math.max(0, searchInput.getSelectionStart());
        int end = Math.max(start, searchInput.getSelectionEnd());
        if (start != end) searchInput.getText().delete(start, end);
        else if (start > 0) searchInput.getText().delete(start - 1, start);
        searchInput.requestFocus();
      }
      @Override public void onSubmit() {
        submitEntry();
      }
      @Override public void onKeyStep(int amount) {
        if (listener != null) listener.onKeyStep(amount);
      }
      @Override public void onTempoStep(int amount) {
        if (listener != null) listener.onTempoStep(amount);
      }
    });
    keyboard.setTag("kanta.home.keyboard");
    addView(keyboard, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    searchInput.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(android.text.Editable e) {
        clearSearch.setVisibility(e.length() == 0 ? GONE : VISIBLE);
        String typed = e.toString();
        boolean changed = !typed.equals(previousSearchText);
        previousSearchText = typed;
        if (changed && !restoringSearchText) {
          beginSongLookup();
          if (listener != null) listener.onSearchQueryChanged(typed);
        }
      }
    });
    searchInput.setOnEditorActionListener((v, actionId, event) -> {
      submitEntry();
      return true;
    });
    searchInput.requestFocus();
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    boolean landscape = getResources().getConfiguration().orientation
        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    // Landscape already presents the Stage's Up next ticker; omit the duplicate queue card.
    if (!lookupDocked) queuePanel.setVisibility(landscape ? GONE : VISIBLE);
    // Let portrait use spare height, but keep the landscape keypad clear of the Stage dock.
    LayoutParams keyboardParams = (LayoutParams) keyboard.getLayoutParams();
    keyboardParams.weight = lookupDocked && !landscape ? 1 : 0;
    keyboardParams.gravity = Gravity.CENTER_HORIZONTAL;
    int paneWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
    int widthCap = dp(LANDSCAPE_KEYBOARD_MAX_WIDTH_DP);
    keyboardParams.width = landscape && paneWidth > widthCap ? widthCap : LayoutParams.MATCH_PARENT;
    keyboard.setVisibility(fullscreenLandscape && landscape ? GONE : VISIBLE);

    boolean shortLandscape = CompactLandscape.applies(this, heightMeasureSpec);
    boolean changed = shortLandscape != compact;
    if (changed) {
      compact = shortLandscape;
      searchInput.setMinHeight(dp(compact ? 48 : 54));
      keyboard.setCompact(compact);
      queuePanel.setMinimumHeight(lookupDocked ? 0 : compact ? 0 : dp(112));
      if (!lookupDocked) queuePanel.setPadding(dp(14), dp(compact ? 2 : 6), dp(14), dp(compact ? 4 : 10));
      LayoutParams queueParams = (LayoutParams) queuePanel.getLayoutParams();
      queueParams.setMargins(0, dp(compact ? 4 : 10), 0, dp(compact ? 4 : 10));
      // Keep the editor and keyboard instances (and their focus/selection) during resize.
      arrangeMatchPanel();
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  private Button stageAction(int label, int description, String tag, boolean fieldAction, Runnable action) {
    Button button = new Button(context);
    button.setText(label);
    button.setTextSize(14);
    button.setAllCaps(false);
    button.setTextColor(fieldAction ? KantaTheme.PRIMARY_CYAN : KantaTheme.TEXT_PRIMARY);
    button.setPaddingRelative(dp(8), 0, dp(8), 0);
    button.setMinWidth(dp(48));
    button.setMinimumWidth(dp(48));
    button.setMinHeight(dp(48));
    button.setFocusable(true);
    button.setContentDescription(context.getString(description));
    button.setTag("kanta.stage.song-action." + tag);
    StateListDrawable background = new StateListDrawable();
    for (int state : new int[]{android.R.attr.state_pressed, android.R.attr.state_focused}) {
      background.addState(new int[]{state}, KantaTheme.backgroundDrawable(
          KantaTheme.SURFACE, 12, KantaTheme.PRIMARY_CYAN, 1, density));
    }
    background.addState(new int[]{}, fieldAction ? new android.graphics.drawable.ColorDrawable(0)
        : KantaTheme.backgroundDrawable(KantaTheme.SURFACE_ELEVATED, 12, KantaTheme.BORDER, 1, density));
    button.setBackground(background);
    button.setOnClickListener(v -> { if (v.isEnabled()) action.run(); });
    return button;
  }

  private Button addSongAction(LinearLayout row, int label, int description, String tag, Runnable action) {
    Button button = stageAction(label, description, tag, false, action);
    LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1);
    params.setMargins(dp(1), 0, dp(1), 0);
    row.addView(button, params);
    return button;
  }

  /** The transport controls current playback or the queue; typed search is separate. */
  public void setPauseResumeState(boolean available, boolean playing, boolean prepared) {
    int label = playing ? R.string.stage_pause : R.string.stage_play;
    int description = playing ? R.string.stage_pause_playback
        : prepared ? R.string.stage_resume_playback : R.string.stage_play;
    String spoken = context.getString(description);
    if (!spoken.contentEquals(pauseResume.getContentDescription())) {
      pauseResume.setText(label);
      pauseResume.setContentDescription(spoken);
    }
    pauseResume.setEnabled(available);
    pauseResume.setAlpha(available ? 1f : 0.45f);
  }

  private void submitEntry() {
    searchInput.requestFocus();
    if (listener != null) listener.onSearchSubmitted(getSearchText());
  }

  public String getSearchText() { return searchInput.getText().toString(); }
  public void setSearchText(String value) { searchInput.setText(value); }
  public void clearSearchText() { searchInput.setText(""); }

  /** Restoring an editor after a rebuild is not a new search. */
  public void restoreSearchText(String value) {
    dismissSongLookup();
    restoringSearchText = true;
    try {
      setSearchText(value);
    } finally {
      restoringSearchText = false;
    }
  }

  private void beginSongLookup() {
    dismissSongLookup();
    if (!lookupDocked || getSearchText().trim().isEmpty()) return;
    songLookupDeadline = SystemClock.uptimeMillis() + SONG_LOOKUP_TIMEOUT_MS;
    lookupHandler.postAtTime(expireSongLookup, songLookupDeadline);
    showMatchStatus(R.string.stage_search_preview_loading);
    setLiveMatchesVisible(true);
  }

  /** A zero generation means publications and outstanding worker results must stay hidden. */
  public long activeSongLookupGeneration() {
    if (songLookupDeadline != 0 && SystemClock.uptimeMillis() >= songLookupDeadline) {
      dismissSongLookup();
    }
    return songLookupDeadline == 0 ? 0 : songLookupGeneration;
  }

  public void dismissSongLookup() {
    lookupHandler.removeCallbacks(expireSongLookup);
    songLookupDeadline = 0;
    songLookupGeneration++;
    setLiveMatchesVisible(false);
    if (lookupDocked) reservedSongs.removeAllViews();
  }

  private void setLiveMatchesVisible(boolean visible) {
    if (liveMatchesVisible == visible) return;
    liveMatchesVisible = visible;
    if (lookupDocked) queuePanel.setVisibility(visible ? VISIBLE : GONE);
    arrangeMatchPanel();
    if (visible) searchInput.post(() -> {
      if (activeSongLookupGeneration() != 0) searchInput.requestRectangleOnScreen(
          new android.graphics.Rect(0, 0, searchInput.getWidth(), searchInput.getHeight()), true);
    });
  }

  private void arrangeMatchPanel() {
    if (!lookupDocked) bringChildToFront(compact && !liveMatchesVisible ? queuePanel : keyboard);
    matchesScroll.getLayoutParams().height = lookupDocked ? dp(96)
        : liveMatchesVisible ? dp(compact ? 68 : 120) : LayoutParams.WRAP_CONTENT;
    matchesScroll.requestLayout();
  }

  public void setFullscreenLandscape(boolean enabled) {
    fullscreenLandscape = enabled;
    requestLayout();
  }

  public void focusSongSearch() {
    searchInput.requestFocus();
  }

  /** Explicit Stage Search can open retained text again; ordinary refreshes cannot. */
  public void reopenSongLookup() {
    if (activeSongLookupGeneration() == 0) beginSongLookup();
  }

  @Override protected void onDetachedFromWindow() {
    dismissSongLookup();
    super.onDetachedFromWindow();
  }

  public void detachSearchDock() {
    dismissSongLookup();
    if (lookupDocked && queuePanel.getParent() instanceof LinearLayout) {
      ((LinearLayout) queuePanel.getParent()).removeView(queuePanel);
    }
  }

  public void revealSongMatches() {
    if (lookupDocked) return;
    queuePanel.post(() -> queuePanel.requestRectangleOnScreen(
        new android.graphics.Rect(0, 0, queuePanel.getWidth(), queueHeading.getHeight()), false));
  }

  public void showMatchStatus(int message) {
    if (activeSongLookupGeneration() == 0) return;
    reservedSongs.removeAllViews();
    reservedSongs.addView(reservationText(context.getString(message)));
  }

  public void showSongLookup(LinearLayout stagePane) {
    lookupDocked = true;
    removeView(queuePanel);
    queuePanel.setTag("kanta.stage.search-dock");
    queuePanel.setMinimumHeight(0);
    queuePanel.setVisibility(GONE);
    ((View) queueHeading.getParent()).setVisibility(GONE);
    queuePanel.setBackground(null);
    queuePanel.setPadding(dp(8), 0, dp(8), 0);
    stagePane.addView(queuePanel, 0, new LayoutParams(-1, -2));
    arrangeMatchPanel();
    reservedSongs.setTag("kanta.stage.search-results");
    queueHeading.setText(R.string.stage_search_preview_title);
    viewQueue.setVisibility(GONE);
    reservedSongs.removeAllViews();
  }

  public void showMatches(long generation, java.util.List<ph.kanta.reborn.core.Song> songs,
      java.util.function.Consumer<ph.kanta.reborn.core.Song> reserve,
      java.util.function.Consumer<ph.kanta.reborn.core.Song> play,
      java.util.function.Consumer<ph.kanta.reborn.core.Song> addToPlaylist) {
    if (generation == 0 || generation != activeSongLookupGeneration()) return;
    reservedSongs.removeAllViews();
    matchesScroll.scrollTo(0, 0);
    if (songs.isEmpty()) {
      reservedSongs.addView(reservationText(context.getString(R.string.stage_search_preview_empty)));
      return;
    }
    for (ph.kanta.reborn.core.Song song : songs) {
      LinearLayout result = new LinearLayout(context);
      result.setBackgroundColor(0);
      result.setGravity(Gravity.CENTER_VERTICAL);
      TextView title = reservationText(song.code + " · " + song.title);
      title.setMaxLines(2);
      title.setTextColor(KantaTheme.TEXT_PRIMARY);
      title.setEllipsize(android.text.TextUtils.TruncateAt.END);
      result.addView(title, new LayoutParams(0, -2, 1));
      addMatchAction(result, R.string.stage_reserve, context.getString(R.string.stage_reserve_result, song.title, song.code),
          "reserve", () -> { if (generation == activeSongLookupGeneration()) reserve.accept(song); });
      addMatchAction(result, R.string.stage_play, context.getString(R.string.stage_play_result, song.title, song.code),
          "play", () -> { if (generation == activeSongLookupGeneration()) play.accept(song); });
      if (addToPlaylist != null) {
        addMatchAction(result, R.string.stage_playlist,
            context.getString(R.string.playlist_add_result, song.title, song.code), "playlist",
            () -> { if (generation == activeSongLookupGeneration()) addToPlaylist.accept(song); });
      }
      reservedSongs.addView(result, new LayoutParams(-1, -2));
    }
  }

  public void showMatches(long generation, java.util.List<ph.kanta.reborn.core.Song> songs,
      java.util.function.Consumer<ph.kanta.reborn.core.Song> reserve,
      java.util.function.Consumer<ph.kanta.reborn.core.Song> play) {
    showMatches(generation, songs, reserve, play, null);
  }

  private void addMatchAction(LinearLayout row, int label, String description, String action, Runnable click) {
    Button button = stageAction(label, label, action, false, click);
    button.setTextSize(12);
    button.setContentDescription(description);
    button.setTag("kanta.stage.match." + action);
    button.setMinHeight(dp(48));
    button.setMinWidth(dp(48));
    button.setMinimumWidth(dp(48));
    button.setPadding(dp(5), 0, dp(5), 0);
    LayoutParams params = new LayoutParams(-2, -2);
    params.setMarginStart(dp(2));
    row.addView(button, params);
  }

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
    row.setTextSize(15);
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
