package ph.kanta.reborn;

import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import ph.kanta.reborn.core.*;
import ph.kanta.reborn.core.companion.KantaConnectHost;
import ph.kanta.reborn.score.ScoreSink;
import ph.kanta.reborn.settings.SettingsStore;
import ph.kanta.reborn.score.ScoreSink.CompletedScore;
import ph.kanta.reborn.core.RenderedPlaybackClock;
import ph.kanta.reborn.core.bgv.ScenicCatalog;
import ph.kanta.reborn.core.score.ScoreResultTier;
import ph.kanta.reborn.core.score.ScoreRevealPlan;
import ph.kanta.reborn.BundledBgvCatalog;
import ph.kanta.reborn.core.bgv.ScenicClip;
import ph.kanta.reborn.BgvLibrary;

/** One app-lifetime state owner. All MediaPlayer access stays on the main Looper. */
public final class SessionController implements ph.kanta.reborn.core.PitchSource {
  interface Listener {
    void changed();
  }

  interface CompanionSearchListener {
    void onCompanionSearchQuery(String query);
  }

  interface DownloadProgressListener {
    void onProgress(String message);
    void onComplete(boolean success, String message);
  }

  private DownloadProgressListener downloadProgressListener;
  CompanionSearchListener companionSearchListener;

  void setDownloadProgressListener(DownloadProgressListener l) {
    downloadProgressListener = l;
  }

  void setCompanionSearchListener(CompanionSearchListener l) {
    companionSearchListener = l;
  }

  final CompanionControl companion;
  final BrowserCompanion browserCompanion;
  public final KantaConnectHost kantaConnectHost;
  final ReservationQueue queue = new ReservationQueue();
  final ExecutorService io = Executors.newSingleThreadExecutor();
  final ExecutorService preparation = Executors.newSingleThreadExecutor();
  final ExecutorService folderWorker = Executors.newSingleThreadExecutor();
  final java.util.concurrent.atomic.AtomicBoolean importCancelled =
      new java.util.concurrent.atomic.AtomicBoolean();
  private File selectedSoundFont;
  final Handler main = new Handler(Looper.getMainLooper());
  final Context context;
  android.graphics.Bitmap scenic;
  String scenicName = "bayanDawn";
  private boolean savingSet = false;
  private LibraryRepository repository;
  private LibraryRepository.ImportProgressListener importProgressListener;
  private volatile boolean legacyManualKeyOffer;
  private final LibraryRepository.ImportProgressListener importProgressDispatcher =
      new LibraryRepository.ImportProgressListener() {
        @Override
        public void onProgress(long total, long copied, String status) {
          main.post(
              () -> {
                LibraryRepository.ImportProgressListener current = importProgressListener;
                if (current != null) current.onProgress(total, copied, status);
              });
        }

        @Override
        public void onComplete(boolean success, String message) {
          main.post(() -> completeImportProgress(success, message));
        }
      };
  private StageMediaStore stageStore;
  StageMediaStore.Selection stageSelection;
  boolean stageReady, startupAudioPrepared, startupLibraryFailed;
  boolean cameraPreviewEnabled;
  String stageKind = "scenic";
  File stageVideo;
  long visualGeneration;
  private long stageRequest;
  private boolean choosingStage;
  private final StageVisualShuffle<PreparedStageVisual> visualShuffle = new StageVisualShuffle<>();
  private final Random visualRandom = new Random();
  private PreparedStageVisual shuffledVisual;
  private android.graphics.Bitmap manualStagePhoto;

  private static final class PreparedStageVisual {
    final File file;
    final android.graphics.Bitmap photo;

    PreparedStageVisual(File file, android.graphics.Bitmap photo) {
      this.file = file;
      this.photo = photo;
    }
  }
  long visualEpoch = android.os.SystemClock.elapsedRealtime();
  public List<Song> songs = Collections.emptyList();
  List<LibraryRepository.SavedSet> savedSets = Collections.emptyList();
  List<LibraryRepository.SoundFontEntry> soundFonts = Collections.emptyList();
  public List<MidiLyrics.Cue> lyrics = Collections.emptyList();
  final ArrayList<Song> played = new ArrayList<>();
  Song current;
  UUID playbackIdentity;
  boolean scoringEnabled;
  int scoreLatency;
  long currentMelodyStart;
  MelodyScoreSession score;
  private MelodyTarget currentTarget;
  private long currentDuration;
  private long currentMelodyStartMillis;
  private boolean currentKeyPresent, currentKeyMinor;
  private int currentKeySharpsFlats;
  private volatile long scoreRequest;
  private long scoreFrameFloor, lastScoreReading = -1;
  private boolean scoreWaitingForControls = true;

  public static final class CompletedScore {
    final UUID playbackIdentity;
    final String title, singer;
    final MelodyScoreSession.Estimate estimate;

    CompletedScore(
        UUID identity, String title, String singer, MelodyScoreSession.Estimate estimate) {
      playbackIdentity = identity;
      this.title = title;
      this.singer = singer;
      this.estimate = estimate;
    }
  }

  CompletedScore lastScore;

  public String singer = "", message = "Opening your library…";
  boolean ready = false, loading = false, playing = false, importing = false;
  private boolean resuming;

  /** Read-only stage state for the presenter in the stage subpackage. */
  public synchronized String stageKind() { return stageKind; }
  public synchronized boolean cameraPreviewEnabled() { return cameraPreviewEnabled; }
  public synchronized android.graphics.Bitmap scenicBitmap() { return scenic; }
  public synchronized File stageVideo() {
    return "video".equals(stageKind) && shuffledVisual != null ? shuffledVisual.file : stageVideo;
  }

  File stagePhotoFile() {
    return "photo".equals(stageKind)
        ? (shuffledVisual != null ? shuffledVisual.file : stageSelection.photo) : null;
  }

  boolean stageShuffleEnabled(boolean video) {
    return video ? settings.getBgvAutoShuffle() : settings.getBgpAutoShuffle();
  }

  void setStageShuffle(boolean video, boolean enabled) {
    if (stageShuffleEnabled(video) == enabled) return;
    if (video) settings.setBgvAutoShuffle(enabled);
    else settings.setBgpAutoShuffle(enabled);
    if ((video ? "video" : "photo").equals(stageKind)) {
      visualShuffle.invalidate();
      if (current == null && !loading) applyStageShuffleBoundary(false);
    }
    publish();
  }
  public synchronized boolean isPlaying() { return playing; }
  /** Current song, or null when nothing is loaded. Read-only, for the stage subpackage. */
  public synchronized Song currentSong() { return current; }

  /** Standby message shown on the Stage while the reserved song waits for its first cue. */
  public synchronized String interludeText() {
    String stored = settings.getInterludeText();
    return stored.isEmpty() ? context.getString(R.string.interlude_default) : stored;
  }

  public synchronized boolean preSongInterludeActive() {
    return current != null && !lyrics.isEmpty()
        && MidiLyrics.activeIndex(lyrics, position()) < 0;
  }

  public synchronized int interludeTextSizeSp() { return settings.getInterludeTextSize(); }

  public synchronized void setInterludeTextSize(int sp) {
    settings.setInterludeTextSize(Math.max(0, Math.min(120, sp)));
    publish();
  }

  public synchronized String interludeMode() { return settings.getInterludeMode(); }

  public synchronized android.graphics.drawable.Drawable interludeImage() {
    if (!"image".equals(interludeMode()) || interludeImageCache != null) {
      return interludeImageCache;
    }
    interludeImageCache = ph.kanta.reborn.core.media.InterludeStore.load(context);
    return interludeImageCache;
  }

  public synchronized void setInterludeText(String value) {
    settings.setInterludeText(value == null ? "" : value.trim());
    publish();
  }

  public synchronized boolean setInterludeImage(Uri source) {
    if (!ph.kanta.reborn.core.media.InterludeStore.install(context, source)) return false;
    settings.setInterludeImage(ph.kanta.reborn.core.media.InterludeStore.FILE_NAME);
    settings.setInterludeMode("image");
    interludeImageCache = ph.kanta.reborn.core.media.InterludeStore.load(context);
    publish();
    return true;
  }

  public synchronized void clearInterludeImage() {
    ph.kanta.reborn.core.media.InterludeStore.delete(context);
    settings.setInterludeImage("");
    settings.setInterludeMode("text");
    interludeImageCache = null;
    publish();
  }

  public synchronized void resetInterlude() {
    settings.setInterludeText("");
    settings.setInterludeTextSize(0);
    settings.setInterludeMode("text");
    settings.clearInterludeStyle();
    interludeImageCache = null;
    publish();
  }

  /** Milliseconds remaining on the vocal countdown, or -1 when none is running. */
  public synchronized long countdownRemainingMs() {
    if (countdownEndsAt < 0) return -1;
    return Math.max(0, countdownEndsAt - SystemClock.elapsedRealtime());
  }

  /** Milliseconds the running vocal countdown was given; 0 when none is running.
   *  Renderers fade the pre-song interlude across this window so the stage is
   *  clear where the first sung line lands. */
  public synchronized long countdownTotalMs() { return countdownTotalMs; }

  // ---- Lyric stage style (persisted in SettingsStore, applied by StageView) ----
  public synchronized boolean lyricAllCaps() { return settings.getLyricAllCaps(); }
  public synchronized void setLyricAllCaps(boolean value) { settings.setLyricAllCaps(value); publish(); }
  public synchronized boolean lyricBold() { return settings.getLyricBold(); }
  public synchronized void setLyricBold(boolean value) { settings.setLyricBold(value); publish(); }
  public synchronized int lyricOutline() { return settings.getLyricOutline(); }
  public synchronized void setLyricOutline(int value) { settings.setLyricOutline(value); publish(); }
  public synchronized int lyricActiveColor() { return settings.getLyricActiveColor(); }
  public synchronized void setLyricActiveColor(int value) { settings.setLyricActiveColor(value); publish(); }
  public synchronized float lyricUpcomingOpacity() { return settings.getLyricUpcomingOpacity(); }
  public synchronized void setLyricUpcomingOpacity(float value) {
    settings.setLyricUpcomingOpacity(value);
    publish();
  }
  public synchronized boolean lyricShowUpcoming() { return settings.getLyricShowUpcoming(); }
  public synchronized void setLyricShowUpcoming(boolean value) {
    settings.setLyricShowUpcoming(value);
    publish();
  }
  public synchronized String lyricAlignment() { return settings.getLyricAlignment(); }
  public synchronized void setLyricAlignment(String value) {
    settings.setLyricAlignment(value);
    publish();
  }
  public synchronized String lyricLines() { return settings.getLyricLines(); }
  public synchronized void setLyricLines(String value) { settings.setLyricLines(value); publish(); }
  public synchronized boolean lyricShowTitle() { return settings.getLyricShowTitle(); }
  public synchronized void setLyricShowTitle(boolean value) {
    settings.setLyricShowTitle(value);
    publish();
  }
  public synchronized boolean lyricShowWatermark() { return settings.getLyricShowWatermark(); }
  public synchronized void setLyricShowWatermark(boolean value) {
    settings.setLyricShowWatermark(value);
    publish();
  }
  public synchronized boolean lyricShowCountdown() { return settings.getLyricShowCountdown(); }
  public synchronized void setLyricShowCountdown(boolean value) {
    settings.setLyricShowCountdown(value);
    publish();
  }
  public synchronized void resetLyricStyle() { settings.clearLyricStyle(); publish(); }

  // ---- Interlude style ----
  public synchronized int interludeTextColor() { return settings.getInterludeTextColor(); }
  public synchronized void setInterludeTextColor(int value) {
    settings.setInterludeTextColor(value);
    publish();
  }
  public synchronized String interludeAnimation() { return settings.getInterludeAnimation(); }
  public synchronized void setInterludeAnimation(String value) {
    settings.setInterludeAnimation(value);
    publish();
  }
  public synchronized float interludeImageScale() { return settings.getInterludeImageScale(); }
  public synchronized void setInterludeImageScale(float value) {
    settings.setInterludeImageScale(value);
    publish();
  }
  public synchronized String interludeImagePlacement() { return settings.getInterludeImagePlacement(); }
  public synchronized void setInterludeImagePlacement(String value) {
    settings.setInterludeImagePlacement(value);
    publish();
  }
  public synchronized boolean interludeShowUpNext() { return settings.getInterludeShowUpNext(); }
  public synchronized void setInterludeShowUpNext(boolean value) {
    settings.setInterludeShowUpNext(value);
    publish();
  }
  public synchronized boolean interludeShowVisualizer() { return settings.getInterludeShowVisualizer(); }
  public synchronized void setInterludeShowVisualizer(boolean value) {
    settings.setInterludeShowVisualizer(value);
    publish();
  }
  public synchronized boolean interludeShowWatermark() { return settings.getInterludeShowWatermark(); }
  public synchronized void setInterludeShowWatermark(boolean value) {
    settings.setInterludeShowWatermark(value);
    publish();
  }

  /** The legacy pair the last folder scan found, or null. */
  public synchronized Uri pendingLegacyIndexUri() { return pendingLegacyIndexUri; }

  public synchronized Uri pendingLegacyBlobUri() { return pendingLegacyBlobUri; }

