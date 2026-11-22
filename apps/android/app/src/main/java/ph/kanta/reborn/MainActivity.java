package ph.kanta.reborn;

import androidx.activity.OnBackPressedCallback;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.*;
import android.speech.RecognizerIntent;
import android.text.*;
import android.view.*;
import android.widget.*;
import ph.kanta.reborn.ui.components.UpNextTicker;
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
import ph.kanta.reborn.core.update.UpdateManifest;
import ph.kanta.reborn.core.update.UpdatePolicy;
import ph.kanta.reborn.update.AppUpdateManager;
import ph.kanta.reborn.update.UpdatePackageInspector;
import ph.kanta.reborn.pdf.PdfSongbooks;
import ph.kanta.reborn.settings.SettingsStore;
import ph.kanta.reborn.ui.KantaTheme;
import ph.kanta.reborn.ui.MenuSurface;
import ph.kanta.reborn.ui.KantaVisualsScreen;
import ph.kanta.reborn.ui.components.KantaProgressBar;

public final class MainActivity extends androidx.activity.ComponentActivity {
  private static final int IMPORT_PDF = 99;
  private static final String PDF_SONGBOOKS = "PDF Songbooks";
  private static final String MENU_STAGE_VISUAL = "Stage & Visual";
  private static final String MENU_VOICE_SCORING = "Voice & Scoring";
  private static final String MENU_INTERMISSION = "Intermission";
  private static final String MENU_PARTY_PADS = "Party Pads";
  private static final String MENU_LEGAL = "Legal";
  private static final String MENU_HELP = "Help";
  private PdfSongbooks pdfSongbooks;
  private static final int MUSIC = 10;
  private static final int LYRIC_FONT_FOLDER = 20;
  private static final int VOICE = 11;
  private static final int EXPORT = 12;
  private static final int FOLDER = 13;
  private static final int PHOTO = 14;
  private static final int VIDEO = 15;
  private static final int CAMERA = 16;
  private static final int MICROPHONE = 17;
  private static final int LEGACY_INDEX = 18;
  private static final int LEGACY_BLOB = 19;
  private static final int LEGACY_FOLDER = 21;
  private static final int NAVY = KantaTheme.BG_MAIN;
  private static final int PANEL = KantaTheme.SURFACE_ELEVATED;
  private static final int CYAN = KantaTheme.PRIMARY_CYAN;
  private static final int ACCENT = KantaTheme.PRIMARY_CYAN;

  private static final String FLUIDR3_GM_NAME = "FluidR3 GM.sf2";

  private SessionController session;
  private SettingsBackupActions settingsBackup;
  private LinearLayout root, body, stagePane, navigationTabs;
  private NestedScrollView pageScroll;
  private View appHeader;
  private StagePresenterImpl stagePresenter;
  private StageSurface fullscreenStage, externalStage;
  private KantaWorkspace workspace;
  private OnBackPressedCallback workspaceBackCallback;
  private TextView nowPlaying;
  private Button playButton, nextButton;
  private final Map<String, Button> navigationButtons = new HashMap<>();
  private Dialog fullscreen, companionDialog, browserRemoteDialog;
  private TextView fullscreenTitle, fullscreenArtist, fullscreenTime, fullscreenQueue;
  private TextView stagePlaybackTime, stageAdjustments, fullscreenAdjustments;
  private Button fullscreenPlayButton, fullscreenNextButton, fullscreenRestartButton;
  private CameraStage camera;
  private boolean cameraPermissionPending, microphonePermissionPending;
  private android.media.AudioDeviceInfo pendingMonitorOutput;
  private AlertDialog monitorOutputDialog;
  private Presentation presentation;
  private DisplayManager displays;
  private String tab = "Home", singer = "";
  private String menuDestination = "More";
  private final ArrayList<String> menuParents = new ArrayList<>();
  private final Map<String, Integer> pageScrollPositions = new HashMap<>();
  private int pageChangeGeneration;
  private Uri pendingLegacyIndex;
  private AlertDialog legacyKeyDialog;
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
  private int workspaceSearchGeneration = 0;
  private int referenceSearchGeneration = 0;
  private String homeEntry = "";
  private Future<?> homeLookup;
  private String lastHomeLookup = "";
  private long lastHomeLookupGeneration;
  private List<Song> lastHomeCatalog;
  private ph.kanta.reborn.ui.HomeScreen lastHomeScreen;
  private View stageBrand, menuBrand;
  private StageControlsPanel embeddedControls, fullscreenControls;
  private Dialog startupDialog;
  private KantaProgressBar startupProgress;
  private TextView startupStatus;
  private View partyPadsPage;
  private SeekBar partyPadVolume;
  private final List<Button> partyPadTriggers = new ArrayList<>();
  private final List<Button> partyPadAssign = new ArrayList<>();
  private final List<Button> partyPadReset = new ArrayList<>();
  private Button stageCameraToggle;
  private int lastOrientation = -1;
  private ph.kanta.reborn.ui.KantaNowPlayingScreen nowPlayingScreen;
  private ph.kanta.reborn.ui.KantaMicVoiceScreen micVoiceScreen;
  private Button micCorrection;
  private SeekBar micScoreTiming;
  private TextView micScoreTimingLabel, micLastScore;
  private Button onlineLyricsButton;
  private int lyricDialogGeneration;
  private int fontPickerGeneration;
  private int visualsSelectedTab;
  private final Map<String, Integer> mediaListPositions = new HashMap<>();
  private final android.util.LruCache<File, android.graphics.Bitmap> visualThumbnails =
      new android.util.LruCache<>(32);
  private final android.util.LruCache<File, Boolean> failedVisualThumbnails =
      new android.util.LruCache<>(32);
  private final ExecutorService thumbnailWorker = new ThreadPoolExecutor(1, 1, 0,
      TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(24));
  private AlertDialog intermissionGuide;
  private TextView intermissionState, intermissionGuideState;
  private String lowerUtilityAccessory;
  private final ArrayList<String> navigationParents = new ArrayList<>();
  private boolean preparingExport = false;
  private byte[] exportBytes;
  private String exportCode;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          session.observeMicrophone();
          finishMonitorSetup();
          refreshMicrophoneReading();
          refreshNowPlayingScreen();
          refreshIntermissionState();
          if (stagePresenter != null) stagePresenter.refresh();
          if (fullscreenStage != null) fullscreenStage.refresh();
          refreshStagePlaybackTime();
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

  @Override protected void attachBaseContext(Context base) {
    android.content.res.Configuration readable = new android.content.res.Configuration();
    readable.fontScale = base.getResources().getConfiguration().fontScale * 1.12f;
    super.attachBaseContext(base.createConfigurationContext(readable));
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    // Configuration changes are handled in-place, so seed the comparison before the first rotation.
    lastOrientation = getResources().getConfiguration().orientation;
    workspaceBackCallback =
        new OnBackPressedCallback(false) {
          @Override
          public void handleOnBackPressed() {
            navigateBack();
          }
        };
    getOnBackPressedDispatcher().addCallback(this, workspaceBackCallback);
    session = ((KantaApplication) getApplication()).session;
    settingsBackup = new SettingsBackupActions(this, session);
    pdfSongbooks = new PdfSongbooks(this, saved, IMPORT_PDF, session.diagnostics::record);
    camera = new CameraStage(this, session);
    if (saved != null) {
      tab = saved.getString("tab", "Home");
      visualsSelectedTab = saved.getInt("visualsSelectedTab") == KantaVisualsScreen.VIDEOS
          ? KantaVisualsScreen.VIDEOS : KantaVisualsScreen.BACKGROUND;
      tab = resolvePageRoute(tab);
      ArrayList<String> parents = saved.getStringArrayList("navigationParents");
      restorePageParents(tab, parents, navigationParents);
      menuDestination = canonicalPage(saved.getString("menuDestination", "More"));
      ArrayList<String> savedMenuParents = saved.getStringArrayList("menuParents");
      restorePageParents(menuDestination, savedMenuParents, menuParents);
      lowerUtilityAccessory = saved.getString("lowerUtilityAccessory");
      boolean legacySearch = "Search".equals(tab)
          || KantaWorkspace.ACCESSORY_SEARCH.equals(lowerUtilityAccessory);
      if (legacySearch) {
        tab = "Home";
        lowerUtilityAccessory = null;
        navigationParents.clear();
      }
      homeEntry = legacySearch ? saved.getString("query", saved.getString("homeEntry", ""))
          : saved.getString("homeEntry", "");
      singer = saved.getString("singer", "");
    }
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    displays = (DisplayManager) getSystemService(DISPLAY_SERVICE);
    if (session.scenic == null && "scenic".equals(session.stageKind()))
      session.scene(session.scenicName);
  }

  @Override
  protected void onResume() {
    super.onResume();
    ((KantaApplication) getApplication()).automaticDiagnostics.setVisible(true);
  }

  @Override
  protected void onPause() {
    ((KantaApplication) getApplication()).automaticDiagnostics.setVisible(false);
    super.onPause();
  }

  @Override
  protected void onStart() {
    super.onStart();
    session.hostVisible(true);
    session.setCountdownHost(this::showCountdown);
    // listen() renders synchronously and creates the primary stage on first launch.
    session.listen(this::render);
    // Forward companion search queries to the Stage search overlay so the host
    // sees what the remote typed.
    session.setCompanionSearchListener(query -> {
      if (stagePresenter != null) {
        stagePresenter.showSearch();
        stagePresenter.setSearchQuery(query);
      }
    });
    if (stagePresenter != null)
      updatePrimaryStageVisibility();
    if (fullscreenStage != null) fullscreenStage.setVisualActive(true);
    handler.post(tick);
    displays.registerDisplayListener(displayListener, handler);
    refreshDisplay();
  }

