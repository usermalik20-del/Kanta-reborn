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
    target.addView(button("Settings", () -> {
      dismissAccessory();
      tab = "Control";
      render();
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

  /**
   * Kanta-Connect pairing: start hosting, then present the scan and type routes together.
   *
   * The QR image and the printed address carry the same information, so a user can either
   * scan or type. Only the address and the pairing code are individually copyable, never a
   * combined blob: a single combined string in the clipboard is the easiest way for the
   * single-session code to leak.
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
    LinearLayout content = column();
    int pad = dp(20);
    content.setPadding(pad, pad, pad, pad);

    TextView heading = text("Kanta-Connect", 20, GOLD);
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    content.addView(heading);
    content.addView(text(info.endpoint(), 15, Color.WHITE));

    QrEncoder.Symbol symbol = null;
    try {
      symbol = QrEncoder.encode(info.link());
    } catch (RuntimeException tooLong) {
      symbol = null;
    }
    if (symbol != null) {
      QrCodeView qr = new QrCodeView(this);
      qr.setSymbol(symbol);
      LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(232), dp(232));
      qrParams.gravity = Gravity.CENTER_HORIZONTAL;
      qrParams.topMargin = dp(10);
      qrParams.bottomMargin = dp(10);
      content.addView(qr, qrParams);
    }

    for (String step : info.instructions()) {
      content.addView(text("\u2022 " + step, 13, 0xFFAFB8D0));
    }
    content.addView(
        text("Scan the code with the Kanta-Connect phone app, or type the address and code.", 12, 0xFFAFB8D0));

    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);
    new AlertDialog.Builder(this)
        .setView(scroll)
        .setPositiveButton(
            "Copy code",
            (dialog, which) -> copyToClipboard("Kanta-Connect code", info.code))
        .setNeutralButton(
            "Copy address",
            (dialog, which) -> copyToClipboard("Kanta-Connect address", info.endpoint()))
        .setNegativeButton("Done", null)
        .show();
    render();
  }

  /** Copies a value to the clipboard without ever logging it. */
  private void copyToClipboard(String label, String value) {
    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
        .setPrimaryClip(ClipData.newPlainText(label, value));
    Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
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

  private void renderPanel() {
    microphoneReading = null;
    monitorReading = null;
    scoreReading = null;
    body.addView(text(session.message, 13, CYAN));
    body.addView(button("YouTube video (online)", this::openYouTubeVideo));

    switch (tab) {
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
            session.microphone.enabled() ? "Turn off microphone" : "Turn on microphone",
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
            session.monitor.enabled() ? "Turn off monitoring" : "Wired / USB monitoring",
            this::toggleMonitoring));
    monitorReading = text("", 13, CYAN);
    body.addView(monitorReading);
    refreshMicrophoneReading();
    body.addView(
        text(
            "Optional voice output for connected wired or USB equipment. Use headphones, start"
                + " quietly and check your equipment volume. Speaker and Bluetooth monitoring are"
                + " blocked when reported by Android. Older Android reports only one output. This"
                + " experimental feature still needs device testing for sound and delay.",
            12,
            Color.WHITE));
    LinearLayout effects = row();
    for (VocalMonitorEffects.Mode value : VocalMonitorEffects.Mode.values()) {
      String title =
          value.name().substring(0, 1)
              + value.name().substring(1).toLowerCase(java.util.Locale.ROOT);
      Button option =
          button(
              title,
              () -> {
                session.monitor.mode(value);
                session.publish();
              });
      option.setEnabled(session.monitor.enabled());
      if (session.monitor.mode() == value) option.setTextColor(GOLD);
      addWeighted(effects, option);
    }
    body.addView(effects);
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
            if (user) timing.setText("Score timing offset: " + (value * 10 - 500) + " ms");
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
            GOLD);
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
    body.addView(text("Sound & pace", 18, GOLD));
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
    body.addView(text("Find your next song", 20, Color.WHITE));
    queryInput = new EditText(this);
    queryInput.setSingleLine();
    queryInput.setTextColor(Color.WHITE);
    queryInput.setTextSize(18);
    queryInput.setHintTextColor(0xFFAFB8D0);
    queryInput.setHint("Song code, title or artist");
    queryInput.setText(query);
    body.addView(queryInput);
    queryInput.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            query = s.toString();
            resultLimit = 50;
            search();
          }

          public void afterTextChanged(Editable e) {}
        });
    EditText singerInput = new EditText(this);
    singerInput.setSingleLine();
    singerInput.setTextColor(Color.WHITE);
    singerInput.setHintTextColor(0xFFAFB8D0);
    singerInput.setHint("Singer name (optional)");
    singerInput.setText(singer);
    body.addView(singerInput);
    singerInput.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            singer = s.toString();
          }

          public void afterTextChanged(Editable e) {}
        });
    LinearLayout tools = row();
    addWeighted(tools, button("Voice search", this::voiceSearch));
    addWeighted(
        tools,
        button(
            "Cancel",
            () -> {
              query = "";
              queryInput.setText("");
            }));
    body.addView(tools);
    for (String[] digits :
        new String[][] {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"RES", "0", "⌫"}}) {
      LinearLayout keys = row();
      for (String digit : digits)
        addWeighted(
            keys,
            button(
                digit,
                () -> {
                  if (digit.equals("RES")) {
                    List<Song> matches = new ArrayList<>();
                    for (Song s : session.songs) if (s.matches(query)) matches.add(s);
                    if (matches.size() == 1) session.reserve(matches.get(0), singer);
                    else
                      new AlertDialog.Builder(this)
                          .setMessage("Choose Reserve beside the song you want below.")
                          .setPositiveButton("OK", null)
                          .show();
                  } else if (digit.equals("⌫")) {
                    if (!query.isEmpty())
                      queryInput.setText(query.substring(0, query.length() - 1));
                  } else queryInput.setText(query + digit);
                }));
      body.addView(keys);
    }
    results = column();
    body.addView(results);
    search();
    queue();
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
                target.addView(text(count + " matching songs", 13, GOLD));
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
    body.addView(text("Up Next · " + session.queue.snapshot().size(), 20, GOLD));
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
    body.addView(text("My Set", 22, Color.WHITE));
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
    if (session.importing) body.addView(button("Cancel import", session::cancelImports));
    body.addView(text("SoundFont: " + session.soundFontName, 14, CYAN));
    body.addView(
        text(
            "SoundFonts up to 32 MB. MIDI/KAR up to 16 MB and six hours. Original files stay"
                + " unchanged.",
            13,
            0xFFAFB8D0));

    body.addView(button("Save Up Next as a playlist", () -> nameSet(false)));
    body.addView(button("Save played songs as Favorites", () -> nameSet(true)));
    body.addView(button("Export private Songbook", () -> export(true)));
    body.addView(button("Export song list (CSV)", () -> export(false)));
    body.addView(text("Saved sets", 20, GOLD));
    for (LibraryRepository.SavedSet set : session.savedSets)
      body.addView(
          button(set.name + " · " + set.songs.size() + " songs", () -> session.loadSet(set)));
    body.addView(text("Original starter songs", 20, GOLD));
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
          "Connect wired or USB headphones or an audio interface. Speaker and Bluetooth monitoring"
              + " are unavailable.";
      session.publish();
      return;
    }
    String[] labels = new String[outputs.size()];
    for (int i = 0; i < labels.length; i++) {
      String label =
          outputs.get(i).getProductName().toString().replaceAll("[\\p{Cntrl}]", " ").trim();
      labels[i] =
          label.isEmpty() ? "Wired / USB output" : label.substring(0, Math.min(80, label.length()));
    }
    new AlertDialog.Builder(this)
        .setTitle("Select wired / USB output")
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

  private void scenic() {
    body.addView(text("Choose your stage", 22, Color.WHITE));
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
    body.addView(text("Loop & party sounds", 22, GOLD));
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
    body.addView(text("Original synthesized party sounds", 18, GOLD));
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
    body.addView(text(title, 16, GOLD));
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
    body.addView(text("Realm", 22, GOLD));
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
    body.addView(text("Lyric size", 18, GOLD));
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

  void fullscreen() {
    if (fullscreen != null && fullscreen.isShowing()) return;
    fullscreen = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
    LinearLayout content = column();
    content.setBackgroundColor(Color.BLACK);
    fullscreenStage = new StageSurface(this, session);
    content.addView(fullscreenStage, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout controls = row();
    addWeighted(controls, button("Play / Pause", session::toggle));
    addWeighted(controls, button("Next", session::next));
    addWeighted(controls, button("Close", this::closeFullscreen));
    content.addView(controls);
    fullscreen.setContentView(content);
    fullscreen.setOnDismissListener(
        d -> {
          fullscreenStage = null;
          if (stagePresenter != null) stagePresenter.setVisualActive(true);
          camera.moveTo(stagePresenter.getCameraPreview());
        });
    if (stagePresenter != null) stagePresenter.setVisualActive(false);
    fullscreen.show();
    camera.moveTo(fullscreenStage);
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
    if (request == MUSIC) {
      List<Uri> uris = new ArrayList<>();
      if (data.getClipData() != null) {
        for (int i = 0; i < data.getClipData().getItemCount() && i < 1000; i++)
          uris.add(data.getClipData().getItemAt(i).getUri());
      } else if (data.getData() != null) uris.add(data.getData());
      showImportProgress();
      session.setImportProgressListener(new LibraryRepository.ImportProgressListener() {
        @Override public void onProgress(long total, long copied, String status) {
          updateImportProgress(total, copied, status);
        }
        @Override public void onComplete(boolean success, String message) {
          hideImportProgress(message);
        }
      });
      session.importSongs(uris);
    }
    else if (request == FOLDER && data.getData() != null) {
      showImportProgress();
      session.setImportProgressListener(new LibraryRepository.ImportProgressListener() {
        @Override public void onProgress(long total, long copied, String status) {
          updateImportProgress(total, copied, status);
        }
        @Override public void onComplete(boolean success, String message) {
          hideImportProgress(message);
        }
      });
      session.importFolder(data.getData());
    }
    } else if (request == VOICE) {
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