  /** Retries the scanned legacy pair with a key the user typed (never persisted). */
  boolean importPendingLegacyKey(
      ph.kanta.reborn.core.legacy.LegacyImportKey key) {
    return key != null && pendingLegacyIndexUri != null && pendingLegacyBlobUri != null
        && importLegacyPair(pendingLegacyIndexUri, pendingLegacyBlobUri, key);
  }

  boolean libraryImporting = false;
  private Listener listener;
  private ScoreSink scoreSink;
  public PlaybackBackend player, pending;
  public RenderedPlaybackClock currentClock;
  float volume = 1, tempo = 1;
  int pitch;
  float lyricScale = 1;
  /** Cached decoded interlude image; cleared whenever the stored image changes. */
  private android.graphics.drawable.Drawable interludeImageCache;
  private final SettingsStore settings;
  /** Music-session equalizer; attached to each playback backend's media session. */
  private final MusicEqualizer equalizer;
  /** Backend a pending equalizer retry targets; null while no retry is scheduled. */
  private PlaybackBackend equalizerRetryFor;
  private int equalizerRetries;
  boolean realm;
  final PartyAudio party;
  final KeypadSounds keypadSounds;
  final MicrophoneInput microphone;
  /** Scenic video library; null when its private storage cannot be opened. */
  final BgvLibrary bgvLibrary;
  final MicrophoneMonitor monitor;
  String microphoneNotice, monitorNotice;
  public final android.content.SharedPreferences options;
  boolean foreground, loopEnabled = true, auxiliaryInterrupted, assigningPad;
  private boolean hostLoopStateSaved, loopEnabledBeforeHidden = true;
  private Runnable autoAdvance;
  private UUID autoAdvanceEntryId;
  private Runnable scheduledMelodyCountdown;
  String loopSongId = "";
  /** Cached copy of the bundled standby interlude; null until prepared on startup. */
  private File bundledInterludeFile;
  /** URIs of the legacy pair found by the last "Add Karaoke Folder" scan. */
  private Uri pendingLegacyIndexUri, pendingLegacyBlobUri;
  final String[] customPads = new String[8];
  private final long[] padGeneration = new long[8];
  String soundFontName = "KANTA original tone";
  private long generation = 0;
  interface CountdownHost {
    Runnable show(int seconds, Runnable complete);
  }
  private CountdownHost countdownHost;
  private Runnable cancelCountdownView;
  private ReservationQueue.Entry countingEntry;
  /** elapsedRealtime millis at which the running vocal countdown ends; -1 when none. */
  private long countdownEndsAt = -1;
  private long countdownTotalMs = 0;
  private boolean countingQueued;
  private boolean pendingHasFocus;

  void setCountdownHost(CountdownHost host) {
    cancelCountdown();
    countdownHost = host;
  }

  void cancelCountdown() {
    cancelScheduledMelodyCountdown();
    if (countingEntry != null) discardPendingStart();
    else clearCountdown();
  }

  private void cancelScheduledMelodyCountdown() {
    if (scheduledMelodyCountdown != null) main.removeCallbacks(scheduledMelodyCountdown);
    scheduledMelodyCountdown = null;
  }

  private void cancelAutoAdvance() {
    if (autoAdvance != null) main.removeCallbacks(autoAdvance);
    autoAdvance = null;
    autoAdvanceEntryId = null;
  }

  private void discardPendingStart() {
    cancelScheduledMelodyCountdown();
    cancelEqualizerRetry();
    ++generation;
    PlaybackBackend discarded = pending;
    pending = null;
    loading = false;
    clearCountdown();
    if (discarded != null) discarded.release();
    if (pendingHasFocus && player == null && !microphone.enabled() && !monitor.enabled()) {
      audio.abandonAudioFocusRequest(focus);
    }
    pendingHasFocus = false;
  }

  private void clearCountdown() {
    countingEntry = null;
    countdownEndsAt = -1;
    countdownTotalMs = 0;
    Runnable cancel = cancelCountdownView;
    cancelCountdownView = null;
    if (cancel != null) cancel.run();
  }

  private void startLocal(ReservationQueue.Entry entry, boolean queued) {
    cancelAutoAdvance();
    // The score celebration runs to natural completion across the song boundary;
    // only Stop, focus loss or leaving the app silences it.
    int seconds = countdownHost == null ? 0 : settings.getCountdownLength();
    if (seconds > 0) {
      // Track cancellation during decoding too; the view is only created in ready().
      countingEntry = entry;
      countingQueued = queued;
    }
    prepare(entry, queued, seconds);
  }
  private final ExecutorService lyricWorker = Executors.newSingleThreadExecutor();
  private boolean lyricSearchBusy;
  private boolean autoLyricSearchBusy;
  
  private long autoLyricSearchAt;
  long lyricLookupGeneration;
  
  private long nextLyricSearchAt;

  private final AudioManager audio;
  private final AudioFocusRequest focus;

  final DiagnosticEvents diagnostics;

