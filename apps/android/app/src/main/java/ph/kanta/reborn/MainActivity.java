package ph.kanta.reborn;

import androidx.activity.OnBackPressedCallback;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
import ph.kanta.reborn.stage.StagePresenter;
import ph.kanta.reborn.stage.StagePresenterImpl;
import ph.kanta.reborn.core.connect.KantaConnectInfo;
import ph.kanta.reborn.core.connect.QrEncoder;
import ph.kanta.reborn.core.update.UpdateManifest;
import ph.kanta.reborn.core.update.UpdatePolicy;
import ph.kanta.reborn.update.AppUpdateManager;
import ph.kanta.reborn.update.UpdatePackageInspector;
import ph.kanta.reborn.stage.QrCodeView;
import ph.kanta.reborn.settings.SettingsStore;
import ph.kanta.reborn.ui.KantaTheme;
import ph.kanta.reborn.ui.components.InlineKeyboard;

public final class MainActivity extends androidx.activity.ComponentActivity {
  private static final int MUSIC = 10,
      LYRIC_FONT_FOLDER = 20,
      VOICE = 11,
      EXPORT = 12,
      FOLDER = 13,
      PHOTO = 14,
      VIDEO = 15,
      CAMERA = 16,
      MICROPHONE = 17,
      LEGACY_INDEX = 18,
      LEGACY_BLOB = 19;
  private static final int NAVY = KantaTheme.BG_MAIN,
      PANEL = KantaTheme.SURFACE_ELEVATED,
      CYAN = KantaTheme.PRIMARY_CYAN,
      ACCENT = KantaTheme.PRIMARY_CYAN;

  private static final String FLUIDR3_GM_NAME = "FluidR3 GM.sf2";

  private SessionController session;
  private LinearLayout root, body, results, stagePane, navigationTabs;
  private View appHeader;
  private boolean returnToHomeAfterAccessory;
  private StagePresenterImpl stagePresenter;
  private StageSurface fullscreenStage, externalStage;
  private KantaWorkspace workspace;
  private OnBackPressedCallback workspaceBackCallback;
  private TextView nowPlaying, microphoneReading, monitorReading, scoreReading;
  private Button playButton, nextButton;
  private final Map<String, Button> navigationButtons = new HashMap<>();
  private Dialog fullscreen, companionDialog, youtubeDialog;
  private TextView fullscreenTitle, fullscreenArtist, fullscreenTime, fullscreenKeyTempo, fullscreenQueue;
  private ProgressBar fullscreenProgress;
  private Button fullscreenPlayButton;
  private AlertDialog youtubeEntryDialog;
  private YouTubeEmbeddedPlayer youtubePlayer;
  private final YouTubePreviewLifecycle youtubeLifecycle = new YouTubePreviewLifecycle();
  private boolean youtubeForeground;
  private CameraStage camera;
  private boolean cameraPermissionPending, microphonePermissionPending, voiceExpanded;
  private Presentation presentation;
  private DisplayManager displays;
  private String tab = "Home", query = "", singer = "";
  private Uri pendingLegacyIndex;
  private AlertDialog legacyKeyDialog;
  private EditText queryInput;
  private int resultLimit = 50;
  private int queueLimit = 20;
  private final Handler handler = new Handler(Looper.getMainLooper());

