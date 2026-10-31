package ph.kanta.reborn;

import androidx.activity.OnBackPressedCallback;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.*;
import android.speech.RecognizerIntent;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import ph.kanta.reborn.core.*;
import ph.kanta.reborn.content.ContentRoot;
import ph.kanta.reborn.content.ContentScanner;
import ph.kanta.reborn.core.catalog.SongCatalog;
import ph.kanta.reborn.core.ReservationQueue;
import ph.kanta.reborn.score.ScoreSink;
import ph.kanta.reborn.stage.StagePresenterImpl;
import ph.kanta.reborn.core.connect.KantaConnectInfo;
import ph.kanta.reborn.core.connect.QrEncoder;
import ph.kanta.reborn.stage.QrCodeView;

public final class MainActivity extends androidx.activity.ComponentActivity {
  private static final int MUSIC = 10,
      VOICE = 11,
      EXPORT = 12,
      FOLDER = 13,
      PHOTO = 14,
      VIDEO = 15,
      CAMERA = 16,
      MICROPHONE = 17;
  private static final int NAVY = 0xFF0B1023,
      PANEL = 0xFF19213E,
      CYAN = 0xFF6FE5E8,
      GOLD = 0xFFF6CE77;
  private SessionController session;
  private LinearLayout root, body, results;
  private StagePresenterImpl stagePresenter;
  private StageSurface fullscreenStage, externalStage;
  private KantaWorkspace workspace;
  private OnBackPressedCallback workspaceBackCallback;
  private TextView nowPlaying, microphoneReading, monitorReading, scoreReading;
  private Button playButton, nextButton;
  private final Map<String, Button> navigationButtons = new HashMap<>();
  private Dialog fullscreen, companionDialog, youtubeDialog;
  private AlertDialog youtubeEntryDialog;
  private YouTubeEmbeddedPlayer youtubePlayer;
  private final YouTubePreviewLifecycle youtubeLifecycle = new YouTubePreviewLifecycle();
  private boolean youtubeForeground;
  private CameraStage camera;
  private boolean cameraPermissionPending, microphonePermissionPending, voiceExpanded;
  private Presentation presentation;
  private DisplayManager displays;
  private String tab = "Control", query = "", singer = "";
  private EditText queryInput;
  private int resultLimit = 50;
  private int queueLimit = 20;
  private final Handler handler = new Handler(Looper.getMainLooper());