  SessionController(Context context, DiagnosticEvents diagnostics) {
    this.context = context;
    this.diagnostics = diagnostics;
    BgvLibrary library = null;
    try {
      library = new BgvLibrary(context);
    } catch (IOException failure) {
      // The scenic video picker is an optional surface. Losing its private directory must not
      // take the whole session — and the user's music — down with it.
      library = null;
    }
    bgvLibrary = library;
    companion = new CompanionControl(this);
    browserCompanion = new BrowserCompanion(this);
    kantaConnectHost = new KantaConnectHost(context, new KantaConnectHost.HostAdapter() {
      @Override public String handleRequest(String action, String argument, String singer) {
        return handleKantaConnectRequest(action, argument, singer);
      }
      @Override public boolean isReady() {
        return ready && !loading && !importing;
      }
    });
    monitor =
        new MicrophoneMonitor(
            context, this::publish, this::setMonitorNotice, this::inputReading);
    microphone =
        new MicrophoneInput(context, this::publish, this::setMicrophoneNotice, monitor::offer);
    options = context.getSharedPreferences("stage-options-v1", Context.MODE_PRIVATE);
    settings = new SettingsStore(context);
    equalizer = new MusicEqualizer(settings);
    volume = Math.min(1f, Math.max(0f, settings.getMasterVolume()));
    tempo = Math.min(2f, Math.max(0.5f, settings.getTempo()));
    pitch = Math.min(12, Math.max(-12, settings.getPitch()));
    lyricScale = Math.max(.75f, Math.min(1.5f, options.getFloat("lyricScale", 1)));
    loopSongId = options.getString("loopSong", "");
    keypadSounds = new KeypadSounds();
    party =
        new PartyAudio(
            this::publish,
            notice -> {
              loopEnabled = false;
              message = notice;
              publish();
            });
    party.volumes(options.getFloat("sfxVolume", .6f), options.getFloat("loopVolume", .35f));
    for (int i = 0; i < 8; i++) customPads[i] = options.getString("pad." + i, "");
    audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    focus =
        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
            .setOnAudioFocusChangeListener(
                change -> {
                  if (change < 0) {
                    auxiliaryInterrupted = true;
                    loopEnabled = false;
                    party.stopAll();
                    interruptMonitor("Voice playback stopped because another app took over audio.");
                    pause();
                    publish();
                  }
                },
                main)
            .build();
    BroadcastReceiver noisy =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context c, Intent i) {
            // Selecting a Bluetooth call route can itself retire A2DP. The muted monitor's
            // exact-device guard, not this media-route broadcast, decides whether it can start.
            if (!monitor.negotiatingCommunication()) {
              interruptMonitor("Audio output changed. Choose your headphones again.");
            }
            pause();
            publish();
          }
        };
    if (Build.VERSION.SDK_INT >= 33)
      context.registerReceiver(
          noisy,
          new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
          Context.RECEIVER_NOT_EXPORTED);
    else
      context.registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    io.execute(
        () -> {
          try {
            repository = new LibraryRepository(context);
            repository.load();
            File bank = repository.soundFont();
            String bankLabel = repository.soundFontName();
            List<Song> loaded = repository.songs();
            List<LibraryRepository.SavedSet> sets = repository.sets();
            List<LibraryRepository.SoundFontEntry> banks = repository.soundFonts();
            List<ReservationQueue.Entry> restored = repository.queue();
            main.post(
                () -> {
                  songs = loaded;
                  savedSets = sets;
                  soundFonts = banks;
                  queue.restore(restored);
                  selectedSoundFont = bank;
                  soundFontName = bankLabel;
                  ready = true;
                  if (importProgressListener != null)
                    repository.setImportProgressListener(importProgressDispatcher);
                  preparePads();
                  prepareInterlude();
                  prepareKeypadSounds();
                  // This marker runs after the three startup audio I/O tasks on the serial executor.
                  io.execute(() -> main.post(() -> {
                    startupAudioPrepared = true;
                    publish();
                  }));
                  restoreStage();
                  message = "Your music. Your stage.";
                  publish();
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  startupLibraryFailed = true;
                  message =
                      "Could not open saved library. Your original files have not been changed.";
                  publish();
                });
          }
        });
  }

  void listen(Listener l) {
    listener = l;
    if (l != null) l.changed();
  }

  void setScoreSink(ScoreSink sink) {
    scoreSink = sink;
  }

  void publish() {
    if (autoAdvanceEntryId != null
        && (queue.peek() == null || !autoAdvanceEntryId.equals(queue.peek().id)))
      cancelAutoAdvance();
    if (countingEntry != null && countingQueued && countingEntry != queue.peek()) cancelCountdown();
    if (!microphone.enabled()) monitor.stop();
    updateIntermission();
    prepareStageShuffle();
    if (listener != null) listener.changed();
  }

  public void selectScenicClip(String clipId) {
    BundledBgvCatalog.Clip clip = BundledBgvCatalog.byId(clipId);
    if (clip == null || !canChangeStageMedia() || stageStore == null) return;
    long request = ++stageRequest;
    choosingStage = true;
    visualShuffle.invalidate();
    message = "Preparing " + clip.title + "…";
    publish();
    io.execute(() -> {
      File destination = new File(stageStore.getRoot(), clip.id + ".video");
      StageMediaStore.Selection saved = null;
      if (prepareBundledStageVideo(destination, clip)) {
        try { saved = stageStore.selectBundledVideo(destination.getName()); }
        catch (Exception unavailable) { }
      }
      StageMediaStore.Selection selection = saved;
      main.post(() -> {
        if (request != stageRequest) return;
        choosingStage = false;
        message = selection == null ? "Could not load " + clip.title : "Background: " + clip.title;
        if (selection == null) publish();
        else applyStage(selection);
      });
    });
  }

  /** IO only; caching a bundled shuffle candidate never changes selection.json. */
  private boolean prepareBundledStageVideo(File destination, BundledBgvCatalog.Clip clip) {
    if (destination.length() == clip.sizeBytes) return true;
    try {
      File temp = File.createTempFile("bgv-", ".tmp", destination.getParentFile());
      try {
        return BundledBgvCatalog.copyTo(context, clip, temp)
            && temp.length() == clip.sizeBytes && temp.renameTo(destination);
      } finally {
        temp.delete();
      }
    } catch (IOException unavailable) {
      return false;
    }
  }

  private void prepareStageShuffle() {
    if (!foreground || !canChangeStageMedia() || choosingStage || stageSelection == null
        || stageStore == null || !visualShuffle.needsPreparation()) return;
    boolean video = "video".equals(stageKind);
    if ((!video && !"photo".equals(stageKind)) || !stageShuffleEnabled(video)) return;
    List<File> pool = new ArrayList<>();
    Map<File, BundledBgvCatalog.Clip> bundled = new HashMap<>();
    for (StageMediaStore.Entry entry : stageSelection.imports) {
      if (entry.video == video) pool.add(entry.file);
    }
    if (video) {
      for (BundledBgvCatalog.Clip clip : BundledBgvCatalog.BUILT_IN) {
        File file = new File(stageStore.getRoot(), clip.id + ".video");
        bundled.put(file, clip);
        pool.add(file);
      }
    }
    long request = visualShuffle.beginPreparation();
    List<File> alternatives = StageVisualShuffle.alternatives(
        pool, video ? stageVideo() : stagePhotoFile(), visualRandom);
    if (alternatives.isEmpty()) return;
    io.execute(() -> {
      for (File candidate : alternatives) {
        try {
          BundledBgvCatalog.Clip clip = bundled.get(candidate);
          if (clip != null ? !prepareBundledStageVideo(candidate, clip) : !candidate.isFile()) continue;
          android.graphics.Bitmap photo = video ? null : StageMediaStore.displayPhoto(candidate);
          PreparedStageVisual prepared = new PreparedStageVisual(candidate, photo);
          main.post(() -> {
            if (visualShuffle.isCurrent(request)) visualShuffle.prepared(request, prepared);
            else if (photo != null) photo.recycle();
          });
          return;
        } catch (Exception | OutOfMemoryError unavailable) {
          // A missing or unreadable candidate must not displace the displayed one.
        }
      }
    });
  }

  /** Only the successful new-song/standby transitions may project an automatic change. */
  private void applyStageShuffleBoundary(boolean startingSong) {
    if (!foreground) return;
    boolean video = "video".equals(stageKind);
    if (!video && !"photo".equals(stageKind)) return;
    if (!stageShuffleEnabled(video)) {
      if (shuffledVisual != null) {
        shuffledVisual = null;
        if (!video) scenic = manualStagePhoto;
        visualGeneration++;
        visualEpoch = SystemClock.elapsedRealtime();
      }
    } else if (startingSong && canChangeStageMedia() && !choosingStage && !cameraPreviewEnabled) {
      PreparedStageVisual prepared = visualShuffle.takeAtSongBoundary();
      if (prepared != null) {
        shuffledVisual = prepared;
        if (!video) scenic = prepared.photo;
        visualGeneration++;
        visualEpoch = SystemClock.elapsedRealtime();
      }
    }
  }

  private void restoreStage() {
    io.execute(
        () -> {
          try {
            stageStore = new StageMediaStore(context);
            StageMediaStore.Selection selection = stageStore.load();

            // If no saved stage (default scenic with no video), initialize default BGV
            final StageMediaStore.Selection finalSelection;
            if (!stageStore.hasSavedSelection() && !settings.getBgvDefaultSelected()) {
              BundledBgvCatalog.Clip defaultClip = BundledBgvCatalog.defaultClip();
              if (defaultClip != null) {
                File dest = new File(stageStore.getRoot(), defaultClip.id + ".video");
                if (BundledBgvCatalog.copyTo(context, defaultClip, dest)) {
                  finalSelection = stageStore.selectBundledVideo(dest.getName());
                  settings.setBgvDefaultSelected(true);
                } else {
                  finalSelection = selection;
                }
              } else {
                finalSelection = selection;
              }
            } else {
              finalSelection = selection;
            }

            main.post(
                () -> {
                  applyStage(finalSelection);
                  // Photo decoding, if needed, is queued by applyStage before this marker.
                  io.execute(() -> main.post(() -> {
                    stageReady = true;
                    publish();
                  }));
                });
          } catch (Exception failure) {
            main.post(
                () -> {
                  stageStore = null;
                  stageReady = true;
                  message =
                      "Saved stage media is unavailable. Scenic backgrounds remain available.";
                  publish();
                });
          }
        });
  }

  void importStage(android.net.Uri uri, boolean video) {
    if (!ready || !canChangeStageMedia() || stageStore == null) return;
    importing = true;
    importCancelled.set(false);
    choosingStage = false;
    visualShuffle.invalidate();
    long token = ++stageRequest;
    message = video ? "Copying your background video…" : "Copying your background photo…";
    publish();
    io.execute(
        () -> {
          try {
            StageMediaStore.Selection selection =
                stageStore.importMedia(uri, video, importCancelled::get);
            main.post(
                () -> {
                  importing = false;
                  message = "Your background is ready. The original file is unchanged.";
                  if (token == stageRequest) applyStage(selection);
                  else {
                    stageSelection = selection;
                    publish();
                  }
                });
          } catch (Exception failure) {
            main.post(
                () -> {
                  importing = false;
                  message =
                      "Stage import stopped or the media is unsupported. Your previous background"
                          + " is retained.";
                  publish();
                });
          }
        });
  }

  void importStageBatch(List<Uri> uris, boolean video) {
    if (!ready || !canChangeStageMedia() || stageStore == null || uris.isEmpty()) return;
    List<Uri> sources = new ArrayList<>(uris);
    importing = true;
    importCancelled.set(false);
    message = context.getString(R.string.media_library_copying);
    publish();
    io.execute(() -> {
      try {
        StageMediaStore.ImportResult result = stageStore.importBatch(sources, video, importCancelled::get);
        StageMediaStore.Selection selection = stageStore.selection();
        android.content.res.Resources resources = context.getResources();
        StringBuilder status = new StringBuilder(resources.getQuantityString(
            R.plurals.media_library_import_added, result.added, result.added));
        if (result.duplicates > 0) status.append(' ').append(resources.getQuantityString(
            R.plurals.media_library_import_duplicates, result.duplicates, result.duplicates));
        if (result.skipped > 0) status.append(' ').append(resources.getQuantityString(
            R.plurals.media_library_import_skipped, result.skipped, result.skipped));
        if (result.cancelled) status.append(' ').append(context.getString(R.string.media_library_import_cancelled));
        main.post(() -> {
          stageSelection = selection;
          visualShuffle.invalidate();
          importing = false;
          message = status.toString();
          publish();
        });
      } catch (Exception failure) {
        main.post(() -> {
          importing = false;
          message = context.getString(R.string.media_library_import_failed);
          publish();
        });
      }
    });
  }

  private boolean removingVisual;

  boolean canChangeStageMedia() {
    return stageReady && !importing && !removingVisual;
  }

  void removeStageImport(File file, boolean video, java.util.function.Consumer<String> completed) {
    if (!canChangeStageMedia() || stageStore == null) {
      completed.accept("Wait for Stage Visuals to finish, then try again.");
      return;
    }
    if (file.equals(video ? stageVideo() : stagePhotoFile())) {
      completed.accept(context.getString(R.string.media_library_visual_remove_blocked));
      return;
    }
    // The store also protects the exact manual fallback, including a queued Use.
    visualShuffle.invalidate();
    removingVisual = true;
    publish();
    io.execute(() -> {
      try {
        boolean deleted = stageStore.removeImport(file.getName(), video);
        StageMediaStore.Selection selection = stageStore.selection();
        main.post(() -> {
          stageSelection = selection;
          visualShuffle.invalidate();
          removingVisual = false;
          publish();
          completed.accept(deleted ? "Saved background removed."
              : "Background removed from the list, but its stored copy could not be erased.");
        });
      } catch (Exception failure) {
        main.post(() -> {
          removingVisual = false;
          publish();
          completed.accept("Could not remove this background. Choose a different background and try again.");
        });
      }
    });
  }

  void chooseStage(String kind) {
    chooseStageMedia(() -> stageStore.select(kind));
  }

  void chooseStageImport(File file, boolean video) {
    chooseStageMedia(() -> stageStore.selectImport(file.getName(), video));
  }

  private void chooseStageMedia(java.util.concurrent.Callable<StageMediaStore.Selection> select) {
    if (!canChangeStageMedia() || stageStore == null) return;
    long token = ++stageRequest;
    choosingStage = true;
    visualShuffle.invalidate();
    io.execute(
        () -> {
          try {
            StageMediaStore.Selection selection = select.call();
            main.post(
                () -> {
                  if (token == stageRequest) {
                    choosingStage = false;
                    applyStage(selection);
                  }
                });
          } catch (Exception failure) {
            main.post(
                () -> {
                  if (token == stageRequest) {
                    choosingStage = false;
                    message = "Could not use this background.";
                    publish();
                  }
                });
          }
        });
  }

  private void applyStage(StageMediaStore.Selection selection) {
    visualShuffle.invalidate();
    if (shuffledVisual != null && "photo".equals(stageKind)) scenic = manualStagePhoto;
    shuffledVisual = null;
    manualStagePhoto = null;
    stageSelection = selection;
    stageKind = selection.kind;
    stageVideo = stageKind.equals("video") ? selection.video : null;
    visualEpoch = android.os.SystemClock.elapsedRealtime();
    long token = ++visualGeneration;
    if (stageKind.equals("photo") && selection.photo != null) {
      io.execute(
          () -> {
            try {
              android.graphics.Bitmap photo = StageMediaStore.displayPhoto(selection.photo);
              main.post(
                  () -> {
                    if (token == visualGeneration && stageKind.equals("photo")) {
                      manualStagePhoto = photo;
                      if (shuffledVisual == null) scenic = photo;
                      publish();
                    } else photo.recycle();
                  });
            } catch (Exception | OutOfMemoryError failure) {
              main.post(
                  () -> {
                    if (token == visualGeneration) {
                      message =
                          "This photo cannot be displayed. Choose another or a Scenic background.";
                      publish();
                    }
                  });
            }
          });
    }
    publish();
  }

  private void prepareInterlude() {
    io.execute(
        () -> {
          try {
            File root = new File(context.getCacheDir(), "interlude-v1");
            if (!root.isDirectory() && !root.mkdirs())
              throw new IOException("Cannot create interlude cache");
            File file = new File(root, "standby-loop.mp3");
            if (BundledInterlude.copyTo(context, file)) {
              main.post(
                  () -> {
                    bundledInterludeFile = file;
                    publish();
                  });
            }
          } catch (Exception failure) {
            // The bundled welcome is optional: a failed cache write leaves the
            // intermission silent; a user import can still be used.
          }
        });
  }


  /** Copies keypad tone assets to cache and loads them into the SoundPool. */
  private void prepareKeypadSounds() {
    // Load keypad sounds on IO thread to avoid blocking UI
    io.execute(
        () -> {
          try {
            File root = new File(context.getCacheDir(), "keypad-v1");
            if (!root.isDirectory() && !root.mkdirs())
              throw new IOException("Cannot create keypad cache");
            String[] names = {"Zero","One","Two","Three","Four","Five","Six","Seven","Eight","Nine","Delete","Reserve"};
            for (String name : names) {
              File file = new File(root, name + ".mp3");
              try (java.io.InputStream in = context.getAssets().open("keypad/" + name + ".mp3");
                   java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
              }
            }
            // Load into SoundPool on IO thread (load() is async but returns immediately)
            keypadSounds.load(root);
          } catch (Exception ignored) {}
        });
  }

  /**
   * Plays a keypad tone if no song is currently playing.
   *
   * <p>Key feedback must never mask or interrupt karaoke audio, so the tone is gated on
   * {@code !isPlaying()}. Accepts "0"–"9", "delete", "reserve".
   */
  public void playKeypadSound(String key) {
    if (playing) return;
    keypadSounds.play(key);
  }

  private void preparePads() {

    io.execute(
        () -> {
          try {
            ArrayList<File> padFiles = new ArrayList<>();
            File padRoot = new File(context.getCacheDir(), "party-v1");
            if (!padRoot.isDirectory() && !padRoot.mkdirs())
              throw new IOException("Cannot create party cache");
            for (int i = 0; i < 8; i++) {
              File file = new File(padRoot, "pad-" + i + ".wav");
              android.util.AtomicFile output = new android.util.AtomicFile(file);
              FileOutputStream stream = output.startWrite();
              try {
                try (InputStream input = context.getAssets().open("party/pad-" + i + ".wav")) {
                  byte[] buffer = new byte[8192];
                  int count;
                  while ((count = input.read(buffer)) != -1) stream.write(buffer, 0, count);
                }
                output.finishWrite(stream);
              } catch (Exception failure) {
                output.failWrite(stream);
                throw failure;
              }
              padFiles.add(file);
            }
            File celebration = new File(padRoot, "score-celebration.mp3");
            try (InputStream input = context.getAssets().open("party/score-celebration.mp3");
                FileOutputStream output = new FileOutputStream(celebration)) {
              byte[] buffer = new byte[8192];
              int count;
              while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            main.post(
                () -> {
                  party.loadBuiltins(padFiles);
                  party.loadCelebration(celebration);
                  for (int i = 0; i < 8; i++) {
                    final int slot = i;
                    Song clip =
                        songs.stream()
                            .filter(song -> song.id.equals(customPads[slot]))
                            .findFirst()
                            .orElse(null);
                    if (clip != null && isAudio(clip)) validatePad(slot, clip, false);
                  }
                });
          } catch (Exception failure) {
            main.post(
                () -> {
                  message = "Party sounds could not be loaded. Karaoke remains available.";
                  publish();
                });
          }
        });
  }

  // PitchSource: the single microphone owner lends its estimate to pitch-based features so
  // they never open a second capture.

  /**
   * The note the pitch game measures against: the tonic of the playing song's key, or A4 when
   * no key is known. A song in A minor therefore targets 440 Hz, and one in C major targets
   * middle C.
   */
  public float practiceTargetHz() {
    if (!currentKeyPresent) return 440f;
    float hz = MidiKey.practiceTargetHz(currentKeySharpsFlats, currentKeyMinor);
    return hz > 0f ? hz : 440f;
  }

  /** The playing song's key name, for the learning assistant. */
  public String practiceKeyName() {
    if (!currentKeyPresent) return "C major";
    String name = MidiKey.name(currentKeySharpsFlats, currentKeyMinor);
    return name == null ? "C major" : name;
  }

  @Override
  public float currentPitchHz() {
    // SPICE is the calibrated estimate and is preferred when it is available; the frame
    // detector is the fallback so the pitch features keep working without the model.
    float calibrated = microphone.calibratedPitchHz();
    if (calibrated > 0f) return calibrated;
    MicrophoneInput.Reading reading = inputReading();
    if (reading == null || reading.pitch == null || reading.pitch.confidence <= 0) return 0f;
    return (float) reading.pitch.frequencyHz;
  }

  @Override
  public long measuredAtMs() {
    if (microphone.calibratedPitchHz() > 0f) return microphone.calibratedMeasuredAt();
    MicrophoneInput.Reading reading = inputReading();
    return reading == null ? 0L : reading.measuredAt;
  }

  private MicrophoneInput.Reading inputReading() {
    return microphone.reading();
  }

  static boolean isAudio(Song song) {
    return song.fileName.endsWith(".mp3") || song.fileName.endsWith(".wav");
  }

  void setScoring(boolean enabled) {
    if (scoringEnabled == enabled) return;
    scoringEnabled = enabled;
    resetScore();
    publish();
  }

  void scoreLatency(int milliseconds) {
    int value = Math.max(-500, Math.min(1500, milliseconds));
    if (scoreLatency == value) return;
    scoreLatency = value;
    resetScore();
    publish();
  }

  boolean scoreReady() {
    return score != null && player != null && player.controlsSettled();
  }

  String scoreStatus() {
    if (!scoringEnabled) return "Song scoring off";
    if (current == null) return "Scoring ready for the next song";
    if (currentTarget == null || !currentTarget.available())
      return currentTarget == null ? "Melody reference unavailable" : currentTarget.unavailable;
    if (!microphone.enabled()) return "Turn on microphone input for a pitch-following estimate";
    if (!scoreReady()) return "Waiting for song playback…";
    MelodyScoreSession.Estimate value = score.estimate();
    return value.reportable
        ? String.format(
            java.util.Locale.ROOT,
            "Live estimate %.0f · coverage %.0f%%",
            value.overall,
            value.coverage)
        : "Listening for enough stable voice input";
  }

  private void resetScore() {
    long request = ++scoreRequest;
    if (score != null) score.cancel();
    score = null;
    lastScoreReading = -1;
    scoreFrameFloor = SystemClock.elapsedRealtime();
    scoreWaitingForControls = true;
    if (!scoringEnabled
        || playbackIdentity == null
        || currentTarget == null
        || !currentTarget.available()) return;
    UUID identity = playbackIdentity;
    MelodyTarget target = currentTarget;
    int key = pitch;
    float rate = tempo;
    preparation.execute(
        () -> {
          if (request != scoreRequest) return;
          MelodyScoreSession prepared = new MelodyScoreSession(identity, target, key, rate);
          main.post(
              () -> {
                if (request != scoreRequest
                    || !scoringEnabled
                    || !identity.equals(playbackIdentity)) {
                  prepared.cancel();
                  return;
                }
                score = prepared;
                scoreFrameFloor = SystemClock.elapsedRealtime();
                publish();
              });
        });
  }

  void observeMicrophone() {
    if (!foreground || !playing || !scoringEnabled || score == null || playbackIdentity == null)
      return;
    if (!player.controlsSettled()) {
      scoreWaitingForControls = true;
      scoreFrameFloor = SystemClock.elapsedRealtime();
      return;
    }
    if (scoreWaitingForControls) {
      scoreWaitingForControls = false;
      scoreFrameFloor = SystemClock.elapsedRealtime();
      return;
    }
    long sourcePosition = position();
    if (sourcePosition >= currentDuration) return;
    MicrophoneInput.Reading reading = microphone.reading();
    if (reading == null || reading.measuredAt <= lastScoreReading) return;
    lastScoreReading = reading.measuredAt;
    // A complete dry frame must belong to this transport/calibration epoch.
    long earliest = reading.measuredAt - 86 - Math.max(0, scoreLatency);
    if (earliest < scoreFrameFloor || reading.pitch == null) return;
    long aligned =
        MelodyScoreSession.align(
            reading.measuredAt - 43,
            SystemClock.elapsedRealtime(),
            sourcePosition,
            tempo,
            scoreLatency);
    score.observe(
        playbackIdentity,
        aligned,
        reading.pitch.frequencyHz,
        reading.pitch.confidence,
        reading.pitch.rms);
  }

  private ScoreResultTier finishScore(boolean applausePlayed) {
    if (!scoringEnabled || current == null || playbackIdentity == null) return null;
    if (score != null) observeMicrophone();
    MelodyScoreSession.Completion completed =
        score == null ? null : score.finish(playbackIdentity);
    MelodyScoreSession.Estimate estimate =
        completed == null ? MelodyScoreSession.Estimate.noSinging() : completed.estimate;
    lastScore = new CompletedScore(playbackIdentity, current.title, singer, estimate);
    if (scoreSink != null) {
      ph.kanta.reborn.score.ScoreSink.CompletedScore sinkScore =
          new ph.kanta.reborn.score.ScoreSink.CompletedScore(
              lastScore.playbackIdentity, lastScore.title, lastScore.singer, lastScore.estimate,
              applausePlayed);
      scoreSink.onScoreCompleted(sinkScore);
    }
    // The user's celebration track layers over the party cues until Stop or focus loss.
    if (foreground && !auxiliaryInterrupted
        && audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
      party.playCelebration();
    return ScoreResultTier.fromScore(ScoreResultTier.present(estimate.overall));
  }

  void setMicrophoneNotice(String notice) {
    microphoneNotice = notice;
    if (notice != null) message = notice;
  }

  void setMonitorNotice(String notice) {
    monitorNotice = notice;
    if (notice != null) message = notice;
  }

  void startMicrophone() {
    if (!foreground
        || context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
    // Audio focus is for audible output. Android governs input sharing via AudioRecord.
    setMicrophoneNotice(null);
    microphone.start();
  }

  void startMonitor(AudioDeviceInfo output) {
    if (!foreground || !microphone.capturing()) {
      setMonitorNotice("Turn on the microphone before starting voice playback.");
      publish();
      return;
    }
    if (MicrophoneMonitor.communication(output) && !allowsBluetoothMonitoring()) {
      setMonitorNotice("Bluetooth voice playback is only available between songs. Use wired or "
          + "USB headphones during songs.");
      publish();
      return;
    }
    if (monitor.enabled()) return;
    if (!MicrophoneMonitor.supported(output)) {
      setMonitorNotice("Choose wired or USB headphones or a supported Bluetooth headset.");
      publish();
      return;
    }
    if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      setMonitorNotice("Audio is busy. Try your headphones again when other audio stops.");
      publish();
      return;
    }
    setMonitorNotice(null);
    if (!monitor.start(output)) {
      setMonitorNotice("Your headphones are no longer available. Reconnect them and choose the "
          + "output again.");
      publish();
    }
  }

  private void interruptMonitor(String notice) {
    if (!monitor.enabled()) return;
    monitor.stop();
    setMonitorNotice(notice);
  }

  boolean allowsBluetoothMonitoring() {
    return !loading && current == null;
  }

  void stopMonitor() {
    setMonitorNotice(null);
    monitor.stop();
    publish();
  }

  void stopMicrophone() {
    setMicrophoneNotice(null);
    setMonitorNotice(null);
    monitor.stop();
    microphone.stop();
    if (current == null && !loopEnabled) audio.abandonAudioFocusRequest(focus);
    publish();
  }

  void hostVisible(boolean visible) {
    scoreWaitingForControls = true;
    scoreFrameFloor = SystemClock.elapsedRealtime();
    if (!visible && foreground) {
      loopEnabledBeforeHidden = loopEnabled;
      hostLoopStateSaved = true;
    }
    foreground = visible;
    if (!visible) {
      visualShuffle.invalidate();
      cancelAutoAdvance();
      cancelScheduledMelodyCountdown();
      cancelCountdown();
      ++lyricLookupGeneration;
      companion.stop();
      browserCompanion.stop();
      monitor.stop();
      microphone.stop();
      party.stopAll();
      loopEnabled = false;
    } else if (hostLoopStateSaved) {
      loopEnabled = loopEnabledBeforeHidden;
      hostLoopStateSaved = false;
    }
    if (visible) {
      if (current == null && !loading) applyStageShuffleBoundary(false);
      prepareStageShuffle();
    }
    updateIntermission();
  }

  private void updateIntermission() {
    Song selected =
        songs.stream()
            .filter(song -> song.id.equals(loopSongId) && isAudio(song))
            .findFirst()
            .orElse(null);
    boolean allowed =
        foreground
            && loopEnabled
            && !auxiliaryInterrupted
            && current == null
            && !loading
            && countingEntry == null;
    if (allowed && audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
      allowed = false;
    if (selected != null && repository != null) {
      File customFile = new File(repository.media, selected.fileName);
      party.loop(customFile, allowed); // custom intermission track loops
    } else {
      party.welcome(bundledInterludeFile, allowed);
    }
  }

  /** Clears the selected loop track without replaying an already played welcome. */
  void useBundledInterlude() {
    loopSongId = "";
    options.edit().remove("loopSong").apply();
    publish();
  }

  void chooseIntermission(Song song) {
    if (!isAudio(song) || song.id.equals(removingSong) || !songs.contains(song)) return;
    loopSongId = song.id;
    options.edit().putString("loopSong", loopSongId).apply();
    publish();
  }

  void toggleIntermission() {
    auxiliaryInterrupted = false;
    loopEnabled = !loopEnabled;
    publish();
  }

  void triggerPad(int slot) {
    if (!foreground || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
      return;
    auxiliaryInterrupted = false;
    if (!party.trigger(slot)) {
      message = "This party pad is still loading or unavailable.";
      publish();
    }
  }

  void stopParty() {
    loopEnabled = false;
    party.stopAll();
    publish();
  }

  /** Downloads a built-in scenic clip. Returns true if the download was started. */
  public boolean downloadScenic(String clipId, Runnable finished) {
    if (bgvLibrary == null) return false;
    ScenicClip clip = ScenicCatalog.byId(clipId);
    if (clip == null) return false;
    return bgvLibrary.download(clip, (id, ok, status) -> main.post(() -> {
      publish();
      finished.run();
    }));
  }

  /** Deletes a previously downloaded clip from the library. */
  public boolean deleteScenic(String clipId) {
    return bgvLibrary != null && bgvLibrary.delete(clipId);
  }

  /** Returns the current download status line, or empty when the library is unavailable. */
  boolean scenicDownloadInProgress() {
    return bgvLibrary != null && bgvLibrary.busy();
  }

  public String scenicDownloadStatus() {
    return bgvLibrary == null ? "" : bgvLibrary.progressLine();
  }

  /** Returns the downloaded scenic clip entries for the library UI, oldest library first. */
  public synchronized List<BgvLibrary.Entry> scenicEntries() {
    return bgvLibrary == null ? Collections.emptyList() : bgvLibrary.entries();
  }

  /** Whether the scenic video library is available on this device. */
  public boolean scenicLibraryAvailable() {
    return bgvLibrary != null;
  }

  /**
   * Plays one party effect slot for a stage cue such as the score reveal.
   *
   * <p>The stage owns presentation but not audio, so it asks the single audio owner instead of
   * reaching for the pads itself. A negative slot is ignored, and an unavailable pad is silent
   * rather than an error: a missing cue must never block the score card.
   */
  public void playEffectCue(int slot) {
    if (slot < 0 || slot >= PartySounds.TITLES.size()) return;
    triggerPad(slot);
  }

  void partyVolumes(float sfx, float intermission) {
    party.volumes(sfx, intermission);
    options
        .edit()
        .putFloat("sfxVolume", party.sfxVolume)
        .putFloat("loopVolume", party.loopVolume)
        .apply();
  }

  void lyricSize(float scale) {
    if (!Float.isFinite(scale)) return;
    lyricScale = Math.max(.75f, Math.min(1.5f, scale));
    options.edit().putFloat("lyricScale", lyricScale).apply();
  }

  void assignPad(int slot, Song song) {
    if (assigningPad || !isAudio(song) || slot < 0 || slot >= 8
        || song.id.equals(removingSong) || !songs.contains(song)) return;
    assigningPad = true;
    validatePad(slot, song, true);
  }

  private void validatePad(int slot, Song song, boolean persist) {
    long token = ++padGeneration[slot];
    io.execute(
        () -> {
          File file = new File(repository.media, song.fileName);
          boolean valid = false;
          try {
            if (file.length() <= 0 || file.length() > 8 * 1024 * 1024) throw new IOException();
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            try {
              metadata.setDataSource(file.getAbsolutePath());
              long duration =
                  Long.parseLong(
                      metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
              MediaExtractor extractor = new MediaExtractor();
              try {
                extractor.setDataSource(file.getAbsolutePath());
                if (extractor.getTrackCount() > 64) throw new IOException();
                for (int track = 0; track < extractor.getTrackCount(); track++) {
                  MediaFormat format = extractor.getTrackFormat(track);
                  String mime = format.getString(MediaFormat.KEY_MIME);
                  if (mime == null || !mime.startsWith("audio/")) continue;
                  valid =
                      ShortClipBounds.accepts(
                          duration,
                          format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                          format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                  break;
                }
              } finally {
                extractor.release();
              }
            } finally {
              metadata.release();
            }
          } catch (Exception ignored) {
            valid = false;
          }
          final boolean accepted = valid;
          main.post(
              () -> {
                if (token != padGeneration[slot]) return;
                if (!accepted) {
                  if (persist) {
                    assigningPad = false;
                    message =
                        "Use a shorter mono or stereo MP3/WAV clip (at most 8 MB / 30 seconds).";
                    publish();
                  }
                  return;
                }
                party.assign(
                    slot,
                    file,
                    success -> {
                      if (persist) {
                        assigningPad = false;
                        if (success) {
                          customPads[slot] = song.id;
                          options.edit().putString("pad." + slot, song.id).apply();
                          message = "Party clip assigned.";
                        } else
                          message =
                              "This clip could not be decoded. Your previous pad is retained.";
                        publish();
                      }
                    });
              });
        });
  }

  void resetPad(int slot) {
    if (assigningPad || slot < 0 || slot >= 8) return;
    padGeneration[slot]++;
    party.reset(slot);
    customPads[slot] = "";
    options.edit().remove("pad." + slot).apply();
    publish();
  }

  void reserve(Song song, String singer) {
    if (!ready || song.id.equals(removingSong) || !songs.contains(song)) return;
    try {
      queue.reserve(UUID.randomUUID(), song, singer);
      message = "Reserved " + song.title;
      saveQueue();
      publish();
    } catch (Exception e) {
      message = "Up Next is full";
      publish();
    }
  }

  void remove(UUID id) {
    queue.remove(id);
    saveQueue();
    publish();
  }

  void move(UUID id, int destination) {
    queue.move(id, destination);
    saveQueue();
    publish();
  }

  public void saveQueue() {
    if (repository == null) return;
    List<ReservationQueue.Entry> saved = queue.snapshot();
    io.execute(
        () -> {
          try {
            repository.saveQueue(saved);
          } catch (Exception e) {
            main.post(
                () -> {
                  message = "The queue could not be saved. It remains available for this session.";
                  publish();
                });
          }
        });
  }

  void next() {
    cancelAutoAdvance();
    if (loading || !ready) return;
    if (removingSong != null || removingBank) {
      advanceAfterRemoval = true;
      return;
    }
    ReservationQueue.Entry entry = queue.peek();
    if (entry == null) {
      message = "Reserve a song first";
      publish();
      return;
    }
    startLocal(entry, true);
  }

  void playSearchResult(Song song, String singer) {
    if (loading || !ready || !foreground || removingSong != null || removingBank
        || !songs.contains(song)) return;
    startLocal(new ReservationQueue.Entry(UUID.randomUUID(), song, singer), false);
  }

  private void prepare(ReservationQueue.Entry entry, boolean queued, int countdownSeconds) {
    loading = true;
    long token = ++generation;
    message = "Opening " + entry.song.title;
    publish();
    File selectedBank = selectedSoundFont;
    preparation.execute(
        () -> {
          try {
            File file = new File(repository.media, entry.song.fileName);
            MidiLyrics.Document midi =
                entry.song.fileName.endsWith(".mid")
                    ? MidiLyrics.decode(file)
                    : null;
            MelodyTarget melody = MelodyTarget.extract(midi);
            boolean keyPresent = midi != null && midi.keyPresent;
            int keySharpsFlats = keyPresent ? midi.keySharpsFlats : 0;
            boolean keyMinor = keyPresent && midi.keyMinor;
            File bank = selectedBank;
            List<MidiLyrics.Cue> cues = midi == null ? Collections.emptyList() : midi.cues;
            long melodyStart = cues.isEmpty() ? 0 : cues.get(0).milliseconds;
            main.post(
                () -> {
                  if (token != generation) return;
                  PlaybackBackend.Callback callbacks =
                      new PlaybackBackend.Callback() {
                        public void ready(PlaybackBackend candidate) {
                          if (token != generation || pending != candidate) {
                            candidate.release();
                            return;
                          }
                          Runnable afterRoute = () -> showReady(candidate);
                          if (!monitor.stopCommunicationForMusic(afterRoute)) afterRoute.run();
                          else {
                            setMonitorNotice("Bluetooth voice playback stopped so the song can play.");
                            publish();
                          }
                        }

                        private boolean stillPending(PlaybackBackend candidate) {
                          if (token != generation || pending != candidate) return false;
                          if (foreground && (!queued || queue.peek() == entry)) return true;
                          discardPendingStart();
                          publish();
                          return false;
                        }

                        private void showReady(PlaybackBackend candidate) {
                          if (!stillPending(candidate)) return;
                          MelodyStartPlan.Schedule startPlan =
                              MelodyStartPlan.at(melodyStart, countdownSeconds,
                                  SystemClock.elapsedRealtime());
                          if (countdownSeconds <= 0 || countdownHost == null) {
                            startReady(candidate);
                            return;
                          }
                          if (!startPlan.startAudioImmediately) {
                            // Unknown melody (non-MIDI or no cues): legacy behavior counts
                            // first and starts the audio when the count ends.
                            Runnable dismiss = countdownHost.show(countdownSeconds,
                                () -> startReady(candidate));
                            countdownEndsAt = SystemClock.elapsedRealtime()
                                + countdownSeconds * 1000L;
                            countdownTotalMs = countdownSeconds * 1000L;
                            if (token == generation && pending == candidate)
                              cancelCountdownView = dismiss;
                            else dismiss.run();
                            return;
                          }
                          // A known intro plays immediately; GO must land on the first sung cue.
                          startReady(candidate);
                          if (startPlan.showCountdown) {
                            long delay =
                                Math.max(
                                    0,
                                    startPlan.countdownAtMillis - SystemClock.elapsedRealtime());
                            long startToken = token;
                            scheduledMelodyCountdown =
                                () -> {
                                  scheduledMelodyCountdown = null;
                                  if (startToken != generation || !foreground
                                      || (player != candidate && pending != candidate)) return;
                                  Runnable showDismiss = countdownHost.show(countdownSeconds,
                                      () -> {});
                                  countdownEndsAt = SystemClock.elapsedRealtime()
                                      + countdownSeconds * 1000L;
                                  countdownTotalMs = countdownSeconds * 1000L;
                                  if (startToken == generation) cancelCountdownView = showDismiss;
                                  else showDismiss.run();
                                };
                            main.removeCallbacks(scheduledMelodyCountdown);
                            main.postDelayed(scheduledMelodyCountdown, delay);
                          }
                        }

                        private void startReady(PlaybackBackend candidate) {
                          countdownEndsAt = -1;
                          countdownTotalMs = 0;
                          if (!stillPending(candidate)) return;
                          if (audio.requestAudioFocus(focus)
                              != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            discardPendingStart();
                            message = "Audio is busy. Try Play again.";
                            publish();
                            return;
                          }
                          pendingHasFocus = true;
                          try {
                            float applyTempo = tempo;
                            int applyPitch = pitch;
                            if (settings.hasSongOverride(entry.song.id)) {
                              applyTempo = settings.getSongTempo(entry.song.id, tempo);
                              applyPitch = settings.getSongPitch(entry.song.id, pitch);
                            }
                            candidate.controls(volume, applyTempo, applyPitch);
                            candidate.start();
                            scheduleEqualizerRetry(candidate);
                          } catch (Exception e) {
                            failed(candidate);
                          }
                        }

                        public void started(PlaybackBackend candidate) {
                          if (player == candidate) {
                            if (!resuming || !foreground) return;
                            resuming = false;
                            scoreWaitingForControls = true;
                            scoreFrameFloor = SystemClock.elapsedRealtime();
                            playing = true;
                            publish();
                            return;
                          }
                          if (!stillPending(candidate)) return;
                          // The successor session already owns the equalizer (startReady
                          // attached it), so releasing the old player must not detach it.
                          releaseCurrent(true);
                          player = candidate;
                          if (!equalizer.isAttached()) scheduleEqualizerRetry(candidate);
                          currentClock = candidate.getClock();
                          pending = null;
                          pendingHasFocus = false;
                          clearCountdown();
                          if (queued) queue.remove(entry.id);
                          current = entry.song;
                          playbackIdentity = entry.id;
                          currentTarget = melody;
                          currentDuration = midi == null ? 0 : midi.durationMilliseconds;
                          currentMelodyStart = melodyStart;
                          currentKeyPresent = keyPresent;
                          currentKeySharpsFlats = keySharpsFlats;
                          currentKeyMinor = keyMinor;
                          resetScore();
                          singer = entry.singer;
                          lyrics = cues;
                          playing = true;
                          loading = false;
                          applyStageShuffleBoundary(true);
                          message = entry.song.title;
                          maybeAutoSearchLyrics();
                          if (queued) saveQueue();
                          publish();
                        }

                        public void completed(PlaybackBackend completed) {
                          if (player != completed) return;
                          boolean applausePlayed = foreground && !auxiliaryInterrupted
                              && party.customReady(PartySounds.APPLAUSE)
                              && audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                              && party.trigger(PartySounds.APPLAUSE);
                          ScoreResultTier completedTier = finishScore(applausePlayed);
                          played.add(current);
                          if (played.size() > 10000) played.remove(0);
                          releaseCurrent();
                          current = null;
                          applyStageShuffleBoundary(false);
                          lyrics = Collections.emptyList();
                          publish();
                          if (!loading) nextIfReserved(completedTier);
                        }

                        public void failed(PlaybackBackend failed) {
                          if (pending == failed) {
                            discardPendingStart();
                            message =
                                "This song or SoundFont could not be played. The reservation"
                                    + " remains in Up Next.";
                          } else if (player == failed) {
                            releaseCurrent();
                            message = "Playback stopped because the media could not be decoded.";
                          } else {
                            failed.release();
                            return;
                          }
                          publish();
                        }
                      };
                  try {
                    PlaybackBackend created =
                        midi == null
                            ? new FileAudioPlayback(file, callbacks)
                            : new SoundFontPlayback(bank, midi, callbacks);
                    pending = created;
                  } catch (Exception | LinkageError e) {
                    clearCountdown();
                    loading = false;
                    diagnostics.record("Prepare song failed", e);
                    message = "Could not prepare this song. Your reservation remains in Up Next.";
                    publish();
                  }
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (token != generation) return;
                  clearCountdown();
                  loading = false;
                  message = "This music file is unreadable or exceeds supported MIDI limits.";
                  publish();
                });
          }
        });
  }

  void setVolume(float value) {
    if (!Float.isFinite(value)) return;
    float adjusted = Math.max(0, Math.min(1, value));
    if (volume == adjusted) return;
    volume = adjusted;
    settings.setMasterVolume(volume);
    try {
      if (player != null) player.controls(volume, effectiveTempo(), effectivePitch());
    } catch (Exception e) {
      message = "Could not apply volume";
    }
  }

  void controls(float volume, float tempo, int pitch) {
    if (!Float.isFinite(volume) || !Float.isFinite(tempo)) return;
    float oldTempo = this.tempo;
    int oldPitch = this.pitch;
    this.volume = Math.min(1, Math.max(0, volume));
    this.tempo = Math.min(2, Math.max(0.5f, tempo));
    this.pitch = Math.min(12, Math.max(-12, pitch));
    if (oldTempo != this.tempo || oldPitch != this.pitch) resetScore();
    // Adjustments made during an active song are remembered for that song.
    if ((oldTempo != this.tempo || oldPitch != this.pitch)
        && current != null && player != null) {
      try {
        settings.setSongOverride(current.id, this.pitch, this.tempo);
      } catch (Exception ignored) {
        // Override persistence is best effort and must never block playback.
      }
    }
    try {
      if (player != null) player.controls(this.volume, this.tempo, this.pitch);
    } catch (Exception e) {
      message = "This audio decoder cannot apply that playback setting.";
    }
    try {
      settings.setMasterVolume(this.volume);
      settings.setTempo(this.tempo);
      settings.setPitch(this.pitch);
    } catch (Exception ignored) {
      // Settings persistence is best effort and must never block playback.
    }
    publish();
  }

  public void setPitch(int pitch) {
    controls(volume, tempo, Math.min(12, Math.max(-12, pitch)));
  }

  public void setTempo(float tempo) {
    controls(volume, Math.min(2, Math.max(0.5f, tempo)), pitch);
  }

  PlaybackSettingsBackup snapshotPlaybackSettings() {
    return new PlaybackSettingsBackup(volume, tempo, pitch);
  }

  boolean canRestorePlaybackSettings() {
    return !playing && !loading && !resuming && current == null && player == null
        && pending == null && countingEntry == null;
  }

  /** Main-thread only; never calls controls(), which can write per-song overrides. */
  boolean restorePlaybackSettings(PlaybackSettingsBackup backup) {
    if (!foreground || !canRestorePlaybackSettings()) return false;
    settings.restorePlaybackDefaults(backup);
    volume = backup.volume;
    tempo = backup.tempo;
    pitch = backup.pitch;
    publish();
    return true;
  }

  private void nextIfReserved(ScoreResultTier tier) {
    ReservationQueue.Entry entry = queue.peek();
    if (entry == null) {
      message = "Set finished. Save your played songs as Favorites in Menu → Presets.";
      if (!microphone.enabled()) audio.abandonAudioFocusRequest(focus);
      publish();
      return;
    }
    cancelAutoAdvance();
    autoAdvanceEntryId = entry.id;
    long delay = tier == null ? 3000L : ScoreRevealPlan.totalMillis(tier) + 5000L;
    autoAdvance =
        () -> {
          autoAdvance = null;
          autoAdvanceEntryId = null;
          if (!foreground || loading || current != null || queue.peek() != entry) return;
          next(); // The manual path, including prepare(), countdown, lyrics and scoring.
        };
    main.postDelayed(autoAdvance, delay);
  }

  /** The music-session equalizer owner; UI reads bands from here. */
  public MusicEqualizer equalizer() {
    return equalizer;
  }

  /** Tempo the active song actually plays at (per-song override wins). */
  public float effectiveTempo() {
    return current != null && settings.hasSongOverride(current.id)
        ? settings.getSongTempo(current.id, tempo)
        : tempo;
  }

  /** Pitch shift the active song actually plays at (per-song override wins). */
  public int effectivePitch() {
    return current != null && settings.hasSongOverride(current.id)
        ? settings.getSongPitch(current.id, pitch)
        : pitch;
  }

  /** Offset of the first sung cue of the current song, 0 when unknown or nothing plays. */
  public long melodyStartMillis() {
    return current != null ? currentMelodyStart : 0;
  }

  boolean currentSongHasOverride() {
    return current != null && settings.hasSongOverride(current.id);
  }

  /** Forgets the active song override and re-applies the app-wide defaults. */
  void clearCurrentSongOverride() {
    if (current == null) return;
    settings.clearSongOverride(current.id);
    if (player != null) {
      try {
        player.controls(volume, tempo, pitch);
      } catch (Exception ignored) {
      }
    }
    message = "Per-song key/tempo reset to your defaults.";
    publish();
  }

  void seekTo(long milliseconds) {
    if (player != null) player.seekTo(milliseconds);
  }

  /** Restarts the current song from the top (transport Previous). */
  void restartCurrent() {
    cancelAutoAdvance();
    if (player == null || current == null) return;
    seekTo(0);
    if (!playing && !loading) toggle();
    else publish();
  }

  void toggle() {
    cancelAutoAdvance();
    if (!foreground || loading || countingEntry != null) return;
    if (player == null) {
      next();
      return;
    }
    if (playing || resuming) pause();
    else if (audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      try {
        resuming = true;
        player.start();
      } catch (IllegalStateException e) {
        resuming = false;
        message = "Please choose Next to reopen this song";
        publish();
      }
    }
  }

  void pause() {
    cancelAutoAdvance();
    cancelScheduledMelodyCountdown();
    scoreWaitingForControls = true;
    scoreFrameFloor = SystemClock.elapsedRealtime();
    if (loading || pending != null) {
      discardPendingStart(); // Also retire work still parsing on IO.
      publish();
    }
    if (player != null && (playing || resuming)) {
      resuming = false;
      try {
        player.pause();
      } catch (IllegalStateException ignored) {
      }
      playing = false;
      publish();
    }
  }

  private void maybeAutoSearchLyrics() {
    if (!settings.getAutoLyrics() || !foreground || current == null || playbackIdentity == null
        || lyrics.isEmpty() || currentDuration <= 0) return;
    long now = SystemClock.elapsedRealtime();
    if (autoLyricSearchBusy || now < autoLyricSearchAt) return;
    autoLyricSearchBusy = true;
    autoLyricSearchAt = now + 8000;
    long request = ++lyricLookupGeneration;
    String title = current.title;
    String artist = current.artist;
    if (title.isEmpty()) { autoLyricSearchBusy = false; return; }
    ph.kanta.reborn.lyrics.LrclibClient client = new ph.kanta.reborn.lyrics.LrclibClient(context);
    java.util.List<MidiLyrics.Cue> cached = client.getCached(title, artist, currentDuration);
    if (cached != null && !cached.isEmpty()) {
      autoLyricSearchBusy = false;
      lyrics = cached;
      message = "Loaded lyrics from local cache."; publish();
      return;
    }
    message = "Searching LRCLIB…"; publish();
    lyricWorker.execute(() -> {
      java.util.List<ph.kanta.reborn.lyrics.LrclibClient.Result> results = Collections.emptyList();
      String error = null;
      try { results = new ph.kanta.reborn.lyrics.LrclibClient(context).search(title, artist); }
      catch (IOException | RuntimeException unavailable) { error = "LRCLIB unavailable"; }
      final java.util.List<ph.kanta.reborn.lyrics.LrclibClient.Result> found = results;
      final String failure = error;
      main.post(() -> {
        autoLyricSearchBusy = false;
        if (!foreground || request != lyricLookupGeneration || current == null
            || !playbackIdentity.equals(request) || !lyrics.isEmpty()) return;
        if (failure != null || found.isEmpty()) { message = "No lyrics found on LRCLIB."; publish(); return; }
        ph.kanta.reborn.lyrics.LrclibClient.Result best = null;
        for (ph.kanta.reborn.lyrics.LrclibClient.Result r : found) {
          if (r.instrumental || r.timedCues.isEmpty()) continue;
          if (r.duration > 0 && Math.abs(r.duration * 1000 - currentDuration) > 8000) continue;
          if (r.artist.length() > 0 && !r.artist.toLowerCase(java.util.Locale.ROOT).contains(current.artist.toLowerCase(java.util.Locale.ROOT))
              && !current.artist.toLowerCase(java.util.Locale.ROOT).contains(r.artist.toLowerCase(java.util.Locale.ROOT))) continue;
          best = r; break;
        }
        if (best == null) { message = "No confident LRCLIB match."; publish(); return; }
        java.util.List<MidiLyrics.Cue> timed = best.timedCues;
        if (timed.isEmpty()) { message = "No usable lyrics in this version."; publish(); return; }
        lyrics = timed;
        client.cacheIfConfident(best, currentDuration);
        message = "Auto-selected line-timed lyrics from LRCLIB."; publish();
      });
    });
  }

  /** Explicit search only. Does not scan/upload a library or replace embedded lyrics. */
  void searchOnlineLyrics(UUID identity, String title, String artist,
      java.util.function.Consumer<java.util.List<ph.kanta.reborn.lyrics.LrclibClient.Result>> completed) {
    if (!foreground || current == null || identity == null || !identity.equals(playbackIdentity)
        || !lyrics.isEmpty()) return;
    long now = SystemClock.elapsedRealtime();
    if (lyricSearchBusy || now < nextLyricSearchAt) {
      message = "A lyric search is already running, or was just sent. Please wait.";
      publish();
      return;
    }
    lyricSearchBusy = true;
    nextLyricSearchAt = now + 1000;
    long request = ++lyricLookupGeneration;
    message = "Searching LRCLIB…";
    publish();
    lyricWorker.execute(() -> {
      java.util.List<ph.kanta.reborn.lyrics.LrclibClient.Result> results = Collections.emptyList();
      String error = null;
      try { results = new ph.kanta.reborn.lyrics.LrclibClient(context).search(title, artist); }
      catch (IOException | RuntimeException unavailable) {
        error = "Could not fetch lyrics from LRCLIB. Check your connection and try again later.";
      }
      final java.util.List<ph.kanta.reborn.lyrics.LrclibClient.Result> found = results;
      final String failure = error;
      main.post(() -> {
        lyricSearchBusy = false;
        if (!foreground || request != lyricLookupGeneration || !identity.equals(playbackIdentity)) return;
        message = failure != null ? failure : found.isEmpty()
            ? "No matching lyrics found on LRCLIB." : "Choose the correct LRCLIB version.";
        publish();
        completed.accept(failure == null ? found : Collections.emptyList());
      });
    });
  }

  boolean applyOnlineLyrics(UUID identity, long request,
      ph.kanta.reborn.lyrics.LrclibClient.Result result) {
    if (!foreground || request != lyricLookupGeneration || identity == null
        || !identity.equals(playbackIdentity) || !lyrics.isEmpty() || result.instrumental) return false;
    java.util.List<MidiLyrics.Cue> timed = result.timedCues;
    if (timed.isEmpty()) return false;
    lyrics = timed;
    new ph.kanta.reborn.lyrics.LrclibClient(context).cacheIfConfident(result, currentDuration);
    message = "Line-timed lyrics from LRCLIB · cached locally.";
    publish();
    return true;
  }

  void stop() {
    cancelAutoAdvance();
    ++lyricLookupGeneration;
    discardPendingStart();
    releaseCurrent();
    current = null;
    applyStageShuffleBoundary(false);
    lyrics = Collections.emptyList();
    if (!microphone.enabled()) audio.abandonAudioFocusRequest(focus);
    publish();
  }

  void startKantaConnectHost() {
    kantaConnectHost.start();
  }

  void stopKantaConnectHost() {
    kantaConnectHost.stop();
  }

  String handleKantaConnectRequest(String action, String argument, String singer) {
    try {
      switch (action) {
        case "snapshot":
          return snapshotJson();
        case "search":
          return searchJson(argument);
        case "reserve":
          return reserveJson(argument, singer);
        case "cancel":
          return cancelJson(argument);
        case "play":
          return playJson();
        case "pause":
          return pauseJson();
        case "next":
          return nextJson();
        default:
          return smartError("Unknown action: " + action);
      }
    } catch (Exception e) {
      return smartError(String.valueOf(e.getMessage()));
    }
  }

  private String smartError(String message) {
    try {
      org.json.JSONObject response = new org.json.JSONObject();
      response.put("status", "error");
      response.put("error", message == null ? "" : message);
      return response.toString();
    } catch (Exception nested) {
      return "{}";
    }
  }

  private String smartOk() {
    try {
      org.json.JSONObject response = new org.json.JSONObject();
      response.put("status", "ok");
      return response.toString();
    } catch (Exception e) {
      return "{}";
    }
  }

  private String snapshotJson() {
    try {
      org.json.JSONArray array = new org.json.JSONArray();
      for (ReservationQueue.Entry e : queue.snapshot()) {
        org.json.JSONObject item = new org.json.JSONObject();
        item.put("id", String.valueOf(e.id));
        item.put("title", e.song.title == null ? "" : e.song.title);
        item.put("artist", e.song.artist == null ? "" : e.song.artist);
        item.put("singer", e.singer == null ? "" : e.singer);
        array.put(item);
      }
      org.json.JSONObject response = new org.json.JSONObject();
      response.put("status", "ok");
      response.put("queue", array);
      response.put("current", current == null ? "" : current.title);
      response.put("playing", playing);
      return response.toString();
    } catch (Exception e) {
      return smartError(String.valueOf(e.getMessage()));
    }
  }

  private String searchJson(String query) {
    try {
      java.util.List<Song> found = new java.util.ArrayList<>();
      for (Song s : songs) {
        if (s.matches(query)) found.add(s);
        if (found.size() >= 50) break;
      }
      org.json.JSONArray array = new org.json.JSONArray();
      for (Song s : found) {
        org.json.JSONObject item = new org.json.JSONObject();
        item.put("id", s.id);
        item.put("title", s.title == null ? "" : s.title);
        item.put("artist", s.artist == null ? "" : s.artist);
        item.put("code", s.code == null ? "" : s.code);
        array.put(item);
      }
      org.json.JSONObject response = new org.json.JSONObject();
      response.put("status", "ok");
      response.put("songs", array);
      return response.toString();
    } catch (Exception e) {
      return smartError(String.valueOf(e.getMessage()));
    }
  }

  private String reserveJson(String argument, String singer) {
    try {
      for (Song s : songs) {
        if (s.id.equals(argument)) {
          reserve(s, singer);
          org.json.JSONObject response = new org.json.JSONObject();
          response.put("status", "ok");
          response.put("reserved", s.title == null ? "" : s.title);
          return response.toString();
        }
      }
      return smartError("Song not found");
    } catch (Exception e) {
      return smartError(String.valueOf(e.getMessage()));
    }
  }

  private String cancelJson(String argument) {
    try {
      cancelAutoAdvance();
      queue.remove(java.util.UUID.fromString(argument));
      org.json.JSONObject response = new org.json.JSONObject();
      response.put("status", "ok");
      response.put("cancelled", argument);
      return response.toString();
    } catch (Exception e) {
      return smartError(String.valueOf(e.getMessage()));
    }
  }

  private String playJson() {
    try {
      if (player == null) {
        next();
      } else if (!playing) {
        toggle();
      }
      org.json.JSONObject response = new org.json.JSONObject();
      response.put("status", "ok");
      response.put("playing", playing);
      return response.toString();
    } catch (Exception e) {
      return smartError(String.valueOf(e.getMessage()));
    }
  }

  private String pauseJson() {
    try {
      pause();
      org.json.JSONObject response = new org.json.JSONObject();
      response.put("status", "ok");
      response.put("playing", playing);
      return response.toString();
    } catch (Exception e) {
      return smartError(String.valueOf(e.getMessage()));
    }
  }

  private String nextJson() {
    try {
      next();
    } catch (Exception e) {
      return smartError(String.valueOf(e.getMessage()));
    }
    return smartOk();
  }

  private void releaseCurrent() {
    releaseCurrent(false);
  }

  /**
   * Releases the current player. With {@code keepEqualizer} the equalizer stays bound — the
   * start acknowledgement of a successor song whose session already owns it.
   */
  private void releaseCurrent(boolean keepEqualizer) {
    resuming = false;
    ++scoreRequest;
    if (score != null) score.cancel();
    score = null;
    playbackIdentity = null;
    currentTarget = null;
    currentDuration = 0;
    currentKeyPresent = false;
    currentKeySharpsFlats = 0;
    currentKeyMinor = false;
    lastScoreReading = -1;
    currentClock = null;

    cancelEqualizerRetry();
    if (!keepEqualizer) equalizer.detach();
    if (player != null) {
      player.release();
      player = null;
    }
    playing = false;
  }

  /**
   * Attaches the equalizer to the backend's media session now, and keeps retrying while the
   * backend stays live: some devices only accept a session effect once audio is audible, which
   * happens a moment after {@link PlaybackBackend#start()}.
   */
  private void scheduleEqualizerRetry(PlaybackBackend candidate) {
    main.removeCallbacks(equalizerRetry);
    equalizerRetries = 0;
    if (equalizer.attach(candidate.getAudioSessionId())) {
      equalizerRetryFor = null;
      return;
    }
    equalizerRetryFor = candidate;
    main.postDelayed(equalizerRetry, 400);
  }

  private void cancelEqualizerRetry() {
    equalizerRetryFor = null;
    main.removeCallbacks(equalizerRetry);
  }

  private final Runnable equalizerRetry = this::tickEqualizerRetry;

  private void tickEqualizerRetry() {
    PlaybackBackend candidate = equalizerRetryFor;
    if (candidate == null || (candidate != player && candidate != pending)
        || equalizer.isAttached()) {
      equalizerRetryFor = null;
      return;
    }
    if (equalizer.attach(candidate.getAudioSessionId())) {
      equalizerRetryFor = null;
      return;
    }
    if (++equalizerRetries >= 15) {
      equalizerRetryFor = null;
      return;
    }
    main.postDelayed(equalizerRetry, 400);
  }

  public long position() {
    try {
      return player == null ? 0 : player.position();
    } catch (IllegalStateException e) {
      return 0;
    }
  }

  public long duration() {
    try {
      return Math.max(0, player == null ? currentDuration : player.duration());
    } catch (IllegalStateException e) {
      return Math.max(0, currentDuration);
    }
  }

  public RenderedPlaybackClock getClock() {
    if (player != null) return player.getClock();
    return currentClock;
  }

  void importSongs(List<Uri> files) {
    if (!ready || importing) return;
    importCancelled.set(false);
    importing = true;
    libraryImporting = true;
    message = "Importing your selected songs…";
    publish();
    io.execute(
        () -> {
          int imported = 0, duplicates = 0, failed = 0;
          List<String> reasons = java.util.Collections.emptyList();
          try {
            LibraryRepository.ImportBatchResult outcome =
                repository.importBatch(files, importCancelled::get);
            imported = outcome.imported;
            duplicates = outcome.duplicates;
            failed = outcome.failed;
            reasons = outcome.reasons;
          } catch (Exception e) {
            diagnostics.record("Import music failed", e);
            failed = files.size();
          }
          if (failed > 0 && !importCancelled.get()) {
            diagnostics.record("Music import: " + failed + " file(s) skipped", null);
          }
          List<Song> loaded = repository.songs();
          File bank = repository.soundFont();
          String bankLabel = repository.soundFontName();
          List<LibraryRepository.SoundFontEntry> banks = repository.soundFonts();
          StringBuilder result = new StringBuilder("Imported ").append(imported).append(" file(s)");
          if (duplicates > 0) result.append(", ").append(duplicates).append(" already in your library");
          if (failed > 0) {
            result.append(". ").append(failed).append(" file(s) skipped");
            int shown = Math.min(3, reasons.size());
            for (int i = 0; i < shown; i++) result.append(" \u2022 ").append(reasons.get(i));
          }
          result.append(".");
          final String importSummary = result.toString();
          final boolean importSucceeded = failed == 0;
          main.post(
              () -> {
                songs = loaded;
                selectedSoundFont = bank;
                soundFontName = bankLabel;
                soundFonts = banks;
                importing = false;
                libraryImporting = false;
                message = importSummary;
                completeImportProgress(importSucceeded, importSummary);
                publish();
              });
        });
  }

  /** True once, when a table import failed because no bundled key unlocked the pair. */
  boolean drainLegacyManualKeyOffer() {
    boolean offered = legacyManualKeyOffer;
    legacyManualKeyOffer = false;
    return offered;
  }

  boolean importLegacyPair(android.net.Uri indexUri, android.net.Uri blobUri,
      ph.kanta.reborn.core.legacy.LegacyImportKey credentials) {
    return importLegacyPair(indexUri, blobUri, credentials == null
        ? java.util.Collections.<ph.kanta.reborn.core.legacy.LegacyImportKey>emptyList()
        : java.util.Collections.singletonList(credentials));
  }

  // Takes ownership of every credential, including rejected/busy requests. The candidate that
  // passes LegacyFileContainerImporter.probeKey is consumed by the import itself; all others
  // are cleared here.
  boolean importLegacyPair(android.net.Uri indexUri, android.net.Uri blobUri,
      java.util.List<ph.kanta.reborn.core.legacy.LegacyImportKey> candidates) {
    if (candidates == null || candidates.isEmpty()) return false;
    if (!ready || importing || indexUri == null || blobUri == null) {
      for (ph.kanta.reborn.core.legacy.LegacyImportKey credential : candidates) credential.close();
      return false;
    }
    importCancelled.set(false); importing = true; libraryImporting = true; message = "Importing legacy song library…"; publish();
    try {
      io.execute(() -> {
        File index = null, blob = null; int good = 0; String resultMessage;
        ph.kanta.reborn.core.legacy.LegacyImportKey chosen = null;
        try {
          index = File.createTempFile("legacy-index-", ".idx", context.getCacheDir());
          blob = File.createTempFile("legacy-blob-", ".blob", context.getCacheDir());
          copyLegacyUri(indexUri, index, 16L * 1024 * 1024, "IDX", "16 MiB", "first");
          copyLegacyUri(blobUri, blob, 1024L * 1024 * 1024L, "BLOB", "1 GiB", "second");
          importProgressDispatcher.onProgress(0, 0, "Checking the song library files…");
          for (ph.kanta.reborn.core.legacy.LegacyImportKey candidate : candidates) {
            if (ph.kanta.reborn.core.legacy.LegacyFileContainerImporter.probeKey(index, blob, candidate)) {
              chosen = candidate;
              break;
            }
          }
          if (chosen == null) {
            legacyManualKeyOffer = candidates.size() > 1;
            resultMessage = "These files could not be unlocked. You can enter a key manually.";
          } else {
            ph.kanta.reborn.core.legacy.LegacyFileContainerImporter.ImportResult result =
                ph.kanta.reborn.core.legacy.LegacyFileContainerImporter.importPair(
                    index, blob, new File(repository.media, "legacy-import"), chosen, importCancelled::get,
                    (done, total) -> importProgressDispatcher.onProgress(total, done,
                        "Reading legacy songs: " + done + "/" + total + "…"));
            if (!result.songs.isEmpty()) {
              importProgressDispatcher.onProgress(0, 0, "Adding songs to your library…");
              good = repository.importLegacyResult(result);
            }
            resultMessage = "Imported " + good + " legacy song(s); " + result.skippedCount + " record(s) skipped.";
          }
        } catch (Exception failure) {
          if (importCancelled.get() || failure instanceof java.io.InterruptedIOException) {
            resultMessage = "Legacy import cancelled.";
          } else {
            resultMessage = "Legacy import failed: " + (failure.getMessage() == null ? "unsupported container" : failure.getMessage());
          }
        } finally {
          for (ph.kanta.reborn.core.legacy.LegacyImportKey credential : candidates) {
            if (credential != chosen) credential.close();
          }
          if (index != null) index.delete(); if (blob != null) blob.delete();
        }
        final int imported = good; final String messageValue = resultMessage;
        main.post(() -> { songs = repository.songs(); importing = false; libraryImporting = false; message = messageValue; completeImportProgress(imported > 0, messageValue); publish(); });
      });
      return true;
    } catch (java.util.concurrent.RejectedExecutionException rejected) {
      for (ph.kanta.reborn.core.legacy.LegacyImportKey credential : candidates) credential.close();
      importing = false;
      libraryImporting = false;
      message = "Import could not start. Please try again.";
      publish();
      return false;
    }
  }

  void downloadFluidR3GM() {
    downloadSoundFont(SoundFontDownload.Bank.FLUID);
  }

  void downloadMuseScoreGeneral() {
    downloadSoundFont(SoundFontDownload.Bank.MUSESCORE);
  }

  void downloadSGMv201() {
    downloadSoundFont(SoundFontDownload.Bank.SGM);
  }

  private void downloadSoundFont(SoundFontDownload.Bank bank) {
    DownloadProgressListener listener = downloadProgressListener;
    downloadProgressListener = null;
    if (importing || repository == null) {
      if (listener != null) listener.onComplete(false, "Music library is busy. Please try again.");
      return;
    }
    importing = true;
    libraryImporting = true;
    message = "Preparing " + bank.label + " download...";
    publish();
    try {
      io.execute(() -> {
        try (InputStream license = context.getAssets().open(bank.licenseName)) {
          boolean installed = SoundFontDownload.install(repository.media, bank, license,
              (phase, copied, expected) -> main.post(() -> {
                message = phase + " " + bank.label + "...";
                if (phase.equals("Downloading")) {
                  message += expected > 0
                      ? String.format(Locale.ROOT, " %d%% (%.1f / %.1f MiB)",
                          copied * 100 / expected, copied / 1048576.0, expected / 1048576.0)
                      : String.format(Locale.ROOT, " %.1f MiB", copied / 1048576.0);
                }
                publish();
                if (listener != null) listener.onProgress(message);
              }), SessionController::validateDownloadedSoundFont);
          File destination = new File(repository.media, bank.fileName);
          if (!installed && repository.soundFonts().stream()
              .noneMatch(entry -> entry.fileName.equals(bank.fileName))) {
            // Recover a completed download from old builds that never registered it.
            validateDownloadedSoundFont(destination);
          }
          repository.registerDownloadedSoundFont(bank);
          List<LibraryRepository.SoundFontEntry> banks = repository.soundFonts();
          main.post(() -> {
            soundFonts = banks;
            finishSoundFontDownload(listener, true, bank.label + (installed
                ? " downloaded successfully. Select it from SoundFonts."
                : " is already available. Select it from SoundFonts."));
          });
        } catch (Exception failure) {
          String detail = failure.getMessage();
          main.post(() -> finishSoundFontDownload(listener, false, "Download failed: "
              + (detail == null ? "Could not install " + bank.label : detail)));
        }
      });
    } catch (RejectedExecutionException failure) {
      finishSoundFontDownload(listener, false, "Download could not start. Please try again.");
    }
  }

  private static void validateDownloadedSoundFont(File file) throws IOException {
    long handle = NativeSoundFont.open(file.getAbsolutePath());
    if (handle == 0) throw new IOException("SoundFont could not be opened on this device");
    NativeSoundFont.close(handle);
  }

  private void finishSoundFontDownload(DownloadProgressListener listener, boolean success, String status) {
    importing = false;
    libraryImporting = false;
    message = status;
    publish();
    if (listener != null) listener.onComplete(success, status);
  }

  private void copyLegacyUri(android.net.Uri uri, File target, long maxBytes,
      String role, String limit, String step) throws IOException {
    importProgressDispatcher.onProgress(0, 0, "Copying " + role + " file…");
    try (InputStream source = context.getContentResolver().openInputStream(uri);
         FileOutputStream out = new FileOutputStream(target)) {
      if (source == null) throw new IOException("Cannot open the " + role + " file.");
      BufferedInputStream in = new BufferedInputStream(source, 65536);
      in.mark(4);
      if ("IDX".equals(role) && in.read() == 'M' && in.read() == 'K'
          && in.read() == 'M' && in.read() == 'D') {
        throw new IOException("That looks like the BLOB file. Select the IDX file first.");
      }
      in.reset();
      byte[] buffer = new byte[65536];
      long copied = 0;
      while (true) {
        if (importCancelled.get()) throw new InterruptedIOException();
        int count = in.read(buffer);
        if (count == -1) break;
        long previous = copied;
        copied += count;
        if (copied > maxBytes) throw new IOException(role + " file exceeds " + limit
            + ". Select the " + role + " file " + step + ".");
        out.write(buffer, 0, count);
        if ((previous >> 23) != (copied >> 23)) {
          importProgressDispatcher.onProgress(0, copied,
              "Copying " + role + " file: " + (copied >> 20) + " MiB…");
        }
      }
      if (copied == 0) throw new IOException("The " + role + " file is empty.");
      out.getFD().sync();
    }
  }

  String activeSoundFontFileName() {
    return selectedSoundFont == null ? "kanta-tone.sf2" : selectedSoundFont.getName();
  }


  private String removingSong;
  private boolean removingBank;
  private boolean advanceAfterRemoval;

  boolean canRemoveLibraryContent() {
    return ready && !importing && removingSong == null && !removingBank;
  }

  void deleteSong(String id, java.util.function.Consumer<String> completed) {
    if (!canRemoveLibraryContent()) {
      completed.accept("Wait for the library to finish, then try again.");
      return;
    }
    if (loading || (current != null && current.id.equals(id))) {
      completed.accept("Finish or stop the song before removing it.");
      return;
    }
    if (assigningPad || id.equals(loopSongId) || java.util.Arrays.asList(customPads).contains(id)) {
      completed.accept("Finish any pad setup and clear this song from Intermission and the party pads before removing it.");
      return;
    }
    removingSong = id;
    io.execute(() -> {
      try {
        boolean deleted = repository.deleteSong(id);
        List<Song> updated = repository.songs();
        List<LibraryRepository.SavedSet> sets = repository.sets();
        main.post(() -> {
          songs = updated;
          savedSets = sets;
          for (ReservationQueue.Entry entry : queue.snapshot())
            if (entry.song.id.equals(id)) queue.remove(entry.id);
          played.removeIf(song -> song.id.equals(id));
          finishSongRemoval(completed, deleted ? "Song removed from your library."
              : "Song removed from your library, but its stored copy could not be erased.");
        });
      } catch (Exception failure) {
        main.post(() -> finishSongRemoval(completed, "Could not remove that song."));
      }
    });
  }

  private void finishSongRemoval(java.util.function.Consumer<String> completed, String status) {
    removingSong = null;
    boolean advance = advanceAfterRemoval;
    advanceAfterRemoval = false;
    publish();
    completed.accept(status);
    if (advance && foreground && current == null && !loading) nextIfReserved(null);
  }

  void deleteSoundFont(String fileName, java.util.function.Consumer<String> completed) {
    if (!canRemoveLibraryContent()) {
      completed.accept("Wait for the library to finish, then try again.");
      return;
    }
    // A previous bank can still belong to the loaded MIDI backend after the user switches banks.
    if (loading || player instanceof SoundFontPlayback) {
      completed.accept("Finish or stop the MIDI song before removing a SoundFont.");
      return;
    }
    removingBank = true;
    io.execute(() -> {
      try {
        boolean deleted = repository.deleteSoundFont(fileName);
        File bank = repository.soundFont();
        String label = repository.soundFontName();
        List<LibraryRepository.SoundFontEntry> banks = repository.soundFonts();
        main.post(() -> {
          selectedSoundFont = bank;
          soundFontName = label;
          soundFonts = banks;
          finishBankRemoval(completed, deleted ? "SoundFont removed."
              : "SoundFont removed from your library, but its stored copy could not be erased.");
        });
      } catch (Exception failure) {
        main.post(() -> finishBankRemoval(completed, "Could not remove that SoundFont."));
      }
    });
  }

  private void finishBankRemoval(java.util.function.Consumer<String> completed, String status) {
    removingBank = false;
    finishSongRemoval(completed, status);
  }

  void selectSoundFont(String fileName) {
    if (!ready || importing) return;
    io.execute(
        () -> {
          try {
            repository.selectSoundFont(fileName);
            File bank = repository.soundFont();
            String label = repository.soundFontName();
            List<LibraryRepository.SoundFontEntry> banks = repository.soundFonts();
            main.post(() -> {
              selectedSoundFont = bank;
              soundFontName = label;
              soundFonts = banks;
              message = "Active SoundFont: " + label + ". Changes apply to the next song.";
              publish();
            });
          } catch (Exception failure) {
            main.post(() -> { message = "Could not activate that SoundFont. " + failure.getMessage(); publish(); });
          }
        });
  }

  void setImportProgressListener(LibraryRepository.ImportProgressListener listener) {
    importProgressListener = listener;
    if (repository != null)
      repository.setImportProgressListener(
          listener == null ? null : importProgressDispatcher);
  }

  private void completeImportProgress(boolean success, String message) {
    LibraryRepository.ImportProgressListener listener = importProgressListener;
    importProgressListener = null;
    if (repository != null) repository.setImportProgressListener(null);
    if (listener != null) listener.onComplete(success, message);
  }

  void cancelImports() {
    importCancelled.set(true);
  }

  void importFolder(Uri tree) {
    if (!ready || importing) return;
    importing = true;
    libraryImporting = true;
    importCancelled.set(false);
    message = "Finding music in your selected folder…";
    publish();
    folderWorker.execute(
        () -> {
          try {
            List<Uri> files =
                DocumentTreeScanner.scan(context.getContentResolver(), tree, importCancelled::get);
            main.post(
                () -> {
                  importing = false;
                  if (importCancelled.get()) {
                    libraryImporting = false;
                    message = "Folder import cancelled.";
                    completeImportProgress(false, message);
                    publish();
                    return;
                  }
                  libraryImporting = false;
                  importSongs(files);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  importing = false;
                  libraryImporting = false;
                  message = "The folder is unavailable, cancelled or exceeds the scan limit.";
                  completeImportProgress(false, message);
                  publish();
                });
          }
        });
  }

  /**
   * The single "Add Karaoke Folder" entry point: one folder may hold songs, background videos
   * and photos, and a legacy index/blob pair. Each group is routed to the destination KANTA
   * already owns, in that order, one group at a time.
   */
  void importKaraokeFolder(Uri tree) {
    if (!ready || importing) return;
    importing = true;
    importCancelled.set(false);
    message = "Reading your selected folder…";
    publish();
    folderWorker.execute(
        () -> {
          try {
            DocumentTreeScanner.Classification found =
                DocumentTreeScanner.scanClassified(
                    context.getContentResolver(), tree, importCancelled::get);
            main.post(() -> {
              importing = false;
              karaokeNextStep(found, 0);
            });
          } catch (Exception e) {
            main.post(() -> {
              importing = false;
              message = "The folder is unavailable, cancelled or exceeds the scan limit.";
              completeImportProgress(false, message);
              publish();
            });
          }
        });
  }

  private void karaokeNextStep(DocumentTreeScanner.Classification found, int step) {
    if (importCancelled.get()) return;
    boolean started = false;
    switch (step) {
      case 0:
        if (found.legacyIndex != null && found.legacyBlob != null) {
          pendingLegacyIndexUri = found.legacyIndex;
          pendingLegacyBlobUri = found.legacyBlob;
          started = importLegacyPair(found.legacyIndex, found.legacyBlob, legacyTableCandidates());
        }
        break;
      case 1:
        if (!found.songs.isEmpty()) {
          importSongs(found.songs);
          started = importing;
        }
        break;
      case 2:
        if (!found.videos.isEmpty()) {
          importStageBatch(found.videos, true);
          started = importing;
        }
        break;
      case 3:
        if (!found.photos.isEmpty()) {
          importStageBatch(found.photos, false);
          started = importing;
        }
        break;
      default:
        karaokeFinished(found);
        return;
    }
    if (started) karaokePoll(found, step + 1, SystemClock.uptimeMillis());
    else karaokeNextStep(found, step + 1);
  }

  /** Waits for the running group to clear the import flag, then starts the next group. */
  private void karaokePoll(DocumentTreeScanner.Classification found, int step, long startedAt) {
    main.postDelayed(
        () -> {
          if (!importing) {
            karaokeNextStep(found, step);
          } else if (SystemClock.uptimeMillis() - startedAt > 30L * 60L * 1000L) {
            importing = false;
            karaokeFinished(found);
          } else {
            karaokePoll(found, step, startedAt);
          }
        },
        300);
  }

  /** Reads the bundled key table exactly like the manual legacy picker does. */
  private List<ph.kanta.reborn.core.legacy.LegacyImportKey> legacyTableCandidates() {
    List<ph.kanta.reborn.core.legacy.LegacyImportKey> candidates = new ArrayList<>();
    try (java.io.InputStream asset =
        context.getAssets().open("legacy-import-keys.txt")) {
      for (ph.kanta.reborn.core.legacy.LegacyKeyTable.Entry entry
          : ph.kanta.reborn.core.legacy.LegacyKeyTable.fromStream(asset)) {
        if (!entry.placeholder) {
          candidates.add(
              ph.kanta.reborn.core.legacy.LegacyImportKey.fromHex(entry.keyHex, entry.ivHex));
        }
      }
    } catch (Exception unusableTable) {
      for (ph.kanta.reborn.core.legacy.LegacyImportKey credential : candidates)
        credential.close();
      candidates.clear();
    }
    return candidates;
  }

  private void karaokeFinished(DocumentTreeScanner.Classification found) {
    StringBuilder summary = new StringBuilder("Folder import finished");
    if (found.legacyIndex != null && found.legacyBlob != null) summary.append(" · legacy pair");
    if (!found.songs.isEmpty()) summary.append(" · ").append(found.songs.size()).append(" song(s)");
    if (!found.videos.isEmpty()) summary.append(" · ").append(found.videos.size()).append(" video(s)");
    if (!found.photos.isEmpty()) summary.append(" · ").append(found.photos.size()).append(" photo(s)");
    message = summary.append('.').toString();
    completeImportProgress(true, message);
    publish();
  }

  void saveSet(String name, List<Song> values) {
    if (!ready || savingSet) return;
    savingSet = true;
    List<Song> captured = new ArrayList<>(values);
    io.execute(
        () -> {
          try {
            repository.saveSet(name, captured);
            List<LibraryRepository.SavedSet> sets = repository.sets();
            main.post(
                () -> {
                  savingSet = false;
                  savedSets = sets;
                  message = "Saved your set";
                  publish();
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  savingSet = false;
                  message = "Could not save the set. Use a name under 120 characters.";
                  publish();
                });
          }
        });
  }

  void createPlaylist(String name, Song firstSong, java.util.function.Consumer<String> completed) {
    editPlaylist(() -> {
      repository.createPlaylist(name, firstSong == null ? null : firstSong.id);
      return firstSong == null ? R.string.playlist_created : R.string.playlist_created_with_song;
    }, completed);
  }

  void addToPlaylist(String playlistId, Song song, java.util.function.Consumer<String> completed) {
    editPlaylist(() -> repository.addToPlaylist(playlistId, song.id)
        ? R.string.playlist_song_added : R.string.playlist_song_already_added, completed);
  }

  private interface PlaylistEdit {
    int apply() throws Exception;
  }

  /** Playlist changes publish the library view only, not transport/intermission side effects. */
  private void editPlaylist(PlaylistEdit edit, java.util.function.Consumer<String> completed) {
    if (!ready) {
      completed.accept(context.getString(R.string.playlist_library_loading));
      return;
    }
    io.execute(() -> {
      try {
        int status = edit.apply();
        List<LibraryRepository.SavedSet> updated = repository.sets();
        main.post(() -> {
          savedSets = updated;
          if (listener != null) listener.changed();
          completed.accept(context.getString(status));
        });
      } catch (Exception failure) {
        diagnostics.record("Saving playlist", failure);
        main.post(() -> completed.accept(context.getString(R.string.playlist_save_failed)));
      }
    });
  }

  private void retireStageShuffleForScenic() {
    visualShuffle.invalidate();
    choosingStage = false;
    if (shuffledVisual != null && "photo".equals(stageKind)) scenic = manualStagePhoto;
    shuffledVisual = null;
    manualStagePhoto = null;
  }

  void scene(String name) {
    retireStageShuffleForScenic();
    scenicName = name;
    stageKind = "scenic";
    stageVideo = null;
    stageRequest++;
    visualGeneration++;
    if (ready && stageStore != null)
      io.execute(
          () -> {
            try {
              stageStore.select("scenic");
            } catch (Exception ignored) {
            }
          });
    io.execute(
        () -> {
          try {
            android.graphics.BitmapFactory.Options options =
                new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream in = context.getAssets().open("scenic/" + name + ".png")) {
              android.graphics.BitmapFactory.decodeStream(in, null, options);
            }
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1600
                || options.outHeight / options.inSampleSize > 1600) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            android.graphics.Bitmap bitmap;
            try (InputStream in = context.getAssets().open("scenic/" + name + ".png")) {
              bitmap = android.graphics.BitmapFactory.decodeStream(in, null, options);
            }
            main.post(
                () -> {
                  if (scenicName.equals(name) && stageKind.equals("scenic")) {
                    scenic = bitmap;
                    publish();
                  }
                });
          } catch (IOException e) {
            main.post(
                () -> {
                  message = "Could not open scenic background";
                  publish();
                });
          }
        });
  }

  void sceneResource(String name, int drawableResource) {
    if (name == null || name.isEmpty() || drawableResource == 0) return;
    retireStageShuffleForScenic();
    scenicName = name;
    stageKind = "scenic";
    stageVideo = null;
    long request = ++stageRequest;
    visualGeneration++;
    if (ready && stageStore != null)
      io.execute(
          () -> {
            try {
              stageStore.select("scenic");
            } catch (Exception ignored) {
            }
          });
    io.execute(
        () -> {
          android.graphics.Bitmap bitmap = null;
          try {
            android.graphics.BitmapFactory.Options options =
                new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeResource(
                context.getResources(), drawableResource, options);
            if (options.outWidth <= 0 || options.outHeight <= 0)
              throw new IOException("Invalid bundled scene artwork");
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1600
                || options.outHeight / options.inSampleSize > 1600) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            bitmap = android.graphics.BitmapFactory.decodeResource(
                context.getResources(), drawableResource, options);
            if (bitmap == null) throw new IOException("Could not decode bundled scene artwork");
            android.graphics.Bitmap result = bitmap;
            main.post(
                () -> {
                  if (request == stageRequest && scenicName.equals(name) && stageKind.equals("scenic")) {
                    scenic = result;
                    publish();
                  } else if (!result.isRecycled()) {
                    result.recycle();
                  }
                });
          } catch (Exception error) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            main.post(
                () -> {
                  if (request == stageRequest && scenicName.equals(name)) {
                    message = "Could not open scenic background";
                    publish();
                  }
                });
          }
        });
  }

  void loadSet(LibraryRepository.SavedSet set) {
    Map<String, Song> catalog = new HashMap<>();
    for (Song s : songs) catalog.put(s.id, s);
    int added = 0;
    for (String id : set.songs) {
      Song song = catalog.get(id);
      if (song != null && queue.snapshot().size() < 1000) {
        queue.reserve(UUID.randomUUID(), song, "");
        added++;
      }
    }
    message = "Added " + added + " available song(s) to Up Next";
    saveQueue();
    publish();
  }
}