  @Override
  protected void onStop() {
    if (intermissionGuide != null) intermissionGuide.dismiss();
    closeInlineStageControls();
    if (homeScreen != null) homeScreen.dismissSongLookup();
    if (homeLookup != null) homeLookup.cancel(true);
    lastHomeLookup = "";
    homeSubmitGeneration++;
    if (legacyKeyDialog != null) legacyKeyDialog.dismiss();
    handler.removeCallbacksAndMessages(null);
    if (companionDialog != null) companionDialog.dismiss();
    companionDialog = null;
    if (browserRemoteDialog != null) browserRemoteDialog.dismiss();
    browserRemoteDialog = null;
    displays.unregisterDisplayListener(displayListener);
    cameraPermissionPending = false;
    microphonePermissionPending = false;
    clearMonitorSetup();
    camera.stop();
    session.listen(null);
    session.setCountdownHost(null);
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
    settingsBackup.close();
    pdfSongbooks.close();
    searchWorker.shutdownNow();
    thumbnailWorker.shutdownNow();
    if (updateManager != null) updateManager.close();
    if (startupDialog != null) startupDialog.dismiss();
    if (fullscreen != null) fullscreen.dismiss();
    super.onDestroy();
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    int newOrient = newConfig.orientation;
    boolean cameraWasOnPrimaryStage =
        camera != null && stagePresenter != null && camera.ownsSurface(stagePresenter);
    if (newOrient != lastOrientation) {
      if (fullscreen == null || !fullscreen.isShowing()) session.cancelCountdown();
      closeInlineStageControls();
      // Rebuild from the incoming configuration. Resources can still reflect the old
      // orientation while this callback is executing in synthetic/runtime checks.
      root = null;
      body = null;
      stagePane = null;
      navigationTabs = null;
      appHeader = null;
      buildWindow(newOrient);
      render();
      // The welcome dialog pins one artwork for the orientation it was created in; rebuild
      // it so a first-launch rotation still shows the matching composition.
      if (startupDialog != null) {
        startupDialog.dismiss();
        startupDialog = null;
        refreshStartup();
      }
      // buildWindow creates a new StagePresenterImpl. Rebind only when the old primary
      // presenter owned the camera; fullscreen/external StageSurface targets stay put.
      if (cameraWasOnPrimaryStage && stagePresenter != null) camera.moveTo(stagePresenter);
    }
    lastOrientation = newOrient;
    // Handset/tablet changes re-lay out the presented accessory so it stays a
    // bottom sheet on a handset and an anchored panel on a large screen.
    if (workspace != null) workspace.requestLayout();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("tab", tab);
    out.putInt("visualsSelectedTab", visualsSelectedTab);
    out.putStringArrayList("navigationParents", new ArrayList<>(navigationParents));
    out.putString("menuDestination", menuDestination);
    out.putStringArrayList("menuParents", new ArrayList<>(menuParents));
    out.putString("lowerUtilityAccessory", lowerUtilityAccessory);
    out.putString("homeEntry", homeEntry);
    out.putString("singer", singer);
    pdfSongbooks.saveState(out);
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density);
  }

  private AlertDialog constrainDialog(AlertDialog dialog) {
    Window window = dialog.getWindow();
    if (window == null) return dialog;
    android.util.DisplayMetrics display = getResources().getDisplayMetrics();
    window.setLayout(Math.min(Math.round(display.widthPixels * 0.86f), dp(560)),
        WindowManager.LayoutParams.WRAP_CONTENT);
    window.setGravity(Gravity.CENTER);
    View list = dialog.getListView();
    if (list == null) list = findDialogList(window.getDecorView());
    if (list == null) return dialog;
    list.setVerticalScrollBarEnabled(true);
    int maxHeight = Math.round(display.heightPixels * 0.60f);
    View constrainedList = list;
    list.post(() -> {
      if (constrainedList.getHeight() <= maxHeight) return;
      ViewGroup.LayoutParams params = constrainedList.getLayoutParams();
      params.height = maxHeight;
      constrainedList.setLayoutParams(params);
    });
    return dialog;
  }

  private View findDialogList(View view) {
    if (view instanceof ListView || view instanceof androidx.recyclerview.widget.RecyclerView) return view;
    if (!(view instanceof ViewGroup)) return null;
    ViewGroup group = (ViewGroup) view;
    for (int i = 0; i < group.getChildCount(); i++) {
      View list = findDialogList(group.getChildAt(i));
      if (list != null) return list;
    }
    return null;
  }

  private View sectionHeader(String title) {
    LinearLayout section = column();
    View top = new View(this);
    top.setBackgroundColor(KantaTheme.BORDER);
    section.addView(top, new LinearLayout.LayoutParams(-1, dp(1)));
    TextView label = text(title, 17, ACCENT);
    androidx.core.view.ViewCompat.setAccessibilityHeading(label, true);
    label.setTypeface(null, android.graphics.Typeface.BOLD);
    label.setPadding(dp(6), dp(14), dp(6), dp(14));
    section.addView(label);
    View bottom = new View(this);
    bottom.setBackgroundColor(KantaTheme.BORDER);
    section.addView(bottom, new LinearLayout.LayoutParams(-1, dp(1)));
    return section;
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

  private Button actionButton(String label, @androidx.annotation.DrawableRes int icon, Runnable action) {
    Button b = button(label, action);
    android.graphics.drawable.Drawable glyph = getDrawable(icon);
    glyph.setBounds(0, 0, dp(18), dp(18));
    b.setCompoundDrawablesRelative(glyph, null, null, null);
    b.setCompoundDrawablePadding(dp(8));
    b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    b.setPadding(dp(10), dp(10), dp(10), dp(10));
    return b;
  }

  private void addWeighted(LinearLayout row, View view) {
    row.addView(view, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
  }

  private void playWithCountdown() {
    session.toggle();
  }

  private Runnable showCountdown(int seconds, Runnable complete) {
    StagePresenter.CountdownListener listener = new StagePresenter.CountdownListener() {
      @Override public void onTick(int remaining) {}
      @Override public void onComplete() { complete.run(); }
    };
    boolean inFullscreen = fullscreen != null && fullscreen.isShowing();
    if (!inFullscreen && showsPrimaryStage() && stagePresenter != null) {
      StagePresenterImpl presenter = stagePresenter;
      presenter.startCountdown(seconds, listener);
      return presenter::cancelCountdown;
    }
    Window window = inFullscreen ? fullscreen.getWindow() : getWindow();
    if (window == null) window = getWindow();
    ph.kanta.reborn.stage.CountdownStage cue = new ph.kanta.reborn.stage.CountdownStage(this);
    int size = dp(ph.kanta.reborn.stage.CountdownStage.BADGE_SIZE_DP);
    window.addContentView(cue, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
    cue.start(seconds, listener);
    return () -> {
      cue.cancel();
      if (cue.getParent() instanceof ViewGroup) ((ViewGroup) cue.getParent()).removeView(cue);
    };
  }

  private ph.kanta.reborn.ui.HomeScreen homeScreen;
  private int homeSubmitGeneration;
  private boolean buildingWindow;

  private void render() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.post(this::render);
      return;
    }
    if (buildingWindow || isFinishing() || isDestroyed()) return;
    if (root == null || body == null) buildWindow();
    refreshStageToggleIndicators();
    refreshPartyPadControls();
    refreshStagePlaybackTime();
    refreshStartup();
    refreshStageEntry();
    refreshLowerUtilityState();
    boolean destination = !navigationParents.isEmpty()
        || !"Home".equals(tab) || lowerUtilityAccessory != null || controlsOpen(embeddedControls);
    if (workspaceBackCallback != null
        && (workspace == null || workspace.activeAccessory() == null))
      workspaceBackCallback.setEnabled(destination);
    // Keep the renderer mounted, but let utility pages use the whole viewport.
    updatePrimaryStageVisibility();
    if (navigationTabs != null) navigationTabs.setVisibility(View.VISIBLE);
    for (Map.Entry<String, Button> entry : navigationButtons.entrySet()) {
      Button nav = entry.getValue();
      boolean active = "Home".equals(entry.getKey()) == showsPrimaryStage();
      nav.setSelected(active);
      nav.setTextColor(active ? ACCENT : KantaTheme.TEXT_MUTED);
      final float navDensity = getResources().getDisplayMetrics().density;
      nav.setBackground(active
          ? KantaTheme.backgroundDrawable(KantaTheme.PRIMARY_BLUE, 16, ACCENT, 1, navDensity)
          : null);
      if (nav.getText() instanceof android.text.Spanned) {
        android.text.Spanned label = (android.text.Spanned) nav.getText();
        for (android.text.style.ImageSpan span : label.getSpans(0, label.length(), android.text.style.ImageSpan.class))
          span.getDrawable().setAlpha(active ? 255 : 96);
      }
    }
    // Refresh the Home queue without losing the user's in-progress keypad entry.
    if ("Home".equals(tab) && lowerUtilityAccessory == null
        && homeScreen != null && homeScreen.getParent() == body) {
      // Lookup is refreshed above without replacing the keypad.
      if (stagePresenter != null) stagePresenter.refresh();
      return;
    }
    // Now Playing is a live projection. Keep its view tree stable while the
    // clock/session publishes; rebuilding it every 100ms drops focus and is
    // needlessly expensive. Navigation into it still builds once below.
    if ("Now Playing".equals(tab) && lowerUtilityAccessory == null
        && nowPlayingScreen != null && nowPlayingScreen.getParent() == body) {
      refreshNowPlayingScreen();
      if (stagePresenter != null) stagePresenter.refresh();
      return;
    }
    if (PDF_SONGBOOKS.equals(tab) && lowerUtilityAccessory == null
        && pdfSongbooks.isMountedIn(body)) {
      if (stagePresenter != null) stagePresenter.refresh();
      return;
    }
    if (MENU_VOICE_SCORING.equals(tab) && lowerUtilityAccessory == null
        && micVoiceScreen != null && micVoiceScreen.getParent() == body) {
      refreshMicrophoneReading();
      refreshMicVoiceOptions();
      return;
    }
    if ("Control".equals(tab) && lowerUtilityAccessory == null
        && musicVolumeSlider != null
        && body.findViewWithTag("kanta.settings.music-volume") == musicVolumeSlider) {
      if (!draggingMusicVolume) musicVolumeSlider.setProgress(Math.round(session.volume * 100));
      if (stagePresenter != null) stagePresenter.refresh();
      return;
    }
    if (MENU_PARTY_PADS.equals(tab) && lowerUtilityAccessory == null
        && partyPadsPage != null && partyPadsPage.getParent() == body) return;
    musicVolumeSlider = null;
    draggingMusicVolume = false;
    pdfSongbooks.unmount();
    partyPadsPage = null;
    partyPadVolume = null;
    partyPadTriggers.clear();
    partyPadAssign.clear();
    partyPadReset.clear();
    body.removeAllViews();
    body.setBackground(usesMenuSurface() ? MenuSurface.background() : null);
    intermissionState = null;
    micVoiceScreen = null;
    micCorrection = null;
    micScoreTiming = null;
    micScoreTimingLabel = null;
    micLastScore = null;
    if (homeScreen != null) homeScreen.detachSearchDock();
    homeScreen = null;
    nowPlayingScreen = null;
    renderLowerUtility();
    refreshLowerUtilityState();
    if (lowerUtilityAccessory == null) renderPanel();
    if (stagePresenter != null) stagePresenter.refresh();
  }

  private boolean showsPrimaryStage() {
    return lowerUtilityAccessory == null
        && "Home".equals(tab);
  }

  private void updatePrimaryStageVisibility() {
    if (stagePane == null || stagePresenter == null) return;
    boolean visible = showsPrimaryStage();
    if (!visible && stagePane.getVisibility() == View.VISIBLE
        && (fullscreen == null || !fullscreen.isShowing())) session.cancelCountdown();
    if (stagePane.getVisibility() != (visible ? View.VISIBLE : View.GONE)) {
      stagePane.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    appHeader.setVisibility(visible || usesMenuSurface() ? View.GONE : View.VISIBLE);
    stageBrand.setTag(visible ? "kanta.header.logo" : null);
    menuBrand.setTag(visible || usesMenuSurface() ? null : "kanta.header.logo");
    if (!visible) setStageControlsExpanded(embeddedControls, false);
    stagePresenter.setVisualActive(visible && session.foreground
        && (fullscreen == null || !fullscreen.isShowing()));
  }

  private void buildWindow() {
    buildWindow(getResources().getConfiguration().orientation);
  }

  private void buildWindow(int orientation) {
    buildingWindow = true;
    try {
      constructWindow(orientation);
    } finally {
      buildingWindow = false;
    }
  }

  private void constructWindow(int orientation) {
    root = column();
    root.setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.BG_MAIN);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets.consumeSystemWindowInsets();
        });
    LinearLayout header = row();
    appHeader = header;
    header.setGravity(Gravity.CENTER_VERTICAL);
    ImageView mark = new ImageView(this);
    mark.setImageResource(ph.kanta.reborn.R.drawable.kanta_mark);
    mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    menuBrand = mark;
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
    header.addView(button(getString(R.string.menu_help), () -> openPage(MENU_HELP)));
    boolean landscape =
        orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    if (landscape) {
      brandTagline.setVisibility(View.GONE);
      brand.setTextSize(20);
      brand.setPadding(dp(4), 0, dp(4), 0);
    }
    root.addView(header);
    LinearLayout main = new LinearLayout(this);
    // Portrait: stage on top, utilities below.
    // Landscape: stage on the left, utilities on the right (wider stage area).
    main.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
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
    stagePresenter.setContentDescription("Karaoke stage");
    workspace.addPersistentLayer(stagePresenter);
    // Register as ScoreSink
    session.setScoreSink(stagePresenter);
    FrameLayout stageLayers = new FrameLayout(this);
    stageLayers.addView(workspace, new FrameLayout.LayoutParams(-1, -1));
    stageAdjustments = makeStageAdjustments("kanta.stage.adjustments");
    stagePane.addView(stageAdjustments, new LinearLayout.LayoutParams(-1, -2));
    stagePane.addView(stageLayers, new LinearLayout.LayoutParams(-1, 0, 1));
    embeddedControls = makeStageControls(false);
    addStageChrome(stageLayers);
    if (landscape) {
      embeddedControls.limitTo(stagePane);
      stagePane.addView(embeddedControls, new LinearLayout.LayoutParams(-1, -2));
    }
    // Split the available main area instead of reserving a fixed landscape height;
    // compact displays must leave usable space for the lower utility destination.
    stagePane.setMinimumHeight(dp(140));
    // Give the backdrop the larger landscape pane while keeping the keypad reachable.
    LinearLayout.LayoutParams stageParams = landscape
        ? new LinearLayout.LayoutParams(0, -1, .56f)
        : new LinearLayout.LayoutParams(-1, 0, .42f);
    main.addView(stagePane, stageParams);
    NestedScrollView scroll = new NestedScrollView(this);
    pageScroll = scroll;
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(landscape);
    scroll.setVerticalFadingEdgeEnabled(!landscape);
    scroll.setFadingEdgeLength(dp(24));
    body = column();
    body.setPadding(0, 0, 0, dp(landscape ? 4 : 14));
    LinearLayout utilities = column();
    if (!landscape) utilities.addView(embeddedControls, new LinearLayout.LayoutParams(-1, -2));
    utilities.addView(body, new LinearLayout.LayoutParams(-1, -2));
    scroll.addView(utilities);
    LinearLayout.LayoutParams contentParams = landscape
        ? new LinearLayout.LayoutParams(0, -1, .44f)
        : new LinearLayout.LayoutParams(-1, 0, .58f);
    main.addView(scroll, contentParams);
    // Bottom navigation is one continuous floating dock with text labels,
    // muted when inactive, cyan + a subtle glow pill when the destination is active.
    LinearLayout tabs = row();
    navigationTabs = tabs;
    tabs.setPadding(0, dp(1), 0, dp(1));
    tabs.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.BG_ELEVATED, 22, KantaTheme.BORDER, 1,
        getResources().getDisplayMetrics().density));
    LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    dockParams.setMargins(0, 0, 0, dp(2));
    tabs.setLayoutParams(dockParams);
    navigationButtons.clear();
    for (String name : new String[] {"Home", "More"}) {
      boolean stageTab = "Home".equals(name);
      int iconId = stageTab ? R.drawable.kanta_home_color : R.drawable.kanta_more_color;
      Button b = button(getString(stageTab ? R.string.nav_stage : R.string.nav_menu),
          () -> selectMainMenu(name));
      b.setMinWidth(0);
      b.setMinimumWidth(0);
      b.setMinHeight(dp(48));
      b.setMinimumHeight(0);
      b.setPadding(dp(2), dp(2), dp(2), dp(2));
      b.setTextSize(11);
      b.setMaxLines(1);
      b.setSelected(name.equals(tab));
      b.setTag("kanta.nav." + name);
      Drawable icon = getDrawable(iconId).mutate();
      icon.setAlpha(name.equals(tab) ? 255 : 96);
      icon.setBounds(0, 0, dp(20), dp(20));
      android.text.SpannableString dockLabel = new android.text.SpannableString("  " + b.getText());
      dockLabel.setSpan(new android.text.style.ImageSpan(icon, android.text.style.ImageSpan.ALIGN_BOTTOM),
          0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      b.setText(dockLabel);
      b.setContentDescription(getString(stageTab ? R.string.nav_stage : R.string.nav_menu));
      b.setTextColor(name.equals(tab) ? ACCENT : KantaTheme.TEXT_MUTED);
      b.setBackground(name.equals(tab)
          ? KantaTheme.backgroundDrawable(KantaTheme.PRIMARY_BLUE, 16, ACCENT, 1,
              getResources().getDisplayMetrics().density)
          : null);
      if ("More".equals(name)) {
        b.setContentDescription(getString(R.string.nav_menu));
        b.setOnLongClickListener(v -> { showWorkspaceActions(v); return true; });
      }
      navigationButtons.put(name, b);
      b.setGravity(Gravity.CENTER);
      tabs.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }
    root.addView(tabs);
    setContentView(root);
    root.requestApplyInsets();
    if (stagePresenter != null) stagePresenter.refresh();
  }

  private void addStageChrome(FrameLayout stageLayers) {
    ImageView mark = new ImageView(this);
    mark.setImageResource(R.drawable.kanta_mark);
    mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    mark.setPadding(dp(8), dp(8), dp(8), dp(8));
    stageBrand = mark;
    LinearLayout stageIdentity = column();
    stageIdentity.setGravity(Gravity.CENTER_HORIZONTAL);
    stageIdentity.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
    View badge = stagePresenter.remoteStatusBadge();
    badge.setTag("kanta.stage.remote-status");
    stageIdentity.addView(badge, new LinearLayout.LayoutParams(-2, -2));
    LinearLayout stageHeader = row();
    stageHeader.addView(stageIdentity, new LinearLayout.LayoutParams(-2, -2));
    stageLayers.addView(stageHeader, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    Button controls = stageChromeButton(R.string.stage_controls, R.drawable.kanta_stage_action_controls,
        "kanta.stage.controls", null);
    bindStageControls(embeddedControls, controls);
    FrameLayout.LayoutParams anchor = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
    anchor.setMarginStart(dp(4));
    anchor.bottomMargin = dp(28);
    stageLayers.addView(controls, anchor);

    LinearLayout stageTools = row();
    stageCameraToggle = stageChromeButton(R.string.stage_camera, R.drawable.kanta_stage_action_camera,
        "kanta.stage.camera", this::toggleCamera);
    stageTools.addView(stageCameraToggle, new LinearLayout.LayoutParams(-2, -2, 1));
    Button expand = stageChromeButton(R.string.stage_fullscreen_label, R.drawable.kanta_stage_action_fullscreen,
        "kanta.stage.fullscreen", this::fullscreen);
    expand.setContentDescription(getString(R.string.stage_fullscreen));
    stageTools.addView(expand, new LinearLayout.LayoutParams(-2, -2, 1));
    stageTools.setGravity(Gravity.END);
    // The status and actions measure side by side instead of overlapping at larger text sizes.
    stageHeader.addView(stageTools, new LinearLayout.LayoutParams(0, -2, 1));
    refreshStageToggleIndicators();

    stagePlaybackTime = text("", 14, KantaTheme.TEXT_PRIMARY);
    stagePlaybackTime.setTag("kanta.stage.time");
    stagePlaybackTime.setShadowLayer(dp(3), 0, dp(1), 0xFF000000);
    FrameLayout.LayoutParams timerParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
    timerParams.setMarginEnd(dp(8));
    timerParams.bottomMargin = dp(36);
    stageLayers.addView(stagePlaybackTime, timerParams);
    refreshStagePlaybackTime();
  }

  private Button stageChromeButton(int label, int icon, String tag, Runnable action) {
    Button button = stageIconButton(label, icon, tag, action);
    button.setTextSize(12);
    button.setMinWidth(dp(48));
    button.setMinimumWidth(dp(48));
    button.setPaddingRelative(dp(6), 0, dp(6), 0);
    button.setTag(tag);
    return button;
  }

  private void refreshStageToggleIndicators() {
    if (stageCameraToggle != null && camera != null) {
      setStageControlState(stageCameraToggle, R.string.stage_camera, camera.enabled());
    }
    refreshStageControls(embeddedControls);
    refreshStageControls(fullscreenControls);
  }

  private android.graphics.drawable.GradientDrawable stageStatusDot(boolean on) {
    android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
    dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    dot.setColor(on ? 0xFF41DF91 : 0xFFF16D79);
    dot.setSize(dp(10), dp(10));
    return dot;
  }

  private Button stageIconButton(int label, int icon, String tag, Runnable action) {
    Button control = menuAction(getString(label), icon, action == null ? () -> {} : action);
    control.setTag(tag);
    control.setCompoundDrawablePadding(dp(5));
    control.setPaddingRelative(dp(8), dp(4), dp(8), dp(4));
    return control;
  }

  /** A bounded inline scroller in landscape/fullscreen, so the lyric viewport keeps its space. */
  @android.annotation.SuppressLint("ViewConstructor")
  private final class StageControlsPanel extends ScrollView {
    final List<Button> pads = new ArrayList<>();
    final String tagPrefix;
    Button anchor, microphone, monitor, scoring;
    int maxHeight = Integer.MAX_VALUE;

    StageControlsPanel(boolean full) {
      super(MainActivity.this);
      tagPrefix = full ? "kanta.fullscreen" : "kanta.stage";
      setTag(tagPrefix + ".controls.panel");
      setVisibility(View.GONE);
      setBackground(KantaTheme.backgroundDrawable(0xDB101B2D, 12, KantaTheme.BORDER, 1,
          getResources().getDisplayMetrics().density));
    }

    void limitTo(View owner) {
      owner.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
        int limit = (b - t) * 43 / 100;
        if (limit > 0 && limit != maxHeight) {
          maxHeight = limit;
          if (getVisibility() == View.VISIBLE) requestLayout();
        }
      });
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      if (maxHeight != Integer.MAX_VALUE) {
        int bounded = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? maxHeight
            : Math.min(MeasureSpec.getSize(heightSpec), maxHeight);
        heightSpec = MeasureSpec.makeMeasureSpec(bounded, MeasureSpec.AT_MOST);
      }
      super.onMeasure(widthSpec, heightSpec);
    }
  }

  private StageControlsPanel makeStageControls(boolean full) {
    StageControlsPanel result = new StageControlsPanel(full);
    LinearLayout panel = column();
    panel.setPadding(dp(6), dp(3), dp(6), dp(6));
    result.addView(panel);
    TextView heading = MenuSurface.label(this, getString(R.string.intermission_pads_heading),
        13, KantaTheme.TEXT_PRIMARY, true);
    androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
    heading.setPaddingRelative(dp(4), 0, 0, 0);
    panel.addView(heading);
    LinearLayout pads = null;
    for (int slot = 0; slot < PartySounds.TITLES.size(); slot++) {
      if (slot % 4 == 0) {
        pads = row();
        panel.addView(pads);
      }
      Button trigger = partyPadTrigger(slot, result.tagPrefix + ".pad.");
      trigger.setTextSize(12);
      trigger.setPadding(dp(3), dp(2), dp(3), dp(2));
      addStageControlCell(pads, trigger);
      result.pads.add(trigger);
    }
    LinearLayout toggles = row();
    panel.addView(toggles);
    result.microphone = addStageControl(toggles, result, "mic", R.string.stage_mic,
        R.drawable.kanta_stage_action_mic, this::toggleMicrophone);
    result.monitor = addStageControl(toggles, result, "monitor", R.string.stage_monitor,
        R.drawable.kanta_stage_action_monitor, this::toggleMonitoring);
    result.scoring = addStageControl(toggles, result, "scoring", R.string.stage_scoring,
        R.drawable.kanta_stage_action_scoring, () -> session.setScoring(!session.scoringEnabled));
    LinearLayout shortcuts = row();
    panel.addView(shortcuts);
    if (!full) {
      addStageControl(shortcuts, result, "queue", R.string.stage_reserve_list,
          R.drawable.kanta_stage_action_queue, () -> openPage("Queue"));
      addStageControl(shortcuts, result, "browser-remote", R.string.browser_remote_title,
          R.drawable.kanta_stage_action_remote, () -> {
            setStageControlsExpanded(result, false);
            openBrowserRemote();
          });
    }
    addStageControl(shortcuts, result, "stop-party", R.string.stage_stop_party_audio,
        R.drawable.kanta_stage_action_stop_audio, session::stopParty);
    return result;
  }

  private Button addStageControl(LinearLayout row, StageControlsPanel panel, String tag,
      int label, int icon, Runnable action) {
    Button control = stageIconButton(label, icon, panel.tagPrefix + ".control." + tag, action);
    control.setTextSize(12);
    control.setPaddingRelative(dp(5), dp(4), dp(5), dp(4));
    addStageControlCell(row, control);
    return control;
  }

  private void addStageControlCell(LinearLayout row, Button control) {
    control.setMinimumWidth(0);
    control.setMinWidth(0);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
    params.setMargins(dp(2), dp(2), dp(2), dp(2));
    row.addView(control, params);
  }

  private void bindStageControls(StageControlsPanel panel, Button anchor) {
    panel.anchor = anchor;
    anchor.setContentDescription(getString(R.string.stage_controls_expand));
    anchor.setOnClickListener(v -> setStageControlsExpanded(panel, !controlsOpen(panel)));
  }

  private boolean controlsOpen(StageControlsPanel panel) {
    return panel != null && panel.getVisibility() == View.VISIBLE;
  }

  private void setStageControlsExpanded(StageControlsPanel panel, boolean expanded) {
    if (panel == null || controlsOpen(panel) == expanded) return;
    panel.setVisibility(expanded ? View.VISIBLE : View.GONE);
    panel.anchor.setSelected(expanded);
    panel.anchor.setContentDescription(getString(expanded ? R.string.stage_controls_collapse
        : R.string.stage_controls_expand));
    if (expanded) {
      panel.scrollTo(0, 0);
      // The portrait tray leads the utility scroll. Reveal it without moving the lyric viewport.
      if (panel.getParent() != stagePane && panel == embeddedControls) pageScroll.scrollTo(0, 0);
      refreshStageControls(panel);
    }
    if (panel == embeddedControls && workspaceBackCallback != null
        && (workspace == null || workspace.activeAccessory() == null))
      workspaceBackCallback.setEnabled(expanded || !navigationParents.isEmpty()
          || !"Home".equals(tab) || lowerUtilityAccessory != null);
  }

  private void closeInlineStageControls() {
    setStageControlsExpanded(embeddedControls, false);
    setStageControlsExpanded(fullscreenControls, false);
  }

  private void refreshStageControls(StageControlsPanel panel) {
    if (!controlsOpen(panel)) return;
    setStageControlState(panel.microphone, R.string.stage_mic, session.microphone.capturing());
    setStageControlState(panel.monitor, R.string.stage_monitor, session.monitor.audible());
    setStageControlState(panel.scoring, R.string.stage_scoring, session.scoringEnabled);
    for (int slot = 0; slot < panel.pads.size(); slot++) {
      refreshPartyPadTrigger(panel.pads.get(slot), slot, false);
    }
  }

  private void setStageControlState(Button button, int label, boolean on) {
    String state = getString(R.string.stage_control_state, getString(label),
        getString(on ? R.string.stage_state_on : R.string.stage_state_off));
    if (state.contentEquals(button.getContentDescription() == null ? "" : button.getContentDescription())) return;
    button.setContentDescription(state);
    button.setSelected(on);
    android.graphics.drawable.GradientDrawable dot = stageStatusDot(on);
    dot.setBounds(0, 0, dp(8), dp(8));
    SpannableString caption = new SpannableString(state + " \uFFFC");
    caption.setSpan(new android.text.style.ImageSpan(dot, android.text.style.DynamicDrawableSpan.ALIGN_BASELINE),
        caption.length() - 1, caption.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    button.setText(caption);
  }

  private void refreshStartup() {
    if (session.startupLibraryFailed) {
      if (startupDialog != null) {
        startupDialog.dismiss();
        startupDialog = null;
        constrainDialog(new AlertDialog.Builder(this).setMessage(session.message)
            .setPositiveButton(android.R.string.ok, null).create()).show();
      }
      return;
    }
    if (session.ready && session.startupAudioPrepared && session.stageReady) {
      if (startupDialog != null) startupDialog.dismiss();
      startupDialog = null;
      return;
    }
    if (startupDialog == null) {
      startupDialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
      startupDialog.setCancelable(false);
      attachStartupContent();
      startupDialog.show();
      startupDialog.getWindow().setLayout(-1, -1);
    }
    startupProgress.setProgress(!session.ready ? 0 : !session.startupAudioPrepared ? 1 : 2);
    startupStatus.setText(!session.ready ? R.string.startup_library
        : !session.startupAudioPrepared ? R.string.startup_audio : R.string.startup_stage);
  }

  /** Reads the live display orientation; dialog resource configurations can be stale. */
  private boolean isLandscape() {
    return getResources().getConfiguration().orientation
        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
  }

  private void attachStartupContent() {
    LinearLayout content = column();
    content.setBackgroundColor(0xFF07051C);
    ImageView art = new ImageView(this);
    // Pick the composition from the live configuration instead of relying on the
    // drawable-land-nodpi qualifier: this dialog is created from attachWindow, whose
    // resource configuration can still describe the previous orientation.
    art.setImageResource(isLandscape()
        ? R.drawable.kanta_startup_art
        : R.drawable.kanta_startup_art);
    art.setScaleType(ImageView.ScaleType.FIT_CENTER);
    art.setContentDescription("KANTA Karaoke");
    content.addView(art, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout information = column();
    information.setPadding(dp(24), dp(8), dp(24), dp(20));
    startupStatus = text("", 16, Color.WHITE);
    startupStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    startupStatus.setGravity(Gravity.CENTER);
    information.addView(startupStatus);
    startupProgress = new KantaProgressBar(this);
    startupProgress.setTag("kanta.startup.progress");
    startupProgress.setContentDescription(getString(R.string.startup_progress));
    startupProgress.setProgress(1.0f);
    information.addView(startupProgress, new LinearLayout.LayoutParams(-1, dp(12)));
    content.addView(information, new LinearLayout.LayoutParams(-1, -2));
    startupDialog.setContentView(content);
    startStartupAnimation();
  }

  private void startStartupAnimation() {
    handler.removeCallbacks(startupAnimator);
    handler.post(startupAnimator);
  }

  private final Runnable startupAnimator = new Runnable() {
    int step = 0;
    @Override public void run() {
      if (startupDialog == null || !startupDialog.isShowing()) return;
      step = (step + 1) % 100;
      startupProgress.setProgress(step / 100.0f);
      if (!session.ready) {
        startupStatus.setText(R.string.startup_library);
      } else if (!session.startupAudioPrepared) {
        startupStatus.setText(R.string.startup_audio);
      } else if (!session.stageReady) {
        startupStatus.setText(R.string.startup_stage);
      } else {
        startupStatus.setText(R.string.startup_complete);
        startupProgress.setProgress(100);
        startupDialog.dismiss();
        startupDialog = null;
        return;
      }
      handler.postDelayed(this, 80);
    }
  };

  /** The More tab is the canonical home for utility destinations. */
  private void showWorkspaceActions(View anchor) {
    if (anchor == null) return;
    PopupMenu menu = new PopupMenu(this, anchor);
    String[] labels = {"Search", "Queue", "Stage Visuals", "Controls", "Pitch", "Coach", "Menu"};
    for (String label : labels) menu.getMenu().add(label);
    menu.setOnMenuItemClickListener(item -> {
      String label = String.valueOf(item.getTitle());
      if ("Search".equals(label)) openStageSearch();
      else if ("Queue".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_QUEUE);
      else if ("Stage Visuals".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_VISUALS);
      else if ("Controls".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_MORE);
      else if ("Pitch".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_PITCH_GAME);
      else if ("Coach".equals(label)) presentAccessory(KantaWorkspace.ACCESSORY_LEARNING_ASSISTANT);
      else selectMainMenu("More");
      return true;
    });
    menu.show();
  }

  private void presentAccessory(String accessoryId) {
    if (accessoryId == null) return;
    if (workspace != null && workspace.activeAccessory() != null) workspace.deactivateAccessory();
    if (KantaWorkspace.ACCESSORY_VISUALS.equals(accessoryId)) {
      visualsSelectedTab = KantaVisualsScreen.BACKGROUND;
    }
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

  /** Controls accessory: pitch, voice, remote controls, diagnostics, and fullscreen. */
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
    target.addView(button(getString(R.string.browser_remote_title), () -> {
      dismissAccessory();
      openBrowserRemote();
    }));
    target.addView(
        button("Diagnostics", () -> {
          dismissAccessory();
          diagnostics(false);
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

  private void openAndroidCompanion() {
    companionDialog = CompanionDialog.show(this, session);
  }

  private void openBrowserRemote() {
    browserRemoteDialog = BrowserCompanionDialog.show(this, session);
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

    SeekBar sensSeek = ph.kanta.reborn.ui.KantaTheme.adjustmentSlider(this);
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

    constrainDialog(new AlertDialog.Builder(this)
        .setView(scroll)
        .setPositiveButton("Close", null)
        .show());
  }

  private boolean usesMenuSurface() {
    if (lowerUtilityAccessory != null) return false;
    switch (tab) {
      case "More":
      case "Presets":
      case "Control":
      case MENU_INTERMISSION:
      case MENU_PARTY_PADS:
      case MENU_STAGE_VISUAL:
      case MENU_VOICE_SCORING:
      case PDF_SONGBOOKS:
      case MENU_LEGAL:
      case MENU_HELP:
        return true;
      default:
        return false;
    }
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
    if (lowerUtilityAccessory != null && !KantaWorkspace.ACCESSORY_VISUALS.equals(lowerUtilityAccessory))
      body.addView(button("Close utility", this::dismissAccessory));
    if (KantaWorkspace.ACCESSORY_QUEUE.equals(lowerUtilityAccessory)) {
      body.addView(text("Queue", 18, ACCENT));
      queueAccessory(body);
    } else if (KantaWorkspace.ACCESSORY_VISUALS.equals(lowerUtilityAccessory)) {
      visualsScreen();
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
    nowPlayingScreen = null;
    String legacyTitle = legacyDestinationTitle();
    if (legacyTitle != null) {
      String tag = "kanta.legacy." + tab.toLowerCase(Locale.ROOT).replace(' ', '-') + ".back";
      body.addView(MenuSurface.header(this, legacyTitle, getString(R.string.menu_back), tag,
          this::navigateBack));
    }
    if (!"Home".equals(tab) && !isReferenceDestination(tab)) {
      if (usesMenuSurface()) {
        TextView status = MenuSurface.detail(this, session.message);
        status.setPadding(dp(18), 0, dp(18), dp(10));
        body.addView(status);
      } else {
        body.addView(text(session.message, 13, CYAN));
      }
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
      case "Queue":
        queueScreen();
        break;
      case MENU_VOICE_SCORING:
        micVoiceScreen();
        break;
      case MENU_STAGE_VISUAL:
        visualsScreen();
        break;
      case "Now Playing":
        nowPlayingScreen();
        break;
      case "Presets":
        mySet();
        break;
      case PDF_SONGBOOKS:
        pdfSongbooks.mount(body, this::navigateBack, this::songbookExports);
        break;
      case MENU_INTERMISSION:
        loop();
        break;
      case MENU_PARTY_PADS:
        partyPadsPage();
        break;
      case MENU_LEGAL:
        libraryNoticesPage();
        break;
      case MENU_HELP:
        helpPage();
        break;
      case "LyricFont":
        lyricFontPage();
        break;
      default:
        control();
    }
  }

  private String legacyDestinationTitle() {
    switch (tab) {
      case "Presets": return getString(R.string.menu_presets);
      case "Control": return getString(R.string.menu_settings);
      case MENU_INTERMISSION: return getString(R.string.menu_intermission);
      case MENU_PARTY_PADS: return getString(R.string.intermission_pads_heading);
      case MENU_LEGAL: return getString(R.string.menu_legal);
      case MENU_HELP: return getString(R.string.menu_help);
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
        || "Queue".equals(destination)
        || MENU_VOICE_SCORING.equals(destination)
        || MENU_STAGE_VISUAL.equals(destination)
        || "Now Playing".equals(destination)
        || "Presets".equals(destination)
        || PDF_SONGBOOKS.equals(destination)
        || "Control".equals(destination)
        || MENU_INTERMISSION.equals(destination)
        || MENU_PARTY_PADS.equals(destination)
        || MENU_LEGAL.equals(destination)
        || MENU_HELP.equals(destination);
  }

  private void selectMainMenu(String destination) {
    boolean inMenu = "More".equals(tab) || navigationParents.contains("More");
    if ("Home".equals(destination)) {
      if (inMenu) rememberMenuRoute();
      navigationParents.clear();
    } else if ("More".equals(destination)) {
      if (inMenu) {
        if (lowerUtilityAccessory != null) showPage(tab);
        return;
      }
      destination = menuDestination;
      navigationParents.clear();
      navigationParents.addAll(menuParents);
    }
    showPage(destination);
  }

  private void rememberMenuRoute() {
    menuDestination = tab;
    menuParents.clear();
    // If Menu was opened from a Stage shortcut, don't restore its old Stage parent.
    int menuRoot = navigationParents.indexOf("More");
    if (menuRoot >= 0) menuParents.addAll(navigationParents.subList(menuRoot, navigationParents.size()));
  }

  private void openStageSearch() {
    selectMainMenu("Home");
    if (homeScreen != null) {
      homeScreen.focusSongSearch();
      homeScreen.reopenSongLookup();
      refreshStageEntry();
    }
  }

  private void openPage(String destination) {
    destination = resolvePageRoute(destination);
    if (!tab.equals(destination)) navigationParents.add(tab);
    showPage(destination);
  }

  private void navigateBack() {
    if (controlsOpen(embeddedControls)) {
      setStageControlsExpanded(embeddedControls, false);
    } else if (lowerUtilityAccessory != null
        || (workspace != null && workspace.activeAccessory() != null)) {
      dismissAccessory();
    } else if (PDF_SONGBOOKS.equals(tab) && pdfSongbooks.backToLibrary()) {
      return;
    } else if (!navigationParents.isEmpty()) {
      if ("More".equals(tab)) rememberMenuRoute();
      String parent = navigationParents.remove(navigationParents.size() - 1);
      showPage(parent.startsWith("deck:") ? parent.substring(5) : parent);
    } else if (!"Home".equals(tab)) {
      if ("More".equals(tab)) rememberMenuRoute();
      showPage("Home");
    }
  }

  /** Old page IDs are accepted for saved navigation and existing shortcuts. */
  private String resolvePageRoute(String destination) {
    if ("Scenic".equals(destination) || "Realm".equals(destination)) {
      visualsSelectedTab = KantaVisualsScreen.VIDEOS;
    }
    return canonicalPage(destination);
  }

  private static String canonicalPage(String destination) {
    switch (destination) {
      case "Scenic":
      case "Realm":
      case "Stage":
      case "Visuals": return MENU_STAGE_VISUAL;
      case "Voice":
      case "Scoring":
      case "Mic & Voice": return MENU_VOICE_SCORING;
      case "Loop": return MENU_INTERMISSION;
      default: return destination;
    }
  }

  private static void restorePageParents(String destination, List<String> saved, List<String> restored) {
    if (saved == null) return;
    for (String parent : saved) {
      String page = canonicalPage(parent.startsWith("deck:") ? parent.substring(5) : parent);
      if (restored.isEmpty() || !restored.get(restored.size() - 1).equals(page)) restored.add(page);
    }
    // v33 showed some destinations through an intermediary alias of that same page.
    if (!restored.isEmpty() && destination.equals(restored.get(restored.size() - 1))) {
      restored.remove(restored.size() - 1);
    }
  }

  private void showPage(String destination) {
    destination = resolvePageRoute(destination);
    boolean changed = !tab.equals(destination) || lowerUtilityAccessory != null;
    NestedScrollView outgoing = pageScroll;
    if (changed) setStageControlsExpanded(embeddedControls, false);
    if (changed && lowerUtilityAccessory == null && outgoing != null && !PDF_SONGBOOKS.equals(tab)) {
      pageScrollPositions.put(tab, outgoing.getScrollY());
    }
    lowerUtilityAccessory = null;
    if (workspace != null && workspace.activeAccessory() != null) workspace.deactivateAccessory();
    tab = destination;
    if (changed && outgoing != null) outgoing.scrollTo(0, 0);
    render();
    if (!changed) return;
    int generation = ++pageChangeGeneration;
    int restoredY = pageScrollPositions.getOrDefault(tab, 0);
    if (restoredY > 0 && body != null && pageScroll != null) {
      NestedScrollView scroll = pageScroll;
      LinearLayout pageBody = body;
      scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @Override public void onLayoutChange(View v, int l, int t, int r, int b,
            int oldL, int oldT, int oldR, int oldB) {
          scroll.removeOnLayoutChangeListener(this);
          if (generation == pageChangeGeneration && body == pageBody && lowerUtilityAccessory == null) {
            scroll.scrollTo(0, restoredY);
          }
        }
      });
    }
  }

  /** Now Playing is a utility projection below the persistent live stage. */
  private void nowPlayingScreen() {
    nowPlayingScreen=new ph.kanta.reborn.ui.KantaNowPlayingScreen(this,new ph.kanta.reborn.ui.KantaNowPlayingScreen.Listener(){
      @Override public void onBack(){navigateBack();}
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
    constrainDialog(new AlertDialog.Builder(this).setTitle("Find lyrics on LRCLIB")
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
            constrainDialog(new AlertDialog.Builder(this).setTitle("Choose the matching version · LRCLIB")
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
                    constrainDialog(new AlertDialog.Builder(this).setTitle("Untimed lyrics · LRCLIB")
                        .setView(scroll).setPositiveButton("Done", null).show());
                  } else Toast.makeText(this, "No usable lyrics in this version. Your song is unchanged.", Toast.LENGTH_LONG).show();
                }).setNegativeButton("Cancel", null).show());
          }))
        .show());
  }

  private String formatPlaybackTime(long ms){long seconds=Math.max(0,ms/1000);return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}

  /** Shared by Stage & Visual and the Stage shortcut. */
  private void visualsScreen() {
    boolean savedMediaActive = !camera.enabled();
    File activeVideo = savedMediaActive && "video".equals(session.stageKind()) ? session.stageVideo() : null;
    String activeScene = savedMediaActive && "scenic".equals(session.stageKind()) ? session.scenicName : null;
    KantaVisualsScreen screen = new KantaVisualsScreen(this, new KantaVisualsScreen.Listener() {
      @Override public void onBack() { navigateBack(); }
      @Override public void onTabSelected(int index) {
        if (visualsSelectedTab == index) return;
        visualsSelectedTab = index;
        if (pageScroll != null) pageScroll.scrollTo(0, 0);
        render();
      }
      @Override public void onBackgroundSelected(String id, int drawableResource) {
        if (!session.canChangeStageMedia()) return;
        camera.stop();
        if (drawableResource == 0) session.scene(id);
        else session.sceneResource(id, drawableResource);
      }
      @Override public void onVideoSelected(String id) {
        if (!session.canChangeStageMedia()) return;
        camera.stop();
        session.selectScenicClip(id);
      }
      @Override public void addControls(int index, LinearLayout target) {
        addStageVisualControls(index, target);
      }
    }, visualsSelectedTab, activeScene, activeVideo == null ? null : activeVideo.getName(),
        session.canChangeStageMedia(), usesMenuSurface());
    screen.setTag("kanta.screen.visuals");
    body.addView(screen, new LinearLayout.LayoutParams(-1, -2));
  }

  private void addStageVisualControls(int section, LinearLayout target) {
    boolean video = section == KantaVisualsScreen.VIDEOS;
    int icon = video ? R.drawable.kanta_submenu_video : R.drawable.kanta_submenu_photo;
    Switch shuffle = new Switch(this);
    shuffle.setTag(video ? "kanta.visuals.shuffle.videos" : "kanta.visuals.shuffle.photos");
    shuffle.setText(video ? R.string.media_library_shuffle_videos : R.string.media_library_shuffle_photos);
    shuffle.setTextColor(KantaTheme.TEXT_PRIMARY);
    shuffle.setMinHeight(dp(48));
    MenuSurface.setLeadingIcon(shuffle, R.drawable.kanta_submenu_shuffle);
    shuffle.setChecked(session.stageShuffleEnabled(video));
    shuffle.setOnCheckedChangeListener((button, checked) -> session.setStageShuffle(video, checked));
    target.addView(shuffle);
    target.addView(text(getString(video ? R.string.media_library_shuffle_videos_help
        : R.string.media_library_shuffle_photos_help), 14, KantaTheme.TEXT_SECONDARY));
    if (video) {
      target.addView(sectionHeader("Scenic library"));
      Button library = actionButton(getString(R.string.scenic_browse_library), icon, this::showScenicVideoLibrary);
      library.setEnabled(session.ready && session.scenicLibraryAvailable() && session.canChangeStageMedia());
      target.addView(library);
    }
    target.addView(sectionHeader(getString(video ? R.string.media_library_videos : R.string.media_library_photos)));
    Button importMedia = actionButton(getString(video ? R.string.media_library_import_videos
        : R.string.media_library_import_photos), R.drawable.kanta_submenu_folder_plus, () -> chooseStageMedia(video));
    importMedia.setTag(video ? "kanta.visuals.video-import" : "kanta.visuals.custom-import");
    importMedia.setEnabled(session.ready && session.canChangeStageMedia());
    target.addView(importMedia);
    target.addView(text(getString(video ? R.string.media_library_videos_help
        : R.string.media_library_photos_help), 14, KantaTheme.TEXT_SECONDARY));
    StageMediaStore.Selection selected = session.stageSelection;
    List<StageMediaStore.Entry> entries = new ArrayList<>();
    if (selected != null) {
      for (StageMediaStore.Entry entry : selected.imports) if (entry.video == video) entries.add(entry);
    }
    if (entries.isEmpty()) {
      target.addView(text(getString(video ? R.string.media_library_videos_empty
          : R.string.media_library_photos_empty), 14, KantaTheme.TEXT_SECONDARY));
    } else {
      File selectedFile = video ? session.stageVideo() : session.stagePhotoFile();
      File fallback = (video ? "video" : "photo").equals(session.stageKind())
          ? (video ? selected.video : selected.photo) : null;
      String key = video ? "kanta.visuals.imported.videos" : "kanta.visuals.imported.photos";
      addCompactMediaList(target, key, entries.size(), position -> {
        StageMediaStore.Entry entry = entries.get(position);
        boolean active = entry.file.equals(selectedFile);
        String type = getString(video ? R.string.media_library_video_details : R.string.media_library_photo_details);
        String title = entry.displayName.isEmpty()
            ? getString(video ? R.string.media_library_video : R.string.media_library_photo)
            : entry.displayName;
        ImageView thumbnail = new ImageView(this);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setImageResource(icon);
        thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        loadVisualThumbnail(entry, thumbnail);
        View item = compactMediaRow(title, type, thumbnail, active,
            session.canChangeStageMedia() && (!active || camera.enabled()), () -> {
              camera.stop();
              session.chooseStageImport(entry.file, video);
            }, session.canChangeStageMedia(), () -> {
              if (active || entry.file.equals(fallback)) {
                showRemovalResult(getString(R.string.media_library_visual_remove_blocked));
                return;
              }
              confirmImportedRemoval(getString(R.string.media_library_remove_confirm, title),
                  getString(R.string.media_library_original_safe),
                  () -> session.removeStageImport(entry.file, video, this::showRemovalResult));
            });
        item.setTag((video ? "kanta.visuals.imported.video." : "kanta.visuals.imported.photo.")
            + entry.file.getName());
        return item;
      });
    }
    if (session.importing) {
      target.addView(actionButton("Cancel import", R.drawable.kanta_submenu_cancel, session::cancelImports));
    }
    if (video || session.importing || !session.message.isEmpty()) {
      target.addView(text(session.message, 14, KantaTheme.TEXT_SECONDARY));
    }
  }

  private void chooseStageMedia(boolean video) {
    if (!session.ready || !session.canChangeStageMedia()) return;
    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(video ? "video/*" : "image/*")
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    startActivityForResult(picker, video ? VIDEO : PHOTO);
  }

  private void addCompactMediaList(LinearLayout target, String tag, int count,
      java.util.function.IntFunction<View> makeRow) {
    RecyclerView list = new RecyclerView(this);
    LinearLayoutManager layout = new LinearLayoutManager(this);
    list.setLayoutManager(layout);
    list.setVerticalScrollBarEnabled(true);
    list.setItemAnimator(null);
    list.setTag(tag);
    int scaledHeight = Math.round(android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP, 38, getResources().getDisplayMetrics()));
    int rowHeight = Math.max(dp(68), scaledHeight);
    list.setAdapter(new RecyclerView.Adapter<CompactMediaHolder>() {
      @Override public int getItemCount() { return count; }
      @Override public CompactMediaHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        FrameLayout frame = new FrameLayout(parent.getContext());
        frame.setLayoutParams(new RecyclerView.LayoutParams(-1, rowHeight));
        return new CompactMediaHolder(frame);
      }
      @Override public void onBindViewHolder(CompactMediaHolder holder, int position) {
        holder.row.removeAllViews();
        holder.row.addView(makeRow.apply(position), new FrameLayout.LayoutParams(-1, -1));
      }
    });
    target.addView(list, new LinearLayout.LayoutParams(-1, rowHeight * Math.min(3, count)));
    int first = mediaListPositions.getOrDefault(tag, 0);
    layout.scrollToPositionWithOffset(Math.min(first, Math.max(0, count - 3)), 0);
    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override public void onScrolled(RecyclerView source, int dx, int dy) {
        if (dy != 0) {
          int position = layout.findFirstVisibleItemPosition();
          if (position >= 0) mediaListPositions.put(tag, position);
        }
      }
    });
  }

  private static final class CompactMediaHolder extends RecyclerView.ViewHolder {
    final FrameLayout row;
    CompactMediaHolder(FrameLayout row) {
      super(row);
      this.row = row;
    }
  }

  private View compactMediaRow(String title, String type, ImageView thumbnail, boolean active,
      boolean canUse, Runnable use, boolean canRemove, Runnable remove) {
    LinearLayout item = row();
    item.setPadding(dp(4), 0, dp(4), 0);
    item.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE, 8,
        active ? ACCENT : KantaTheme.BORDER, 1, getResources().getDisplayMetrics().density));
    if (thumbnail != null) item.addView(thumbnail, new LinearLayout.LayoutParams(dp(44), dp(44)));
    LinearLayout labels = column();
    labels.setPadding(dp(6), 0, dp(4), 0);
    TextView name = KantaTheme.titleText(this, title, 14);
    name.setSingleLine();
    name.setEllipsize(TextUtils.TruncateAt.END);
    labels.addView(name);
    TextView description = KantaTheme.bodyText(this,
        active ? getString(R.string.media_library_active_type, type) : type, 12);
    if (active) description.setTextColor(ACCENT);
    description.setSingleLine();
    description.setEllipsize(TextUtils.TruncateAt.END);
    labels.addView(description);
    item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    if (!active || canUse) {
      Button useButton = compactMediaButton(getString(R.string.media_library_use),
          getString(R.string.media_library_use_item, title), use);
      useButton.setEnabled(canUse);
      item.addView(useButton);
    }
    Button removeButton = compactMediaButton(getString(R.string.label_remove),
        getString(R.string.media_library_remove_item, title), remove);
    removeButton.setEnabled(canRemove);
    item.addView(removeButton);
    return item;
  }

  private Button compactMediaButton(String title, String description, Runnable action) {
    return compactMediaButton(title, description, action, 0);
  }

  private Button compactMediaButton(String title, String description, Runnable action, @androidx.annotation.DrawableRes int icon) {
    Button control = button(title, action);
    if (icon != 0) {
      MenuSurface.setLeadingIcon(control, icon);
      control.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      control.setPaddingRelative(dp(14), dp(8), dp(14), dp(8));
    } else {
      control.setTextSize(12);
      control.setMinWidth(dp(48));
      control.setMinimumWidth(dp(48));
      control.setPadding(dp(8), 0, dp(8), 0);
    }
    control.setContentDescription(description);
    return control;
  }

  private void loadVisualThumbnail(StageMediaStore.Entry entry, ImageView target) {
    // Only bound rows request previews. Keep a bounded cache across ordinary screen refreshes.
    File key = entry.file;
    android.graphics.Bitmap known = visualThumbnails.get(key);
    if (known != null) {
      target.setImageBitmap(known);
      return;
    }
    if (failedVisualThumbnails.get(key) != null || thumbnailWorker.isShutdown()) return;
    // Decode on a separate worker so scrolling doesn't block library or playback work. Avoid
    // queuing an unbounded number of obsolete rows while the user scrolls through a large list.
    java.lang.ref.WeakReference<ImageView> row = new java.lang.ref.WeakReference<>(target);
    try {
      thumbnailWorker.execute(() -> {
        android.graphics.Bitmap bitmap = visualThumbnails.get(key);
        if (bitmap == null && row.get() != null && failedVisualThumbnails.get(key) == null) {
          try { bitmap = StageMediaStore.thumbnail(entry); }
          catch (IOException | RuntimeException | OutOfMemoryError unavailable) { /* Show type icon. */ }
          if (bitmap != null) visualThumbnails.put(key, bitmap);
          else failedVisualThumbnails.put(key, true);
        }
        android.graphics.Bitmap preview = bitmap;
        if (preview != null) handler.post(() -> {
          ImageView image = row.get();
          if (!isDestroyed() && image != null) image.setImageBitmap(preview);
        });
      });
    } catch (RejectedExecutionException busy) {
      // The type icon stays visible; the next row bind can request its preview again.
    }
  }

  /** The UI exposes only controls backed by the existing microphone and monitor owners. */
  private void micVoiceScreen() {
    micVoiceScreen =
        new ph.kanta.reborn.ui.KantaMicVoiceScreen(
            this,
            new ph.kanta.reborn.ui.KantaMicVoiceScreen.Listener() {
              @Override public void onBack() { navigateBack(); }
              @Override public void onMicToggle() { toggleMicrophone(); }
              @Override public void onMonitorToggle() { toggleMonitoring(); }
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
            }, usesMenuSurface());
    micVoiceScreen.setTag("kanta.screen.mic-voice");
    addMicVoiceOptions();
    refreshMicrophoneReading();
    refreshMicVoiceOptions();
    body.addView(micVoiceScreen, new LinearLayout.LayoutParams(-1, -1));
  }

  /** Adds the settings-only controls to the same scrolling page as the shortcut. */
  private void addMicVoiceOptions() {
    // KantaMicVoiceScreen has a fixed header followed by its scrollable card column.
    ScrollView scroll = (ScrollView) micVoiceScreen.getChildAt(1);
    LinearLayout content = (LinearLayout) scroll.getChildAt(0);
    LinearLayout options = column();
    float density = getResources().getDisplayMetrics().density;
    options.setBackground(KantaTheme.backgroundDrawable(
        KantaTheme.SURFACE_ELEVATED, 16, KantaTheme.BORDER, 1, density));
    options.setPadding(dp(16), dp(14), dp(16), dp(14));
    options.addView(KantaTheme.titleText(this, "Advanced voice settings", 16));

    micCorrection = button("Turn on pitch correction",
        () -> session.monitor.correction(!session.monitor.correctionEnabled()));
    micCorrection.setTag("kanta.pitchcorrection.toggle");
    options.addView(micCorrection);
    options.addView(text(
        "Pitch correction becomes available after the headphone output is verified. It nudges"
            + " stable notes toward the nearest semitone, not the song melody. It may add delay"
            + " or change your voice tone. It does not affect scoring and turns off with"
            + " monitoring. Your singing is not saved or uploaded.",
        13, KantaTheme.TEXT_SECONDARY));

    micScoreTimingLabel = text("", 13, KantaTheme.TEXT_PRIMARY);
    options.addView(micScoreTimingLabel);
    micScoreTiming = ph.kanta.reborn.ui.KantaTheme.adjustmentSlider(this);
    micScoreTiming.setTag("kanta.score.timing");
    micScoreTiming.setContentDescription("Score timing offset, minus 500 to 1500 milliseconds");
    micScoreTiming.setMax(200);
    micScoreTiming.setKeyProgressIncrement(1);
    micScoreTiming.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      private boolean dragging;

      @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; }

      @Override public void onStopTrackingTouch(SeekBar bar) {
        dragging = false;
        session.scoreLatency(bar.getProgress() * 10 - 500);
      }

      @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
        if (!fromUser) return;
        int offset = value * 10 - 500;
        micScoreTimingLabel.setText(getString(R.string.score_timing_offset, offset));
        if (!dragging) session.scoreLatency(offset); // Keyboard and accessibility changes.
      }
    });
    options.addView(micScoreTiming);
    options.addView(text(
        "Check timing on your device before adjusting this. Changing the timing, key or tempo"
            + " clears the current estimate. Backing music can affect the estimate.",
        13, KantaTheme.TEXT_SECONDARY));
    micLastScore = text("", 15, KantaTheme.PRIMARY_CYAN);
    micLastScore.setTag("kanta.score.last");
    options.addView(micLastScore);
    content.addView(options, new LinearLayout.LayoutParams(-1, -2));
  }

  private void refreshMicVoiceOptions() {
    micCorrection.setText(session.monitor.correctionEnabled()
        ? "Turn off pitch correction" : "Turn on pitch correction");
    micCorrection.setEnabled(session.monitor.correctionAvailable());
    if (!micScoreTiming.isPressed()) {
      micScoreTiming.setProgress((session.scoreLatency + 500) / 10);
      micScoreTimingLabel.setText(getString(R.string.score_timing_offset, session.scoreLatency));
    }
    SessionController.CompletedScore completed = session.lastScore;
    if (completed == null || !session.scoringEnabled) {
      micLastScore.setVisibility(View.GONE);
      return;
    }
    String result = completed.estimate.reportable
        ? String.format(Locale.ROOT, "Estimate %.0f/100 · coverage %.0f%%",
            completed.estimate.overall, completed.estimate.coverage)
        : "Not enough stable voice input for a score";
    micLastScore.setText((CharSequence) (completed.singer.isEmpty()
        ? getString(R.string.mic_last_song, completed.title, result)
        : getString(R.string.mic_last_song_with_singer, completed.title, completed.singer, result)));
    micLastScore.setVisibility(View.VISIBLE);
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

  private void queueScreen() {
    java.util.List<ReservationQueue.Entry> entries = session.queue.snapshot();
    String[][] rows = new String[entries.size()][6];
    for (int i = 0; i < entries.size(); i++) {
      ReservationQueue.Entry entry = entries.get(i);
      rows[i][0] = String.valueOf(i + 1);
      rows[i][1] = entry.song.title;
      rows[i][2] = entry.singer.isEmpty() ? entry.song.artist : entry.singer;
      rows[i][3] = "false";
      rows[i][4] = "#30245B";
      rows[i][5] = String.valueOf(artworkResourceForSong(entry.song));
    }
    ph.kanta.reborn.ui.KantaQueueScreen screen =
        new ph.kanta.reborn.ui.KantaQueueScreen(
            this,
            new ph.kanta.reborn.ui.KantaQueueScreen.Listener() {
              @Override public void onBack() { navigateBack(); }
              @Override public void onClear() {
                session.queue.clear();
                session.saveQueue();
                session.publish();
                render();
              }
              @Override public void onAddSongs() {
                openStageSearch();
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



  /** Reference-led home dashboard. Existing playback and workspace owners remain authoritative. */
  private void homeDashboard() {
    homeScreen = new ph.kanta.reborn.ui.HomeScreen(
            this,
            new ph.kanta.reborn.ui.HomeScreen.Listener() {
              @Override public void onHome() {
                selectMainMenu("Home");
              }
              @Override public void onSing() { openPage("Sing"); }
              @Override public void onPerformance() { openPage("Performance"); }
              @Override public void onExperience() { openPage("Experience"); }
              @Override public void onSettings() { openPage("More"); }
              @Override public void onSearch() { openStageSearch(); }
              @Override public void onSearchQueryChanged(String typed) {
                // Cancel pending RES work even if the user edits back to the same text.
                homeSubmitGeneration++;
                homeEntry = typed;
                refreshStageEntry();
              }
              @Override public void onSearchSubmitted(String typed) { submitHomeEntry(typed); }
              @Override public void onKeySound(String key) { session.playKeypadSound(key); }
              @Override public void onQueue() { openPage("Queue"); }
              @Override public void onVisuals() { openPage(MENU_STAGE_VISUAL); }
              @Override public void onMicVoice() { openPage(MENU_VOICE_SCORING); }
              @Override public void onNextSong() { session.next(); }
              @Override public void onPauseResumeSong() {
                if (session.ready && session.foreground && !session.loading) playWithCountdown();
              }
              @Override public void onStopSong() {
                homeSubmitGeneration++;
                session.stop();
              }
              @Override public void onKeyStep(int amount) {
                session.controls(session.volume, session.effectiveTempo(), session.effectivePitch() + amount);
              }
              @Override public void onTempoStep(int amount) {
                float tempo = session.effectiveTempo() + amount * 0.05f;
                session.controls(session.volume, Math.max(.5f, Math.min(2f, tempo)), session.effectivePitch());
              }
              @Override public void onMySet() { openPage("Presets"); }
              @Override public void onFavorites() { openPage("Presets"); }
              @Override public void onRecent() { openPage("Presets"); }
              @Override public void onMore() { openPage("More"); }
              @Override public void onScenic() { openPage("Scenic"); }
              @Override public void onRealm() { openPage("Realm"); }
            });
    homeScreen.showSongLookup(stagePane);
    homeScreen.restoreSearchText(homeEntry);
    refreshStageEntry();
    body.addView(homeScreen, new LinearLayout.LayoutParams(-1, -1));
  }

  private boolean isHomeLookupOpen() {
    return "Home".equals(tab) && lowerUtilityAccessory == null;
  }

  private void refreshStageEntry() {
    if (stagePresenter == null) return;
    boolean home = isHomeLookupOpen();
    stagePresenter.presenceOverlay().setEnteredCode(home ? homeEntry : "");
    String requested = home ? homeEntry.trim() : "";
    final ph.kanta.reborn.ui.HomeScreen target = homeScreen;
    if (home && target != null) {
      target.setPauseResumeState(
          session.ready && session.foreground && !session.loading, session.playing, session.player != null);
    }
    final long lookupGeneration =
        home && target != null ? target.activeSongLookupGeneration() : 0;
    if (lookupGeneration == 0 || requested.isEmpty() || !session.ready || !session.foreground) {
      if (homeLookup != null) {
        homeLookup.cancel(true);
        homeLookup = null;
        referenceSearchGeneration++;
      }
      lastHomeLookup = "";
      return;
    }
    List<Song> library = session.songs;
    if (requested.equals(lastHomeLookup) && library == lastHomeCatalog
        && target == lastHomeScreen && lookupGeneration == lastHomeLookupGeneration) return;
    if (homeLookup != null) homeLookup.cancel(true);
    target.showMatchStatus(R.string.stage_search_preview_loading);
    lastHomeLookup = requested;
    lastHomeCatalog = library;
    lastHomeScreen = target;
    lastHomeLookupGeneration = lookupGeneration;
    final int token = ++referenceSearchGeneration;
    homeLookup = searchWorker.submit(() -> {
      ArrayList<Song> matches = new ArrayList<>();
      boolean numeric = SongCodes.isDigits(requested);
      String[] terms = numeric ? null : Song.searchTerms(requested);
      for (Song song : library) {
        if (Thread.currentThread().isInterrupted()) return;
        int rank = numeric ? song.numericPreviewRank(requested) : (song.matchesTerms(terms) ? 0 : -1);
        if (rank < 0) continue;
        int index = matches.size();
        if (numeric) while (index > 0 && matches.get(index - 1).numericPreviewRank(requested) > rank) index--;
        if (index < 3) matches.add(index, song);
        if (matches.size() > 3) matches.remove(3);
        if (matches.size() == 3 && (!numeric || matches.get(2).numericPreviewRank(requested) == 0)) break;
      }
      handler.post(() -> {
        if (token != referenceSearchGeneration || !isHomeLookupOpen() || !session.foreground
            || !requested.equals(homeEntry.trim()) || homeScreen != target || library != session.songs
            || lookupGeneration != target.activeSongLookupGeneration()) return;
        target.showMatches(lookupGeneration, matches, song -> {
          if (isHomeLookupOpen() && session.foreground && target == homeScreen
              && requested.equals(homeEntry.trim()) && library == session.songs) reserveFromSearch(song);
        }, song -> {
          if (isHomeLookupOpen() && session.foreground && target == homeScreen
              && requested.equals(homeEntry.trim()) && library == session.songs)
            session.playSearchResult(song, singer);
        }, song -> {
          if (isHomeLookupOpen() && session.foreground && target == homeScreen
              && requested.equals(homeEntry.trim()) && library == session.songs)
            choosePlaylistForSong(song);
        });
        if (numeric) {
          int digitCount = requested.length();
          session.diagnostics.record("Stage keypad: " + digitCount + " digit(s), "
              + matches.size() + " shown, " + library.size() + " in library", null);
        }
      });
    });
  }

  private void reserveFromSearch(Song song) {
    session.reserve(song, singer);
    Toast.makeText(this, session.message, Toast.LENGTH_SHORT).show();
  }

  private void submitHomeEntry(String typed) {
    final String requested = typed == null ? "" : typed.trim();
    if (homeScreen == null || requested.isEmpty()) return;
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
        if (token != homeSubmitGeneration || source != homeScreen || !isHomeLookupOpen()
            || !session.foreground || isFinishing() || isDestroyed()
            || !entered.equals(source.getSearchText())) return;
        if (match != null && library == session.songs) {
          int previousCount = session.queue.snapshot().size();
          session.reserve(match, singer);
          Toast.makeText(this, session.message, Toast.LENGTH_SHORT).show();
          if (session.queue.snapshot().size() > previousCount) source.clearSearchText();
        } else {
          refreshStageEntry();
          source.revealSongMatches();
        }
      });
    });
  }




  private ph.kanta.reborn.ui.GroupScreen groupScreen(
      String title, String purpose, ph.kanta.reborn.ui.GroupScreen.Item[] items) {
    return new ph.kanta.reborn.ui.GroupScreen(
        this,
        title,
        purpose,
        items,
        new ph.kanta.reborn.ui.GroupScreen.Listener() {
          @Override public void onBack() { navigateBack(); }
          @Override public void onAction(String action) { handleGroupAction(action); }
        });
  }

  private void handleGroupAction(String action) {
    switch (action) {
      case "sing-menu": openPage("Sing"); break;
      case "performance-menu": openPage("Performance"); break;
      case "experience-menu": openPage("Experience"); break;
      case "find-song":
        openStageSearch();
        break;
      case "my-songs":
      case "favorites":
      case "recent":
        openPage("Presets");
        break;
      case "import-pdf":
        openPage(PDF_SONGBOOKS);
        break;
      case "queue":
        openPage("Queue");
        break;
      case "lyrics":
        findOnlineLyrics();
        break;
      case "voice-scoring":
        openPage(MENU_VOICE_SCORING);
        break;
      case "key-tempo":
        openPage("Pitch & Tempo");
        break;
      case "stage-visual":
      case "background":
      case "effects":
        visualsSelectedTab = KantaVisualsScreen.BACKGROUND;
        openPage(MENU_STAGE_VISUAL);
        break;
      case "camera":
        toggleCamera();
        break;
      case "realm":
        openPage("Realm");
        break;
      case "bgv":
      case "scenic":
        openPage("Scenic");
        break;
      case "intermission":
      case "party":
        openPage(MENU_INTERMISSION);
        break;
      case "party-pads":
        openPage(MENU_PARTY_PADS);
        break;
      case "display":
        fullscreen();
        break;
      case "browser-remote":
        openBrowserRemote();
        break;
      case "settings":
        openPage("Control");
        break;
      case "backup":
        settingsBackup.show();
        break;
      case "legal":
        openPage(MENU_LEGAL);
        break;
      case "help":
        openPage(MENU_HELP);
        break;
      default:
        break;
    }
  }

  private void singGroup() {
    body.addView(groupScreen("Sing", "What do you want to sing?", new ph.kanta.reborn.ui.GroupScreen.Item[] {
      new ph.kanta.reborn.ui.GroupScreen.Item("my-songs", "Presets", getString(R.string.menu_presets_summary), ph.kanta.reborn.R.drawable.kanta_my_songs_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("favorites", "Favorites", "Saved songs", ph.kanta.reborn.R.drawable.kanta_favorites_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("recent", "Recent", "Recently played", ph.kanta.reborn.R.drawable.kanta_recent_color)
    }));
  }

  private void performanceGroup() {
    body.addView(groupScreen("Performance", "Help me while I’m singing.", new ph.kanta.reborn.ui.GroupScreen.Item[] {
      new ph.kanta.reborn.ui.GroupScreen.Item("lyrics", "Find lyrics", "Look up timed lyrics for this song", ph.kanta.reborn.R.drawable.kanta_lyrics_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("voice-scoring", getString(R.string.menu_voice_scoring), getString(R.string.menu_voice_summary), R.drawable.kanta_sing_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("key-tempo", "Key & Tempo", "Fit the song to your voice", ph.kanta.reborn.R.drawable.kanta_key_tempo_color)
    }));
  }

  private void experienceGroup() {
    body.addView(groupScreen("Experience", "How should your karaoke session look and feel?", new ph.kanta.reborn.ui.GroupScreen.Item[] {
      new ph.kanta.reborn.ui.GroupScreen.Item("stage-visual", getString(R.string.menu_stage_visual), getString(R.string.menu_visuals_summary), R.drawable.kanta_experience_color),
      new ph.kanta.reborn.ui.GroupScreen.Item("party", "Intermission", "Between-song music and sounds", R.drawable.kanta_party_color)
    }));
  }

  private void moreGroup() {
    body.addView(MenuSurface.header(this, getString(R.string.nav_menu), getString(R.string.menu_back),
        "kanta.group.back", this::navigateBack));
    LinearLayout core = MenuSurface.section(body, "KANTA");
    addMenuRow(core, "my-songs", getString(R.string.menu_presets), getString(R.string.menu_presets_summary),
        R.drawable.kanta_menu_presets);
    addMenuRow(core, "import-pdf", getString(R.string.menu_songbook), getString(R.string.menu_songbook_summary),
        R.drawable.kanta_menu_books);
    addMenuRow(core, "stage-visual", getString(R.string.menu_stage_visual), getString(R.string.menu_visuals_summary),
        R.drawable.kanta_menu_visual);
    addMenuRow(core, "intermission", getString(R.string.menu_intermission), getString(R.string.menu_intermission_summary),
        R.drawable.kanta_menu_music_list);
    addMenuRow(core, "party-pads", getString(R.string.intermission_pads_heading), getString(R.string.menu_party_pads_summary),
        R.drawable.kanta_menu_party_pads);
    addMenuRow(core, "voice-scoring", getString(R.string.menu_voice_scoring), getString(R.string.menu_voice_summary),
        R.drawable.kanta_menu_music_mic);
    addMenuRow(core, "settings", getString(R.string.menu_settings), getString(R.string.menu_settings_summary),
        R.drawable.kanta_menu_gear);
    LinearLayout remotes = MenuSurface.section(body, getString(R.string.menu_remotes));
    addMenuRow(remotes, "browser-remote", getString(R.string.browser_remote_title),
        getString(R.string.browser_remote_summary), R.drawable.kanta_menu_phone_radio);
    LinearLayout support = MenuSurface.section(body, getString(R.string.menu_support));
    addMenuRow(support, "backup", getString(R.string.settings_backup_title),
        getString(R.string.menu_backup_summary), R.drawable.kanta_menu_cloud_upload);
    addMenuRow(support, "legal", getString(R.string.menu_legal), getString(R.string.menu_legal_summary),
        R.drawable.kanta_menu_shield_check);
    addMenuRow(support, "help", getString(R.string.menu_help), getString(R.string.menu_help_summary),
        R.drawable.kanta_menu_help);
  }

  private void addMenuRow(LinearLayout section, String id, String title, String detail, int icon) {
    section.addView(MenuSurface.row(this, title, detail, icon, "kanta.group." + id,
        () -> handleGroupAction(id)));
  }

  private Button menuAction(String label, int icon, Runnable action) {
    Button result = actionButton(label, icon, action);
    MenuSurface.actionStyle(result, false);
    return result;
  }

  private Button menuButton(String label, Runnable action) {
    Button result = button(label, action);
    MenuSurface.actionStyle(result, false);
    return result;
  }

  private SeekBar musicVolumeSlider;
  private boolean draggingMusicVolume;

  private void control() {
    LinearLayout mix = MenuSurface.section(body, getString(R.string.menu_sound_mix));
    mix.addView(MenuSurface.label(this, "Music volume", 16, Color.WHITE, false));
    SeekBar volume = ph.kanta.reborn.ui.KantaTheme.adjustmentSlider(this);
    volume.setTag("kanta.settings.music-volume");
    volume.setContentDescription("Music volume");
    volume.setMax(100);
    volume.setProgress(Math.round(session.volume * 100));
    musicVolumeSlider = volume;
    volume.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar b) {
            draggingMusicVolume = true;
          }

          public void onStopTrackingTouch(SeekBar b) {
            draggingMusicVolume = false;
          }

          public void onProgressChanged(SeekBar b, int value, boolean user) {
            if (user) session.setVolume(value / 100f);
          }
        });
    mix.addView(volume);
    LinearLayout lyrics = MenuSurface.section(body, getString(R.string.menu_lyrics));
    SettingsStore lyricSettings = new SettingsStore(this);
    Button[] autoLyrics = new Button[1];
    autoLyrics[0] = menuButton(getString(lyricSettings.getAutoLyrics()
        ? R.string.auto_lyrics_disable : R.string.auto_lyrics_enable), () -> {
      lyricSettings.setAutoLyrics(!lyricSettings.getAutoLyrics());
      autoLyrics[0].setText(lyricSettings.getAutoLyrics()
          ? R.string.auto_lyrics_disable : R.string.auto_lyrics_enable);
    });
    autoLyrics[0].setTag("kanta.settings.auto-lyrics");
    MenuSurface.setLeadingIcon(autoLyrics[0], R.drawable.kanta_submenu_lyrics);
    lyrics.addView(autoLyrics[0]);
    lyrics.addView(MenuSurface.detail(this, getString(R.string.auto_lyrics_details)));
    lyrics.addView(menuAction(getString(R.string.menu_lyric_font), R.drawable.kanta_submenu_type,
        () -> openPage("LyricFont")));
    LinearLayout support = MenuSurface.section(body, getString(R.string.menu_diagnostics));
    support.addView(menuAction("Diagnostics", R.drawable.kanta_submenu_check,
        () -> diagnostics(true)));

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
    LinearLayout hero = MenuSurface.section(body, getString(R.string.menu_library_hero));
    hero.addView(MenuSurface.detail(this, getString(R.string.menu_library_intro)));
    LinearLayout folderIntro = row();
    folderIntro.setPadding(0, dp(8), 0, dp(6));
    ImageView folderIcon = MenuSurface.icon(this, R.drawable.kanta_submenu_folder, 11);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(52));
    iconParams.setMarginEnd(dp(12));
    folderIntro.addView(folderIcon, iconParams);
    LinearLayout folderLabels = column();
    folderLabels.addView(MenuSurface.label(this, getString(R.string.menu_add_folder_heading),
        16, Color.WHITE, true));
    folderLabels.addView(MenuSurface.detail(this, getString(R.string.legacy_folder_help)));
    folderIntro.addView(folderLabels, new LinearLayout.LayoutParams(0, -2, 1));
    hero.addView(folderIntro);
    Button legacyFolder = actionButton(getString(R.string.menu_add_karaoke_folder), R.drawable.kanta_submenu_folder,
        () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), LEGACY_FOLDER));
    MenuSurface.actionStyle(legacyFolder, true);
    legacyFolder.setTag("kanta.library.add-folder");
    legacyFolder.setEnabled(session.canRemoveLibraryContent());
    hero.addView(legacyFolder);

    LinearLayout library = MenuSurface.section(body, session.ready && session.songs.isEmpty()
        ? getString(R.string.menu_library_empty) : getString(R.string.menu_library_songs));
    if (session.ready && session.songs.isEmpty()) {
      library.addView(MenuSurface.detail(this, getString(R.string.menu_library_empty_details)));
    }
    if (session.importing) {
      library.addView(MenuSurface.detail(this, session.message));
      library.addView(menuAction("Cancel import", R.drawable.kanta_submenu_cancel, session::cancelImports));
    }
    library.addView(text(getResources().getQuantityString(R.plurals.imported_song_count,
        session.songs.size(), session.songs.size()), 14, KantaTheme.TEXT_SECONDARY));
    Button removeSong = menuAction("Remove an imported song", R.drawable.kanta_submenu_remove,
        this::chooseSongToRemove);
    removeSong.setEnabled(session.canRemoveLibraryContent() && !session.songs.isEmpty());
    library.addView(removeSong);

    LinearLayout imports = MenuSurface.section(body, getString(R.string.menu_library_import));
    Button importButton =
        menuAction(
            session.importing ? "Importing…" : "Add music or SoundFont",
            R.drawable.kanta_submenu_add_list,
            () -> chooseMusic(false));
    importButton.setEnabled(session.ready && !session.importing);
    imports.addView(importButton);
    imports.addView(text(getString(R.string.media_library_presets_multi), 14, KantaTheme.TEXT_SECONDARY));
    Button folder =
        menuAction(
            "Import a music folder",
            R.drawable.kanta_submenu_folder_plus,
            () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), FOLDER));
    folder.setEnabled(session.ready && !session.importing);
    imports.addView(folder);
    imports.addView(menuAction("Import legacy idx + blob", R.drawable.kanta_submenu_folder_plus, this::pickLegacyIndex));

    LinearLayout playlists = MenuSurface.section(body, getString(R.string.playlists_title));
    Button createPlaylist = menuAction(getString(R.string.playlist_create),
        R.drawable.kanta_submenu_add_list, () -> namePlaylist(null));
    createPlaylist.setEnabled(session.ready);
    playlists.addView(createPlaylist);
    for (LibraryRepository.SavedSet set : session.savedSets) {
      playlists.addView(menuAction(playlistLabel(set), R.drawable.kanta_submenu_document,
          () -> showPlaylist(set)));
    }
    playlists.addView(menuAction("Save Up Next as a playlist", R.drawable.kanta_submenu_add_list, () -> nameSet(false)));
    playlists.addView(menuAction("Save played songs as Favorites", R.drawable.kanta_submenu_check, () -> nameSet(true)));


    LinearLayout soundFonts = MenuSurface.section(body, "SoundFonts");
    soundFonts.addView(text("Active SoundFont · " + session.soundFontName, 14, CYAN));
    for (LibraryRepository.SoundFontEntry bank : session.soundFonts) {
      LinearLayout item = row();
      item.addView(
          text(bank.name, 14, Color.WHITE),
          new LinearLayout.LayoutParams(0, -2, 1));
      boolean active = bank.fileName.equals(session.activeSoundFontFileName());
      if (active) item.addView(text("ACTIVE", 12, CYAN));
      else item.addView(menuAction("Use", R.drawable.kanta_submenu_check, () -> session.selectSoundFont(bank.fileName)));
      if (LibraryRepository.removableSoundFont(bank.fileName)) {
        Button remove = menuAction("Remove", R.drawable.kanta_submenu_remove, () ->
            confirmImportedRemoval("Remove " + bank.name + "?", "Your original file stays unchanged."
                + (active ? " Future songs will use the KANTA original tone." : ""),
                () -> session.deleteSoundFont(bank.fileName, this::showRemovalResult)));
        remove.setContentDescription("Remove SoundFont " + bank.name);
        remove.setEnabled(session.canRemoveLibraryContent());
        item.addView(remove);
      }
      soundFonts.addView(item);
    }
    soundFonts.addView(menuAction("Download FluidR3 GM (~148 MB)", R.drawable.kanta_submenu_download, () -> downloadFluidR3GM()));
    soundFonts.addView(menuAction("Download MuseScore General (~216 MB)", R.drawable.kanta_submenu_download, () -> downloadMuseScoreGeneral()));
    soundFonts.addView(menuAction("Download SGM-v2.01 Yamaha Grand (~528 MB)", R.drawable.kanta_submenu_download, () -> downloadSGMv201()));
    soundFonts.addView(MenuSurface.detail(this, getString(R.string.menu_soundfont_details)));

  }

  private void songbookExports(LinearLayout parent) {
    LinearLayout exports = MenuSurface.section(parent, getString(R.string.menu_songbook_exports));
    exports.addView(menuAction("Export private Songbook", R.drawable.kanta_submenu_share, () -> export(true)));
    exports.addView(menuAction("Export song list (CSV)", R.drawable.kanta_submenu_copy, () -> export(false)));
  }

  private String playlistLabel(LibraryRepository.SavedSet set) {
    int size = set.songs.size();
    return getString(R.string.playlist_summary, set.name,
        getResources().getQuantityString(R.plurals.playlist_song_count, size, size));
  }

  private void choosePlaylistForSong(Song song) {
    List<LibraryRepository.SavedSet> sets = session.savedSets;
    AlertDialog.Builder picker = new AlertDialog.Builder(this)
        .setTitle(R.string.playlist_add)
        .setPositiveButton(R.string.playlist_create_new, (dialog, which) -> namePlaylist(song))
        .setNegativeButton(android.R.string.cancel, null);
    if (sets.isEmpty()) {
      picker.setMessage(R.string.playlist_none);
    } else {
      String[] labels = new String[sets.size()];
      for (int i = 0; i < labels.length; i++) labels[i] = playlistLabel(sets.get(i));
      picker.setItems(labels, (dialog, which) -> addPlaylistSong(sets.get(which).id, song));
    }
    constrainDialog(picker.show());
  }

  private void namePlaylist(Song firstSong) {
    if (!session.foreground) return;
    EditText name = new EditText(this);
    name.setHint(R.string.playlist_name);
    name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    name.setSingleLine(true);
    name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(LibraryRepository.PLAYLIST_NAME_LIMIT)});
    LinearLayout fields = column();
    fields.setPadding(dp(24), 0, dp(24), 0);
    fields.addView(name);
    AlertDialog dialog = constrainDialog(new AlertDialog.Builder(this)
        .setTitle(R.string.playlist_create)
        .setView(fields)
        .setPositiveButton(R.string.playlist_create_action, null)
        .setNegativeButton(android.R.string.cancel, null)
        .show());
    Runnable create = () -> {
      String entered = name.getText().toString().trim();
      if (entered.isEmpty()) {
        name.setError(getString(R.string.playlist_name_required));
        return;
      }
      if (!session.foreground) return;
      dialog.dismiss();
      session.createPlaylist(entered, firstSong, this::showPlaylistResult);
    };
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> create.run());
    name.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    name.setOnEditorActionListener((view, action, event) -> {
      if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
      create.run();
      return true;
    });
    name.requestFocus();
  }

  private String playlistSongLabel(Song song) {
    return getString(R.string.library_controls_song_picker_row, song.code, song.title);
  }

  private void showPlaylist(LibraryRepository.SavedSet set) {
    Set<String> wanted = new HashSet<>(set.songs);
    Map<String, Song> available = new HashMap<>();
    for (Song song : session.songs) if (wanted.contains(song.id)) available.put(song.id, song);
    List<String> titles = new ArrayList<>();
    for (String id : set.songs) {
      Song song = available.get(id);
      if (song != null) titles.add(playlistSongLabel(song));
    }
    AlertDialog.Builder details = new AlertDialog.Builder(this)
        .setTitle(set.name)
        .setPositiveButton(R.string.playlist_add_songs, (dialog, which) -> searchPlaylistSongs(set))
        .setNegativeButton(android.R.string.cancel, null);
    if (titles.isEmpty()) {
      details.setMessage(set.songs.isEmpty() ? R.string.playlist_empty : R.string.playlist_unavailable);
    } else {
      details.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles), null)
          .setNeutralButton(R.string.playlist_reserve_all, (dialog, which) -> {
            if (session.foreground) {
              session.loadSet(set);
              showPlaylistResult(session.message);
            }
          });
    }
    constrainDialog(details.show());
  }

  private void searchPlaylistSongs(LibraryRepository.SavedSet set) {
    EditText query = new EditText(this);
    query.setHint(R.string.playlist_search_hint);
    query.setInputType(InputType.TYPE_CLASS_TEXT);
    query.setSingleLine(true);
    TextView status = text(getString(R.string.playlist_search_hint), 14, KantaTheme.TEXT_SECONDARY);
    ListView results = new ListView(this);
    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
    results.setAdapter(adapter);
    LinearLayout fields = column();
    fields.setPadding(dp(16), 0, dp(16), 0);
    fields.addView(query);
    fields.addView(status);
    fields.addView(results, new LinearLayout.LayoutParams(-1, Math.min(dp(280),
        Math.round(getResources().getDisplayMetrics().heightPixels * .42f))));
    AlertDialog dialog = constrainDialog(new AlertDialog.Builder(this)
        .setTitle(getString(R.string.playlist_search_title, set.name))
        .setView(fields)
        .setNegativeButton(android.R.string.cancel, null)
        .show());
    List<Song> shown = new ArrayList<>();
    results.setOnItemClickListener((parent, view, position, id) -> addPlaylistSong(set.id, shown.get(position)));
    class PlaylistSearch implements TextWatcher {
      private static final int RESULT_LIMIT = 50;
      private Future<?> lookup;
      private int generation;

      private void cancel() {
        generation++;
        if (lookup != null) lookup.cancel(true);
      }

      @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
      @Override public void afterTextChanged(Editable input) {
        cancel();
        int token = generation;
        String requested = input.toString().trim();
        shown.clear();
        adapter.clear();
        status.setVisibility(View.VISIBLE);
        status.setText(requested.isEmpty() ? R.string.playlist_search_hint : R.string.stage_search_preview_loading);
        if (requested.isEmpty()) return;
        List<Song> catalog = session.songs;
        lookup = searchWorker.submit(() -> {
          List<Song> found = new ArrayList<>();
          boolean numeric = SongCodes.isDigits(requested);
          String[] terms = numeric ? null : Song.searchTerms(requested);
          for (Song song : catalog) {
            if (Thread.currentThread().isInterrupted()) return;
            if (numeric ? song.numericPreviewRank(requested) < 0 : !song.matchesTerms(terms)) continue;
            found.add(song);
            if (found.size() > RESULT_LIMIT) break;
          }
          handler.post(() -> {
            if (token != generation || !dialog.isShowing()) return;
            boolean more = found.size() > RESULT_LIMIT;
            if (more) found.remove(RESULT_LIMIT);
            shown.addAll(found);
            List<String> labels = new ArrayList<>(found.size());
            for (Song song : found) labels.add(playlistSongLabel(song));
            adapter.addAll(labels);
            status.setVisibility(found.isEmpty() || more ? View.VISIBLE : View.GONE);
            status.setText(more ? R.string.playlist_search_more : R.string.stage_search_preview_empty);
          });
        });
      }
    }
    PlaylistSearch search = new PlaylistSearch();
    query.addTextChangedListener(search);
    dialog.setOnDismissListener(closed -> {
      query.removeTextChangedListener(search);
      search.cancel();
    });
    query.requestFocus();
  }

  private void addPlaylistSong(String playlistId, Song song) {
    if (session.foreground) session.addToPlaylist(playlistId, song, this::showPlaylistResult);
  }

  private void showPlaylistResult(String status) {
    if (session.foreground && !isFinishing() && !isDestroyed())
      Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
  }

  private void chooseSongToRemove() {
    List<Song> songs = session.songs;
    ArrayAdapter<Song> adapter = new ArrayAdapter<Song>(this,
        android.R.layout.simple_list_item_1, songs) {
      @Override public View getView(int position, View recycled, ViewGroup parent) {
        TextView label = (TextView) super.getView(position, recycled, parent);
        Song song = getItem(position);
        if (song != null) label.setText(getString(R.string.library_controls_song_picker_row,
            song.code, song.title));
        return label;
      }
    };
    constrainDialog(new AlertDialog.Builder(this).setTitle(R.string.library_controls_song_remove)
        .setAdapter(adapter, (dialog, position) -> {
          Song song = songs.get(position);
          confirmImportedRemoval(getString(R.string.library_controls_song_remove_confirm, song.title),
              getString(R.string.library_controls_song_remove_details),
              () -> session.deleteSong(song.id, this::showRemovalResult));
        }).setNegativeButton(android.R.string.cancel, null).show());
  }

  private void confirmImportedRemoval(String title, String detail, Runnable confirmed) {
    constrainDialog(new AlertDialog.Builder(this).setTitle(title).setMessage(detail)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.label_remove, (dialog, which) -> confirmed.run()).show());
  }

  private void showRemovalResult(String status) {
    if (!isFinishing() && !isDestroyed()) Toast.makeText(this, status, Toast.LENGTH_LONG).show();
  }

  private void downloadFluidR3GM() {
    showDownloadProgress("FluidR3 GM", session::downloadFluidR3GM);
  }

  private void downloadMuseScoreGeneral() {
    showDownloadProgress("MuseScore General", session::downloadMuseScoreGeneral);
  }

  private void downloadSGMv201() {
    showDownloadProgress("SGM-v2.01 Yamaha Grand", session::downloadSGMv201);
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

  private void showDownloadProgress(String name, Runnable downloadAction) {
    android.app.ProgressDialog dialog = new android.app.ProgressDialog(this);
    dialog.setTitle("Downloading " + name);
    dialog.setMessage("Preparing download...");
    dialog.setIndeterminate(true);
    dialog.setCancelable(false);
    dialog.show();
    session.setDownloadProgressListener(new SessionController.DownloadProgressListener() {
      @Override public void onProgress(String message) {
        runOnUiThread(() -> {
          if (dialog.isShowing()) dialog.setMessage(message);
        });
      }
      @Override public void onComplete(boolean success, String message) {
        runOnUiThread(() -> {
          dialog.dismiss();
          constrainDialog(new android.app.AlertDialog.Builder(MainActivity.this)
              .setTitle(success ? "Download Complete" : "Download Failed")
              .setMessage(message)
              .setPositiveButton("OK", null)
              .show());
        });
      }
    });
    downloadAction.run();
  }
  private void nameSet(boolean played) {
    ArrayList<Song> songs = new ArrayList<>();
    if (played) songs.addAll(session.played);
    else for (ReservationQueue.Entry e : session.queue.snapshot()) songs.add(e.song);
    if (songs.isEmpty()) {
      constrainDialog(new AlertDialog.Builder(this)
          .setMessage(
              played
                  ? "Finish a song first to save the songs you sang."
                  : "Reserve songs first to save a playlist.")
          .setPositiveButton("OK", null)
          .show());
      return;
    }
    EditText name = new EditText(this);
    name.setHint("Set name");
    constrainDialog(new AlertDialog.Builder(this)
        .setTitle(played ? "Save Favorites" : "Save playlist")
        .setView(name)
        .setPositiveButton("Save", (d, w) -> session.saveSet(name.getText().toString(), songs))
        .setNegativeButton("Cancel", null)
        .show());
  }

  void toggleMonitoring() {
    if (session.monitor.enabled()) {
      session.stopMonitor();
      return;
    }
    if (pendingMonitorOutput != null) {
      clearMonitorSetup();
      refreshMicrophoneReading();
      return;
    }
    if (!session.foreground || microphonePermissionPending || monitorOutputDialog != null) return;
    List<android.media.AudioDeviceInfo> outputs = session.monitor.outputs();
    if (outputs.isEmpty()) {
      showVoiceNotice(true, "Connect wired or USB headphones, an audio interface or a Bluetooth"
          + " headset. Phone and Bluetooth speakers are not supported.");
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
    monitorOutputDialog = new AlertDialog.Builder(this)
        .setTitle("Choose your headphones")
        .setItems(labels, (dialog, selected) -> {
          if (!session.foreground) return;
          android.media.AudioDeviceInfo output = outputs.get(selected);
          if (MicrophoneMonitor.communication(output) && !session.allowsBluetoothMonitoring()) {
            showVoiceNotice(true, "Bluetooth voice playback is available between songs. Use wired"
                + " or USB headphones during songs.");
            return;
          }
          if (session.microphone.capturing()) {
            session.startMonitor(output);
          } else {
            pendingMonitorOutput = output;
            session.setMonitorNotice(null);
            if (!session.microphone.enabled()) startMicrophoneWithPermission();
            refreshMicrophoneReading();
          }
        })
        .setNegativeButton("Cancel", null)
        .create();
    monitorOutputDialog.setOnDismissListener(dialog -> monitorOutputDialog = null);
    monitorOutputDialog.show();
    constrainDialog(monitorOutputDialog);
  }

  private void clearMonitorSetup() {
    pendingMonitorOutput = null;
    if (monitorOutputDialog != null) monitorOutputDialog.dismiss();
  }

  private void finishMonitorSetup() {
    if (pendingMonitorOutput == null || microphonePermissionPending || !session.foreground) return;
    if (session.microphone.capturing()) {
      android.media.AudioDeviceInfo output = pendingMonitorOutput;
      pendingMonitorOutput = null; // startMonitor can publish synchronously.
      session.startMonitor(output);
    } else if (!session.microphone.enabled()) {
      pendingMonitorOutput = null;
      session.setMonitorNotice("Microphone did not start. Check microphone access and try again.");
    }
  }

  private void showVoiceNotice(boolean monitor, String notice) {
    if (monitor) session.setMonitorNotice(notice);
    else session.setMicrophoneNotice(notice);
    session.publish();
    if (micVoiceScreen == null) Toast.makeText(this, notice, Toast.LENGTH_LONG).show();
  }

  void toggleMicrophone() {
    if (session.microphone.enabled()) {
      clearMonitorSetup();
      session.stopMicrophone();
      return;
    }
    startMicrophoneWithPermission();
  }

  private void startMicrophoneWithPermission() {
    if (!session.foreground || microphonePermissionPending) return;
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      microphonePermissionPending = true;
      refreshMicrophoneReading();
      requestPermissions(new String[] {android.Manifest.permission.RECORD_AUDIO}, MICROPHONE);
      return;
    }
    session.startMicrophone();
  }

  private void refreshMicrophoneReading() {
    if (micVoiceScreen != null && MENU_VOICE_SCORING.equals(tab)) {
      MicrophoneInput.Reading reading = session.microphone.reading();
      micVoiceScreen.setInputLevel(
          reading == null ? 0f : (float) reading.rms,
          !session.microphone.enabled()
              ? "Microphone off"
              : !session.microphone.capturing()
                  ? "Starting microphone…"
                  : reading == null ? "Listening for input…" : "Microphone active");
      micVoiceScreen.setMicrophoneState(session.microphone.enabled(), session.microphone.capturing(),
          microphonePermissionPending, session.microphoneNotice);
      micVoiceScreen.setMonitorState(pendingMonitorOutput != null, session.monitor.enabled(),
          session.monitor.routeVerified(),
          session.monitor.audible(), session.monitor.gain(), session.monitor.maxGain(),
          session.monitor.mode().name(),
          session.monitorNotice);
      micVoiceScreen.setScoreState(session.scoringEnabled, session.scoreStatus());
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
        boolean settingUpMonitor = pendingMonitorOutput != null;
        clearMonitorSetup();
        String notice = "Microphone permission was not granted. Local playback still works.";
        if (settingUpMonitor) session.setMonitorNotice(notice);
        showVoiceNotice(false, notice);
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

  private void showScenicVideoLibrary() {
    if (!session.ready || session.importing || !session.scenicLibraryAvailable()) return;
    LinearLayout list = column();
    list.setPadding(dp(12), dp(8), dp(12), dp(8));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(list);
    AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.scenic_library_title)
        .setView(scroll).setNegativeButton(R.string.button_close, null).create();
    renderScenicVideoLibrary(list, dialog);
    dialog.show();
    constrainDialog(dialog);
  }

  private void renderScenicVideoLibrary(LinearLayout list, AlertDialog dialog) {
    list.removeAllViews();
    list.addView(sectionHeader("Scenic download"));
    list.addView(text(getString(R.string.scenic_library_intro), 14, KantaTheme.TEXT_SECONDARY));
    String status = session.scenicDownloadStatus();
    if (!status.isEmpty()) list.addView(text(status, 14, CYAN));
    Map<String, BgvLibrary.Entry> downloaded = new HashMap<>();
    for (BgvLibrary.Entry entry : session.scenicEntries()) downloaded.put(entry.id, entry);
    for (ph.kanta.reborn.core.bgv.ScenicClip clip : ph.kanta.reborn.core.bgv.ScenicCatalog.BUILT_IN) {
      BgvLibrary.Entry entry = downloaded.get(clip.id);
      list.addView(text(getString(R.string.scenic_library_details, clip.title, clip.credit,
          clip.licence, formatBytes(clip.expectedBytes)), 14, Color.WHITE));
      LinearLayout buttons = row();
      if (entry == null) {
        Button download = actionButton(getString(R.string.scenic_download), R.drawable.kanta_submenu_download, () -> {
          if (session.downloadScenic(clip.id, () -> {
            if (dialog.isShowing()) renderScenicVideoLibrary(list, dialog);
          })) renderScenicVideoLibrary(list, dialog);
        });
        download.setEnabled(!session.scenicDownloadInProgress());
        addWeighted(buttons, download);
      } else {
        addWeighted(buttons, actionButton(getString(R.string.scenic_use_video), R.drawable.kanta_submenu_video, () -> {
          camera.stop();
          session.importStage(Uri.fromFile(entry.file), true);
          dialog.dismiss();
        }));
        addWeighted(buttons, actionButton(getString(R.string.label_remove), R.drawable.kanta_submenu_remove, () -> {
          session.deleteScenic(entry.id);
          renderScenicVideoLibrary(list, dialog);
        }));
      }
      list.addView(buttons);
    }
  }

  private void loop() {
    LinearLayout music = MenuSurface.section(body, getString(R.string.intermission_music_heading));
    intermissionState = text("", 15, ACCENT);
    intermissionState.setTag("kanta.intermissions.status");
    music.addView(intermissionState);
    refreshIntermissionState();
    Button guide = menuAction(getString(R.string.intermission_guide_button),
        R.drawable.kanta_submenu_lyrics, this::showIntermissionGuide);
    guide.setTag("kanta.intermissions.guide");
    music.addView(guide);
    music.addView(menuButton(getString(session.loopEnabled ? R.string.intermission_turn_off
        : R.string.intermission_turn_on), session::toggleIntermission));
    if (!session.loopSongId.isEmpty()) {
      music.addView(menuButton(getString(R.string.intermission_use_bundled), session::useBundledInterlude));
    }
    volumeControl(music, getString(R.string.intermission_music_volume), session.party.loopVolume,
        value -> session.partyVolumes(session.party.sfxVolume, value));

    LinearLayout musicTracks = MenuSurface.section(body, getString(R.string.intermission_tracks_heading));
    Button add = menuAction(getString(R.string.intermission_add_music),
        R.drawable.kanta_submenu_add_list, () -> chooseMusic(true));
    add.setTag("kanta.intermissions.add");
    add.setEnabled(session.ready && session.canRemoveLibraryContent());
    musicTracks.addView(add);
    musicTracks.addView(text(getString(R.string.intermission_tracks_intro), 14, KantaTheme.TEXT_SECONDARY));
    if (session.importing) {
      musicTracks.addView(menuAction("Cancel import", R.drawable.kanta_submenu_cancel, session::cancelImports));
    }
    List<Song> songs = session.songs;
    List<Song> tracks = new ArrayList<>();
    for (Song song : songs) if (!song.starter && SessionController.isAudio(song)) tracks.add(song);
    if (tracks.isEmpty()) {
      musicTracks.addView(text(getString(R.string.intermission_tracks_empty), 14, KantaTheme.TEXT_SECONDARY));
    } else {
      addCompactMediaList(musicTracks, "kanta.intermissions.tracks", tracks.size(), position -> {
        Song track = tracks.get(position);
        boolean active = track.id.equals(session.loopSongId);
        String format = track.fileName.endsWith(".wav") ? "WAV" : "MP3";
        View item = compactMediaRow(track.title, format, null, active,
            !active && session.canRemoveLibraryContent(), () -> session.chooseIntermission(track),
            session.canRemoveLibraryContent(), () -> {
              if (track.id.equals(session.loopSongId) || Arrays.asList(session.customPads).contains(track.id)) {
                showRemovalResult(getString(R.string.intermission_remove_blocked));
                return;
              }
              confirmImportedRemoval(getString(R.string.media_library_remove_confirm, track.title),
                  getString(R.string.library_controls_song_remove_details),
                  () -> session.deleteSong(track.id, this::showRemovalResult));
            });
        item.setTag("kanta.intermissions.track." + track.id);
        return item;
      });
    }

  }

  private void partyPadsPage() {
    LinearLayout content = MenuSurface.section(body, getString(R.string.menu_sound_mix), R.drawable.kanta_submenu_volume);
    partyPadsPage = content;
    content.setTag("kanta.party-pads");
    partyPadVolume = volumeControl(content, getString(R.string.intermission_pad_volume),
        session.party.sfxVolume, value -> session.partyVolumes(value, session.party.loopVolume));
    content.addView(menuAction(getString(R.string.intermission_stop_all),
        R.drawable.kanta_submenu_stop, session::stopParty));
    LinearLayout clips = column();
    content.addView(clips);
    for (int i = 0; i < PartySounds.TITLES.size(); i++) {
      final int slot = i;
      String title = PartySounds.TITLES.get(slot);
      LinearLayout actions = row();
      Button trigger = partyPadTrigger(slot, "kanta.party-pad.trigger.");
      actions.addView(trigger, new LinearLayout.LayoutParams(0, -2, 1));
      Button assign = compactMediaButton(getString(R.string.intermission_assign_clip),
          getString(R.string.intermission_assign_pad, title),
          () -> chooseAudio(title, song -> session.assignPad(slot, song)));
      assign.setTag("kanta.party-pad.assign." + slot);
      actions.addView(assign);
      Button reset = compactMediaButton(getString(R.string.intermission_reset_pad),
          getString(R.string.intermission_reset_pad_named, title), () -> session.resetPad(slot), R.drawable.kanta_submenu_reset);
      reset.setTag("kanta.party-pad.reset." + slot);
      actions.addView(reset);
      clips.addView(actions);
      partyPadTriggers.add(trigger);
      partyPadAssign.add(assign);
      partyPadReset.add(reset);
    }
    clips.addView(MenuSurface.detail(this, getString(R.string.intermission_pads_guide)));
    refreshPartyPadControls();
  }

  private Button partyPadTrigger(int slot, String tagPrefix) {
    Button trigger = menuButton(PartySounds.TITLES.get(slot), () -> session.triggerPad(slot));
    trigger.setTag(tagPrefix + slot);
    return trigger;
  }

  private void refreshPartyPadTrigger(Button trigger, int slot, boolean showCustomLabel) {
    String title = PartySounds.TITLES.get(slot);
    String label = session.customPads[slot].isEmpty() ? title
        : getString(R.string.intermission_pad_custom, title);
    if (!TextUtils.equals(label, trigger.getContentDescription())) trigger.setContentDescription(label);
    if (showCustomLabel && !TextUtils.equals(label, trigger.getText())) trigger.setText(label);
    trigger.setEnabled(session.party.ready(slot));
  }

  private void refreshPartyPadControls() {
    if (partyPadVolume != null) {
      partyPadVolume.setProgress(Math.round(session.party.sfxVolume * 100));
    }
    for (int slot = 0; slot < partyPadTriggers.size(); slot++) {
      refreshPartyPadTrigger(partyPadTriggers.get(slot), slot, true);
      partyPadAssign.get(slot).setEnabled(!session.assigningPad);
      partyPadReset.get(slot).setVisibility(session.customPads[slot].isEmpty()
          ? View.GONE : View.VISIBLE);
    }
  }

  private void showIntermissionGuide() {
    ScrollView scroll = new ScrollView(this);
    LinearLayout content = column();
    content.setPadding(dp(16), 0, dp(16), dp(8));
    scroll.addView(content);
    TextView live = text("", 15, ACCENT);
    live.setTag("kanta.intermissions.guide.status");
    content.addView(live);
    content.addView(sectionHeader(getString(R.string.intermission_music_heading)));
    content.addView(text(getString(R.string.intermission_music_guide), 15, KantaTheme.TEXT_PRIMARY));
    content.addView(sectionHeader(getString(R.string.intermission_removal_heading)));
    content.addView(text(getString(R.string.intermission_remove_guide), 15, KantaTheme.TEXT_PRIMARY));
    intermissionGuideState = live;
    refreshIntermissionState();
    AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.intermission_guide_button)
        .setView(scroll).setPositiveButton(R.string.button_close, null).create();
    intermissionGuide = dialog;
    dialog.setOnDismissListener(ignored -> {
      if (intermissionGuide == dialog) {
        intermissionGuide = null;
        intermissionGuideState = null;
      }
    });
    dialog.show();
    constrainDialog(dialog);
    scroll.post(() -> {
      int limit = Math.round(getResources().getDisplayMetrics().heightPixels * .60f);
      if (scroll.getHeight() > limit) {
        ViewGroup.LayoutParams params = scroll.getLayoutParams();
        params.height = limit;
        scroll.setLayoutParams(params);
      }
    });
  }

  private void refreshIntermissionState() {
    if (intermissionState == null && intermissionGuideState == null) return;
    String title = getString(R.string.intermission_bundled);
    if (!session.loopSongId.isEmpty()) {
      for (Song song : session.songs) {
        if (song.id.equals(session.loopSongId) && SessionController.isAudio(song)) {
          title = song.title;
          break;
        }
      }
    }
    int state = !session.loopEnabled ? R.string.intermission_music_off
        : session.party.loopPlaying() ? R.string.intermission_music_playing
        : !session.canRestorePlaybackSettings() ? R.string.intermission_music_waiting
        : session.auxiliaryInterrupted ? R.string.intermission_music_interrupted
        : R.string.intermission_music_idle;
    String status = getString(R.string.intermission_active_track, title) + "\n" + getString(state);
    if (intermissionState != null && !TextUtils.equals(intermissionState.getText(), status)) {
      intermissionState.setText(status);
    }
    if (intermissionGuideState != null && !TextUtils.equals(intermissionGuideState.getText(), status)) {
      intermissionGuideState.setText(status);
    }
  }

  private void chooseFontFolder() {
    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), LYRIC_FONT_FOLDER);
  }

  private void chooseLyricFont() {
    ++fontPickerGeneration;
    ph.kanta.reborn.font.FontStore fonts = ph.kanta.reborn.font.FontStore.getInstance(this);
    showLyricFontOptions(fonts.bundledFonts(), true);
  }

  private void chooseCustomLyricFont() {
    int pickerRequest = ++fontPickerGeneration;
    Uri root = new ContentRoot(this).getContentRootUri();
    if (root == null) { chooseFontFolder(); return; }
    Toast.makeText(this, "Looking for fonts…", Toast.LENGTH_SHORT).show();
    ph.kanta.reborn.font.FontStore fonts = ph.kanta.reborn.font.FontStore.getInstance(this);
    fonts.discover(root, entries -> {
      if (pickerRequest != fontPickerGeneration || isFinishing() || isDestroyed()
          || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return;
      showLyricFontOptions(entries, false);
    });
  }

  private void showLyricFontOptions(
      java.util.List<ph.kanta.reborn.font.FontStore.FontEntry> entries, boolean bundled) {
    String saved = new SettingsStore(this).getLyricFont();
    String[] labels = new String[entries.size() + 1];
    labels[0] = (saved.isEmpty() ? "✓ " : "") + "KANTA default";
    for (int i = 0; i < entries.size(); i++) {
      var entry = entries.get(i);
      labels[i + 1] = (entry.uri.toString().equals(saved) ? "✓ " : "") + entry.name;
    }
    constrainDialog(new AlertDialog.Builder(this).setTitle(bundled ? "Lyric font" : "Custom lyric fonts")
        .setItems(labels, (dialog, index) -> previewLyricFont(
            index == 0 ? null : entries.get(index - 1).uri,
            index == 0 ? "KANTA default" : entries.get(index - 1).name))
        .setNeutralButton(bundled ? "Custom fonts" : "Choose folder",
            (d, w) -> { if (bundled) chooseCustomLyricFont(); else chooseFontFolder(); })
        .setNegativeButton("Cancel", null).show());
  }

  private void previewLyricFont(Uri uri, String name) {
    var fonts = ph.kanta.reborn.font.FontStore.getInstance(this);
    TextView sample = text("Sing more, together.", 32, KantaTheme.TEXT_PRIMARY);
    sample.setGravity(Gravity.CENTER);
    sample.setPadding(dp(20), dp(24), dp(20), dp(24));
    sample.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    AlertDialog preview = new AlertDialog.Builder(this).setTitle(name).setView(sample)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Use font", (d, w) -> fonts.select(uri, success -> {
          if (isFinishing() || isDestroyed()) return;
          Toast.makeText(this, success ? "Lyric font updated."
              : "This font could not be loaded. Using the default font.", Toast.LENGTH_LONG).show();
        })).show();
    constrainDialog(preview);
    if (uri != null) {
      preview.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
      fonts.preview(uri, face -> {
        if (!preview.isShowing()) return;
        if (face == null) sample.setText(R.string.lyric_font_load_failed);
        else sample.setTypeface(face);
        preview.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(face != null);
      });
    }
  }

  private void importLegacyFolder(Uri tree) {
    if (!session.canRemoveLibraryContent()) return;
    session.io.execute(() -> {
      Uri[] pair;
      try {
        pair = LegacyFolderPicker.find(getContentResolver(), tree);
      } catch (IOException | RuntimeException failure) {
        pair = null;
      }
      Uri[] selected = pair;
      handler.post(() -> {
        if (isFinishing() || isDestroyed() || !session.foreground) return;
        if (selected == null) {
          Toast.makeText(this, R.string.legacy_folder_invalid, Toast.LENGTH_LONG).show();
        } else {
          startLegacyPair(selected[0], selected[1]);
        }
      });
    });
  }

  private void pickLegacyIndex() {
    pendingLegacyIndex = null;
    constrainDialog(new AlertDialog.Builder(this)
        .setTitle("Legacy import — step 1 of 2")
        .setMessage("Select the IDX file first: it is the smaller of the two files. The larger BLOB file is requested next.")
        .setPositiveButton("Choose IDX", (dialog, which) -> launchLegacyIndexPicker())
        .setNegativeButton("Cancel", null)
        .show());
  }

  private void launchLegacyIndexPicker() {
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

  private void startLegacyPair(Uri index, Uri blob) {
    if (!BuildConfig.PRIVATE_LEGACY_IMPORT) {
      requestLegacyKey(index, blob);
      return;
    }
    ph.kanta.reborn.core.legacy.LegacyImportKey credentials;
    try (java.io.InputStream asset = getAssets().open("legacy-import-key.bin")) {
      credentials = ph.kanta.reborn.core.legacy.LegacyImportKey.fromPrivateAsset(asset);
    } catch (IOException unavailable) {
      Toast.makeText(this, "This private build cannot unlock legacy files. Rebuild it with the correct key file.",
          Toast.LENGTH_LONG).show();
      return;
    }
    submitLegacyPair(index, blob, credentials);
  }

  private void submitLegacyPair(Uri index, Uri blob,
      ph.kanta.reborn.core.legacy.LegacyImportKey credentials) {
    if (session.importLegacyPair(index, blob, credentials)) {
      showImportProgress();
      attachImportProgressListener();
    } else {
      Toast.makeText(this, "Import could not start. Please try again.", Toast.LENGTH_LONG).show();
    }
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
    constrainDialog(dialog);
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
      submitLegacyPair(index, blob, credentials);
    });
  }

  private void chooseMusic(boolean audioOnly) {
    if (!session.canRemoveLibraryContent()) return;
    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(audioOnly ? "audio/*" : "*/*")
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    startActivityForResult(picker, MUSIC);
  }

  private void chooseAudio(String title, java.util.function.Consumer<Song> selected) {
    List<Song> audio = new ArrayList<>();
    for (Song song : session.songs) if (SessionController.isAudio(song)) audio.add(song);
    if (audio.isEmpty()) {
      constrainDialog(new AlertDialog.Builder(this)
          .setTitle(title)
          .setMessage(R.string.intermission_pad_add_first)
          .setPositiveButton("OK", null)
          .show());
      return;
    }
    String[] names = audio.stream().map(song -> song.title).toArray(String[]::new);
    constrainDialog(new AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(names, (dialog, index) -> selected.accept(audio.get(index)))
        .setNegativeButton("Cancel", null)
        .show());
  }

  private SeekBar volumeControl(
      LinearLayout target, String title, float initial, java.util.function.Consumer<Float> apply) {
    target.addView(MenuSurface.label(this, title, 16, Color.WHITE, false));
    SeekBar bar = ph.kanta.reborn.ui.KantaTheme.adjustmentSlider(this);
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
    target.addView(bar);
    return bar;
  }

  void closeFullscreen() {
    if (fullscreen != null) fullscreen.dismiss();
  }

  private String stageTime(long milliseconds) {
    long seconds = Math.max(0, milliseconds / 1000);
    return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
  }

  /** Refreshes only the stage's live projection; transport and timing stay in SessionController. */
  private String playbackTimeLabel() {
    long duration = session.duration();
    return getString(R.string.playback_times, stageTime(session.position()),
        duration > 0 ? stageTime(duration) : "--:--");
  }

  private TextView makeStageAdjustments(String tag) {
    TextView label = text("", 11, KantaTheme.TEXT_SECONDARY);
    label.setTag(tag);
    label.setGravity(Gravity.CENTER);
    label.setPadding(dp(6), dp(2), dp(6), dp(2));
    refreshStageAdjustments(label);
    return label;
  }

  private void refreshStageAdjustments(TextView label) {
    if (label == null) return;
    int pitch = session.effectivePitch();
    String signedPitch = pitch > 0 ? "+" + pitch : Integer.toString(pitch);
    String values = getString(R.string.stage_adjustment_readout, signedPitch, session.effectiveTempo());
    if (!values.contentEquals(label.getText())) label.setText(values);
  }

  private void refreshStagePlaybackTime() {
    refreshStageAdjustments(stageAdjustments);
    if (stagePlaybackTime == null) return;
    stagePlaybackTime.setVisibility(session.current == null ? View.GONE : View.VISIBLE);
    if (session.current != null) stagePlaybackTime.setText(playbackTimeLabel());
  }

  private void refreshFullscreenControls() {
    if (fullscreen == null || !fullscreen.isShowing()) return;
    if (fullscreenTitle != null)
      fullscreenTitle.setText(session.current == null ? "Ready for your next song" : session.current.title);
    if (fullscreenArtist != null) {
      String artist = session.current == null ? "KANTA" : session.current.artist;
      String singerName = session.current == null ? "" : session.singer;
      String credit = artist == null || artist.isEmpty() ? singerName
          : singerName.isEmpty() ? artist : artist + " · " + singerName;
      fullscreenArtist.setText(credit);
      fullscreenArtist.setVisibility(credit.isEmpty() ? View.GONE : View.VISIBLE);
    }
    if (fullscreenTime != null) fullscreenTime.setText(playbackTimeLabel());
    refreshStageAdjustments(fullscreenAdjustments);
    if (fullscreenQueue != null)
      fullscreenQueue.setText(fullscreenQueue.getContext().getString(ph.kanta.reborn.R.string.queue_count, session.queue.snapshot().size()));
    if (fullscreenPlayButton != null) {
      boolean enabled = session.ready && session.foreground && !session.loading;
      String label = getString(session.playing ? R.string.stage_pause : R.string.stage_play);
      if (!TextUtils.equals(label, fullscreenPlayButton.getText())) fullscreenPlayButton.setText(label);
      String description = getString(session.playing ? R.string.stage_pause_playback
          : session.player != null ? R.string.stage_resume_playback : R.string.stage_play);
      if (!TextUtils.equals(description, fullscreenPlayButton.getContentDescription()))
        fullscreenPlayButton.setContentDescription(description);
      fullscreenPlayButton.setEnabled(enabled);
      fullscreenNextButton.setEnabled(enabled);
      fullscreenRestartButton.setEnabled(enabled && session.player != null);
    }
  }

  void fullscreen() {
    if (fullscreen != null && fullscreen.isShowing()) return;
    setStageControlsExpanded(embeddedControls, false);
    session.cancelCountdown();
    androidx.activity.ComponentDialog stageDialog = new androidx.activity.ComponentDialog(
        this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
    fullscreen = stageDialog;
    LinearLayout stageLayout = column();
    stageLayout.setBackgroundColor(KantaTheme.BG_MAIN);
    fullscreen.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    FrameLayout stageFrame = new FrameLayout(this);
    stageFrame.setTag("kanta.fullscreen.stage");
    UpNextTicker fullscreenUpNext = new UpNextTicker(this);
    fullscreenStage = new StageSurface(this, session, fullscreenUpNext);
    fullscreenStage.setFullscreenMode(true);
    stageFrame.addView(fullscreenStage, new FrameLayout.LayoutParams(-1, -1));

    LinearLayout overlay = column();
    overlay.setPadding(dp(16), dp(12), dp(16), dp(16));
    overlay.setFocusable(false);
    LinearLayout header = row();
    Button close = transportButton(R.string.stage_controls_close, this::closeFullscreen);
    close.setContentDescription(getString(R.string.stage_exit_fullscreen));
    close.setTag("kanta.fullscreen.close");
    MenuSurface.setLeadingIcon(close, R.drawable.kanta_stage_action_exit_fullscreen);
    header.addView(close);
    fullscreenAdjustments = makeStageAdjustments("kanta.fullscreen.adjustments");
    fullscreenAdjustments.setShadowLayer(dp(3), 0, dp(1), 0xFF000000);
    header.addView(fullscreenAdjustments, new LinearLayout.LayoutParams(0, -2, 1));
    header.setGravity(Gravity.CENTER_VERTICAL);
    fullscreenControls = makeStageControls(true);
    Button controls = stageChromeButton(R.string.stage_controls, R.drawable.kanta_stage_action_controls,
        "kanta.fullscreen.controls", null);
    bindStageControls(fullscreenControls, controls);
    StageControlsPanel inlineControls = fullscreenControls;
    stageDialog.getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() {
        if (controlsOpen(inlineControls)) setStageControlsExpanded(inlineControls, false);
        else closeFullscreen();
      }
    });
    header.addView(controls);
    overlay.addView(header);

    View spacer = new View(this);
    overlay.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));

    LinearLayout details = column();
    details.setTag("kanta.fullscreen.dock");
    details.setPadding(dp(8), 0, dp(8), 0);

    LinearLayout summary = column();
    LinearLayout identity = column();
    LinearLayout timeline = row();
    LinearLayout status = row();
    fullscreenTitle = text("Ready for your next song", 22, KantaTheme.TEXT_PRIMARY);
    fullscreenTitle.setTypeface(null, android.graphics.Typeface.BOLD);
    fullscreenTitle.setTag("kanta.fullscreen.title");
    fullscreenTitle.setSingleLine(true);
    fullscreenTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
    fullscreenArtist = text("KANTA", 14, KantaTheme.TEXT_SECONDARY);
    fullscreenArtist.setTag("kanta.fullscreen.artist");
    fullscreenArtist.setSingleLine(true);
    fullscreenArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
    fullscreenTitle.setShadowLayer(dp(3), 0, dp(1), 0xFF000000);
    fullscreenArtist.setShadowLayer(dp(3), 0, dp(1), 0xFF000000);
    identity.addView(fullscreenTitle);
    identity.addView(fullscreenArtist);
    summary.addView(identity);

    timeline.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    fullscreenTime = text("0:00 / --:--", 12, KantaTheme.TEXT_SECONDARY);
    fullscreenTime.setTag("kanta.fullscreen.time");
    fullscreenTime.setShadowLayer(dp(3), 0, dp(1), 0xFF000000);
    timeline.addView(fullscreenTime);
    summary.addView(timeline);

    LinearLayout transport = row();
    fullscreenQueue = text("Queue (0)", 12, KantaTheme.TEXT_SECONDARY);
    fullscreenQueue.setTag("kanta.fullscreen.queue");
    fullscreenQueue.setSingleLine(true);
    fullscreenQueue.setShadowLayer(dp(3), 0, dp(1), 0xFF000000);
    status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    status.addView(fullscreenQueue);
    fullscreenRestartButton = transportButton(R.string.stage_restart, session::restartCurrent);
    fullscreenRestartButton.setTag("kanta.fullscreen.restart");
    fullscreenPlayButton = transportButton(R.string.stage_play, session::toggle);
    fullscreenPlayButton.setTag("kanta.fullscreen.play-pause");
    fullscreenNextButton = transportButton(R.string.stage_next, session::next);
    fullscreenNextButton.setTag("kanta.fullscreen.next");
    fullscreenNextButton.setContentDescription(getString(R.string.stage_next_song));
    addWeighted(transport, fullscreenRestartButton);
    addWeighted(transport, fullscreenPlayButton);
    addWeighted(transport, fullscreenNextButton);

    details.addView(status);
    details.addView(summary);
    details.addView(transport);
    details.addView(fullscreenUpNext, new LinearLayout.LayoutParams(-1, -2));
    stageFrame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      boolean wide = r - l > b - t && r - l >= dp(520)
          * Math.max(1f, getResources().getConfiguration().fontScale);
      if ((summary.getOrientation() == LinearLayout.HORIZONTAL) != wide) {
        summary.removeAllViews();
        summary.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        if (wide) {
          addWeighted(summary, identity);
          LinearLayout.LayoutParams timelineParams = new LinearLayout.LayoutParams(0, -2, 1);
          timelineParams.setMarginStart(dp(12));
          summary.addView(timeline, timelineParams);
        } else {
          summary.addView(identity);
          summary.addView(timeline);
        }
      }
    });
    StageSurface dialogStage = fullscreenStage;
    View.OnLayoutChangeListener updateStageInsets = (v, l, t, r, b, ol, ot, or, ob) ->
        dialogStage.setOverlayInsets(header.getBottom() + overlay.getTop(),
            stageFrame.getHeight() - details.getTop() - overlay.getTop());
    header.addOnLayoutChangeListener(updateStageInsets);
    details.addOnLayoutChangeListener(updateStageInsets);
    overlay.addView(details);
    stageFrame.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    fullscreenStage.refresh();
    stageLayout.addView(stageFrame, new LinearLayout.LayoutParams(-1, 0, 1));
    inlineControls.limitTo(stageLayout);
    stageLayout.addView(inlineControls, new LinearLayout.LayoutParams(-1, -2));
    fullscreen.setContentView(stageLayout);
    fullscreen.setOnDismissListener(
        d -> {
          if (fullscreen != d) return;
          fullscreenControls = null;
          fullscreen = null;
          fullscreenStage = null;
          fullscreenTitle = null;
          fullscreenAdjustments = null;
          fullscreenArtist = null;
          fullscreenTime = null;
          fullscreenQueue = null;
          fullscreenPlayButton = fullscreenNextButton = fullscreenRestartButton = null;
          session.cancelCountdown();
          if (!isFinishing() && !isDestroyed()) {
            updatePrimaryStageVisibility();
            camera.moveTo(stagePresenter);
          }
        });
    if (stagePresenter != null) stagePresenter.setVisualActive(false);
    fullscreen.show();
    if (fullscreen.getWindow() != null) fullscreen.getWindow().setLayout(-1, -1);
    camera.moveTo(fullscreenStage);
    refreshFullscreenControls();
  }

  private Button transportButton(int label, Runnable action) {
    Button control = KantaTheme.secondaryButton(this, getString(label),
        action == null ? null : v -> action.run());
    control.setAllCaps(false);
    control.setTextSize(15);
    control.setMinHeight(dp(48));
    control.setMinWidth(0);
    control.setMinimumWidth(0);
    control.setPadding(dp(2), 0, dp(2), 0);
    control.setTextColor(KantaTheme.TEXT_PRIMARY);
    control.setShadowLayer(dp(4), 0, dp(1), 0xFF000000);
    control.setBackground(androidx.core.content.ContextCompat.getDrawable(this,
        android.R.drawable.list_selector_background));
    return control;
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
    constrainDialog(new AlertDialog.Builder(this)
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
        .show());
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

  private static List<Uri> pickedDocuments(Intent data, int limit) {
    Set<Uri> found = new LinkedHashSet<>();
    ClipData clip = data.getClipData();
    if (clip != null) {
      for (int i = 0; i < Math.min(clip.getItemCount(), limit); i++) {
        Uri uri = clip.getItemAt(i).getUri();
        if (uri != null) found.add(uri);
      }
    }
    if (data.getData() != null && found.size() < limit) found.add(data.getData());
    return new ArrayList<>(found);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == IMPORT_PDF) {
      pdfSongbooks.acceptResult(result, data);
      return;
    }
    if (result != RESULT_OK || data == null) {
      if (request == LEGACY_INDEX || request == LEGACY_BLOB) pendingLegacyIndex = null;
      if (request == EXPORT) {
        exportBytes = null;
        exportCode = null;
      }
      return;
    }
    if (request == PHOTO || request == VIDEO) {
      if (!session.ready || !session.canChangeStageMedia()) return;
      List<Uri> sources = pickedDocuments(data, StageMediaStore.MAX_IMPORTS);
      if (!sources.isEmpty()) session.importStageBatch(sources, request == VIDEO);
      return;
    }
    if (request == LEGACY_FOLDER && data.getData() != null) {
      importLegacyFolder(data.getData());
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
      startLegacyPair(index, data.getData());
      return;
    }
    if (request == MUSIC) {
      List<Uri> uris = pickedDocuments(data, 1000);
      if (uris.isEmpty() || !session.canRemoveLibraryContent()) return;
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
        chooseCustomLyricFont();
      } catch (SecurityException | IllegalArgumentException unavailable) {
        Toast.makeText(this, "This folder could not be kept. Choose a folder with read access.",
            Toast.LENGTH_LONG).show();
      }
    }
    else if (request == VOICE) {
      ArrayList<String> values = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if (values != null && !values.isEmpty()) {
        homeEntry = values.get(0);
        openStageSearch();
        if (homeScreen != null) homeScreen.setSearchText(homeEntry);
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
                      constrainDialog(new AlertDialog.Builder(this)
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
                          .show());
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

  /** Shows the import progress dialog. Imports run off the main thread and are cancellable:
   * the dialog Cancel action and Presets "Cancel import" both signal the running batch. */
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

  private void libraryNoticesPage() {
    LinearLayout notices = MenuSurface.section(body, getString(R.string.library_notices));
    int[] labels = {R.string.library_runtime_notices, R.string.library_soundfont_license,
        R.string.library_privacy, R.string.library_music_rights};
    String[] assets = {"runtime-notices.txt", "tinysoundfont-license.txt", "privacy.txt", "legal.txt"};
    for (int i = 0; i < labels.length; i++) {
      String asset = assets[i];
      String title = getString(labels[i]);
      notices.addView(menuAction(title, R.drawable.kanta_submenu_lyrics, () -> policy(asset, title)));
    }
  }

  private void policy(String asset, String title) {
    try (InputStream in = getAssets().open(asset)) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
      ScrollView scroll = new ScrollView(this);
      LinearLayout page = column();
      page.setBackground(MenuSurface.background());
      page.setPadding(0, dp(12), 0, 0);
      LinearLayout document = MenuSurface.section(page, title);
      TextView content = MenuSurface.detail(this, bytes.toString("UTF-8"));
      content.setTextIsSelectable(true);
      document.addView(content);
      scroll.addView(page);
      constrainDialog(new AlertDialog.Builder(this)
          .setView(scroll)
          .setPositiveButton(R.string.button_close, null)
          .show());
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
    if (BuildConfig.PRIVATE_LEGACY_IMPORT) {
      constrainDialog(new AlertDialog.Builder(this).setTitle("Private build")
          .setMessage("Install a newer private build to update this app.")
          .setPositiveButton("OK", null).show());
      return;
    }
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

  private void diagnostics(boolean inMenu) {
    StringBuilder report = new StringBuilder();
    report.append("KANTA Karaoke · diagnostics\n");
    report.append("Report tag   : KANTA-")
        .append(java.util.UUID.randomUUID().toString().substring(0, 8)).append('\n');
    report.append("Captured     : ").append(java.time.Instant.now()).append("\n\n");
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
    report.append("song loaded  : ").append(session.current != null).append('\n');
    report.append("position     : ").append(session.position()).append(" ms\n");

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

    report.append(session.diagnostics.snapshot());

    final String reportText = report.toString();
    TextView content = text(getString(R.string.manual_diagnostics_details) + "\n\n"
        + reportText, 12, Color.WHITE);
    content.setPadding(dp(20), dp(12), dp(20), dp(12));
    content.setTypeface(android.graphics.Typeface.MONOSPACE);
    content.setTextIsSelectable(true);
    ScrollView scroll = new ScrollView(this);
    if (inMenu) {
      LinearLayout page = column();
      page.setBackground(MenuSurface.background());
      page.setPadding(0, dp(12), 0, 0);
      LinearLayout automatic = MenuSurface.section(page, getString(R.string.automatic_diagnostics));
      AutomaticDiagnostics reporting = ((KantaApplication) getApplication()).automaticDiagnostics;
      if (reporting.available()) {
        automatic.addView(MenuSurface.detail(this, getString(R.string.automatic_diagnostics_details)));
        android.widget.Switch consent = new android.widget.Switch(this);
        consent.setText(R.string.automatic_diagnostics);
        consent.setTextColor(Color.WHITE);
        consent.setMinHeight(dp(48));
        consent.setChecked(reporting.enabled());
        consent.setOnCheckedChangeListener((button, checked) -> {
          if (reporting.setEnabled(checked) != checked) {
            consent.setChecked(false);
            Toast.makeText(this, R.string.automatic_diagnostics_save_failed, Toast.LENGTH_SHORT).show();
          }
        });
        automatic.addView(consent);
      } else {
        automatic.addView(MenuSurface.detail(this,
            getString(R.string.automatic_diagnostics_unavailable)));
      }
      LinearLayout card = MenuSurface.section(page, getString(R.string.menu_issue_report));
      card.addView(content);
      scroll.addView(page);
    } else {
      scroll.addView(content);
    }
    AlertDialog.Builder reportDialog = new AlertDialog.Builder(this)
        .setView(scroll)
        .setPositiveButton(
            "Copy report",
            (dialog, which) ->
                ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("KANTA diagnostics", reportText)))
        .setNeutralButton("Share", (d, w) -> {
          Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
              .putExtra(Intent.EXTRA_TEXT, reportText)
              .putExtra(Intent.EXTRA_SUBJECT, "KANTA diagnostics");
          startActivity(Intent.createChooser(i, "Share diagnostics"));
        })
        .setNegativeButton(R.string.button_close, null);
    if (!inMenu) reportDialog.setTitle(R.string.menu_issue_report);
    AlertDialog shown = reportDialog.show();
    if (inMenu) constrainDialog(shown);
  }

  private void helpPage() {
    addHelpSection(R.string.menu_help_singing, R.string.menu_help_singing_details);
    addHelpSection(R.string.menu_help_voice, R.string.menu_help_voice_details);
    addHelpSection(R.string.menu_help_remote, R.string.menu_help_remote_details);
  }

  private void lyricFontPage() {
    LyricFontPage page = new LyricFontPage(this, MenuSurface.this);
    pageScroll.removeAllViews();
    pageScroll.addView(page);
    tab = "LyricFont";
  }

  private void addHelpSection(int title, int detail) {
    MenuSurface.section(body, getString(title)).addView(MenuSurface.detail(this, getString(detail)));
  }
}