  private android.app.ProgressDialog importProgressDialog;
  private final ExecutorService searchWorker = Executors.newSingleThreadExecutor();
  private int searchGeneration = 0;
  private int workspaceSearchGeneration = 0;
  private boolean preparingExport = false;
  private byte[] exportBytes;
  private String exportCode;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          session.observeMicrophone();
          refreshScoreReading();
          refreshMicrophoneReading();
          if (stagePresenter != null) stagePresenter.refresh();
          if (fullscreenStage != null) fullscreenStage.refresh();
          if (externalStage != null) externalStage.refresh();
          handler.postDelayed(this, 100);
        }
      };
  private final DisplayManager.DisplayListener displayListener =
      new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) {
          refreshDisplay();
        }

        public void onDisplayChanged(int id) {
          refreshDisplay();
        }

        public void onDisplayRemoved(int id) {
          refreshDisplay();
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    workspaceBackCallback =
        new OnBackPressedCallback(false) {
          @Override
          public void handleOnBackPressed() {
            // Enabled only while a transient workspace accessory is visible.
            dismissAccessory();
          }
        };
    getOnBackPressedDispatcher().addCallback(this, workspaceBackCallback);
    session = ((KantaApplication) getApplication()).session;
    camera = new CameraStage(this, session);
    if (saved != null) {
      tab = saved.getString("tab", "Control");
      query = saved.getString("query", "");
      singer = saved.getString("singer", "");
    }
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    displays = (DisplayManager) getSystemService(DISPLAY_SERVICE);
    if (session.scenic == null) session.scene(session.scenicName);
  }

  @Override
  protected void onStart() {
    super.onStart();
    youtubeForeground = true;
    session.hostVisible(true);
    if (stagePresenter != null) stagePresenter.setVisualActive(fullscreen == null || !fullscreen.isShowing());
    if (fullscreenStage != null) fullscreenStage.setVisualActive(true);
    session.listen(this::render);
    handler.post(tick);
    displays.registerDisplayListener(displayListener, handler);
    refreshDisplay();
  }

  @Override
  protected void onPause() {
    youtubeForeground = false;
    closeYouTubeVideo();
    if (youtubeEntryDialog != null) youtubeEntryDialog.dismiss();
    youtubeEntryDialog = null;
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    youtubeForeground = true;
  }

  private void reconcileOnlinePreviewWithLocalPlayback() {
    if (session != null && session.playing && youtubeLifecycle.active() != null) {
      closeYouTubeVideo();
    }
  }

  private void closeYouTubeVideo() {
    YouTubePreviewLifecycle.Session previousSession = youtubeLifecycle.active();
    youtubeLifecycle.close();
    YouTubeEmbeddedPlayer previousPlayer = youtubePlayer;
    youtubePlayer = null;
    if (previousPlayer != null) previousPlayer.release();
    Dialog previous = youtubeDialog;
    youtubeDialog = null;
    if (previous != null) previous.dismiss();
    if (previousSession != null) session.releaseOnlineAudio(previousSession.id);
  }

  @Override
  protected void onStop() {
    handler.removeCallbacksAndMessages(null);
    closeYouTubeVideo();
    if (companionDialog != null) companionDialog.dismiss();
    companionDialog = null;
    displays.unregisterDisplayListener(displayListener);
    cameraPermissionPending = false;
    microphonePermissionPending = false;
    camera.stop();
    session.listen(null);
    session.hostVisible(false);
    if (stagePresenter != null) stagePresenter.setVisualActive(false);
    if (fullscreenStage != null) fullscreenStage.setVisualActive(false);
    if (!isChangingConfigurations()) session.pause();
    if (presentation != null) presentation.dismiss();
    presentation = null;
    externalStage = null;
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    searchGeneration++;
    searchWorker.shutdownNow();
    if (fullscreen != null) fullscreen.dismiss();
    super.onDestroy();
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    // Handset/tablet changes re-lay out the presented accessory so it stays a
    // bottom sheet on a handset and an anchored panel on a large screen.
    if (workspace != null) workspace.requestLayout();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("tab", tab);
    out.putString("query", query);
    out.putString("singer", singer);
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String label, int size, int color) {
    TextView v = new TextView(this);
    v.setText(label);
    v.setTextColor(color);
    v.setTextSize(size);
    v.setPadding(dp(6), dp(6), dp(6), dp(6));
    return v;
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private LinearLayout row() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  private Button button(String label, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextColor(Color.WHITE);
    b.setTextSize(14);
    b.setMinHeight(dp(48));
    b.setBackgroundTintList(ColorStateList.valueOf(PANEL));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private void addWeighted(LinearLayout row, View view) {
    row.addView(view, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
  }

  private void render() {
    if (isFinishing() || isDestroyed()) return;
    reconcileOnlinePreviewWithLocalPlayback();
    searchGeneration++;
    queryInput = null;
    results = null;
    if (root == null) buildWindow();
    nowPlaying.setText(
        session.current == null
            ? "Ready for your next song"
            : session.current.title + (session.singer.isEmpty() ? "" : " · " + session.singer));
    playButton.setText(session.playing ? "Pause" : "Play");
    playButton.setEnabled(session.ready && !session.loading);
    nextButton.setEnabled(session.ready && !session.loading);
    for (Map.Entry<String, Button> entry : navigationButtons.entrySet()) {
      entry.getValue().setSelected(entry.getKey().equals(tab));
      entry.getValue().setTextColor(entry.getKey().equals(tab) ? GOLD : Color.WHITE);
    }
    body.removeAllViews();
    renderPanel();
    if (stagePresenter != null) stagePresenter.refresh();
  }

  private void buildWindow() {
    root = column();
    root.setBackgroundColor(NAVY);
    root.setPadding(dp(12), 0, dp(12), 0);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              dp(12),
              insets.getSystemWindowInsetTop(),
              dp(12),
              insets.getSystemWindowInsetBottom());
          return insets.consumeSystemWindowInsets();
        });
    setContentView(root);
    root.requestApplyInsets();
    LinearLayout header = row();
    ImageView mark = new ImageView(this);
    mark.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
    mark.setContentDescription("KANTA");
    header.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));
    TextView brand = text("KANTA · ᜃᜈ᜔ᜆ", 22, GOLD);
    brand.setTypeface(null, android.graphics.Typeface.BOLD);
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(button("Help", this::help));
    root.addView(header);
    LinearLayout main = new LinearLayout(this);
    boolean wide = getResources().getConfiguration().screenWidthDp >= 700;
    main.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
    root.addView(main, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout stagePane = column();
    // Compact Workspace: the stage is the persistent base layer. Utilities are
    // transient accessories presented above it, so the player is never lost.
    workspace = new KantaWorkspace(this, null);
    workspace.setListener(
        new KantaWorkspace.Listener() {
          @Override
          public void onAccessoryActivated(String accessoryId) {
            if (workspaceBackCallback != null) workspaceBackCallback.setEnabled(true);
          }

          @Override
          public void onAccessoryDeactivated(String accessoryId) {
            if (workspaceBackCallback != null) workspaceBackCallback.setEnabled(false);
            // Hide stage presenter accessories when workspace dismisses them
            if (stagePresenter != null) {
              if (KantaWorkspace.ACCESSORY_SEARCH.equals(accessoryId)) {
                stagePresenter.hideSearch();
              } else if (KantaWorkspace.ACCESSORY_QUEUE.equals(accessoryId)) {
                stagePresenter.hideQueue();
              }
            }
          }
        });
    // Create StagePresenterImpl with all stage overlays
    ContentRoot contentRoot = new ContentRoot(this);
    ContentScanner scanner = new ContentScanner(getContentResolver(), contentRoot);
    SongCatalog catalog = new ph.kanta.reborn.core.catalog.SqliteSongCatalog(this, contentRoot, scanner);
    stagePresenter = new StagePresenterImpl(this, session, catalog, session.queue);
    stagePresenter.setTag("kanta.stage.primary");
    stagePresenter.setOnClickListener(v -> fullscreen());
    stagePresenter.setContentDescription("Karaoke stage. Double tap to open fullscreen.");
    workspace.addPersistentLayer(stagePresenter);
    // Register as ScoreSink
    session.setScoreSink(stagePresenter);
    stagePane.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1));
    nowPlaying =
        text(
            session.current == null
                ? "Ready for your next song"
                : session.current.title + (session.singer.isEmpty() ? "" : " · " + session.singer),
            15,
            Color.WHITE);
    stagePane.addView(nowPlaying);
    LinearLayout transport = row();
    playButton = button("Play", session::toggle);
    addWeighted(transport, playButton);
    nextButton = button("Next", session::next);
    addWeighted(transport, nextButton);
    addWeighted(transport, button("Fullscreen", this::fullscreen));
    stagePane.addView(transport);
    stagePane.addView(workspaceToolbar());
    if (wide) main.addView(stagePane, new LinearLayout.LayoutParams(0, -1, 1.2f));
    else
      main.addView(
          stagePane,
          new LinearLayout.LayoutParams(
              -1, dp(getResources().getConfiguration().orientation == 2 ? 200 : 300)));
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = column();
    body.setPadding(dp(4), 0, dp(4), dp(14));
    scroll.addView(body);
    if (wide) main.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
    else main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout tabs = row();
    for (String name : new String[] {"My Set", "Loop", "Scenic", "Realm", "Control"}) {
      Button b =
          button(
              name,
              () -> {
                tab = name;
                render();
              });
      b.setMinWidth(0);
      b.setMinimumWidth(0);
      b.setPadding(dp(3), dp(4), dp(3), dp(4));
      b.setTextSize(12);
      b.setMaxLines(2);
      b.setSelected(name.equals(tab));
      b.setTag("kanta.nav." + name);
      if (name.equals(tab)) b.setTextColor(GOLD);
      navigationButtons.put(name, b);
      tabs.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    root.addView(tabs);
    if (stagePresenter != null) stagePresenter.refresh();
  }

  /**
   * Compact Workspace toolbar (Search | Queue | Visuals | Controls).
   *
   * Mirrors the iOS compact workspace action row: each entry presents a
   * transient accessory above the persistent stage instead of navigating away
   * from it. RC1: every button owns its own identity, so no container
   * identifier can be inherited by a child. RC2: the control-opening entry is
   * labelled "Controls", never "Settings", because Settings is a separate
   * navigation destination and the duplicate label made assistive queries
   * ambiguous.
   */
  private LinearLayout workspaceToolbar() {
    LinearLayout bar = row();
    bar.setBackgroundColor(PANEL);
    bar.setPadding(dp(4), dp(4), dp(4), dp(4));

    Button search = workspaceButton("Search", KantaWorkspace.ACCESSORY_SEARCH);
    Button queue = workspaceButton("Queue", KantaWorkspace.ACCESSORY_QUEUE);
    Button visuals = workspaceButton("Visuals", KantaWorkspace.ACCESSORY_VISUALS);
    Button controls = workspaceButton("Controls", KantaWorkspace.ACCESSORY_MORE);
    Button pitchGame = workspaceButton("Pitch", KantaWorkspace.ACCESSORY_PITCH_GAME);
    Button learning = workspaceButton("Coach", KantaWorkspace.ACCESSORY_LEARNING_ASSISTANT);
    addWeighted(bar, search);
    addWeighted(bar, queue);
    addWeighted(bar, visuals);
    addWeighted(bar, controls);
    addWeighted(bar, pitchGame);
    addWeighted(bar, learning);
    return bar;
  }

  /** One workspace action. Its own identity lives on the button itself. */
  private Button workspaceButton(String label, String accessoryId) {
    Button b = button(label, () -> presentAccessory(accessoryId));
    b.setTag("kanta.workspace." + accessoryId);
    b.setContentDescription("KANTA " + label);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setPadding(dp(3), dp(4), dp(3), dp(4));
    b.setTextSize(12);
    b.setMaxLines(1);
    return b;
  }

  /**
   * Presents one transient accessory above the persistent stage. The stage is
   * never rebuilt, so playback, lyrics and camera are preserved.
   */
  private void presentAccessory(String accessoryId) {
    if (workspace == null) return;
    workspaceSearchGeneration++;
    workspace.activateAccessory(accessoryId);
    LinearLayout target = workspace.accessoryContent();
    target.removeAllViews();
    switch (accessoryId) {
      case KantaWorkspace.ACCESSORY_SEARCH:
        workspace.addAccessoryHeader("Search");
        // Use the StagePresenterImpl's SearchOverlay
        if (stagePresenter != null) stagePresenter.showSearch();
        break;
      case KantaWorkspace.ACCESSORY_QUEUE:
        workspace.addAccessoryHeader("Queue");
        // Use the StagePresenterImpl's QueueOverlay
        if (stagePresenter != null) stagePresenter.showQueue();
        break;
      case KantaWorkspace.ACCESSORY_VISUALS:
        workspace.addAccessoryHeader("Stage Visuals");
        visualsAccessory(target);
        break;
      case KantaWorkspace.ACCESSORY_PITCH_GAME:
        workspace.addAccessoryHeader("Pitch Practice");
        if (stagePresenter != null) stagePresenter.showPitchGame(session.practiceTargetHz());
        break;
      case KantaWorkspace.ACCESSORY_LEARNING_ASSISTANT:
        workspace.addAccessoryHeader("Voice Coach");
        if (stagePresenter != null) stagePresenter.showLearningAssistant();
        break;
      default:
        workspace.addAccessoryHeader("Controls");
        controlsAccessory(target);
        break;
    }
  }

  /** Dismisses the active accessory, leaving the stage untouched. */
  private void dismissAccessory() {
    workspaceSearchGeneration++;
    if (workspace != null) workspace.deactivateAccessory();
  }

  /** Search accessory: a compact read of the library with reservation. */
  private void searchAccessory(LinearLayout target) {
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setTextColor(Color.WHITE);
    input.setTextSize(16);
    input.setHintTextColor(0xFFAFB8D0);
    input.setHint("Song code, title or artist");
    input.setText(query);
    input.setContentDescription("KANTA workspace search input");
    target.addView(input);
    final LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    target.addView(list);
    input.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            query = s.toString();
            renderWorkspaceSearch(list);
          }

          public void afterTextChanged(Editable e) {}
        });
    renderWorkspaceSearch(list);
  }

  /** Fills the workspace search list from the current query. */
  private void renderWorkspaceSearch(LinearLayout list) {
    list.removeAllViews();
    final int token = ++workspaceSearchGeneration;
    final String requested = query;
    final int limit = 50;
    final List<Song> catalog = session.songs;
    searchWorker.execute(
        () -> {
          ArrayList<Song> found = new ArrayList<>();
          for (Song s : catalog) {
            if (Thread.currentThread().isInterrupted()) return;
            if (s.matchesTerms(Song.searchTerms(requested))) found.add(s);
            if (found.size() >= limit) break;
          }
          handler.post(
              () -> {
                if (token != searchGeneration || isDestroyed()) return;
                list.removeAllViews();
                if (found.isEmpty()) {
                  list.addView(text("No matching songs", 14, 0xFFAFB8D0));
                  return;
                }
                for (Song s : found) {
                  LinearLayout item = row();
                  item.addView(
                      text(
                          (s.code.isEmpty() ? "" : s.code + " \u00b7 ")
                              + s.title
                              + (s.artist.isEmpty() ? "" : "\n" + s.artist),
                          15,
                          Color.WHITE),
                      new LinearLayout.LayoutParams(0, -2, 1));
                  item.addView(button("Reserve", () -> session.reserve(s, singer)));
                  list.addView(item);
                }
              });
        });
  }

  /** Queue accessory: a read-only projection of the current queue. */
  private void queueAccessory(LinearLayout target) {
    target.addView(text("Up Next \u00b7 " + session.queue.snapshot().size(), 18, GOLD));
    List<ReservationQueue.Entry> entries = session.queue.snapshot();
    if (entries.isEmpty()) {
      target.addView(text("Reserve a song, then choose Play to begin.", 14, 0xFFAFB8D0));
      return;
    }
    int n = 0;
    for (ReservationQueue.Entry e : entries) {
      if (n >= queueLimit) break;
      final int position = n++;
      LinearLayout item = row();
      item.addView(
          text(
              (position + 1) + ". " + e.song.title + (e.singer.isEmpty() ? "" : " \u00b7 " + e.singer),
              15,
              Color.WHITE),
          new LinearLayout.LayoutParams(0, -2, 1));
      if (position > 0) item.addView(button("\u2191", () -> session.move(e.id, position - 1)));
      item.addView(button("Remove", () -> session.remove(e.id)));
      target.addView(item);
    }
  }

  /** Visuals accessory: camera and stage background controls. */
  private void visualsAccessory(LinearLayout target) {
    target.addView(
        button(
            camera.enabled() ? "Turn off front camera" : "Turn on front camera",
            this::toggleCamera));
    target.addView(
        button(
            "Choose your photo",
            () ->
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    PHOTO)));
    target.addView(
        button(
            "Choose your silent background video",
            () ->
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("video/*")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    VIDEO)));
  }

  /** Controls accessory: settings, companion control, Kanta-Connect host, fullscreen. */
  private void controlsAccessory(LinearLayout target) {
  /** Controls accessory: settings, companion control, Kanta-Connect host, fullscreen. */
  private void controlsAccessory(LinearLayout target) {
    target.addView(button("Settings", () -> {
      dismissAccessory();
      tab = "Control";
      render();
    }));
    target.addView(
        button("Pitch Practice", () -> {
          dismissAccessory();
          presentAccessory(KantaWorkspace.ACCESSORY_PITCH_GAME);
        }));
    target.addView(
        button("Voice Coach", () -> {
          dismissAccessory();
          presentAccessory(KantaWorkspace.ACCESSORY_LEARNING_ASSISTANT);
        }));
    target.addView(
        button("Companion control", () -> {
          dismissAccessory();
          companionDialog = CompanionDialog.show(this, session);
        }));
    boolean hosting = session.kantaConnectHost.isHosting();
    target.addView(button(
        hosting ? "Stop Kanta-Connect" : "Start Kanta-Connect",
        () -> {
          dismissAccessory();
          if (hosting) {
            session.stopKantaConnectHost();
            render();
          } else {
            kantaConnect();
          }
        }));
    target.addView(
        button("Diagnostics", () -> {
          dismissAccessory();
          diagnostics();
        }));
    target.addView(
        button("Fullscreen stage", () -> {
          dismissAccessory();
          fullscreen();
        }));
  }