  private android.app.ProgressDialog importProgressDialog;
  private final ExecutorService searchWorker = Executors.newSingleThreadExecutor();
  private AppUpdateManager updateManager;
  private AlertDialog updateDialog;
  private boolean updateBusy;
  private String updateStatusText;
  private TextView updateStatus;
  private ProgressBar updateProgress;
  private Button updateInstallButton;
  private File updateReadyApk;
  private int searchGeneration = 0;
  private int workspaceSearchGeneration = 0;
  private int referenceSearchGeneration = 0;
  private ph.kanta.reborn.ui.KantaNowPlayingScreen nowPlayingScreen;
  private ph.kanta.reborn.ui.KantaMicVoiceScreen micVoiceScreen;
  private Button onlineLyricsButton;
  private int lyricDialogGeneration;
  private int fontPickerGeneration;
  private int visualsSelectedTab;
  private String searchSource = "All";
  private String lowerUtilityAccessory;
  private boolean controlDeckVisible;
  private String controlDeckReturnTab = "Home";
  private boolean preparingExport = false;
  private byte[] exportBytes;
  private String exportCode;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          session.observeMicrophone();
          refreshScoreReading();
          refreshMicrophoneReading();
          refreshNowPlayingScreen();
          if (stagePresenter != null) stagePresenter.refresh();
          if (fullscreenStage != null) fullscreenStage.refresh();
          refreshFullscreenControls();
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
            if (controlDeckVisible) {
              closeControlDeck();
            } else if (lowerUtilityAccessory != null
                || (workspace != null && workspace.activeAccessory() != null)) {
              dismissAccessory();
            } else if (isReferenceDestination(tab)) {
              navigateHome();
            } else {
              setEnabled(false);
            }
          }
        };
    getOnBackPressedDispatcher().addCallback(this, workspaceBackCallback);
    session = ((KantaApplication) getApplication()).session;
    camera = new CameraStage(this, session);
    if (saved != null) {
      tab = saved.getString("tab", "Home");
      query = saved.getString("query", "");
      singer = saved.getString("singer", "");
    }
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    displays = (DisplayManager) getSystemService(DISPLAY_SERVICE);
    if (session.scenic == null && "scenic".equals(session.stageKind()))
      session.scene(session.scenicName);
  }

  @Override
  protected void onStart() {
    super.onStart();
    youtubeForeground = true;
    session.hostVisible(true);
    // listen() renders synchronously and creates the primary stage on first launch.
    session.listen(this::render);
    if (stagePresenter != null)
      stagePresenter.setVisualActive(fullscreen == null || !fullscreen.isShowing());
    if (fullscreenStage != null) fullscreenStage.setVisualActive(true);
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
    homeSubmitGeneration++;
    if (legacyKeyDialog != null) legacyKeyDialog.dismiss();
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
    if (updateManager != null) updateManager.close();
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
    Button b = KantaTheme.secondaryButton(this, label, v -> action.run());
    b.setAllCaps(false);
    b.setMinHeight(dp(48));
    return b;
  }

  private void addWeighted(LinearLayout row, View view) {
    row.addView(view, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
  }

  /**
   * Starts a new reservation through the fun Ready/1-4/Kanta countdown. Resuming an already
   * prepared song, or a request with no reservation, keeps the existing single-owner transport
   * path and does not delay playback.
   */
  private void playWithCountdown() {
    if (session.playing || !session.ready || session.loading) {
      session.toggle();
      return;
    }
    if (session.player != null || stagePresenter == null || session.queue.peek() == null) {
      session.toggle();
      return;
    }
    int seconds = new SettingsStore(this).getCountdownLength();
    if (seconds <= 0) {
      session.toggle();
      return;
    }
    stagePresenter.startCountdown(
        seconds,
        new StagePresenter.CountdownListener() {
          @Override
          public void onTick(int remaining) {}

          @Override
          public void onComplete() {
            session.toggle();
          }
        });
  }

  private ph.kanta.reborn.ui.HomeScreen homeScreen;
  private int homeSubmitGeneration;

  private void render() {
    if (isFinishing() || isDestroyed()) return;
    reconcileOnlinePreviewWithLocalPlayback();
    searchGeneration++;
    queryInput = null;
    results = null;
    if (root == null) buildWindow();
    refreshLowerUtilityState();
    boolean destination = controlDeckVisible || !"Home".equals(tab) || lowerUtilityAccessory != null;
    if (workspaceBackCallback != null
        && (workspace == null || workspace.activeAccessory() == null))
      workspaceBackCallback.setEnabled(destination);
    // The stage is the one persistent playback surface. Destinations are rendered in the
    // utility pane beneath it; never detach or hide the stage while navigating.
    if (appHeader != null) appHeader.setVisibility(View.VISIBLE);
    if (stagePane != null) stagePane.setVisibility(View.VISIBLE);
    if (navigationTabs != null) navigationTabs.setVisibility(View.VISIBLE);
    for (Map.Entry<String, Button> entry : navigationButtons.entrySet()) {
      Button nav = entry.getValue();
      boolean active = entry.getKey().equals(tab);
      nav.setSelected(active);
      nav.setTextColor(active ? ACCENT : KantaTheme.TEXT_MUTED);
      final float navDensity = getResources().getDisplayMetrics().density;
      nav.setBackground(active
          ? KantaTheme.backgroundDrawable(KantaTheme.PRIMARY_BLUE, 16, ACCENT, 1, navDensity)
          : null);
      android.graphics.drawable.Drawable[] dr = nav.getCompoundDrawables();
      if (dr[1] != null) dr[1].setAlpha(active ? 255 : 96);
    }
    // Refresh the Home queue without losing the user's in-progress keypad entry.
    if ("Home".equals(tab) && lowerUtilityAccessory == null && !controlDeckVisible
        && homeScreen != null && homeScreen.getParent() == body) {
      refreshHomeReservations();
      if (stagePresenter != null) stagePresenter.refresh();
      return;
    }
    // Now Playing is a live projection. Keep its view tree stable while the
    // clock/session publishes; rebuilding it every 100ms drops focus and is
    // needlessly expensive. Navigation into it still builds once below.
    if ("Now Playing".equals(tab) && lowerUtilityAccessory == null
        && nowPlayingScreen != null && body != null) {
      refreshNowPlayingScreen();
      if (stagePresenter != null) stagePresenter.refresh();
      return;
    }
    body.removeAllViews();
    homeScreen = null;
    nowPlayingScreen = null;
    if (controlDeckVisible) {
      renderControlDeck();
      if (stagePresenter != null) stagePresenter.refresh();
      return;
    }
    renderLowerUtility();
    refreshLowerUtilityState();
    if (lowerUtilityAccessory == null) renderPanel();
    if (stagePresenter != null) stagePresenter.refresh();
  }

  private void buildWindow() {
    root = column();
    root.setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.BG_MAIN);
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
    appHeader = header;
    header.setGravity(Gravity.CENTER_VERTICAL);
    ImageView mark = new ImageView(this);
    mark.setImageResource(ph.kanta.reborn.R.drawable.kanta_mark);
    mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    mark.setContentDescription("Open KANTA control deck");
    mark.setTag("kanta.header.logo");
    mark.setClickable(true);
    mark.setFocusable(true);
    mark.setOnClickListener(v -> toggleControlDeck());
    header.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));
    LinearLayout brandBlock = column();
    brandBlock.setPadding(dp(10), 0, 0, 0);
    TextView brand = text("KANTA", 24, ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    brand.setTypeface(null, android.graphics.Typeface.BOLD);
    brandBlock.addView(brand);
    TextView brandTagline = text("SING MORE. TOGETHER.", 9, ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    brandTagline.setLetterSpacing(0.16f);
    brandBlock.addView(brandTagline);
    header.addView(brandBlock, new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(button("Help", this::help));
    root.addView(header);
    LinearLayout main = new LinearLayout(this);
    // The live stage is always the first row. Utilities flow below it on every
    // form factor; a tablet must not turn the stage into a side rail.
    main.setOrientation(LinearLayout.VERTICAL);
    root.addView(main, new LinearLayout.LayoutParams(-1, 0, 1));
    stagePane = column();
    // The workspace now owns only the persistent presenter. Utility actions are
    // routed to the lower panel, never layered over this surface.
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
            if (stagePresenter != null) {
              if (KantaWorkspace.ACCESSORY_SEARCH.equals(accessoryId)) {
                stagePresenter.hideSearch();
              } else if (KantaWorkspace.ACCESSORY_QUEUE.equals(accessoryId)) {
                stagePresenter.hideQueue();
              }
            }
            if (returnToHomeAfterAccessory) {
              returnToHomeAfterAccessory = false;
              tab = "Home";
              handler.post(MainActivity.this::render);
            }
          }
        });
    // Create StagePresenterImpl with all stage overlays. Stage search must find the songs the
    // active player can actually play, so the catalog merges the (dormant) SQLite index with
    // the live library snapshot.
    ContentRoot contentRoot = new ContentRoot(this);
    ContentScanner scanner = new ContentScanner(getContentResolver(), contentRoot);
    SongCatalog catalog =
        new ph.kanta.reborn.core.catalog.MergedSongCatalog(
            new ph.kanta.reborn.core.catalog.SqliteSongCatalog(this, contentRoot, scanner),
            new ph.kanta.reborn.core.catalog.LibrarySongCatalog(() -> session.songs));
    stagePresenter = new StagePresenterImpl(this, session, catalog, session.queue);
    stagePresenter.setTag("kanta.stage.primary");
    stagePresenter.setOnClickListener(v -> { controlDeckVisible = false; tab = "Now Playing"; render(); });
    stagePresenter.setContentDescription("Karaoke stage. Tap to open Now Playing.");
    workspace.addPersistentLayer(stagePresenter);
    // Register as ScoreSink
    session.setScoreSink(stagePresenter);
    stagePane.addView(workspace, new LinearLayout.LayoutParams(-1, -1));
    // Split the available main area instead of reserving a fixed landscape height;
    // compact displays must leave usable space for the lower utility destination.
    stagePane.setMinimumHeight(dp(140));
    main.addView(stagePane, new LinearLayout.LayoutParams(-1, 0, 0.42f));
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = column();
    body.setPadding(dp(4), 0, dp(4), dp(14));
    scroll.addView(body);
    main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 0.58f));
    // Bottom navigation is one continuous floating dock: icon over label,
    // muted when inactive, cyan + a subtle glow pill when the destination is active.
    LinearLayout tabs = row();
    navigationTabs = tabs;
    tabs.setPadding(dp(6), dp(5), dp(6), dp(5));
    tabs.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.BG_ELEVATED, 22, KantaTheme.BORDER, 1,
        getResources().getDisplayMetrics().density));
    LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    dockParams.setMargins(dp(12), dp(2), dp(12), dp(12));
    tabs.setLayoutParams(dockParams);
    final int[] navIcons = {
      R.drawable.kanta_home_color,
      R.drawable.kanta_sing_color,
      R.drawable.kanta_performance_color,
      R.drawable.kanta_experience_color,
      R.drawable.kanta_more_color
    };
    for (String name : new String[] {"Home", "Sing", "Performance", "Experience", "More"}) {
      final int iconIndex = java.util.Arrays.asList(
          new String[]{"Home", "Sing", "Performance", "Experience", "More"}).indexOf(name);
      Button b =
          button(
              name,
              () -> {
                controlDeckVisible = false;
                lowerUtilityAccessory = null;
                tab = name;
                render();
              });
      b.setMinWidth(0);
      b.setMinimumWidth(0);
      b.setPadding(dp(2), dp(4), dp(2), dp(4));
      b.setTextSize(11);
      b.setMaxLines(1);
      b.setSelected(name.equals(tab));
      b.setTag("kanta.nav." + name);
      Drawable icon = getDrawable(navIcons[iconIndex]).mutate();
      icon.setAlpha(name.equals(tab) ? 255 : 96);
      b.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
      b.setCompoundDrawablePadding(dp(3));
      b.setTextColor(name.equals(tab) ? ACCENT : KantaTheme.TEXT_MUTED);
      b.setBackground(name.equals(tab)
          ? KantaTheme.backgroundDrawable(KantaTheme.PRIMARY_BLUE, 16, ACCENT, 1,
              getResources().getDisplayMetrics().density)
          : null);
      if ("More".equals(name)) {
        b.setContentDescription("More. Long press for workspace actions.");
        b.setOnLongClickListener(v -> { showWorkspaceActions(v); return true; });
      }
      navigationButtons.put(name, b);
      tabs.addView(b, new LinearLayout.LayoutParams(0, dp(56), 1));
    }
    root.addView(tabs);
    if (stagePresenter != null) stagePresenter.refresh();
  }

  /** Logo-triggered special controller; the header, live stage and bottom tabs stay mounted. */
  private void toggleControlDeck() {
    if (controlDeckVisible) {
      closeControlDeck();
      return;
    }
    controlDeckReturnTab = tab;
    lowerUtilityAccessory = null;
    controlDeckVisible = true;
    render();
  }

  private void closeControlDeck() {
    controlDeckVisible = false;
    tab = controlDeckReturnTab == null ? "Home" : controlDeckReturnTab;
    render();
  }

  /** The More tab is the canonical home for utility destinations. */
  private void showWorkspaceActions(View anchor) {
    if (anchor == null) return;
    PopupMenu menu = new PopupMenu(this, anchor);
    String[] labels = {"Search", "Queue", "Visuals", "Controls", "Pitch", "Coach", "More & Settings"};
    for (String label : labels) menu.getMenu().add(label);
    menu.setOnMenuItemClickListener(item -> {
      String label = String.valueOf(item.getTitle());
      controlDeckVisible = false;
      if ("Search".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_SEARCH);
      else if ("Queue".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_QUEUE);
      else if ("Visuals".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_VISUALS);
      else if ("Controls".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_MORE);
      else if ("Pitch".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_PITCH_GAME);
      else if ("Coach".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_LEARNING_ASSISTANT);
      else { lowerUtilityAccessory = null; tab = "More"; render(); }
      return true;
    });
    menu.show();
  }

  private void renderControlDeck() {
    TextView heading = text("KANTA Control Deck", 18, ACCENT);
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    body.addView(heading);
    ph.kanta.reborn.ui.KantaControlDeck deck =
        new ph.kanta.reborn.ui.KantaControlDeck(this, new ph.kanta.reborn.ui.KantaControlDeck.Listener() {
          @Override public void onVolumeDelta(float delta) {
            session.setVolume(Math.max(0f, Math.min(1f, session.volume + delta)));
            render();
          }
          @Override public void onAction(String action) { handleControlDeckAction(action); }
        });
    deck.setVolume(session.volume);
    body.addView(deck, new LinearLayout.LayoutParams(-1, -2));
  }

  private void handleControlDeckAction(String action) {
    switch (action) {
      case "search": controlDeckVisible = false; presentAccessory(KantaWorkspace.ACCESSORY_SEARCH); break;
      case "queue": controlDeckVisible = false; presentAccessory(KantaWorkspace.ACCESSORY_QUEUE); break;
      case "visuals": controlDeckVisible = false; presentAccessory(KantaWorkspace.ACCESSORY_VISUALS); break;
      case "controls": controlDeckVisible = false; presentAccessory(KantaWorkspace.ACCESSORY_MORE); break;
      case "pitch": controlDeckVisible = false; presentAccessory(KantaWorkspace.ACCESSORY_PITCH_GAME); break;
      case "coach": controlDeckVisible = false; presentAccessory(KantaWorkspace.ACCESSORY_LEARNING_ASSISTANT); break;
      case "play": playWithCountdown(); break;
      case "restart": session.restartCurrent(); break;
      case "next": session.next(); break;
      case "stop": session.stop(); break;
      case "microphone": toggleMicrophone(); break;
      case "monitor": toggleMonitoring(); break;
      case "camera": toggleCamera(); break;
      case "fullscreen": fullscreen(); break;
      case "key_down": session.controls(session.volume, session.tempo, Math.max(-12, session.pitch - 1)); break;
      case "key_up": session.controls(session.volume, session.tempo, Math.min(12, session.pitch + 1)); break;
      case "tempo_down": session.controls(session.volume, Math.max(.5f, session.tempo - .05f), session.pitch); break;
      case "tempo_up": session.controls(session.volume, Math.min(2f, session.tempo + .05f), session.pitch); break;
      case "now_playing": controlDeckVisible = false; tab = "Now Playing"; render(); break;
      case "my_set": controlDeckVisible = false; tab = "My Set"; render(); break;
      case "youtube": openYouTubeVideo(); break;
      case "help": help(); break;
      default: break;
    }
  }

  /** Routes a workspace action into the lower utility panel. */
  private void presentAccessory(String accessoryId) {
    if (accessoryId == null) return;
    if (workspace != null && workspace.activeAccessory() != null) workspace.deactivateAccessory();
    returnToHomeAfterAccessory = false;
    lowerUtilityAccessory = accessoryId;
    render();
  }

  /** Dismisses the active accessory, leaving the stage untouched. */
  private void dismissAccessory() {
    workspaceSearchGeneration++;
    lowerUtilityAccessory = null;
    if (workspace != null) workspace.deactivateAccessory();
    render();
  }

  /** Search accessory: a compact read of the library with reservation. */
  private void searchAccessory(LinearLayout target) {
    LinearLayout inputRow = new LinearLayout(this);
    inputRow.setOrientation(LinearLayout.HORIZONTAL);
    inputRow.setGravity(Gravity.CENTER_VERTICAL);
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setShowSoftInputOnFocus(false);
    input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
        | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    input.setTextColor(Color.WHITE);
    input.setTextSize(16);
    input.setHintTextColor(0xFFAFB8D0);
    input.setHint("Song code, title or artist");
    input.setText(query);
    input.setSelection(input.length());
    input.setContentDescription("KANTA workspace search input");
    input.setTag("kanta.utility.search-input");
    inputRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

    Button keyboardToggle = button("ABC", () -> {});
    keyboardToggle.setTag("kanta.utility.keyboard-toggle");
    keyboardToggle.setContentDescription("Switch KANTA keyboard layout");
    keyboardToggle.setMinWidth(0);
    keyboardToggle.setMinimumWidth(0);
    keyboardToggle.setPadding(dp(10), dp(4), dp(10), dp(4));
    inputRow.addView(keyboardToggle, new LinearLayout.LayoutParams(dp(64), dp(52)));
    target.addView(inputRow);

    InlineKeyboard keyboard = new InlineKeyboard(this, new InlineKeyboard.Listener() {
      @Override public void onText(String value) {
        int start = Math.max(0, input.getSelectionStart());
        int end = Math.max(start, input.getSelectionEnd());
        input.getText().replace(start, end, value);
        input.setSelection(start + value.length());
        input.requestFocus();
      }
      @Override public void onBackspace() {
        int start = Math.max(0, input.getSelectionStart());
        int end = Math.max(start, input.getSelectionEnd());
        if (start != end) input.getText().delete(start, end);
        else if (start > 0) input.getText().delete(start - 1, start);
        input.requestFocus();
      }
      @Override public void onSubmit() { input.requestFocus(); }
    });
    keyboardToggle.setOnClickListener(v -> {
      keyboard.toggle();
      boolean letters = keyboard.isLetters();
      keyboardToggle.setText(letters ? "123" : "ABC");
      keyboardToggle.setContentDescription(letters
          ? "Switch KANTA keyboard to numbers"
          : "Switch KANTA keyboard to letters");
      input.requestFocus();
    });
    keyboard.setTag("kanta.utility.keyboard");
    target.addView(keyboard, new LinearLayout.LayoutParams(-1, dp(204)));

    final LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    target.addView(list);

    input.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {
        query = s.toString();
        renderWorkspaceSearch(list);
      }
      public void afterTextChanged(Editable e) {}
    });
    input.requestFocus();
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
                if (token != workspaceSearchGeneration || isDestroyed()) return;
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
    target.addView(text("Up Next \u00b7 " + session.queue.snapshot().size(), 18, ACCENT));
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

  /** Controls accessory: pitch settings, pitch practice, voice coach, companion control, Kanta-Connect host, fullscreen. */
  private void controlsAccessory(LinearLayout target) {
    target.addView(
        button("Pitch Settings", () -> {
          dismissAccessory();
          showPitchSettings();
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
            if (stagePresenter != null) {
              stagePresenter.hideKantaConnectQR();
              stagePresenter.setRemoteState(ph.kanta.reborn.stage.RemoteStatusView.State.DISCONNECTED);
            }
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
        button("App update", () -> {
          dismissAccessory();
          showUpdatePanel();
        }));
    target.addView(
        button("Fullscreen stage", () -> {
          dismissAccessory();
          fullscreen();
        }));
  }

  /**
   * Kanta-Connect pairing: start hosting, then display the QR code on the persistent stage.
   *
   * The QR image and the printed address carry the same information, so a user can either
   * scan or type. Only the address and the pairing code are individually copyable, never a
   * combined blob: a single combined string in the clipboard is the easiest way for the
   * single-session code to leak.
   *
   * The QR code is shown as an overlay on the karaoke stage. Tap the stage to dismiss.
   * The hosting continues in the background until explicitly stopped.
   */
  private void kantaConnect() {
    session.startKantaConnectHost();
    String code = session.kantaConnectHost.pairingCode();
    if (code == null) {
      Toast.makeText(this, "Kanta-Connect could not start hosting here.", Toast.LENGTH_LONG).show();
      return;
    }
    String host = KantaConnectInfo.chooseHost(localPrivateAddresses());
    if (host == null) {
      session.stopKantaConnectHost();
      Toast.makeText(this, "Join a Wi-Fi network first: Kanta-Connect needs a local address.", Toast.LENGTH_LONG).show();
      return;
    }

    final KantaConnectInfo info = KantaConnectInfo.of(host, code);

    // Show QR code on the stage overlay
    if (stagePresenter != null) {
      StagePresenter.KantaConnectQRInfo qrInfo =
          new StagePresenter.KantaConnectQRInfo(info.host, info.port, info.code, info.link());
      stagePresenter.showKantaConnectQR(qrInfo);
    }

    // Update remote status to show advertising state
    if (stagePresenter != null) {
      stagePresenter.setRemoteState(ph.kanta.reborn.stage.RemoteStatusView.State.ADVERTISING);
    }

    Toast.makeText(this,
        "Kanta-Connect hosting on " + info.endpoint() + " — QR shown on stage. Tap stage to dismiss.",
        Toast.LENGTH_LONG).show();

    render();
  }

  /** Copies a value to the clipboard without ever logging it. */
  private void copyToClipboard(String label, String value) {
    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
        .setPrimaryClip(ClipData.newPlainText(label, value));
    Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
  }

  /** Shows pitch settings dialog (sensitivity, target pitch). */
  private void showPitchSettings() {
    SettingsStore store = new SettingsStore(this);
    LinearLayout content = column();
    content.setPadding(dp(24), dp(16), dp(24), dp(16));

    TextView title = text("Pitch Settings", 20, ACCENT);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    title.setGravity(android.view.Gravity.CENTER);
    content.addView(title);

    // Sensitivity
    TextView sensLabel = text("Voice Coach Sensitivity", 16, Color.WHITE);
    sensLabel.setPadding(0, dp(16), 0, dp(4));
    content.addView(sensLabel);

    TextView sensDesc = text("Higher = stricter pitch evaluation. Lower = more forgiving.", 12, 0xFFAFB8D0);
    content.addView(sensDesc);

    SeekBar sensSeek = new SeekBar(this);
    sensSeek.setMax(200);
    float currentSens = store.getAssistantSensitivity();
    sensSeek.setProgress((int) ((currentSens - 0.1f) / 2.9f * 200));
    content.addView(sensSeek);

    TextView sensValue = text(String.format(java.util.Locale.ROOT, "%.2f", currentSens), 18, ACCENT);
    sensValue.setGravity(android.view.Gravity.CENTER);
    content.addView(sensValue);

    sensSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
          float val = 0.1f + (progress / 200f) * 2.9f;
          sensValue.setText(String.format(java.util.Locale.getDefault(), "%.2f", val));
        }
      }
      @Override public void onStartTrackingTouch(SeekBar seekBar) {}
      @Override public void onStopTrackingTouch(SeekBar seekBar) {
        float val = 0.1f + (seekBar.getProgress() / 200f) * 2.9f;
        store.setAssistantSensitivity(val);
        sensValue.setText(String.format(java.util.Locale.getDefault(), "%.2f", val));
        Toast.makeText(MainActivity.this, "Sensitivity saved: " + String.format(java.util.Locale.getDefault(), "%.2f", val), Toast.LENGTH_SHORT).show();
      }
    });

    // Preset buttons
    LinearLayout presets = row();
    presets.setOrientation(LinearLayout.HORIZONTAL);
    presets.setPadding(0, dp(8), 0, dp(8));
    for (String preset : new String[] {"Beginner (0.5)", "Normal (1.0)", "Pro (2.0)"}) {
      String[] parts = preset.split(" \\(");
      float val = Float.parseFloat(parts[1].replace(")", ""));
      Button b = button(parts[0], () -> {
        store.setAssistantSensitivity(val);
        int prog = (int) ((val - 0.1f) / 2.9f * 200);
        sensSeek.setProgress(prog);
        sensValue.setText(String.format(java.util.Locale.getDefault(), "%.2f", val));
        Toast.makeText(MainActivity.this, "Sensitivity: " + String.format(java.util.Locale.getDefault(), "%.2f", val), Toast.LENGTH_SHORT).show();
      });
      b.setTextSize(12);
      presets.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    content.addView(presets);

    // Target Pitch
    TextView targetLabel = text("Default Target Pitch", 16, Color.WHITE);
    targetLabel.setPadding(0, dp(16), 0, dp(4));
    content.addView(targetLabel);

    TextView targetDesc = text("Used when no song is playing or as manual override.", 12, 0xFFAFB8D0);
    content.addView(targetDesc);

    final String[] notes = {"C4", "C#4", "D4", "D#4", "E4", "F4", "F#4", "G4", "G#4", "A4", "A#4", "B4",
                            "C5", "C#5", "D5", "D#5", "E5", "F5", "F#5", "G5", "G#5", "A5", "A#5", "B5"};
    final float[] noteHz = {261.63f, 277.18f, 293.66f, 311.13f, 329.63f, 349.23f, 369.99f, 392.00f, 415.30f, 440.00f, 466.16f, 493.88f,
                            523.25f, 554.37f, 587.33f, 622.25f, 659.25f, 698.46f, 739.99f, 783.99f, 830.61f, 880.00f, 932.33f, 987.77f};

    String currentName = store.getPitchTargetName();
    int currentIdx = 9;
    for (int i = 0; i < notes.length; i++) {
      if (notes[i].equals(currentName)) { currentIdx = i; break; }
    }

    Spinner noteSpinner = new Spinner(this);
    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, notes);
    noteSpinner.setAdapter(adapter);
    noteSpinner.setSelection(currentIdx);
    content.addView(noteSpinner);

    noteSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        store.setPitchTargetHz(noteHz[position]);
        store.setPitchTargetName(notes[position]);
        Toast.makeText(MainActivity.this, "Target pitch: " + notes[position] + " (" + String.format(java.util.Locale.getDefault(), "%.1f", noteHz[position]) + " Hz)", Toast.LENGTH_SHORT).show();
      }
      @Override
      public void onNothingSelected(AdapterView<?> parent) {}
    });

    // Manual Hz input
    LinearLayout hzRow = row();
    hzRow.setOrientation(LinearLayout.HORIZONTAL);
    hzRow.setPadding(0, dp(8), 0, dp(8));
    TextView hzLabel = text("Custom Hz:", 14, Color.WHITE);
    EditText hzInput = new EditText(this);
    hzInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    hzInput.setHint("e.g. 440.0");
    hzInput.setTextColor(Color.WHITE);
    hzInput.setHintTextColor(0xFF888888);
    hzInput.setText(String.format(java.util.Locale.ROOT, "%.1f", store.getPitchTargetHz()));
    hzRow.addView(hzLabel, new LinearLayout.LayoutParams(dp(100), -2));
    hzRow.addView(hzInput, new LinearLayout.LayoutParams(0, -2, 1));
    content.addView(hzRow);

    Button applyHz = button("Apply Custom Hz", () -> {
      try {
        float hz = Float.parseFloat(hzInput.getText().toString());
        if (hz > 20 && hz < 2000) {
          store.setPitchTargetHz(hz);
          store.setPitchTargetName("Custom");
          Toast.makeText(MainActivity.this, "Target pitch: " + String.format(java.util.Locale.getDefault(), "%.1f", hz) + " Hz", Toast.LENGTH_SHORT).show();
        } else {
          Toast.makeText(MainActivity.this, "Please enter 20-2000 Hz", Toast.LENGTH_SHORT).show();
        }
      } catch (NumberFormatException e) {
        Toast.makeText(MainActivity.this, "Invalid number", Toast.LENGTH_SHORT).show();
      }
    });
    content.addView(applyHz);

    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);

    new AlertDialog.Builder(this)
        .setView(scroll)
        .setPositiveButton("Close", null)
        .show();
  }

  /** The device's non-loopback IPv4 addresses, in interface order. */
  private java.util.List<String> localPrivateAddresses() {
    java.util.List<String> addresses = new java.util.ArrayList<>();
    try {
      for (java.net.NetworkInterface network :
          java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
        if (!network.isUp() || network.isLoopback()) continue;
        for (java.net.InterfaceAddress address : network.getInterfaceAddresses()) {
          java.net.InetAddress inet = address.getAddress();
          if (inet instanceof java.net.Inet4Address) addresses.add(inet.getHostAddress());
        }
      }
    } catch (Exception unavailable) {
      // An interface list is best effort; an empty result reports "join Wi-Fi" instead.
    }
    return addresses;
  }

  private void refreshLowerUtilityState() {
    if (nowPlaying == null || playButton == null || nextButton == null) return;
    nowPlaying.setText(
        session.current == null
            ? "Ready for your next song"
            : session.current.title + (session.singer.isEmpty() ? "" : " · " + session.singer));
    playButton.setText(session.playing ? "Pause" : "Play");
    playButton.setEnabled(session.ready && !session.loading);
    nextButton.setEnabled(session.ready && !session.loading);
  }

  /** Lower utility strip; deliberately outside the persistent stage row. */
  private void renderLowerUtility() {
    nowPlaying = null;
    playButton = null;
    nextButton = null;
    if (lowerUtilityAccessory != null)
      body.addView(button("Close utility", this::dismissAccessory));
    if (KantaWorkspace.ACCESSORY_SEARCH.equals(lowerUtilityAccessory)) {
      body.addView(text("Search", 18, ACCENT));
      searchAccessory(body);
    } else if (KantaWorkspace.ACCESSORY_QUEUE.equals(lowerUtilityAccessory)) {
      body.addView(text("Queue", 18, ACCENT));
      queueAccessory(body);
    } else if (KantaWorkspace.ACCESSORY_VISUALS.equals(lowerUtilityAccessory)) {
      body.addView(text("Visuals", 18, ACCENT));
      visualsAccessory(body);
    } else if (KantaWorkspace.ACCESSORY_MORE.equals(lowerUtilityAccessory)) {
      body.addView(text("Controls", 18, ACCENT));
      controlsAccessory(body);
    } else if (KantaWorkspace.ACCESSORY_PITCH_GAME.equals(lowerUtilityAccessory)) {
      body.addView(text("Pitch Practice", 18, ACCENT));
      body.addView(button("Start pitch practice", () -> stagePresenter.startPitchPractice()));
      ph.kanta.reborn.stage.PitchGameOverlay overlay = stagePresenter.detachPitchGameOverlay();
      overlay.showPanel();
      body.addView(overlay, new LinearLayout.LayoutParams(-1, dp(420)));
    } else if (KantaWorkspace.ACCESSORY_LEARNING_ASSISTANT.equals(lowerUtilityAccessory)) {
      body.addView(text("Voice Coach", 18, ACCENT));
      body.addView(button("Start voice coach", () -> stagePresenter.startVoiceCoach()));
      ph.kanta.reborn.stage.LearningAssistantOverlay overlay = stagePresenter.detachLearningAssistantOverlay();
      overlay.showPanel();
      body.addView(overlay, new LinearLayout.LayoutParams(-1, dp(420)));
    }
  }

  private void renderPanel() {
    microphoneReading = null;
    monitorReading = null;
    scoreReading = null;
    nowPlayingScreen = null;
    String legacyTitle = legacyDestinationTitle();
    if (legacyTitle != null) {
      String tag = "kanta.legacy." + tab.toLowerCase(Locale.ROOT).replace(' ', '-') + ".back";
      body.addView(new ph.kanta.reborn.ui.components.KantaScreenHeader(
          this, legacyTitle, "Back to Home", tag, v -> navigateHome()));
    }
    if (!"Home".equals(tab) && !isReferenceDestination(tab)) {
      body.addView(text(session.message, 13, CYAN));
      body.addView(button("YouTube video (online)", this::openYouTubeVideo));
    }

    switch (tab) {
      case "Home":
        homeDashboard();
        break;
      case "Sing":
        singGroup();
        break;
      case "Performance":
        performanceGroup();
        break;
      case "Experience":
        experienceGroup();
        break;
      case "More":
        moreGroup();
        break;
      case "Search":
        searchScreen();
        break;
      case "Queue":
        queueScreen();
        break;
      case "Pitch & Tempo":
        pitchTempoScreen();
        break;
      case "Mic & Voice":
        micVoiceScreen();
        break;
      case "Visuals":
        visualsScreen();
        break;
      case "Now Playing":
        nowPlayingScreen();
        break;
      case "My Set":
        mySet();
        break;
      case "Scenic":
        scenic();
        break;
      case "Loop":
        loop();
        break;
      case "Realm":
        realm();
        break;
      default:
        control();
    }
  }

  private String legacyDestinationTitle() {
    switch (tab) {
      case "My Set": return "My Set";
      case "Scenic": return "Scenic";
      case "Realm": return "Realm";
      case "Control": return "Settings";
      case "Loop": return "Party Mode";
      default: return null;
    }
  }


  private void openHomeAccessory(String accessoryId) {
    presentAccessory(accessoryId);
  }

  private boolean isGroupDestination(String destination) {
    return "Sing".equals(destination)
        || "Performance".equals(destination)
        || "Experience".equals(destination)
        || "More".equals(destination);
  }

  private boolean isReferenceDestination(String destination) {
    return "Sing".equals(destination)
        || "Performance".equals(destination)
        || "Experience".equals(destination)
        || "More".equals(destination)
        || "Search".equals(destination)
        || "Queue".equals(destination)
        || "Pitch & Tempo".equals(destination)
        || "Mic & Voice".equals(destination)
        || "Visuals".equals(destination)
        || "Now Playing".equals(destination)
        || "My Set".equals(destination)
        || "Scenic".equals(destination)
        || "Realm".equals(destination)
        || "Control".equals(destination);
  }

  private void navigateHome() {
    boolean wasVisuals = "Visuals".equals(tab);
    tab = "Home";
    if (wasVisuals) visualsSelectedTab = 0;
    micVoiceScreen = null;
    nowPlayingScreen = null;
    render();
  }

  /** Now Playing is a utility projection below the persistent live stage. */
  private void nowPlayingScreen() {
    nowPlayingScreen=new ph.kanta.reborn.ui.KantaNowPlayingScreen(this,new ph.kanta.reborn.ui.KantaNowPlayingScreen.Listener(){
      @Override public void onBack(){navigateHome();}
      @Override public void onFullscreen(){fullscreen();}
      @Override public void onPlayPause(){session.toggle();}
      @Override public void onPrevious(){ session.restartCurrent(); }
      @Override public void onNext(){session.next();}
      @Override public void onShuffle(){}
      @Override public void onRepeat(){}
      @Override public void onKeyDown(){session.controls(session.volume,session.tempo,Math.max(-12,session.pitch-1));}
      @Override public void onKeyUp(){session.controls(session.volume,session.tempo,Math.min(12,session.pitch+1));}
      @Override public void onTempoDown(){session.controls(session.volume,Math.max(.5f,session.tempo-.05f),session.pitch);}
      @Override public void onTempoUp(){session.controls(session.volume,Math.min(2f,session.tempo+.05f),session.pitch);}
      @Override public void onFavorite(){}
      @Override public void onOverflow(){findOnlineLyrics();}
    });

    nowPlayingScreen.setTag("kanta.screen.now-playing");
    body.addView(nowPlayingScreen,new LinearLayout.LayoutParams(-1,-2));
    onlineLyricsButton = button("Find lyrics on LRCLIB", this::findOnlineLyrics);
    LinearLayout.LayoutParams lyricsActionParams = new LinearLayout.LayoutParams(-1, -2);
    lyricsActionParams.setMargins(0, dp(16), 0, dp(8));
    body.addView(onlineLyricsButton, lyricsActionParams);
    refreshNowPlayingScreen();
  }
  private void refreshNowPlayingScreen() {
    if(nowPlayingScreen==null||!"Now Playing".equals(tab))return;
    Song current=session.current;
    if (onlineLyricsButton != null) onlineLyricsButton.setEnabled(current != null && session.lyrics.isEmpty());
    // Truthful empty state: the headline stays the exact phrase the runtime check asserts,
    // and the only empty-state instruction is repeated once, under it.
    nowPlayingScreen.setSongInfo(current==null?"Nothing playing":current.title,
        current==null?"Reserve a song to begin":(session.singer.isEmpty()?current.artist:session.singer));
    long position=Math.max(0,session.position()),duration=Math.max(0,session.duration());
    nowPlayingScreen.setTimes(formatPlaybackTime(position),duration>0?formatPlaybackTime(duration):"—");
    nowPlayingScreen.setProgress(duration>0?Math.min(1f,position/(float)duration):0f);
    nowPlayingScreen.setPlaying(session.playing);
    nowPlayingScreen.setKey(session.effectivePitch());
    nowPlayingScreen.setTempo(session.effectiveTempo());
    nowPlayingScreen.setTransportCapabilities(session.queue.peek()!=null);
    java.util.List<ph.kanta.reborn.core.MidiLyrics.Cue> cues=session.lyrics;
    if(current==null)nowPlayingScreen.setLyrics("","No song is playing","Reserve a song to begin","");
    else if(cues==null||cues.isEmpty())nowPlayingScreen.setLyrics("","No timed lyrics available","","");
    else {
      int active = ph.kanta.reborn.core.MidiLyrics.activeIndex(cues, position);
      if (active < 0) {
        String first = cues.get(0).line;
        String second = cues.size() > 1 ? cues.get(1).line : "";
        nowPlayingScreen.setLyrics("", "Get ready to sing…", first, second, 0);
      } else {
        String previous = active > 0 ? cues.get(active - 1).line : "";
        String line = cues.get(active).line;
        String next = active + 1 < cues.size() ? cues.get(active + 1).line : "";
        String following = active + 2 < cues.size() ? cues.get(active + 2).line : "";
        nowPlayingScreen.setLyrics(
            previous, line, next, following,
            ph.kanta.reborn.core.LyricWordAnimation.highlightedCharacters(cues.get(active), position));
      }
    }
  }
  private void findOnlineLyrics() {
    Song song = session.current;
    if (song == null || session.playbackIdentity == null) {
      Toast.makeText(this, "Play a song before finding lyrics.", Toast.LENGTH_LONG).show(); return;
    }
    if (!session.lyrics.isEmpty()) {
      Toast.makeText(this, "This song already has timed lyrics. They will not be replaced.", Toast.LENGTH_LONG).show(); return;
    }
    UUID identity = session.playbackIdentity;
    int dialogRequest = ++lyricDialogGeneration;
    LinearLayout fields = column();
    fields.setPadding(dp(20), dp(8), dp(20), dp(8));
    fields.addView(text("Search sends the title and artist below, plus your IP address and app identification, "
        + "to lrclib.net. No audio, singer names or library files are sent. Choose a matching version yourself. "
        + "Lyrics stay in memory for this playback; embedded lyrics are kept.", 14, Color.WHITE));
    EditText title = new EditText(this); title.setHint("Song title"); title.setText(song.title);
    title.setContentDescription("Song title for LRCLIB search"); fields.addView(title);
    EditText artist = new EditText(this); artist.setHint("Artist (optional)"); artist.setText(song.artist);
    artist.setContentDescription("Artist for LRCLIB search"); fields.addView(artist);
    new AlertDialog.Builder(this).setTitle("Find lyrics on LRCLIB")
        .setView(fields).setNegativeButton("Cancel", null)
        .setPositiveButton("Search", (dialog, which) ->
          session.searchOnlineLyrics(identity, title.getText().toString(), artist.getText().toString(), results -> {
            if (isFinishing() || isDestroyed() || dialogRequest != lyricDialogGeneration
                || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return;
            if (results.isEmpty()) {
              Toast.makeText(this, session.message, Toast.LENGTH_LONG).show(); return;
            }
            long request = session.lyricLookupGeneration;
            String[] choices = new String[results.size()];
            for (int i = 0; i < results.size(); i++) {
              ph.kanta.reborn.lyrics.LrclibClient.Result match = results.get(i);
              choices[i] = match.title + " · " + match.artist + " · " + match.album
                  + " · " + formatPlaybackTime((long) (match.duration * 1000))
                  + (match.instrumental ? " · Instrumental" : match.timedCues.isEmpty()
                      ? " · Untimed" : " · Line-timed");
            }
            new AlertDialog.Builder(this).setTitle("Choose the matching version · LRCLIB")
                .setItems(choices, (matches, index) -> {
                  if (!identity.equals(session.playbackIdentity) || request != session.lyricLookupGeneration) return;
                  ph.kanta.reborn.lyrics.LrclibClient.Result match = results.get(index);
                  if (session.applyOnlineLyrics(identity, request, match)) return;
                  if (!match.plainLyrics.isEmpty() && !match.instrumental) {
                    String plain = match.plainLyrics.length() > 65536
                        ? match.plainLyrics.substring(0, 65536) + "\n[Preview truncated]" : match.plainLyrics;
                    TextView lyrics = text(plain, 18, Color.WHITE);
                    lyrics.setTextIsSelectable(true);
                    ScrollView scroll = new ScrollView(this); scroll.setPadding(dp(20), dp(12), dp(20), dp(12));
                    scroll.addView(lyrics);
                    new AlertDialog.Builder(this).setTitle("Untimed lyrics · LRCLIB")
                        .setView(scroll).setPositiveButton("Done", null).show();
                  } else Toast.makeText(this, "No usable lyrics in this version. Your song is unchanged.", Toast.LENGTH_LONG).show();
                }).setNegativeButton("Cancel", null).show();
          }))
        .show();
  }

  private String formatPlaybackTime(long ms){long seconds=Math.max(0,ms/1000);return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}

  /** Full-screen visual picker backed by the existing scenic, camera and import owners. */
  private void visualsScreen() {
    ph.kanta.reborn.ui.KantaVisualsScreen screen =
        new ph.kanta.reborn.ui.KantaVisualsScreen(
            this,
            new ph.kanta.reborn.ui.KantaVisualsScreen.Listener() {
              @Override public void onBack() { navigateHome(); }
              @Override public void onTabSelected(int index, String label) {
                visualsSelectedTab = index;
              }
              @Override public void onBackgroundSelected(String name) {
                if (!session.stageReady) return;
                camera.stop();
                switch (name) {
                  case "Default": session.sceneResource("default", ph.kanta.reborn.R.drawable.kanta_stage_phone); break;
                  case "Stage Lights": session.sceneResource("stageLights", ph.kanta.reborn.R.drawable.kanta_visual_stage_lights); break;
                  case "Galaxy": session.sceneResource("galaxy", ph.kanta.reborn.R.drawable.kanta_visual_galaxy); break;
                  case "Sunset": session.sceneResource("sunset", ph.kanta.reborn.R.drawable.kanta_visual_sunset); break;
                  case "Ocean": session.sceneResource("ocean", ph.kanta.reborn.R.drawable.kanta_visual_ocean); break;
                  case "City": session.sceneResource("city", ph.kanta.reborn.R.drawable.kanta_visual_city); break;
                  case "Neon": session.sceneResource("neon", ph.kanta.reborn.R.drawable.kanta_visual_neon); break;
                  case "Particles": session.sceneResource("particles", ph.kanta.reborn.R.drawable.kanta_visual_particles); break;
                  default: return;
                }
              }
              @Override public void onCameraToggle(boolean enabled) {
                if (enabled != camera.enabled()) toggleCamera();
              }
              @Override public void onEffectSelected(String name) {
                Toast.makeText(
                    MainActivity.this,
                    "Stage effect " + name + " is not available yet. Your current background is unchanged.",
                    Toast.LENGTH_LONG).show();
              }
              @Override public void onCustomImport() { chooseCustomStageMedia(); }
            });
    screen.setTag("kanta.screen.visuals");
    screen.setSelectedTab(visualsSelectedTab);
    screen.setSelectedScene(session.scenicName);
    screen.setCameraState(camera.enabled(), camera.streaming());
    body.addView(screen, new LinearLayout.LayoutParams(-1, -1));
  }

  private void chooseCustomStageMedia() {
    new AlertDialog.Builder(this)
        .setTitle("Custom stage background")
        .setItems(
            new String[] {"Choose photo", "Choose video"},
            (dialog, selected) -> {
              Intent picker =
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .addCategory(Intent.CATEGORY_OPENABLE)
                      .setType(selected == 0 ? "image/*" : "video/*");
              startActivityForResult(picker, selected == 0 ? PHOTO : VIDEO);
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  /** The UI exposes only controls backed by the existing microphone and monitor owners. */
  private void micVoiceScreen() {
    micVoiceScreen =
        new ph.kanta.reborn.ui.KantaMicVoiceScreen(
            this,
            new ph.kanta.reborn.ui.KantaMicVoiceScreen.Listener() {
              @Override public void onBack() { navigateHome(); }
              @Override public void onMicToggle(boolean enabled) {
                if (enabled) toggleMicrophone();
                else session.stopMicrophone();
              }
              @Override public void onMonitorToggle(boolean enabled) {
                if (enabled) toggleMonitoring();
                else session.stopMonitor();
              }
              @Override public void onMonitorGainChanged(float value) {
                session.monitor.gain(value);
              }
              @Override public void onMonitorModeSelected(String mode) {
                session.monitor.mode(monitorModeFor(mode));
                session.publish();
              }
              @Override public void onScoringToggle(boolean enabled) {
                session.setScoring(enabled);
              }
            });
    micVoiceScreen.setTag("kanta.screen.mic-voice");
    micVoiceScreen.setState(
        session.microphone.enabled(),
        session.microphone.capturing(),
        session.monitor.enabled(),
        session.monitor.audible(),
        session.monitor.gain(),
        session.monitor.mode().name(),
        session.scoringEnabled,
        session.scoreStatus());
    body.addView(micVoiceScreen, new LinearLayout.LayoutParams(-1, -1));
  }

  /**
   * Resolves a monitor-effect label to its engine preset.
   *
   * <p>The label is the chip text, not an enum name, so the mapping is explicit and a renamed
   * label can never silently select the wrong reverb. Unknown labels fall back to dry output
   * rather than failing.
   */
  private static ph.kanta.reborn.core.VocalMonitorEffects.Mode monitorModeFor(String label) {
    if (label == null) return ph.kanta.reborn.core.VocalMonitorEffects.Mode.DRY;
    switch (label.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "small room": return ph.kanta.reborn.core.VocalMonitorEffects.Mode.SMALL_ROOM;
      case "room": return ph.kanta.reborn.core.VocalMonitorEffects.Mode.ROOM;
      case "studio": return ph.kanta.reborn.core.VocalMonitorEffects.Mode.STUDIO;
      case "concert hall": return ph.kanta.reborn.core.VocalMonitorEffects.Mode.CONCERT_HALL;
      case "hall": return ph.kanta.reborn.core.VocalMonitorEffects.Mode.HALL;
      case "cathedral": return ph.kanta.reborn.core.VocalMonitorEffects.Mode.CATHEDRAL;
      case "plate reverb": return ph.kanta.reborn.core.VocalMonitorEffects.Mode.PLATE_REVERB;
      case "slap echo": return ph.kanta.reborn.core.VocalMonitorEffects.Mode.SLAP_ECHO;
      case "warm tape": return ph.kanta.reborn.core.VocalMonitorEffects.Mode.WARM_TAPE;
      default: return ph.kanta.reborn.core.VocalMonitorEffects.Mode.DRY;
    }
  }

  private int artworkResourceForSong(Song song) {
    int index = Math.floorMod(song.id.hashCode(), 8);
    switch (index) {
      case 0: return ph.kanta.reborn.R.drawable.kanta_art_01;
      case 1: return ph.kanta.reborn.R.drawable.kanta_art_02;
      case 2: return ph.kanta.reborn.R.drawable.kanta_art_03;
      case 3: return ph.kanta.reborn.R.drawable.kanta_art_04;
      case 4: return ph.kanta.reborn.R.drawable.kanta_art_05;
      case 5: return ph.kanta.reborn.R.drawable.kanta_art_06;
      case 6: return ph.kanta.reborn.R.drawable.kanta_art_07;
      default: return ph.kanta.reborn.R.drawable.kanta_art_08;
    }
  }

  /** Search projects the current catalog; it does not claim unsupported YouTube discovery. */
  private void searchScreen() {
    final java.util.ArrayList<Song> visibleSongs = new java.util.ArrayList<>();
    final ph.kanta.reborn.ui.KantaSearchScreen[] screenRef =
        new ph.kanta.reborn.ui.KantaSearchScreen[1];
    ph.kanta.reborn.ui.KantaSearchScreen screen =
        new ph.kanta.reborn.ui.KantaSearchScreen(
            this,
            new ph.kanta.reborn.ui.KantaSearchScreen.Listener() {
              @Override public void onBack() { navigateHome(); }
              @Override public void onSearchTextChanged(String value) {
                query = value == null ? "" : value;
                renderSearchResults(screenRef[0], visibleSongs);
              }
              @Override public void onTabSelected(int index, String label) {
                searchSource = label;
                renderSearchResults(screenRef[0], visibleSongs);
              }
              @Override public void onSing(String title, String artist) {
                for (Song song : visibleSongs) {
                  if (song.title.equals(title)
                      && (song.artist.isEmpty() ? "Unknown artist" : song.artist).equals(artist)) {
                    session.reserve(song, singer);
                    Toast.makeText(MainActivity.this,
                        "Added " + song.title + " to Queue", Toast.LENGTH_SHORT).show();
                    return;
                  }
                }
              }
              @Override public void onSourceAll() {}
              @Override public void onSourceKanta() {}
              @Override public void onSourceYouTube() {
                screenRef[0].showEmpty();
                Toast.makeText(MainActivity.this,
                    "YouTube discovery is not enabled. Paste a video link to open the official player.",
                    Toast.LENGTH_LONG).show();
                openYouTubeVideo();
              }
              @Override public void onSourceLocal() {}
            });
    screenRef[0] = screen;
    screen.setTag("kanta.screen.search");
    body.addView(screen, new LinearLayout.LayoutParams(-1, -1));
    // Prefill only for a committed Home entry; a bare tap on Search opens the full library.
    String committedQuery = query;
    query = "";
    if (!committedQuery.isEmpty()) screen.setQuery(committedQuery);
    renderSearchResults(screen, visibleSongs);
  }

  private void renderSearchResults(
      ph.kanta.reborn.ui.KantaSearchScreen screen,
      java.util.ArrayList<Song> visibleSongs) {
    if (screen == null || !"Search".equals(tab)) return;
    final int token = ++referenceSearchGeneration;
    final String requested = query;
    final String source = searchSource;
    if ("YouTube".equals(source)) {
      visibleSongs.clear();
      screen.showEmpty();
      return;
    }
    final java.util.List<Song> catalog = new java.util.ArrayList<>(session.songs);
    screen.showLoading();
    searchWorker.execute(() -> {
      java.util.ArrayList<Song> matches = new java.util.ArrayList<>();
      String[] terms = Song.searchTerms(requested);
      for (Song song : catalog) {
        if (Thread.currentThread().isInterrupted()) return;
        if ("KANTA".equals(source) && !song.starter) continue;
        if ("Local".equals(source) && song.starter) continue;
        if (song.matchesTerms(terms)) matches.add(song);
        if (matches.size() >= 50) break;
      }
      handler.post(() -> {
        if (token != referenceSearchGeneration || !"Search".equals(tab)
            || isFinishing() || isDestroyed()) return;
        visibleSongs.clear();
        visibleSongs.addAll(matches);
        String[][] rows = new String[matches.size()][4];
        for (int i = 0; i < matches.size(); i++) {
          Song song = matches.get(i);
          rows[i][0] = song.title;
          rows[i][1] = song.artist.isEmpty() ? "Unknown artist" : song.artist;
          rows[i][2] = song.starter ? "#30245B" : "#17334A";
          rows[i][3] = String.valueOf(artworkResourceForSong(song));
        }
        screen.showResults(rows);
      });
    });
  }

  /** Queue is a projection of the single authoritative reservation FIFO. */
  private void queueScreen() {
    java.util.List<ReservationQueue.Entry> entries = session.queue.snapshot();
    String[][] rows = new String[entries.size()][6];
    for (int i = 0; i < entries.size(); i++) {
      ReservationQueue.Entry entry = entries.get(i);
      rows[i][0] = String.valueOf(i + 1);
      rows[i][1] = entry.song.title;
      rows[i][2] = entry.youtube == null
          ? (entry.singer.isEmpty() ? entry.song.artist : entry.singer)
          : "YouTube video";
      rows[i][3] = "false";
      rows[i][4] = entry.youtube == null ? "#30245B" : "#17334A";
      rows[i][5] = String.valueOf(entry.youtube == null
          ? artworkResourceForSong(entry.song) : ph.kanta.reborn.R.drawable.kanta_stage_tv);
    }
    ph.kanta.reborn.ui.KantaQueueScreen screen =
        new ph.kanta.reborn.ui.KantaQueueScreen(
            this,
            new ph.kanta.reborn.ui.KantaQueueScreen.Listener() {
              @Override public void onBack() { navigateHome(); }
              @Override public void onClear() {
                session.queue.clear();
                session.saveQueue();
                session.publish();
                render();
              }
              @Override public void onAddSongs() {
                tab = "Search";
                searchSource = "All";
                render();
              }
              @Override public void onRemove(int position) {
                java.util.List<ReservationQueue.Entry> current = session.queue.snapshot();
                if (position < 0 || position >= current.size()) return;
                session.remove(current.get(position).id);
                render();
              }
              @Override public void onItemClicked(int position) {
                java.util.List<ReservationQueue.Entry> current = session.queue.snapshot();
                if (position < 0 || position >= current.size()) return;
                ReservationQueue.Entry selected = current.get(position);
                if (selected.youtube != null) {
                  Toast.makeText(MainActivity.this,
                      "Host-controlled YouTube queue playback is not available yet.",
                      Toast.LENGTH_LONG).show();
                  return;
                }
                session.move(selected.id, 0);
                session.next();
                render();
              }
            });
    screen.setTag("kanta.screen.queue");
    screen.setCount(entries.size());
    screen.setItems(rows);
    screen.showAddSongsCTA();
    body.addView(screen, new LinearLayout.LayoutParams(-1, -1));
  }

  /** All pitch and tempo changes are delegated to SessionController. */
  private void pitchTempoScreen() {
    final ph.kanta.reborn.ui.KantaPitchTempoScreen[] screenRef =
        new ph.kanta.reborn.ui.KantaPitchTempoScreen[1];
    ph.kanta.reborn.ui.KantaPitchTempoScreen screen =
        new ph.kanta.reborn.ui.KantaPitchTempoScreen(
            this,
            new ph.kanta.reborn.ui.KantaPitchTempoScreen.Listener() {
              private void refreshValues() {
                if (screenRef[0] != null) {
                  screenRef[0].setKey(session.effectivePitch());
                  screenRef[0].setTempo(session.effectiveTempo());
                }
              }
              @Override public void onBack() { navigateHome(); }
              @Override public void onReset() {
                if (session.currentSongHasOverride()) session.clearCurrentSongOverride();
                else session.controls(session.volume, 1.0f, 0);
                refreshValues();
              }
              @Override public void onKeyDown() {
                session.controls(session.volume, session.tempo, Math.max(-12, session.pitch - 1));
                refreshValues();
              }
              @Override public void onKeyUp() {
                session.controls(session.volume, session.tempo, Math.min(12, session.pitch + 1));
                refreshValues();
              }
              @Override public void onTempoDown() {
                session.controls(session.volume, Math.max(0.5f, session.tempo - 0.05f), session.pitch);
                refreshValues();
              }
              @Override public void onTempoUp() {
                session.controls(session.volume, Math.min(2.0f, session.tempo + 0.05f), session.pitch);
                refreshValues();
              }
              @Override public void onTempoChanged(float value) {
                session.controls(session.volume, value, session.pitch);
                refreshValues();
              }
            });
    screenRef[0] = screen;
    screen.setTag("kanta.screen.pitch-tempo");
    screen.setKey(session.effectivePitch());
    screen.setTempo(session.effectiveTempo());
    body.addView(screen, new LinearLayout.LayoutParams(-1, -1));
  }

  /** Reference-led home dashboard. Existing playback and workspace owners remain authoritative. */
  private void homeDashboard() {
    homeScreen = new ph.kanta.reborn.ui.HomeScreen(
            this,
            new ph.kanta.reborn.ui.HomeScreen.Listener() {
              @Override public void onHome() {
                tab = "Home";
                render();
              }
              @Override public void onSing() { tab = "Sing"; render(); }
              @Override public void onPerformance() { tab = "Performance"; render(); }
              @Override public void onExperience() { tab = "Experience"; render(); }
              @Override public void onSettings() { tab = "More"; render(); }
              @Override public void onSearch() { tab = "Search"; searchSource = "All"; render(); }
              @Override public void onSearchQueryChanged(String typed) {
                // Cancel pending RES work even if the user edits back to the same text.
                homeSubmitGeneration++;
              }
              @Override public void onSearchSubmitted(String typed) { submitHomeEntry(typed); }
              @Override public void onQueue() { tab = "Queue"; render(); }
              @Override public void onYouTube() { openYouTubeVideo(); }
              @Override public void onVisuals() { tab = "Visuals"; render(); }
              @Override public void onMicVoice() { tab = "Mic & Voice"; render(); }
              @Override public void onPitchTempo() { tab = "Pitch & Tempo"; render(); }
              @Override public void onMySet() { tab = "My Set"; render(); }
              @Override public void onFavorites() { tab = "My Set"; render(); }
              @Override public void onRecent() { tab = "My Set"; render(); }
              @Override public void onMore() { tab = "More"; render(); }
              @Override public void onScenic() { tab = "Scenic"; render(); }
              @Override public void onRealm() { tab = "Realm"; render(); }
            });
    refreshHomeReservations();
    body.addView(homeScreen, new LinearLayout.LayoutParams(-1, -1));
  }

  private void submitHomeEntry(String typed) {
    final String requested = typed == null ? "" : typed.trim();
    if (requested.isEmpty() || homeScreen == null) return;
    if (!session.ready) {
      Toast.makeText(this, "Your library is still loading. Try again shortly.", Toast.LENGTH_SHORT).show();
      return;
    }
    final int token = ++homeSubmitGeneration;
    final ph.kanta.reborn.ui.HomeScreen source = homeScreen;
    final String entered = source.getSearchText();
    final List<Song> library = session.songs;
    searchWorker.execute(() -> {
      final Song match = Song.uniqueCodeMatch(library, requested);
      handler.post(() -> {
        if (token != homeSubmitGeneration || source != homeScreen || !"Home".equals(tab)
            || !session.foreground || isFinishing() || isDestroyed()
            || !entered.equals(source.getSearchText())) return;
        if (match != null && library == session.songs) {
          int previousCount = session.queue.snapshot().size();
          session.reserve(match, singer);
          Toast.makeText(this, session.message, Toast.LENGTH_SHORT).show();
          if (session.queue.snapshot().size() > previousCount) source.clearSearchText();
        } else {
          query = requested;
          searchSource = "All";
          tab = "Search";
          render();
        }
      });
    });
  }

  private void refreshHomeReservations() {
    List<ReservationQueue.Entry> entries = session.queue.snapshot();
    List<String> songs = new java.util.ArrayList<>();
    for (int i = 0; i < Math.min(3, entries.size()); i++) {
      ReservationQueue.Entry entry = entries.get(i);
      songs.add(entry.song.title + (entry.singer.isEmpty() ? "" : " · " + entry.singer));
    }
    homeScreen.setReservedSongs(songs, entries.size());
  }


  private ph.kanta.reborn.ui.GroupScreen groupScreen(
      String title, String purpose, ph.kanta.reborn.ui.GroupScreen.Item[] items) {
    return new ph.kanta.reborn.ui.GroupScreen(
        this,
        title,
        purpose,
        items,
        new ph.kanta.reborn.ui.GroupScreen.Listener() {
          @Override public void onBack() { navigateHome(); }
          @Override public void onAction(String action) { handleGroupAction(action); }
        });
  }

  private void handleGroupAction(String action) {
    switch (action) {
      case "find-song":
        tab = "Search";
        searchSource = "All";
        render();
        break;
      case "my-songs":
      case "favorites":
      case "recent":
        tab = "My Set";
        render();
        break;
      case "queue":
        tab = "Queue";
        render();
        break;
      case "youtube":
        openYouTubeVideo();
        break;
      case "now-playing":
      case "lyrics":
        tab = "Now Playing";
        render();
        break;
      case "voice":
        tab = "Mic & Voice";
        render();
        break;
      case "key-tempo":
        tab = "Pitch & Tempo";
        render();
        break;
      case "coach":
        tab = "Mic & Voice";
        voiceExpanded = true;
        render();
        break;
      case "background":
      case "camera":
      case "effects":
        tab = "Visuals";
        render();
        break;
      case "scenic":
        tab = "Scenic";
        render();
        break;
      case "party":
        tab = "Loop";
        render();
        break;
      case "display":
        fullscreen();
        break;
      case "settings":
        tab = "Control";
        render();
        break;
      case "companion":
        companionDialog = CompanionDialog.show(this, session);
        break;
      case "diagnostics":
        diagnostics();
        break;
      case "help":
        help();
        break;
      default:
        break;
    }
  }

  private void singGroup() {
    body.addView(groupScreen("Sing", "What do you want to sing?", new ph.kanta.reborn.ui.GroupScreen.Item[] {
      new ph.kanta.reborn.ui.GroupScreen.Item("find-song", "Find a song", "KANTA, YouTube or Local", ph.kanta.reborn.R.drawable.kanta_search_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("my-songs", "My songs", "My Set and playlists", ph.kanta.reborn.R.drawable.kanta_my_songs_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("favorites", "Favorites", "Saved songs", ph.kanta.reborn.R.drawable.kanta_favorites_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("recent", "Recent", "Recently played", ph.kanta.reborn.R.drawable.kanta_recent_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("queue", "Queue", "Up next", ph.kanta.reborn.R.drawable.kanta_queue_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("youtube", "YouTube", "Optional online video", ph.kanta.reborn.R.drawable.kanta_youtube_color)
    }));
  }

  private void performanceGroup() {
    body.addView(groupScreen("Performance", "Help me while I’m singing.", new ph.kanta.reborn.ui.GroupScreen.Item[] {
      new ph.kanta.reborn.ui.GroupScreen.Item("now-playing", "Now Playing", "Playback controls", ph.kanta.reborn.R.drawable.kanta_performance_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("lyrics", "Lyrics", "Follow along while singing", ph.kanta.reborn.R.drawable.kanta_lyrics_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("voice", "Voice", "Microphone and hear my voice", ph.kanta.reborn.R.drawable.kanta_sing_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("key-tempo", "Key & Tempo", "Fit the song to your voice", ph.kanta.reborn.R.drawable.kanta_key_tempo_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("coach", "Singing Coach", "Scoring and practice", ph.kanta.reborn.R.drawable.kanta_coach_color)
    }));
  }

  private void experienceGroup() {
    body.addView(groupScreen("Experience", "How should your karaoke session look and feel?", new ph.kanta.reborn.ui.GroupScreen.Item[] {
      new ph.kanta.reborn.ui.GroupScreen.Item("background", "Background", "Choose your stage", ph.kanta.reborn.R.drawable.kanta_background_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("camera", "Camera", "Add a live preview", ph.kanta.reborn.R.drawable.kanta_camera_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("effects", "Effects", "Stage and voice sound", ph.kanta.reborn.R.drawable.kanta_effects_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("scenic", "Scenic", "Animated stage scenes", ph.kanta.reborn.R.drawable.kanta_scenic_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("realm", "Realm", "Ambient stage mood", ph.kanta.reborn.R.drawable.kanta_realm_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("party", "Party Mode", "Between-song music and sounds", ph.kanta.reborn.R.drawable.kanta_party_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("display", "Display", "Fullscreen and external stage", ph.kanta.reborn.R.drawable.kanta_display_color)
    }));
  }

  private void moreGroup() {
    body.addView(groupScreen("More", "Advanced settings and tools.", new ph.kanta.reborn.ui.GroupScreen.Item[] {
      new ph.kanta.reborn.ui.GroupScreen.Item("settings", "Settings", "Advanced voice and app settings", ph.kanta.reborn.R.drawable.kanta_settings_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("companion", "Companion control", "Use another device as a remote", ph.kanta.reborn.R.drawable.kanta_companion_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("diagnostics", "Diagnostics", "Inspect app and audio state", ph.kanta.reborn.R.drawable.kanta_diagnostics_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("help", "Help", "Learn how KANTA works", ph.kanta.reborn.R.drawable.kanta_help_color)
    }));
  }

  private void openYouTubeVideo() {
    if (!youtubeForeground || isFinishing() || isDestroyed()) return;
    if (youtubeEntryDialog != null) youtubeEntryDialog.dismiss();
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
        | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    input.setHint("https://www.youtube.com/watch?v=...");
    LinearLayout panel = column();
    panel.setPadding(dp(20), dp(8), dp(20), dp(8));
    panel.addView(text("Optional online video. YouTube/Google may collect playback data and show ads. "
        + "Local karaoke continues to work without Internet.", 14, Color.WHITE));
    panel.addView(input);
    youtubeEntryDialog = new AlertDialog.Builder(this)
        .setTitle("Open YouTube video")
        .setView(panel)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Continue", (dialog, which) -> {
          YouTubeVideoReference reference = YouTubeVideoReference.parse(
              input.getText().toString().trim());
          if (reference == null) {
            Toast.makeText(this, "Enter a valid HTTPS YouTube video link.", Toast.LENGTH_LONG).show();
            return;
          }
          showYouTubeVideo(reference);
        }).create();
    final AlertDialog entry = youtubeEntryDialog;
    entry.setOnDismissListener(ignored -> {
      if (youtubeEntryDialog == entry) youtubeEntryDialog = null;
    });
    entry.show();
  }

  void showYouTubeVideo(YouTubeVideoReference reference) {
    // This is a standalone foreground preview, not a queue reservation.
    if (!youtubeForeground || isFinishing() || isDestroyed()) return;
    if (!session.onlineAudioAllowed()) {
      Toast.makeText(this, "Pause local karaoke and turn off microphone input before opening an online video.",
          Toast.LENGTH_LONG).show();
      return;
    }
    closeYouTubeVideo();
    final YouTubePreviewLifecycle.Session online = youtubeLifecycle.open(reference);
    if (!session.claimOnlineAudio(online.id, () -> {
      if (youtubeLifecycle.active() != null && youtubeLifecycle.active().id.equals(online.id)) {
        closeYouTubeVideo();
      }
    })) {
      youtubeLifecycle.close(online.id);
      return;
    }
    final Dialog dialog = new Dialog(this);
    youtubeDialog = dialog;
    LinearLayout layout = column();
    layout.setBackgroundColor(Color.BLACK);
    YouTubeEmbeddedPlayer player = new YouTubeEmbeddedPlayer(this, () -> {
      if (!youtubeForeground || !youtubeLifecycle.close(online.id)) return;
      session.releaseOnlineAudio(online.id);
      dialog.dismiss();
      Toast.makeText(this, "Online video could not load. Check your connection and try again.",
          Toast.LENGTH_LONG).show();
    });
    youtubePlayer = player;
    player.setTag("kanta.youtube.player");
    layout.addView(player, new LinearLayout.LayoutParams(-1, 0, 1));
    layout.addView(button("Close online video", dialog::dismiss));
    layout.addView(text("YouTube has its own playback controls. Opening it stops effects and between-song music. Local audio actions close the online video.", 12, Color.WHITE));
    dialog.setContentView(layout);
    dialog.setOnDismissListener(ignored -> {
      youtubeLifecycle.close(online.id);
      player.release();
      session.releaseOnlineAudio(online.id);
      if (youtubePlayer == player) youtubePlayer = null;
      if (youtubeDialog == dialog) youtubeDialog = null;
    });
    dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setLayout(-1, -1);
    }
    player.show(reference);
  }

  private void voiceControls() {
    body.addView(
        button(
            session.microphone.enabled()
                ? "Turn off microphone"
                : session.microphone.capturing()
                    ? "Starting microphone…"
                    : "Turn on microphone",
            this::toggleMicrophone));
    microphoneReading = text("", 14, CYAN);
    microphoneReading.setTag("kanta.microphone.reading");
    body.addView(microphoneReading);
    refreshMicrophoneReading();
    body.addView(
        text(
            "Effects apply only to voice output. Pitch estimates use the original input. No singing"
                + " is saved or uploaded. Optional correction changes monitored output only.",
            12,
            Color.WHITE));

    body.addView(
        button(
            session.monitor.enabled()
                ? "Turn off monitoring"
                : "Wired / USB / Bluetooth monitoring",
            this::toggleMonitoring));
    monitorReading = text("", 13, CYAN);
    body.addView(monitorReading);
    refreshMicrophoneReading();
    body.addView(
        text(
            "Optional voice output for connected wired, USB or Bluetooth headset equipment. Use"
                + " headphones, start quietly and check your equipment volume. Bluetooth headsets"
                + " use Android communication audio, so sound is mono call quality with higher"
                + " delay and needs Android 12 or newer. Speaker, Bluetooth-speaker and unknown"
                + " reported routes are blocked. Older Android reports only one output. This"
                + " experimental feature still needs device testing for sound and delay.",
            12,
            Color.WHITE));
    // One row per four presets: ten effects do not fit a single phone row, and the display name
    // comes from the enum so an underscore can never reach the user.
    LinearLayout effects = null;
    int shown = 0;
    for (VocalMonitorEffects.Mode value : VocalMonitorEffects.Mode.values()) {
      if (shown % 4 == 0) {
        effects = row();
        body.addView(effects);
      }
      shown++;
      Button option =
          button(
              value.label(),
              () -> {
                session.monitor.mode(value);
                session.publish();
              });
      option.setEnabled(session.monitor.enabled());
      if (session.monitor.mode() == value) option.setTextColor(ACCENT);
      addWeighted(effects, option);
    }
    Button correction =
        button(
            session.monitor.correctionEnabled()
                ? "Turn off pitch correction"
                : "Turn on pitch correction",
            () -> session.monitor.correction(!session.monitor.correctionEnabled()));
    correction.setTag("kanta.pitchcorrection.toggle");
    correction.setEnabled(session.monitor.correctionAvailable());
    body.addView(correction);
    body.addView(
        text(
            "Experimental chromatic correction: enable wired/USB monitoring first. Stable voice"
                + " follows the nearest semitone; uncertain input stays unshifted. This adds delay"
                + " and can change voice tone or create artifacts. It does not follow the song's"
                + " melody or change scoring. Leaving monitoring turns it off.",
            12,
            Color.WHITE));
    body.addView(text("Voice output · maximum 35%", 13, Color.WHITE));
    SeekBar voiceGain = new SeekBar(this);
    voiceGain.setEnabled(session.monitor.enabled());
    voiceGain.setContentDescription("Voice output volume, maximum 35 percent");
    voiceGain.setMax(35);
    voiceGain.setProgress(Math.round(session.monitor.gain() * 100));
    voiceGain.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {
            session.publish();
          }

          public void onProgressChanged(SeekBar bar, int value, boolean user) {
            if (user) session.monitor.gain(value / 100f);
          }
        });
    body.addView(voiceGain);
    Button scoring =
        button(
            session.scoringEnabled ? "Turn off song scoring" : "Turn on song scoring",
            () -> session.setScoring(!session.scoringEnabled));
    scoring.setTag("kanta.score.toggle");
    body.addView(scoring);
    scoreReading = text("", 14, CYAN);
    body.addView(scoreReading);
    refreshScoreReading();
    body.addView(
        text(
            "Optional pitch-following estimate for a single identified MIDI melody. Backing music"
                + " can affect it. Silence or unreliable input does not produce a score. Check"
                + " timing on your device; changing key, pace or timing clears the current"
                + " estimate.",
            12,
            Color.WHITE));
    TextView timing = text("Score timing offset: " + session.scoreLatency + " ms", 13, Color.WHITE);
    body.addView(timing);
    SeekBar scoreTiming = new SeekBar(this);
    scoreTiming.setContentDescription("Score timing offset, minus 500 to 1500 milliseconds");
    scoreTiming.setMax(200);
    scoreTiming.setProgress((session.scoreLatency + 500) / 10);
    scoreTiming.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {
            session.scoreLatency(bar.getProgress() * 10 - 500);
          }

          public void onProgressChanged(SeekBar bar, int value, boolean user) {
            if (user) timing.setText(timing.getContext().getString(ph.kanta.reborn.R.string.score_timing_offset, value * 10 - 500));
          }
        });
    body.addView(scoreTiming);
  }

  private void refreshScoreReading() {
    if (scoreReading != null) scoreReading.setText(session.scoreStatus());
  }

  private void lastScore() {
    SessionController.CompletedScore completed = session.lastScore;
    if (completed == null || !session.scoringEnabled) return;
    String result =
        completed.estimate.reportable
            ? String.format(
                java.util.Locale.ROOT,
                "Estimate %.0f/100 · coverage %.0f%%",
                completed.estimate.overall,
                completed.estimate.coverage)
            : "Not enough stable voice input for a score";
    TextView label =
        text(
            "Last song · "
                + completed.title
                + (completed.singer.isEmpty() ? "" : " · " + completed.singer)
                + "\n"
                + result,
            15,
            ACCENT);
    label.setTag("kanta.score.last");
    body.addView(label);
  }

  private void control() {
    Button companion = button("Companion control", () -> companionDialog = CompanionDialog.show(this, session));
    companion.setTag("kanta.companion.settings");
    body.addView(companion);
    Button voiceSettings =
        button(
            session.microphone.enabled()
                ? "Microphone on · voice settings"
                : "Microphone & voice settings",
            () -> {
              voiceExpanded = !voiceExpanded;
              session.publish();
            });
    voiceSettings.setTag("kanta.voice.settings");
    body.addView(voiceSettings);
    lastScore();
    if (voiceExpanded) voiceControls();
    body.addView(text("Sound & pace", 18, ACCENT));
    LinearLayout tuning = row();
    addWeighted(
        tuning,
        button("Key −", () -> session.controls(session.volume, session.tempo, session.pitch - 1)));
    addWeighted(
        tuning,
        button(
            "Key " + (session.pitch > 0 ? "+" : "") + session.pitch,
            () -> session.controls(session.volume, session.tempo, 0)));
    addWeighted(
        tuning,
        button("Key +", () -> session.controls(session.volume, session.tempo, session.pitch + 1)));
    body.addView(tuning);
    LinearLayout pace = row();
    addWeighted(
        pace,
        button(
            "Slower",
            () -> session.controls(session.volume, session.tempo - 0.05f, session.pitch)));
    addWeighted(
        pace,
        button(
            String.format(java.util.Locale.ROOT, "%.0f%%", session.tempo * 100),
            () -> session.controls(session.volume, 1, session.pitch)));
    addWeighted(
        pace,
        button(
            "Faster",
            () -> session.controls(session.volume, session.tempo + 0.05f, session.pitch)));
    body.addView(pace);
    SeekBar volume = new SeekBar(this);
    volume.setContentDescription("Music volume");
    volume.setMax(100);
    volume.setProgress(Math.round(session.volume * 100));
    volume.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {
            session.publish();
          }

          public void onProgressChanged(SeekBar b, int value, boolean user) {
            if (user) session.setVolume(value / 100f);
          }
        });
    body.addView(volume);
    body.addView(text("Advanced settings", 18, ACCENT));
    body.addView(text("Song discovery is available from Sing → Find a song.", 14, 0xFFAFB8D0));
  }

  private void search() {
    if (results == null) return;
    int token = ++searchGeneration;
    String requested = query;
    String[] terms = Song.searchTerms(requested);
    LinearLayout target = results;
    int limit = resultLimit;
    List<Song> catalog = session.songs;
    searchWorker.execute(
        () -> {
          ArrayList<Song> found = new ArrayList<>();
          int total = 0;
          for (Song s : catalog) {
            if (Thread.currentThread().isInterrupted()) return;
            if (s.matchesTerms(terms)) {
              total++;
              if (found.size() < limit) found.add(s);
            }
          }
          int count = total;
          handler.post(
              () -> {
                if (token != searchGeneration || target != results || isDestroyed()) return;
                target.removeAllViews();
                target.addView(text(count + " matching songs", 13, ACCENT));
                for (Song s : found) {
                  LinearLayout item = row();
                  TextView name =
                      text(
                          (s.code.isEmpty() ? "" : s.code + " · ")
                              + s.title
                              + (s.artist.isEmpty() ? "" : "\n" + s.artist),
                          15,
                          Color.WHITE);
                  item.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                  item.addView(button("Reserve", () -> session.reserve(s, singer)));
                  target.addView(item);
                }
                if (count > found.size())
                  target.addView(
                      button(
                          "Show more songs",
                          () -> {
                            resultLimit = Math.min(resultLimit + 50, 150000);
                            search();
                          }));
              });
        });
  }

  private void queue() {
    body.addView(text("Up Next · " + session.queue.snapshot().size(), 20, ACCENT));
    int n = 0;
    for (ReservationQueue.Entry e : session.queue.snapshot()) {
      if (n >= queueLimit) break;
      final int position = n++;
      LinearLayout item = row();
      item.addView(
          text(
              (position + 1) + ". " + e.song.title + (e.singer.isEmpty() ? "" : " · " + e.singer),
              15,
              Color.WHITE),
          new LinearLayout.LayoutParams(0, -2, 1));
      if (position > 0) item.addView(button("↑", () -> session.move(e.id, position - 1)));
      item.addView(button("Remove", () -> session.remove(e.id)));
      body.addView(item);
    }
    if (session.queue.snapshot().size() > queueLimit)
      body.addView(
          button(
              "Show more reservations",
              () -> {
                queueLimit += 20;
                render();
              }));
    if (n == 0) body.addView(text("Reserve a song, then choose Play to begin.", 14, 0xFFAFB8D0));
  }

  private void mySet() {
    Button importButton =
        button(
            session.importing ? "Importing…" : "Add music or SoundFont",
            () -> {
              Intent i =
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .setType("*/*")
                      .addCategory(Intent.CATEGORY_OPENABLE)
                      .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
              startActivityForResult(i, MUSIC);
            });
    importButton.setEnabled(session.ready && !session.importing);
    body.addView(importButton);
    Button folder =
        button(
            "Import a music folder",
            () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), FOLDER));
    folder.setEnabled(session.ready && !session.importing);
    body.addView(folder);
    body.addView(button("Lyric font", this::chooseLyricFont));
    if (session.importing) body.addView(button("Cancel import", session::cancelImports));
    body.addView(text("Active SoundFont · " + session.soundFontName, 14, CYAN));
    body.addView(text("SoundFonts", 20, ACCENT));
    for (LibraryRepository.SoundFontEntry bank : session.soundFonts) {
      LinearLayout item = row();
      item.addView(
          text(bank.name, 14, Color.WHITE),
          new LinearLayout.LayoutParams(0, -2, 1));
      boolean active = bank.fileName.equals(session.activeSoundFontFileName());
      if (active) item.addView(text("ACTIVE", 12, CYAN));
      else item.addView(button("Use", () -> session.selectSoundFont(bank.fileName)));
      body.addView(item);
    }
    body.addView(button("Download FluidR3 GM (~148 MB)", () -> downloadFluidR3GM()));
    body.addView(button("Download MuseScore General (~216 MB)", () -> downloadMuseScoreGeneral()));
    body.addView(button("Download SGM-v2.01 Yamaha Grand (~528 MB)", () -> downloadSGMv201()));
    body.addView(
        text(
            "Imported MIDI/KAR and audio", 20, ACCENT));
    List<Song> importedSongs = new ArrayList<>();
    for (Song song : session.songs) if (!song.starter) importedSongs.add(song);
    if (importedSongs.isEmpty()) {
      body.addView(text("No personal songs imported yet. Add MIDI, KAR, MP3 or WAV files above.", 13, 0xFFAFB8D0));
    } else {
      int shown = Math.min(importedSongs.size(), 40);
      body.addView(text(importedSongs.size() + " imported item(s)", 13, 0xFFAFB8D0));
      for (int i = 0; i < shown; i++) {
        Song song = importedSongs.get(i);
        LinearLayout item = row();
        item.addView(
            text(song.title + (song.artist.isEmpty() ? "" : "\n" + song.artist), 14, Color.WHITE),
            new LinearLayout.LayoutParams(0, -2, 1));
        item.addView(button("Reserve", () -> session.reserve(song, "")));
        item.addView(button("Remove", () -> confirmRemoveSong(song)));
        body.addView(item);
      }
      if (importedSongs.size() > shown)
        body.addView(text("The full collection remains searchable in Control.", 13, 0xFFAFB8D0));
    }
    body.addView(
        button("Import legacy idx + blob", this::pickLegacyIndex));
    body.addView(
        text(
            "SoundFont banks are validated before activation. MIDI/KAR files have no fixed size or duration cap; import time depends on the file.",
            13,
            0xFFAFB8D0));

    body.addView(button("Save Up Next as a playlist", () -> nameSet(false)));
    body.addView(button("Save played songs as Favorites", () -> nameSet(true)));
    body.addView(button("Export private Songbook", () -> export(true)));
    body.addView(button("Export song list (CSV)", () -> export(false)));
    body.addView(text("Saved sets", 20, ACCENT));
    for (LibraryRepository.SavedSet set : session.savedSets)
      body.addView(
          button(set.name + " · " + set.songs.size() + " songs", () -> session.loadSet(set)));
    body.addView(text("Original starter songs", 20, ACCENT));
    for (int i = 0; i < LibraryRepository.STARTERS.length; i++) {
      final String id = "starter:" + LibraryRepository.STARTERS[i];
      boolean present = session.songs.stream().anyMatch(s -> s.id.equals(id));
      LinearLayout item = row();
      item.addView(
          text(LibraryRepository.TITLES[i], 15, Color.WHITE),
          new LinearLayout.LayoutParams(0, -2, 1));
      item.addView(button(present ? "Remove" : "Restore", () -> session.starter(id, !present)));
      body.addView(item);
    }
    body.addView(
        button(
            "AndroidX and runtime software notices",
            () -> policy("runtime-notices.txt", "Runtime software notices")));
    body.addView(
        button(
            "TinySoundFont license",
            () -> policy("tinysoundfont-license.txt", "TinySoundFont · MIT")));
    body.addView(button("Privacy", () -> policy("privacy.txt", "Privacy")));
    body.addView(button("Music rights", () -> policy("legal.txt", "Music rights")));
  }


  private void downloadFluidR3GM() {
    session.downloadFluidR3GM();
  }

  private void downloadMuseScoreGeneral() {
    session.downloadMuseScoreGeneral();
  }

  private void downloadSGMv201() {
    session.downloadSGMv201();
  }

  private String formatBytes(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
  }

  private String readAssetText(String assetName) {
    try (java.io.InputStream is = getAssets().open(assetName);
         java.io.InputStreamReader isr = new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8);
         java.io.BufferedReader br = new java.io.BufferedReader(isr)) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line).append("\n");
      }
      return sb.toString();
    } catch (Exception e) {
      return "License file not found: " + assetName;
    }
  }
  private void nameSet(boolean played) {
    ArrayList<Song> songs = new ArrayList<>();
    if (played) songs.addAll(session.played);
    else for (ReservationQueue.Entry e : session.queue.snapshot()) songs.add(e.song);
    if (songs.isEmpty()) {
      new AlertDialog.Builder(this)
          .setMessage(
              played
                  ? "Finish a song first to save the songs you sang."
                  : "Reserve songs first to save a playlist.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    EditText name = new EditText(this);
    name.setHint("Set name");
    new AlertDialog.Builder(this)
        .setTitle(played ? "Save Favorites" : "Save playlist")
        .setView(name)
        .setPositiveButton("Save", (d, w) -> session.saveSet(name.getText().toString(), songs))
        .setNegativeButton("Cancel", null)
        .show();
  }

  void toggleMonitoring() {
    if (session.monitor.enabled()) {
      session.stopMonitor();
      return;
    }
    if (!session.foreground || !session.microphone.capturing()) {
      session.message = "Turn on microphone input before enabling monitoring.";
      session.publish();
      return;
    }
    List<android.media.AudioDeviceInfo> outputs = session.monitor.outputs();
    if (outputs.isEmpty()) {
      session.message =
          "Connect wired or USB headphones, an audio interface or a Bluetooth headset. Speaker"
              + " and Bluetooth-speaker monitoring are unavailable.";
      session.publish();
      return;
    }
    String[] labels = new String[outputs.size()];
    for (int i = 0; i < labels.length; i++) {
      String label =
          outputs.get(i).getProductName().toString().replaceAll("[\\p{Cntrl}]", " ").trim();
      labels[i] =
          label.isEmpty()
              ? "Wired / USB / Bluetooth output"
              : label.substring(0, Math.min(80, label.length()));
    }
    new AlertDialog.Builder(this)
        .setTitle("Select wired / USB / Bluetooth output")
        .setItems(labels, (dialog, selected) -> session.startMonitor(outputs.get(selected)))
        .setNegativeButton("Cancel", null)
        .show();
  }

  void toggleMicrophone() {
    if (session.microphone.enabled()) {
      session.stopMicrophone();
      return;
    }
    if (!session.foreground || microphonePermissionPending) return;
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      microphonePermissionPending = true;
      requestPermissions(new String[] {android.Manifest.permission.RECORD_AUDIO}, MICROPHONE);
      return;
    }
    session.startMicrophone();
  }

  private void refreshMicrophoneReading() {
    if (micVoiceScreen != null && "Mic & Voice".equals(tab)) {
      MicrophoneInput.Reading reading = session.microphone.reading();
      micVoiceScreen.setInputLevel(
          reading == null ? 0f : (float) reading.rms,
          !session.microphone.enabled()
              ? "Microphone off"
              : !session.microphone.capturing()
                  ? "Starting microphone…"
                  : reading == null ? "Listening for input…" : "Microphone input active");
      micVoiceScreen.setMonitorState(
          session.monitor.enabled(), session.monitor.audible(), session.monitor.gain(),
          session.monitor.mode().name());
      micVoiceScreen.setScoreState(session.scoringEnabled, session.scoreStatus());
    }
    if (microphoneReading == null) return;
    MicrophoneInput.Reading value = session.microphone.reading();
    String label;
    if (!session.microphone.enabled()) label = "Microphone off";
    else if (!session.microphone.capturing()) label = "Starting microphone…";
    else if (value == null) label = "Listening — waiting for input";
    else {
      double db = 20 * Math.log10(Math.max(.000001, value.rms));
      label = String.format(java.util.Locale.ROOT, "Input %.0f dBFS", db);
      if (value.pitch != null)
        label += String.format(java.util.Locale.ROOT, " · Pitch %.1f Hz", value.pitch.frequencyHz);
      else label += " · No stable pitch";
    }
    microphoneReading.setText(label);
    if (monitorReading != null) {
      String output =
          !session.monitor.enabled()
              ? "Monitoring off"
              : session.monitor.audible()
                  ? "Monitoring on · "
                      + Math.round(session.monitor.gain() * 100)
                      + "%"
                      + (session.monitor.correctionEnabled() ? " · pitch correction" : "")
                  : "Monitoring muted · checking connection or input";
      monitorReading.setText(output);
    }
  }

  void toggleCamera() {
    if (camera.enabled()) {
      camera.stop();
      session.publish();
      return;
    }
    if (!session.foreground || cameraPermissionPending) return;
    if (checkSelfPermission(android.Manifest.permission.CAMERA)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      cameraPermissionPending = true;
      requestPermissions(new String[] {android.Manifest.permission.CAMERA}, CAMERA);
      return;
    }
    camera.start(fullscreen != null && fullscreen.isShowing() ? fullscreenStage : stagePresenter);
    session.publish();
  }

  boolean cameraStreaming() {
    return camera.streaming();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    if (request == MICROPHONE) {
      if (!microphonePermissionPending) return;
      microphonePermissionPending = false;
      if (session.foreground
          && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
              == android.content.pm.PackageManager.PERMISSION_GRANTED) session.startMicrophone();
      else {
        session.message = "Microphone permission was not granted. Local playback still works.";
        session.publish();
      }
      return;
    }
    if (request != CAMERA || !cameraPermissionPending) return;
    cameraPermissionPending = false;
    if (session.foreground
        && checkSelfPermission(android.Manifest.permission.CAMERA)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) toggleCamera();
    else {
      session.message = "Camera permission was not granted. Your saved background is unchanged.";
      session.publish();
    }
  }

  /**
   * Opens the downloaded scenic video library.
   *
   * <p>The list is read on a worker thread because the library owner touches disk. Selection is
   * applied through the same stage-import path used for local videos, so the stage keeps a single
   * owner for the active background.
   */
  private void showScenicVideoLibrary() {
    if (!session.ready || session.importing) return;
    session.message = "Opening your video library\u2026";
    session.publish();
    new Thread(
            () -> {
              List<BgvLibrary.Entry> entries = session.scenicEntries();
              final List<BgvLibrary.Entry> downloaded = new ArrayList<>(entries);
              runOnUiThread(
                  () -> {
                    if (!"Opening your video library\u2026".equals(session.message)) return;
                    session.message = "";
                    LinearLayout list = new LinearLayout(MainActivity.this);
                    list.setOrientation(LinearLayout.VERTICAL);
                    for (BgvLibrary.Entry entry : downloaded) {
                      TextView title = new TextView(MainActivity.this);
                      title.setText(title.getContext().getString(ph.kanta.reborn.R.string.scenic_title_attribution, entry.title, entry.attribution()));
                      title.setTextColor(Color.WHITE);
                      title.setPadding(12, 8, 12, 8);
                      final String id = entry.id;
                      title.setOnClickListener(
                          v -> {
                            camera.stop();
                            session.importStage(
                                new android.net.Uri.Builder()
                                    .scheme(ContentResolver.SCHEME_FILE)
                                    .authority(id)
                                    .build(),
                                true);
                            session.message = "Background set. Library updated.";
                            session.publish();
                          });
                      list.addView(title);
                      Button remove = new Button(MainActivity.this);
                      remove.setText(ph.kanta.reborn.R.string.label_remove);
                      remove.setTag(id);
                      remove.setOnClickListener(
                          v -> {
                            session.deleteScenic(id);
                            recreate();
                          });
                      list.addView(remove);
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Background video library");
                    builder.setView(list);
                    builder.setNegativeButton("Close", null);
                    builder.show();
                  });
            })
        .start();
  }

  private void scenic() {
    body.addView(text("Choose your stage", 22, Color.WHITE));
    body.addView(button("Background video library", this::showScenicVideoLibrary));
    body.addView(
        button(
            camera.enabled() ? "Turn off front camera" : "Turn on front camera",
            this::toggleCamera));
    body.addView(
        text(
            "Optional live preview only. Nothing is recorded. Camera turns off when you leave"
                + " KANTA. TV output uses a still background.",
            14,
            Color.WHITE));
    Button photo =
        button(
            "Choose your photo",
            () ->
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    PHOTO));
    Button video =
        button(
            "Choose your silent background video",
            () ->
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("video/*")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    VIDEO));
    photo.setEnabled(session.stageReady && !session.importing);
    video.setEnabled(session.stageReady && !session.importing);
    body.addView(photo);
    body.addView(video);
    body.addView(
        text(
            "Photos up to 32 MB; videos up to 512 MB and six hours. Originals stay unchanged. Video"
                + " sound is muted.",
            14,
            Color.WHITE));
    if (session.stageSelection != null && session.stageSelection.photo != null)
      body.addView(
          button(
              "Use your last photo",
              () -> {
                camera.stop();
                session.chooseStage("photo");
              }));
    if (session.stageSelection != null && session.stageSelection.video != null)
      body.addView(
          button(
              "Use your last video",
              () -> {
                camera.stop();
                session.chooseStage("video");
              }));
    if (session.importing) body.addView(button("Cancel import", session::cancelImports));
    String[] names = {
      "bayanDawn",
      "bambooMist",
      "coralHaze",
      "deepLagoon",
      "emberSunset",
      "indigoHorizon",
      "mangoGlow",
      "moonlitBay",
      "orchidNight",
      "pearlSky"
    };
    for (String name : names) {
      String title = name.replaceAll("([a-z])([A-Z])", "$1 $2");
      body.addView(
          button(
              (session.stageKind.equals("scenic") && session.scenicName.equals(name) ? "✓ " : "")
                  + title,
              () -> {
                if (session.stageReady) {
                  camera.stop();
                  session.scene(name);
                }
              }));
    }
  }

  private void loop() {
    body.addView(text("Loop & party sounds", 22, ACCENT));
    body.addView(
        text(
            "Choose an imported MP3/WAV for between-song music. It stops for karaoke and when you"
                + " leave the app. Party clips can be assigned from My Set (up to 8 MB / 30"
                + " seconds).",
            15,
            Color.WHITE));
    Song loopSong =
        session.songs.stream()
            .filter(song -> song.id.equals(session.loopSongId))
            .findFirst()
            .orElse(null);
    body.addView(
        button(
            loopSong == null ? "Choose between-song music" : "Music: " + loopSong.title,
            () -> chooseAudio("Between-song music", session::chooseIntermission)));
    Button enabled =
        button(
            session.loopEnabled ? "Turn off between-song music" : "Turn on between-song music",
            session::toggleIntermission);
    enabled.setEnabled(loopSong != null);
    body.addView(enabled);
    volumeControl(
        "Between-song volume",
        session.party.loopVolume,
        value -> session.partyVolumes(session.party.sfxVolume, value));
    volumeControl(
        "Party sound volume",
        session.party.sfxVolume,
        value -> session.partyVolumes(value, session.party.loopVolume));
    body.addView(text("Built-in party sounds", 18, ACCENT));
    body.addView(button("Stop all party audio", session::stopParty));
    for (int i = 0; i < PartySounds.TITLES.size(); i++) {
      final int slot = i;
      LinearLayout row = row();
      Button trigger =
          button(
              PartySounds.TITLES.get(i) + (session.customPads[i].isEmpty() ? "" : " · custom"),
              () -> session.triggerPad(slot));
      trigger.setEnabled(session.party.ready(i));
      row.addView(trigger, new LinearLayout.LayoutParams(0, -2, 1));
      Button assign =
          button(
              "Assign clip",
              () ->
                  chooseAudio(PartySounds.TITLES.get(slot), song -> session.assignPad(slot, song)));
      assign.setEnabled(!session.assigningPad);
      row.addView(assign);
      if (!session.customPads[i].isEmpty())
        row.addView(button("Reset", () -> session.resetPad(slot)));
      body.addView(row);
    }
    queue();
  }

  private void chooseFontFolder() {
    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), LYRIC_FONT_FOLDER);
  }

  private void chooseLyricFont() {
    int pickerRequest = ++fontPickerGeneration;
    Uri root = new ContentRoot(this).getContentRootUri();
    if (root == null) { chooseFontFolder(); return; }
    Toast.makeText(this, "Looking for fonts…", Toast.LENGTH_SHORT).show();
    ph.kanta.reborn.font.FontStore fonts = ph.kanta.reborn.font.FontStore.getInstance(this);
    fonts.discover(root, entries -> {
      if (pickerRequest != fontPickerGeneration || isFinishing() || isDestroyed()
          || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return;
      String[] labels = new String[entries.size() + 2];
      String saved = new SettingsStore(this).getLyricFont();
      labels[0] = (saved.isEmpty() ? "✓ " : "") + "Built-in font";
      for (int i = 0; i < entries.size(); i++) labels[i + 1] =
          (entries.get(i).uri.toString().equals(saved) ? "✓ " : "") + entries.get(i).name;
      labels[labels.length - 1] = "Choose another content folder…";
      new AlertDialog.Builder(this).setTitle(entries.isEmpty() ? "Lyric font · no fonts found" : "Lyric font")
          .setItems(labels, (dialog, index) -> {
            if (index == labels.length - 1) { chooseFontFolder(); return; }
            Uri selected = index == 0 ? null : entries.get(index - 1).uri;
            fonts.select(selected, success -> {
              if (isFinishing() || isDestroyed()) return;
              Toast.makeText(this, success ? "Lyric font updated."
                  : "This font could not be loaded. Using the built-in font.", Toast.LENGTH_LONG).show();
              if (stagePresenter != null) stagePresenter.invalidate();
              if (fullscreenStage != null) fullscreenStage.invalidate();
            });
          }).setNegativeButton("Cancel", null).show();
    });
  }

  private void pickLegacyIndex() {
    pendingLegacyIndex = null;
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream");
    startActivityForResult(intent, LEGACY_INDEX);
  }

  private void pickLegacyBlob() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream");
    startActivityForResult(intent, LEGACY_BLOB);
  }

  private EditText legacyKeyInput(String hint, int digits) {
    EditText input = new EditText(this);
    input.setHint(hint);
    input.setContentDescription(hint);
    input.setSingleLine(true);
    input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    input.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(digits)});
    input.setSaveEnabled(false);
    input.setSaveFromParentEnabled(false);
    input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    return input;
  }

  private static void clearLegacyKeyInput(EditText input) {
    android.text.Editable text = input.getText();
    text.replace(0, text.length(), new String(new char[text.length()]));
    text.clear();
  }

  private void requestLegacyKey(Uri index, Uri blob) {
    if (legacyKeyDialog != null) legacyKeyDialog.dismiss();
    EditText keyInput = legacyKeyInput("Key: 64 hexadecimal digits", 64);
    EditText ivInput = legacyKeyInput("IV: 32 hexadecimal digits", 32);
    LinearLayout panel = column();
    panel.setPadding(dp(20), dp(8), dp(20), dp(8));
    panel.setSaveEnabled(false);
    panel.addView(text("Use only files you have permission to import. Enter the key and IV supplied "
        + "for your files. They are used for this import only and are not saved. "
        + "You will need to enter them again for another import.", 14, Color.WHITE));
    panel.addView(keyInput);
    panel.addView(ivInput);
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("Unlock legacy files")
        .setView(panel)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Import", null)
        .create();
    legacyKeyDialog = dialog;
    dialog.setOnDismissListener(ignored -> {
      clearLegacyKeyInput(keyInput);
      clearLegacyKeyInput(ivInput);
      if (legacyKeyDialog == dialog) legacyKeyDialog = null;
    });
    if (dialog.getWindow() != null) {
      dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }
    dialog.show();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
      ph.kanta.reborn.core.legacy.LegacyImportKey credentials;
      try {
        credentials = ph.kanta.reborn.core.legacy.LegacyImportKey.fromHex(
            keyInput.getText(), ivInput.getText());
      } catch (IllegalArgumentException invalid) {
        ivInput.setError("Enter a 64-digit key and a 32-digit IV using 0-9 and A-F.");
        return;
      }
      clearLegacyKeyInput(keyInput);
      clearLegacyKeyInput(ivInput);
      dialog.dismiss();
      if (session.importLegacyPair(index, blob, credentials)) {
        showImportProgress();
        attachImportProgressListener();
      } else {
        Toast.makeText(this, "Import could not start. Please try again.", Toast.LENGTH_LONG).show();
      }
    });
  }

  private void chooseAudio(String title, java.util.function.Consumer<Song> selected) {
    List<Song> audio = new ArrayList<>();
    for (Song song : session.songs) if (SessionController.isAudio(song)) audio.add(song);
    if (audio.isEmpty()) {
      new AlertDialog.Builder(this)
          .setTitle(title)
          .setMessage("Add an MP3 or WAV in My Set first. Your originals stay unchanged.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    String[] names = audio.stream().map(song -> song.title).toArray(String[]::new);
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(names, (dialog, index) -> selected.accept(audio.get(index)))
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void volumeControl(
      String title, float initial, java.util.function.Consumer<Float> apply) {
    body.addView(text(title, 16, ACCENT));
    SeekBar bar = new SeekBar(this);
    bar.setMax(100);
    bar.setProgress(Math.round(initial * 100));
    bar.setContentDescription(title);
    bar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar seek, int progress, boolean user) {
            if (user) apply.accept(progress / 100f);
          }

          public void onStartTrackingTouch(SeekBar seek) {}

          public void onStopTrackingTouch(SeekBar seek) {}
        });
    body.addView(bar);
  }

  private void realm() {
    body.addView(
        text(
            "Animated lights behind your lyrics. Turn this off for a still stage.",
            16,
            Color.WHITE));
    body.addView(
        button(
            session.realm ? "Turn off animated lights" : "Turn on animated lights",
            () -> {
              session.realm = !session.realm;
              session.publish();
            }));
    body.addView(text("Lyric size", 18, ACCENT));
    SeekBar size = new SeekBar(this);
    size.setMax(75);
    size.setProgress(Math.round((session.lyricScale - .75f) * 100));
    size.setContentDescription("Lyric size");
    size.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar bar, int value, boolean user) {
            if (user) session.lyricSize(.75f + value / 100f);
          }

          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {
            session.publish();
          }
        });
    body.addView(size);
  }

  void closeFullscreen() {
    if (fullscreen != null) fullscreen.dismiss();
  }

  private String stageTime(long milliseconds) {
    long seconds = Math.max(0, milliseconds / 1000);
    return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
  }

  /** Refreshes only the stage's live projection; transport and timing stay in SessionController. */
  private void refreshFullscreenControls() {
    if (fullscreen == null || !fullscreen.isShowing()) return;
    if (fullscreenTitle != null)
      fullscreenTitle.setText(session.current == null ? "Ready for your next song" : session.current.title);
    if (fullscreenArtist != null)
      fullscreenArtist.setText(
          session.current == null ? "KANTA" :
              (session.current.artist == null ? "" : session.current.artist)
                  + (session.singer.isEmpty() ? "" : " · " + session.singer));
    long duration = session.duration();
    long position = session.position();
    if (fullscreenProgress != null)
      fullscreenProgress.setProgress(duration > 0
          ? (int) Math.max(0, Math.min(1000, Math.round(position * 1000.0 / duration))) : 0);
    if (fullscreenTime != null)
      fullscreenTime.setText(fullscreenTime.getContext().getString(ph.kanta.reborn.R.string.playback_times, stageTime(position), duration > 0 ? stageTime(duration) : "--:--"));
    if (fullscreenKeyTempo != null)
      fullscreenKeyTempo.setText(fullscreenKeyTempo.getContext().getString(ph.kanta.reborn.R.string.playback_key_tempo, session.pitch > 0 ? "+" : "", session.pitch, Math.round(session.tempo * 100f)));
    if (fullscreenQueue != null)
      fullscreenQueue.setText(fullscreenQueue.getContext().getString(ph.kanta.reborn.R.string.queue_count, session.queue.snapshot().size()));
    if (fullscreenPlayButton != null)
      fullscreenPlayButton.setText(session.playing ? "Pause" : "Play");
  }

  void fullscreen() {
    if (fullscreen != null && fullscreen.isShowing()) return;
    fullscreen = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
    FrameLayout stageFrame = new FrameLayout(this);
    stageFrame.setTag("kanta.fullscreen.stage");
    stageFrame.setBackgroundColor(KantaTheme.BG_MAIN);
    fullscreenStage = new StageSurface(this, session);
    fullscreenStage.setFullscreenMode(true);
    stageFrame.addView(fullscreenStage, new FrameLayout.LayoutParams(-1, -1));

    LinearLayout overlay = column();
    overlay.setPadding(dp(16), dp(12), dp(16), dp(16));
    overlay.setFocusable(false);
    LinearLayout header = row();
    TextView stageHeading = text("NOW PLAYING", 13, KantaTheme.TEXT_PRIMARY);
    stageHeading.setTypeface(null, android.graphics.Typeface.BOLD);
    addWeighted(header, stageHeading);
    Button close = button("Close", this::closeFullscreen);
    close.setTag("kanta.fullscreen.close");
    header.addView(close);
    overlay.addView(header);

    View spacer = new View(this);
    overlay.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));

    LinearLayout details = column();
    details.setPadding(dp(16), dp(14), dp(16), dp(14));
    details.setBackground(KantaTheme.backgroundDrawable(0xE8081426, 18, KantaTheme.BORDER, 1,
        getResources().getDisplayMetrics().density));
    fullscreenTitle = text("Ready for your next song", 20, KantaTheme.TEXT_PRIMARY);
    fullscreenTitle.setTypeface(null, android.graphics.Typeface.BOLD);
    fullscreenTitle.setTag("kanta.fullscreen.title");
    fullscreenArtist = text("KANTA", 13, KantaTheme.TEXT_SECONDARY);
    fullscreenArtist.setTag("kanta.fullscreen.artist");
    details.addView(fullscreenTitle);
    details.addView(fullscreenArtist);

    fullscreenProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    fullscreenProgress.setMax(1000);
    fullscreenProgress.setProgress(0);
    fullscreenProgress.setProgressTintList(ColorStateList.valueOf(KantaTheme.PRIMARY_CYAN));
    fullscreenProgress.setProgressBackgroundTintList(ColorStateList.valueOf(KantaTheme.BORDER));
    fullscreenProgress.setTag("kanta.fullscreen.progress");
    LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(6));
    progressParams.topMargin = dp(12);
    progressParams.bottomMargin = dp(4);
    details.addView(fullscreenProgress, progressParams);
    fullscreenTime = text("0:00 / --:--", 11, KantaTheme.TEXT_SECONDARY);
    fullscreenTime.setTag("kanta.fullscreen.time");
    details.addView(fullscreenTime);

    LinearLayout transport = row();
    fullscreenQueue = text("Queue (0)", 12, KantaTheme.TEXT_SECONDARY);
    fullscreenQueue.setTag("kanta.fullscreen.queue");
    transport.addView(fullscreenQueue, new LinearLayout.LayoutParams(0, -2, 1));
    fullscreenPlayButton = KantaTheme.primaryButton(this, "Play", v -> session.toggle());
    fullscreenPlayButton.setTag("kanta.fullscreen.play-pause");
    transport.addView(fullscreenPlayButton, new LinearLayout.LayoutParams(dp(104), dp(48)));
    Button next = KantaTheme.secondaryButton(this, "Next", v -> session.next());
    next.setTag("kanta.fullscreen.next");
    LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(88), dp(48));
    nextParams.leftMargin = dp(8);
    transport.addView(next, nextParams);
    details.addView(transport);

    LinearLayout performance = row();
    Button keyDown = button("Key −", () -> session.setPitch(session.pitch - 1));
    keyDown.setTag("kanta.fullscreen.key.decrement");
    Button keyUp = button("Key +", () -> session.setPitch(session.pitch + 1));
    keyUp.setTag("kanta.fullscreen.key.increment");
    Button tempoDown = button("Slower", () -> session.setTempo(session.tempo - .05f));
    tempoDown.setTag("kanta.fullscreen.tempo.decrement");
    Button tempoUp = button("Faster", () -> session.setTempo(session.tempo + .05f));
    tempoUp.setTag("kanta.fullscreen.tempo.increment");
    addWeighted(performance, keyDown);
    addWeighted(performance, keyUp);
    addWeighted(performance, tempoDown);
    addWeighted(performance, tempoUp);
    LinearLayout.LayoutParams performanceParams = new LinearLayout.LayoutParams(-1, -2);
    performanceParams.topMargin = dp(10);
    details.addView(performance, performanceParams);
    fullscreenKeyTempo = text("", 12, KantaTheme.PRIMARY_CYAN);
    fullscreenKeyTempo.setGravity(Gravity.CENTER);
    fullscreenKeyTempo.setTag("kanta.fullscreen.key-tempo");
    details.addView(fullscreenKeyTempo);
    overlay.addView(details);
    stageFrame.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    fullscreen.setContentView(stageFrame);
    fullscreen.setOnDismissListener(
        d -> {
          fullscreenStage = null;
          fullscreenTitle = null;
          fullscreenArtist = null;
          fullscreenTime = null;
          fullscreenKeyTempo = null;
          fullscreenQueue = null;
          fullscreenProgress = null;
          fullscreenPlayButton = null;
          // The primary presenter remains the authoritative camera owner after fullscreen
          // dismissal; utility destinations never own or replace the camera stage.
          if (stagePresenter != null) stagePresenter.setVisualActive(true);
          camera.moveTo(stagePresenter);
        });
    if (stagePresenter != null) stagePresenter.setVisualActive(false);
    fullscreen.show();
    if (fullscreen.getWindow() != null) fullscreen.getWindow().setLayout(-1, -1);
    camera.moveTo(fullscreenStage);
    refreshFullscreenControls();
  }

  private void refreshDisplay() {
    if (presentation != null) {
      presentation.dismiss();
      presentation = null;
      externalStage = null;
    }
    for (Display display : displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
      if (display.getDisplayId() == getWindowManager().getDefaultDisplay().getDisplayId()) continue;
      try {
        presentation = new Presentation(this, display);
        externalStage = new StageSurface(presentation.getContext(), session);
        presentation.setContentView(externalStage);
        presentation.show();
        break;
      } catch (WindowManager.InvalidDisplayException e) {
        presentation = null;
        externalStage = null;
      }
    }
  }

  private void voiceSearch() {
    new AlertDialog.Builder(this)
        .setTitle("Voice search")
        .setMessage(
            "Your device's speech provider may process your voice online. KANTA uses the result"
                + " only to search; it will not reserve a song automatically.")
        .setPositiveButton(
            "Continue",
            (d, w) -> {
              Intent i =
                  new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                      .putExtra(
                          RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                          RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                      .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song title or artist");
              try {
                startActivityForResult(i, VOICE);
              } catch (ActivityNotFoundException e) {
                Toast.makeText(
                        this, "Voice search is not available on this device", Toast.LENGTH_LONG)
                    .show();
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void export(boolean encrypted) {
    if (exportBytes != null || preparingExport) return;
    preparingExport = true;
    List<Song> captured = session.songs;
    session.io.execute(
        () -> {
          try {
            String csv = PrivateSongbook.csv(captured);
            PrivateSongbook.Export book = encrypted ? PrivateSongbook.seal(csv) : null;
            byte[] bytes =
                (encrypted ? book.envelope : csv).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            handler.post(
                () -> {
                  preparingExport = false;
                  if (isDestroyed()) return;
                  exportBytes = bytes;
                  exportCode = encrypted ? book.accessCode : null;
                  Intent intent =
                      new Intent(Intent.ACTION_CREATE_DOCUMENT)
                          .addCategory(Intent.CATEGORY_OPENABLE)
                          .setType(encrypted ? "application/octet-stream" : "text/csv")
                          .putExtra(
                              Intent.EXTRA_TITLE,
                              encrypted ? "KANTA-My-Songbook.kanta-book" : "KANTA-My-Songbook.csv");
                  startActivityForResult(intent, EXPORT);
                });
          } catch (Exception e) {
            handler.post(
                () -> {
                  preparingExport = false;
                  if (!isDestroyed())
                    Toast.makeText(this, "Could not prepare your Songbook", Toast.LENGTH_LONG)
                        .show();
                });
          }
        });
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null) {
      if (request == LEGACY_INDEX || request == LEGACY_BLOB) pendingLegacyIndex = null;
      if (request == EXPORT) {
        exportBytes = null;
        exportCode = null;
      }
      return;
    }
    if ((request == PHOTO || request == VIDEO) && data.getData() != null) {
      session.importStage(data.getData(), request == VIDEO);
      return;
    }
    if (request == LEGACY_INDEX && data.getData() != null) {
      pendingLegacyIndex = data.getData();
      pickLegacyBlob();
      return;
    }
    if (request == LEGACY_BLOB && data.getData() != null) {
      if (pendingLegacyIndex == null) { Toast.makeText(this, "Choose the index file first.", Toast.LENGTH_LONG).show(); return; }
      Uri index = pendingLegacyIndex;
      pendingLegacyIndex = null;
      requestLegacyKey(index, data.getData());
      return;
    }
    if (request == MUSIC) {
      List<Uri> uris = new ArrayList<>();
      if (data.getClipData() != null) {
        for (int i = 0; i < data.getClipData().getItemCount() && i < 1000; i++)
          uris.add(data.getClipData().getItemAt(i).getUri());
      } else if (data.getData() != null) uris.add(data.getData());
      showImportProgress();
      attachImportProgressListener();
      session.importSongs(uris);
    }
    else if (request == FOLDER && data.getData() != null) {
      showImportProgress();
      attachImportProgressListener();
      session.importFolder(data.getData());
    }
    else if (request == LYRIC_FONT_FOLDER && data.getData() != null) {
      Uri root = data.getData();
      try {
        int grants = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (grants == 0) throw new SecurityException();
        getContentResolver().takePersistableUriPermission(root, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        new ContentRoot(this).setContentRootUri(root);
        chooseLyricFont();
      } catch (SecurityException | IllegalArgumentException unavailable) {
        Toast.makeText(this, "This folder could not be kept. Choose a folder with read access.",
            Toast.LENGTH_LONG).show();
      }
    }
    else if (request == VOICE) {
      ArrayList<String> values = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if (values != null && !values.isEmpty()) {
        query = values.get(0);
        tab = "Control";
        render();
      }
    } else if (request == EXPORT) {
      Uri uri = data.getData();
      byte[] bytes = exportBytes;
      String code = exportCode;
      exportBytes = null;
      exportCode = null;
      if (uri == null || bytes == null) return;
      session.io.execute(
          () -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
              if (out == null) throw new IOException();
              out.write(bytes);
              handler.post(
                  () -> {
                    if (isDestroyed()) return;
                    if (code != null)
                      new AlertDialog.Builder(this)
                          .setTitle("Keep this code separate from your file")
                          .setMessage(
                              code
                                  + "\n\n"
                                  + "Open the Songbook page on the KANTA website and choose your"
                                  + " saved file. Anyone with both the file and this code can read"
                                  + " the list.")
                          .setPositiveButton(
                              "Copy code",
                              (d, w) ->
                                  ((android.content.ClipboardManager)
                                          getSystemService(CLIPBOARD_SERVICE))
                                      .setPrimaryClip(
                                          ClipData.newPlainText("KANTA Songbook code", code)))
                          .setNegativeButton("Done", null)
                          .show();
                    else Toast.makeText(this, "Song list saved", Toast.LENGTH_LONG).show();
                  });
            } catch (Exception e) {
              handler.post(
                  () ->
                      Toast.makeText(this, "Could not save the export", Toast.LENGTH_LONG).show());
            }
          });
    }
  }

  /** Confirms removal of an imported song from this device. */
  private void confirmRemoveSong(Song song) {
    new android.app.AlertDialog.Builder(this)
        .setTitle("Remove \"" + song.title + "\"?")
        .setMessage("The imported file is removed from this device; the original file stays where it is.")
        .setPositiveButton("Remove", (dialog, which) -> session.deleteSong(song.id))
        .setNegativeButton("Cancel", null)
        .show();
  }

  /** Shows the import progress dialog. Imports run off the main thread and are cancellable:
   * the dialog Cancel action and My Set "Cancel import" both signal the running batch. */
  private void showImportProgress() {
    if (importProgressDialog == null) {
      importProgressDialog = new android.app.ProgressDialog(this);
      importProgressDialog.setTitle("Importing");
      importProgressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
      importProgressDialog.setMax(100);
      importProgressDialog.setCancelable(true);
      importProgressDialog.setOnCancelListener(dialog -> session.cancelImports());
    }
    importProgressDialog.setProgress(0);
    importProgressDialog.setIndeterminate(true);
    importProgressDialog.setMessage("Preparing\u2026");
    importProgressDialog.show();
  }

  /**
   * Moves the import dialog. A provider that will not report a size gives total 0, and then only
   * the message can progress, so the bar stays indeterminate rather than sitting at zero.
   */
  private void updateImportProgress(long total, long copied, String status) {
    if (importProgressDialog == null || !importProgressDialog.isShowing()) return;
    if (status != null) importProgressDialog.setMessage(status);
    if (total > 0) {
      importProgressDialog.setIndeterminate(false);
      importProgressDialog.setProgress((int) Math.max(0, Math.min(100, copied * 100 / total)));
    } else {
      importProgressDialog.setIndeterminate(true);
    }
  }

  /** Dismisses the import dialog and reports the outcome the repository returned. */
  private void attachImportProgressListener() {
    session.setImportProgressListener(new LibraryRepository.ImportProgressListener() {
      @Override
      public void onProgress(long total, long copied, String status) {
        updateImportProgress(total, copied, status);
      }

      @Override
      public void onComplete(boolean success, String message) {
        hideImportProgress(message);
      }
    });
  }

  private void dismissImportProgress() {
    android.app.ProgressDialog dialog = importProgressDialog;
    importProgressDialog = null;
    if (dialog != null && dialog.isShowing()) dialog.dismiss();
  }

  private void hideImportProgress(String message) {
    if (importProgressDialog != null && importProgressDialog.isShowing())
      importProgressDialog.dismiss();
    if (message != null && !message.isEmpty())
      Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private void policy(String asset, String title) {
    try (InputStream in = getAssets().open(asset)) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
      TextView content = text(bytes.toString("UTF-8"), 15, Color.WHITE);
      ScrollView scroll = new ScrollView(this);
      scroll.addView(content);
      new AlertDialog.Builder(this)
          .setTitle(title)
          .setView(scroll)
          .setPositiveButton("Close", null)
          .show();
    } catch (IOException e) {
      Toast.makeText(this, "Policy could not be opened", Toast.LENGTH_LONG).show();
    }
  }

  /**
   * Diagnostics (S14): a read-only report of runtime state.
   *
   * Never includes pairing codes, tokens or key material — only whether a feature is
   * active — so the report is safe to copy and share.
   */
  private void showUpdatePanel() {
    if (updateManager == null) {
      File staging = new File(getFilesDir(), "update");
      updateManager =
          new AppUpdateManager(
              staging,
              UpdatePackageInspector.installedVersionCode(
                  getPackageManager(), getPackageName()),
              android.os.Build.VERSION.SDK_INT);
    }
    LinearLayout content = column();
    int pad = dp(20);
    content.setPadding(pad, pad, pad, pad);
    TextView heading = text("App update", 20, ACCENT);
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    content.addView(heading);
    content.addView(
        text(
            "Installed " + installedVersionText() + "\nFeed: " + UpdatePolicy.FEED_URL,
            12, 0xFFAFB8D0));
    updateStatus =
        text(
            updateStatusText == null
                ? "Choose \"Check for updates\" to fetch the update feed."
                : updateStatusText,
            13, Color.WHITE);
    content.addView(updateStatus);
    updateProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    updateProgress.setMax(1);
    updateProgress.setVisibility(updateBusy ? View.VISIBLE : View.GONE);
    content.addView(updateProgress);
    Button check = button("Check for updates", () -> checkForUpdate());
    updateInstallButton = button("Install update", () -> installReadyUpdate());
    updateInstallButton.setEnabled(updateReadyApk != null);
    content.addView(check);
    content.addView(updateInstallButton);
    updateDialog = new AlertDialog.Builder(this)
        .setTitle("App update")
        .setView(content)
        .setNegativeButton("Close", null)
        .create();
    updateDialog.show();
  }

  private String installedVersionText() {
    try {
      android.content.pm.PackageInfo info =
          getPackageManager().getPackageInfo(getPackageName(), 0);
      return info.versionName
          + " (code "
          + UpdatePackageInspector.installedVersionCode(
              getPackageManager(), getPackageName())
          + ")";
    } catch (Exception unavailable) {
      return "(unknown)";
    }
  }

  private void checkForUpdate() {
    if (updateManager == null || updateBusy) {
      return;
    }
    updateBusy = true;
    updateReadyApk = null;
    updateStatusText = "Checking the update feed…";
    if (updateStatus != null) updateStatus.setText(updateStatusText);
    if (updateProgress != null) updateProgress.setVisibility(View.VISIBLE);
    updateManager.checkForUpdate(
        new AppUpdateManager.Listener() {
          @Override
          public void onState(AppUpdateManager.State state, String detail) {
            handler.post(
                () -> {
                  updateBusy =
                      state == AppUpdateManager.State.CHECKING
                          || state == AppUpdateManager.State.DOWNLOADING
                          || state == AppUpdateManager.State.VERIFYING;
                  switch (state) {
                    case IDLE:
                      updateStatusText = "Up to date (" + (detail == null ? "no update" : detail) + ").";
                      break;
                    case DOWNLOADING:
                      updateStatusText = "Downloading update…";
                      break;
                    case VERIFYING:
                      updateStatusText = "Verifying the downloaded update…";
                      break;
                    case READY:
                      updateStatusText = "Update downloaded and verified.";
                      break;
                    case CANCELLED:
                      updateStatusText = "Update check cancelled.";
                      break;
                    case FAILED:
                      updateStatusText =
                          "Update failed: " + (detail == null ? "unknown reason" : detail);
                      break;
                    default:
                      updateStatusText = "…";
                  }
                  if (updateProgress != null) {
                    updateProgress.setVisibility(updateBusy ? View.VISIBLE : View.GONE);
                  }
                  if (updateStatus != null) updateStatus.setText(updateStatusText);
                });
          }

          @Override
          public void onProgress(long bytes, long total) {
            handler.post(
                () -> {
                  if (updateProgress == null) {
                    return;
                  }
                  int max = (int) Math.min(Math.max(total, 1), 1_000_000);
                  updateProgress.setMax(max);
                  updateProgress.setProgress((int) Math.min(bytes, max));
                });
          }

          @Override
          public void onReady(UpdateManifest.AndroidBuild build, File apk) {
            handler.post(
                () -> {
                  updateBusy = false;
                  updateReadyApk = apk;
                  updateStatusText =
                      "Update ready: " + build.versionName() + " (code " + build.versionCode() + ")";
                  if (updateProgress != null) updateProgress.setVisibility(View.GONE);
                  if (updateStatus != null) updateStatus.setText(updateStatusText);
                  if (updateInstallButton != null) updateInstallButton.setEnabled(true);
                });
          }
        });
  }

  private void installReadyUpdate() {
    if (updateReadyApk == null) {
      return;
    }
    if (UpdatePackageInspector.canRequestPackageInstalls(this)) {
      try {
        startActivity(UpdatePackageInspector.installIntent(this, updateReadyApk));
      } catch (Exception refused) {
        updateStatusText = "Installer could not start: " + refused.getMessage();
      }
    } else {
      updateStatusText =
          "KANTA needs the \"Install unknown apps\" permission before it can update itself.";
      try {
        startActivity(UpdatePackageInspector.manageUnknownSourcesIntent(this));
      } catch (Exception refused) {
        updateStatusText =
            "Could not open the install-permission screen: " + refused.getMessage();
      }
    }
    if (updateStatus != null) updateStatus.setText(updateStatusText);
  }

  private void diagnostics() {
    StringBuilder report = new StringBuilder();
    report.append("KANTA Karaoke · diagnostics\n\n");
    try {
      android.content.pm.PackageInfo info =
          getPackageManager().getPackageInfo(getPackageName(), 0);
      report.append("version      : ").append(info.versionName).append('\n');
    } catch (Exception unavailable) {
      report.append("version      : unavailable\n");
    }
    report.append("device       : ")
        .append(android.os.Build.MANUFACTURER)
        .append(' ')
        .append(android.os.Build.MODEL)
        .append(" · Android ")
        .append(android.os.Build.VERSION.RELEASE)
        .append(" (API ")
        .append(android.os.Build.VERSION.SDK_INT)
        .append(")\n");

    report.append("\nlibrary\n");
    report.append("ready        : ").append(session.ready).append('\n');
    report.append("loading      : ").append(session.loading).append('\n');
    report.append("importing    : ").append(session.importing).append('\n');
    report.append("songs        : ").append(session.songs.size()).append('\n');
    report.append("up next      : ").append(session.queue.snapshot().size()).append('\n');

    report.append("\nplayback\n");
    report.append("playing      : ").append(session.playing).append('\n');
    report.append("current      : ")
        .append(session.current == null ? "(none)" : session.current.title)
        .append('\n');
    report.append("singer       : ")
        .append(session.singer.isEmpty() ? "(none)" : session.singer)
        .append('\n');
    report.append("position     : ").append(session.position()).append(" ms\n");
    report.append("soundfont    : ").append(session.soundFontName).append('\n');

    report.append("\naudio\n");
    report.append("volume       : ").append(session.volume).append('\n');
    report.append("tempo        : ").append(session.tempo).append('\n');
    report.append("pitch        : ").append(session.pitch).append(" semitones\n");
    report.append("microphone   : ").append(session.microphone.enabled()).append('\n');
    report.append("monitor      : ").append(session.monitor.enabled()).append('\n');
    report.append("scoring      : ").append(session.scoringEnabled).append('\n');

    report.append("\nstage\n");
    report.append("background   : ").append(session.stageKind).append('\n');
    report.append("camera       : ").append(session.cameraPreviewEnabled).append('\n');
    report.append("visual epoch : ").append(session.visualGeneration).append('\n');

    report.append("\ncompanion\n");
    report.append("android      : ").append(session.companion.enabled()).append('\n');
    report.append("browser      : ").append(session.browserCompanion.active()).append('\n');
    report.append("smart link   : ")
        .append(session.kantaConnectHost.isHosting() ? "hosting (code withheld)" : "off")
        .append('\n');

    report.append("\nmessage      : ").append(session.message).append('\n');

    report.append("\nupdate\n");
    report.append("state        : ")
        .append(updateStatusText == null ? "idle" : updateStatusText)
        .append('\n');

    final String reportText = report.toString();
    TextView content = text(reportText, 12, Color.WHITE);
    content.setTypeface(android.graphics.Typeface.MONOSPACE);
    content.setTextIsSelectable(true);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);
    new AlertDialog.Builder(this)
        .setTitle("Diagnostics")
        .setView(scroll)
        .setPositiveButton(
            "Copy",
            (dialog, which) ->
                ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("KANTA diagnostics", reportText)))
        .setNegativeButton("Close", null)
        .show();
  }

  private void help() {
    new AlertDialog.Builder(this)
        .setTitle("Your karaoke night")
        .setMessage(
            "1. Try the three original songs, or add your own MIDI, KAR, MP3 or WAV in My Set.\n\n"
                + "2. Find a song in Control and choose Reserve. Add a singer name if you like.\n\n"
                + "3. Choose Play. Next starts the next reservation. Tap the stage for"
                + " fullscreen.\n\n"
                + "4. Save playlists or the songs you finished in My Set.\n\n"
                + "SoundFonts can be added in My Set. Loop has party sounds and between-song music."
                + " Voice settings in Control offer microphone input, experimental wired/USB"
                + " effects and optional MIDI pitch-following scores. Check input timing on your"
                + " device. Experimental pitch correction is available after verified monitoring. Android companion pairing is in Control; share the full code only with a trusted person on the same Wi-Fi. Use"
                + " music you have permission to use.")
        .setPositiveButton("Done", null)
        .show();
  }
}
