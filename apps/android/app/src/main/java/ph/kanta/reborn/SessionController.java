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
import ph.kanta.reborn.BundledBgvCatalog;
import ph.kanta.reborn.core.bgv.ScenicClip;
import ph.kanta.reborn.BgvLibrary;

/** One app-lifetime state owner. All MediaPlayer access stays on the main Looper. */
public final class SessionController implements ph.kanta.reborn.core.PitchSource {
  interface Listener {
    void changed();
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
  boolean stageReady;
  boolean cameraPreviewEnabled;
  String stageKind = "scenic";
  File stageVideo;
  long visualGeneration;
  private long stageRequest;
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
  MelodyScoreSession score;
  private MelodyTarget currentTarget;
  private long currentDuration;
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

  /** Read-only stage state for the presenter in the stage subpackage. */
  public synchronized String stageKind() { return stageKind; }
  public synchronized boolean cameraPreviewEnabled() { return cameraPreviewEnabled; }
  public synchronized android.graphics.Bitmap scenicBitmap() { return scenic; }
  public synchronized File stageVideo() { return stageVideo; }
  public synchronized boolean isPlaying() { return playing; }
  /** Current song, or null when nothing is loaded. Read-only, for the stage subpackage. */
  public synchronized Song currentSong() { return current; }

  boolean libraryImporting = false;
  private Listener listener;
  private ScoreSink scoreSink;
  public PlaybackBackend player, pending;
  public RenderedPlaybackClock currentClock;
  float volume = 1, tempo = 1;
  int pitch;
  float lyricScale = 1;
  private final SettingsStore settings;
  boolean realm;
  final PartyAudio party;
  final KeypadSounds keypadSounds;
  final MicrophoneInput microphone;
  /** Scenic video library; null when its private storage cannot be opened. */
  final BgvLibrary bgvLibrary;
  final MicrophoneMonitor monitor;
  public final android.content.SharedPreferences options;
  boolean foreground, loopEnabled = true, auxiliaryInterrupted, assigningPad;
  String loopSongId = "";
  /** Cached copy of the bundled standby interlude; null until prepared on startup. */
  private File bundledInterludeFile;
  final String[] customPads = new String[8];
  private final long[] padGeneration = new long[8];
  String soundFontName = "KANTA original tone";
  private long generation = 0;
  private final ExecutorService lyricWorker = Executors.newSingleThreadExecutor();
  private boolean lyricSearchBusy;
  private boolean autoLyricSearchBusy;
  
  private long autoLyricSearchAt;
  long lyricLookupGeneration;
  
  private long nextLyricSearchAt;

  private final AudioManager audio;
  private final AudioFocusRequest focus;
  private UUID onlineAudioOwner;
  private Runnable retireOnlineAudio;

  SessionController(Context context) {
    this.context = context;
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
            context, this::publish, notice -> message = notice, this::inputReading);
    microphone =
        new MicrophoneInput(context, this::publish, notice -> message = notice, monitor::offer);
    options = context.getSharedPreferences("stage-options-v1", Context.MODE_PRIVATE);
    settings = new SettingsStore(context);
    volume = Math.min(1f, Math.max(0f, settings.getMasterVolume()));
    tempo = Math.min(2f, Math.max(0.5f, settings.getTempo()));
    pitch = Math.min(12, Math.max(-12, settings.getPitch()));
    lyricScale = Math.max(.75f, Math.min(1.5f, options.getFloat("lyricScale", 1)));
    loopSongId = options.getString("loopSong", "");
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
                    monitor.stop();
                    microphone.stop();
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
            monitor.stop();
            microphone.stop();
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
                  restoreStage();
                  message = "Your music. Your stage.";
                  publish();
                });
          } catch (Exception e) {
            main.post(
                () -> {
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
    if (!microphone.enabled()) monitor.stop();
    updateIntermission();
    if (listener != null) listener.changed();
  }

  private void restoreStage() {
    io.execute(
        () -> {
          try {
            stageStore = new StageMediaStore(context);
            StageMediaStore.Selection selection = stageStore.load();

            // If no saved stage (default scenic with no video), initialize default BGV
            final StageMediaStore.Selection finalSelection;
            if ("scenic".equals(selection.kind) && selection.video == null && !settings.getBgvDefaultSelected()) {
              BundledBgvCatalog.Clip defaultClip = BundledBgvCatalog.defaultClip();
              if (defaultClip != null) {
                File dest = new File(stageStore.getRoot(), defaultClip.id + ".video");
                if (BundledBgvCatalog.copyTo(context, defaultClip, dest)) {
                  stageStore.setVideo(dest.getName());
                  settings.setBgvDefaultSelected(true);
                  // Reload selection
                  finalSelection = stageStore.load();
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
                  stageReady = true;
                  applyStage(finalSelection);
                });
          } catch (Exception failure) {
            main.post(
                () -> {
                  stageReady = true;
                  message =
                      "Saved stage media is unavailable. Scenic backgrounds remain available.";
                  publish();
                });
          }
        });
  }

  void importStage(android.net.Uri uri, boolean video) {
    if (!ready || !stageReady || importing) return;
    importing = true;
    importCancelled.set(false);
    long token = ++stageRequest;
    message = video ? "Copying your background video…" : "Copying your background photo…";
    publish();
    io.execute(
        () -> {
          try {
            if (stageStore == null) stageStore = new StageMediaStore(context);
            StageMediaStore.Selection selection =
                stageStore.importMedia(uri, video, importCancelled::get);
            main.post(
                () -> {
                  importing = false;
                  if (token == stageRequest) {
                    applyStage(selection);
                    message = "Your background is ready. The original file is unchanged.";
                  }
                  publish();
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

  void chooseStage(String kind) {
    if (!stageReady || stageStore == null || importing) return;
    long token = ++stageRequest;
    io.execute(
        () -> {
          try {
            StageMediaStore.Selection selection = stageStore.select(kind);
            main.post(
                () -> {
                  if (token == stageRequest) {
                    applyStage(selection);
                    publish();
                  }
                });
          } catch (Exception failure) {
            main.post(
                () -> {
                  if (token == stageRequest) {
                    message = "Choose a photo or video first.";
                    publish();
                  }
                });
          }
        });
  }

  private void applyStage(StageMediaStore.Selection selection) {
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
                      scenic = photo;
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
            main.post(() -> keypadSounds.load(root));
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
            main.post(
                () -> {
                  party.loadBuiltins(padFiles);
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

  private void finishScore() {
    if (!scoringEnabled || score == null || current == null || playbackIdentity == null) return;
    observeMicrophone();
    MelodyScoreSession.Completion completed = score.finish(playbackIdentity);
    if (completed != null) {
      lastScore =
          new CompletedScore(completed.playbackId, current.title, singer, completed.estimate);
      if (scoreSink != null) {
        ph.kanta.reborn.score.ScoreSink.CompletedScore sinkScore = new ph.kanta.reborn.score.ScoreSink.CompletedScore(
            lastScore.playbackIdentity, lastScore.title, lastScore.singer, lastScore.estimate);
        scoreSink.onScoreCompleted(sinkScore);
      }
    }
  }

  boolean onlineAudioAllowed() {
    return foreground && !playing && !loading && !microphone.enabled() && !monitor.enabled();
  }

  boolean onlineAudioActive() { return onlineAudioOwner != null; }

  boolean claimOnlineAudio(UUID owner, Runnable retire) {
    Objects.requireNonNull(owner);
    Objects.requireNonNull(retire);
    if (!onlineAudioAllowed()) return false;
    revokeOnlineAudio();
    onlineAudioOwner = owner;
    retireOnlineAudio = retire;
    // Online playback starts only through the visible player's own controls.
    loopEnabled = false;
    party.stopAll();
    audio.abandonAudioFocusRequest(focus);
    publish();
    return true;
  }

  void releaseOnlineAudio(UUID owner) {
    if (onlineAudioOwner == null || !onlineAudioOwner.equals(owner)) return;
    onlineAudioOwner = null;
    retireOnlineAudio = null;
    publish();
  }

  private void revokeOnlineAudio() {
    Runnable retire = retireOnlineAudio;
    onlineAudioOwner = null;
    retireOnlineAudio = null;
    // Clear the token before synchronous native teardown can call back into this owner.
    if (retire != null) retire.run();
  }

  private int requestLocalAudioFocus() {
    revokeOnlineAudio();
    return audio.requestAudioFocus(focus);
  }

  void startMicrophone() {
    if (!foreground
        || context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
    if (requestLocalAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      message = "Microphone input needs audio focus. Try again when other audio has stopped.";
      publish();
      return;
    }
    microphone.start();
  }

  void startMonitor(AudioDeviceInfo output) {
    if (!foreground || !microphone.capturing()) {
      message = "Turn on microphone input before enabling monitoring.";
      publish();
      return;
    }
    if (!monitor.start(output)) {
      message =
          "Connect wired, USB or Bluetooth headset audio, then select that output again. Speaker"
              + " and Bluetooth-speaker monitoring are unavailable.";
      publish();
    }
  }

  void stopMonitor() {
    monitor.stop();
    publish();
  }

  void stopMicrophone() {
    monitor.stop();
    microphone.stop();
    if (current == null && !loopEnabled) audio.abandonAudioFocusRequest(focus);
    publish();
  }

  void hostVisible(boolean visible) {
    scoreWaitingForControls = true;
    scoreFrameFloor = SystemClock.elapsedRealtime();
    foreground = visible;
    if (!visible) {
      ++lyricLookupGeneration;
      revokeOnlineAudio();
      companion.stop();
      browserCompanion.stop();
      monitor.stop();
      microphone.stop();
      party.stopAll();
      loopEnabled = false;
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
            && !onlineAudioActive()
            && loopEnabled
            && !auxiliaryInterrupted
            && current == null
            && !loading
            && queue.peek() == null;
    if (allowed && requestLocalAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
      allowed = false;
    File intermissionSource = null;
    if (selected != null && repository != null) {
      intermissionSource = new File(repository.media, selected.fileName);
    } else if (bundledInterludeFile != null) {
      intermissionSource = bundledInterludeFile; // bundled standby welcome
    }
    party.loop(intermissionSource, allowed);
  }

  /** Clears any imported track so the bundled standby welcome plays again. */
  void useBundledInterlude() {
    loopSongId = "";
    options.edit().remove("loopSong").apply();
    publish();
  }

  void chooseIntermission(Song song) {
    if (!isAudio(song)) return;
    loopSongId = song.id;
    options.edit().putString("loopSong", loopSongId).apply();
    publish();
  }

  void toggleIntermission() {
    auxiliaryInterrupted = false;
    loopEnabled = !loopEnabled;
    if (loopEnabled) revokeOnlineAudio();
    publish();
  }

  
    } catch (Exception ignored) {}
  }

void triggerPad(int slot) {
    if (!foreground || requestLocalAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
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
  public boolean downloadScenic(String clipId) {
    if (bgvLibrary == null) return false;
    ScenicClip clip = ScenicCatalog.byId(clipId);
    if (clip == null) return false;
    return bgvLibrary.download(clip, (id, ok, status) -> publish());
  }

  /** Deletes a previously downloaded clip from the library. */
  public boolean deleteScenic(String clipId) {
    return bgvLibrary != null && bgvLibrary.delete(clipId);
  }

  /** Returns the current download status line, or empty when the library is unavailable. */
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
    if (assigningPad || !isAudio(song) || slot < 0 || slot >= 8) return;
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
    if (!ready) return;
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
    if (loading || !ready) return;
    ReservationQueue.Entry entry = queue.peek();
    if (entry == null) {
      message = "Reserve a song first";
      publish();
      return;
    }
    if (entry.youtube != null) {
      // Never hand a web reservation to the local file decoder.
      message = "Online video is reserved, but host-controlled YouTube playback is not ready. "
          + "The reservation remains in Up Next.";
      publish();
      return;
    }
    prepare(entry);
  }

  private void prepare(ReservationQueue.Entry entry) {
    if (entry.youtube != null) {
      throw new IllegalArgumentException("Online media cannot enter the local decoder");
    }
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
                          if (queue.snapshot().stream().noneMatch(e -> e.id.equals(entry.id))) {
                            pending = null;
                            candidate.release();
                            loading = false;
                            publish();
                            return;
                          }
                          if (requestLocalAudioFocus()
                              != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            pending = null;
                            candidate.release();
                            loading = false;
                            message = "Audio is busy. Try Play again.";
                            publish();
                            return;
                          }
                          try {
                            float applyTempo = tempo;
                            int applyPitch = pitch;
                            if (settings.hasSongOverride(entry.song.id)) {
                              applyTempo = settings.getSongTempo(entry.song.id, tempo);
                              applyPitch = settings.getSongPitch(entry.song.id, pitch);
                            }
                            candidate.controls(volume, applyTempo, applyPitch);
                            candidate.start();
                          } catch (Exception e) {
                            failed(candidate);
                          }
                        }

                        public void started(PlaybackBackend candidate) {
                          if (player == candidate) {
                            scoreWaitingForControls = true;
                            scoreFrameFloor = SystemClock.elapsedRealtime();
                            playing = true;
                            publish();
                            return;
                          }
                          if (token != generation
                              || pending != candidate
                              || queue.snapshot().stream().noneMatch(e -> e.id.equals(entry.id))) {
                            candidate.release();
                            if (pending == candidate) {
                              pending = null;
                              loading = false;
                              publish();
                            }
                            return;
                          }
                          releaseCurrent();
                          player = candidate;
                          pending = null;
                          queue.remove(entry.id);
                          current = entry.song;
                          playbackIdentity = entry.id;
                          currentTarget = melody;
                          currentDuration = midi == null ? 0 : midi.durationMilliseconds;
                          currentKeyPresent = keyPresent;
                          currentKeySharpsFlats = keySharpsFlats;
                          currentKeyMinor = keyMinor;
                          resetScore();
                          singer = entry.singer;
                          lyrics = cues;
                          playing = true;
                          loading = false;
                          message = entry.song.title;
                          maybeAutoSearchLyrics();
                          saveQueue();
                          publish();
                        }

                        public void completed(PlaybackBackend completed) {
                          if (player != completed) return;
                          finishScore();
                          played.add(current);
                          if (played.size() > 10000) played.remove(0);
                          releaseCurrent();
                          current = null;
                          lyrics = Collections.emptyList();
                          publish();
                          if (!loading) nextIfReserved();
                        }

                        public void failed(PlaybackBackend failed) {
                          if (pending == failed) {
                            pending = null;
                            failed.release();
                            loading = false;
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
                    currentClock = created.getClock();
                  } catch (Exception | LinkageError e) {
                    loading = false;
                    message = "Could not prepare this song. Your reservation remains in Up Next.";
                    publish();
                  }
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (token != generation) return;
                  loading = false;
                  message = "This music file is unreadable or exceeds supported MIDI limits.";
                  publish();
                });
          }
        });
  }

  void setVolume(float value) {
    if (!Float.isFinite(value)) return;
    volume = Math.max(0, Math.min(1, value));
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

  private void nextIfReserved() {
    if (queue.peek() != null) next();
    else {
      message = "Set finished. Save your played songs in My Set.";
      if (!microphone.enabled()) audio.abandonAudioFocusRequest(focus);
      publish();
    }
  }

  /** Tempo the active song actually plays at (per-song override wins). */
  float effectiveTempo() {
    return current != null && settings.hasSongOverride(current.id)
        ? settings.getSongTempo(current.id, tempo)
        : tempo;
  }

  /** Pitch shift the active song actually plays at (per-song override wins). */
  int effectivePitch() {
    return current != null && settings.hasSongOverride(current.id)
        ? settings.getSongPitch(current.id, pitch)
        : pitch;
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
    if (player == null || current == null) return;
    seekTo(0);
    if (!playing && !loading) toggle();
    else publish();
  }

  void toggle() {
    if (loading) return;
    if (player == null) {
      next();
      return;
    }
    if (playing) pause();
    else if (requestLocalAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      try {
        player.start();
      } catch (IllegalStateException e) {
        message = "Please choose Next to reopen this song";
        publish();
      }
    }
  }

  void pause() {
    scoreWaitingForControls = true;
    scoreFrameFloor = SystemClock.elapsedRealtime();
    if (loading || pending != null) {
      ++generation; // Also retire work still parsing on IO, before a player exists.
      if (pending != null) pending.release();
      pending = null;
      loading = false;
      publish();
    }
    if (player != null && playing) {
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
    ++lyricLookupGeneration;
    ++generation;
    if (pending != null) {
      pending.release();
      pending = null;
    }
    loading = false;
    releaseCurrent();
    current = null;
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
      if (playing) pause();
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

    if (player != null) {
      player.release();
      player = null;
    }
    playing = false;
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
            failed = files.size();
          }
          List<Song> loaded = repository.songs();
          File bank = repository.soundFont();
          String bankLabel = repository.soundFontName();
          List<LibraryRepository.SoundFontEntry> banks = repository.soundFonts();
          StringBuilder result = new StringBuilder("Imported ").append(imported).append(" file(s)");
          if (duplicates > 0) result.append(", ").append(duplicates).append(" already in My Set");
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

  // Takes ownership of credentials, including rejected/busy requests.
  boolean importLegacyPair(android.net.Uri indexUri, android.net.Uri blobUri,
      ph.kanta.reborn.core.legacy.LegacyImportKey credentials) {
    if (credentials == null) return false;
    if (!ready || importing || indexUri == null || blobUri == null) {
      credentials.close();
      return false;
    }
    importCancelled.set(false); importing = true; libraryImporting = true; message = "Importing legacy song library…"; publish();
    try {
      io.execute(() -> {
        File index = null, blob = null; int good = 0; String resultMessage;
        try (ph.kanta.reborn.core.legacy.LegacyImportKey owned = credentials) {
          index = File.createTempFile("legacy-index-", ".idx", context.getCacheDir());
          blob = File.createTempFile("legacy-blob-", ".blob", context.getCacheDir());
          copyLegacyUri(indexUri, index, 16L * 1024 * 1024);
          copyLegacyUri(blobUri, blob, 1024L * 1024 * 1024);
          ph.kanta.reborn.core.legacy.LegacyFileContainerImporter.ImportResult result =
              ph.kanta.reborn.core.legacy.LegacyFileContainerImporter.importPair(
                  index, blob, new File(repository.media, "legacy-import"), owned);
          good = repository.importLegacyResult(result);
          resultMessage = "Imported " + good + " legacy song(s); " + result.skippedCount + " record(s) skipped.";
        } catch (Exception failure) {
          resultMessage = "Legacy import failed: " + (failure.getMessage() == null ? "unsupported container" : failure.getMessage());
        } finally { if (index != null) index.delete(); if (blob != null) blob.delete(); }
        final int imported = good; final String messageValue = resultMessage;
        main.post(() -> { songs = repository.songs(); importing = false; libraryImporting = false; message = messageValue; completeImportProgress(imported > 0, messageValue); publish(); });
      });
      return true;
    } catch (java.util.concurrent.RejectedExecutionException rejected) {
      credentials.close();
      importing = false;
      libraryImporting = false;
      message = "Import could not start. Please try again.";
      publish();
      return false;
    }
  }

  void downloadFluidR3GM() {
    if (importing) return;
    importing = true;
    libraryImporting = true;
    message = "Preparing FluidR3 GM download...";
    publish();
    io.execute(() -> {
      File destination = new File(repository.media, "FluidR3_GM.sf2");
      if (destination.exists()) {
        main.post(() -> {
          importing = false;
          libraryImporting = false;
          message = "FluidR3 GM already exists. Use it from SoundFonts list.";
          publish();
        });
        return;
      }
      File tempZip = null;
      try {
        tempZip = File.createTempFile("fluidr3gm-", ".zip", repository.media);
        long totalBytes = 148L * 1024L * 1024L;
        long downloaded = 0;
        main.post(() -> {
          message = "Downloading FluidR3 GM... 0%";
          publish();
        });
        try (java.io.InputStream in = new java.net.URL("https://ftp.osuosl.org/pub/musescore/soundfont/fluid-soundfont.zip").openStream();
             java.io.FileOutputStream out = new java.io.FileOutputStream(tempZip)) {
          byte[] buffer = new byte[65536];
          int count;
          while ((count = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            out.write(buffer, 0, count);
            downloaded += count;
            if (downloaded % (1024 * 1024) < count) {
              long finalDownloaded = downloaded;
              main.post(() -> {
                int pct = totalBytes > 0 ? (int) (finalDownloaded * 100 / totalBytes) : 0;
                message = "Downloading FluidR3 GM... " + pct + "% (" + formatBytes(finalDownloaded) + " / " + formatBytes(totalBytes) + ")";
                publish();
              });
            }
          }
          out.getFD().sync();
        }
        main.post(() -> {
          message = "Extracting FluidR3 GM...";
          publish();
        });
        // Extract the .SF2 file from ZIP
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(tempZip)) {
          java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
          while (entries.hasMoreElements()) {
            java.util.zip.ZipEntry entry = entries.nextElement();
            if (entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".sf2")) {
              try (java.io.InputStream zipIn = zip.getInputStream(entry);
                   java.io.FileOutputStream out = new java.io.FileOutputStream(destination)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = zipIn.read(buffer)) != -1) {
                  out.write(buffer, 0, count);
                }
              }
              break;
            }
          }
        }
        if (!destination.exists()) throw new IOException("No .sf2 file found in ZIP");
        main.post(() -> {
          message = "Validating FluidR3 GM...";
          publish();
        });
        long test = NativeSoundFont.open(destination.getAbsolutePath());
        if (test == 0) throw new IOException("Downloaded file is not a valid SoundFont");
        NativeSoundFont.close(test);
        final String licenseText = readAssetText("fluidr3gm-license.txt");
        final File licenseFile = new File(repository.media, "fluidr3gm-license.txt");
        try (java.io.FileWriter fw = new java.io.FileWriter(licenseFile)) {
          fw.write(licenseText);
        }
        main.post(() -> {
          importing = false;
          libraryImporting = false;
          message = "FluidR3 GM downloaded successfully! Select it from SoundFonts.";
          publish();
        });
      } catch (Exception e) {
        if (tempZip != null) tempZip.delete();
        if (destination.exists()) destination.delete();
        main.post(() -> {
          importing = false;
          libraryImporting = false;
          message = "Download failed: " + e.getMessage();
          publish();
        });
      }
    });
  }


  void downloadMuseScoreGeneral() {
    if (importing) return;
    importing = true;
    libraryImporting = true;
    message = "Preparing MuseScore General download...";
    publish();
    io.execute(() -> {
      File destination = new File(repository.media, "MuseScore_General.sf2");
      if (destination.exists()) {
        main.post(() -> {
          importing = false;
          libraryImporting = false;
          message = "MuseScore General already exists. Use it from SoundFonts list.";
          publish();
        });
        return;
      }
      File temp = null;
      try {
        temp = File.createTempFile("musescore-", ".tmp", repository.media);
        long totalBytes = 216L * 1024L * 1024L;
        long downloaded = 0;
        main.post(() -> {
          message = "Downloading MuseScore General... 0%";
          publish();
        });
        try (java.io.InputStream in = new java.net.URL("https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/MuseScore_General.sf2").openStream();
             java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
          byte[] buffer = new byte[65536];
          int count;
          while ((count = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            out.write(buffer, 0, count);
            downloaded += count;
            if (downloaded % (1024 * 1024) < count) {
              long finalDownloaded = downloaded;
              main.post(() -> {
                int pct = totalBytes > 0 ? (int) (finalDownloaded * 100 / totalBytes) : 0;
                message = "Downloading MuseScore General... " + pct + "% (" + formatBytes(finalDownloaded) + " / " + formatBytes(totalBytes) + ")";
                publish();
              });
            }
          }
          out.getFD().sync();
        }
        main.post(() -> {
          message = "Validating MuseScore General...";
          publish();
        });
        long test = NativeSoundFont.open(temp.getAbsolutePath());
        if (test == 0) throw new IOException("Downloaded file is not a valid SoundFont");
        NativeSoundFont.close(test);
        if (!temp.renameTo(destination)) throw new IOException("Cannot save SoundFont");
        final String licenseText = readAssetText("musescore-license.txt");
        final File licenseFile = new File(repository.media, "musescore-license.txt");
        try (java.io.FileWriter fw = new java.io.FileWriter(licenseFile)) {
          fw.write(licenseText);
        }
        main.post(() -> {
          importing = false;
          libraryImporting = false;
          message = "MuseScore General downloaded successfully! Select it from SoundFonts.";
          publish();
        });
      } catch (Exception e) {
        if (temp != null) temp.delete();
        main.post(() -> {
          importing = false;
          libraryImporting = false;
          message = "Download failed: " + e.getMessage();
          publish();
        });
      }
    });
  }

  void downloadSGMv201() {
    if (importing) return;
    importing = true;
    libraryImporting = true;
    message = "Preparing SGM-v2.01 download...";
    publish();
    io.execute(() -> {
      File destination = new File(repository.media, "SGM-v2.01-YamahaGrand-Guit-Bass-v2.7.sf2");
      if (destination.exists()) {
        main.post(() -> {
          importing = false;
          libraryImporting = false;
          message = "SGM-v2.01 already exists. Use it from SoundFonts list.";
          publish();
        });
        return;
      }
      File temp = null;
      try {
        temp = File.createTempFile("sgm-", ".tmp", repository.media);
        long totalBytes = 528L * 1024L * 1024L;
        long downloaded = 0;
        main.post(() -> {
          message = "Downloading SGM-v2.01... 0%";
          publish();
        });
        try (java.io.InputStream in = new java.net.URL("https://huggingface.co/spaces/projectlosangeles/Orpheus-Mono-Melodies-Mixer/resolve/main/SGM-v2.01-YamahaGrand-Guit-Bass-v2.7.sf2").openStream();
             java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
          byte[] buffer = new byte[65536];
          int count;
          while ((count = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            out.write(buffer, 0, count);
            downloaded += count;
            if (downloaded % (1024 * 1024) < count) {
              long finalDownloaded = downloaded;
              main.post(() -> {
                int pct = totalBytes > 0 ? (int) (finalDownloaded * 100 / totalBytes) : 0;
                message = "Downloading SGM-v2.01... " + pct + "% (" + formatBytes(finalDownloaded) + " / " + formatBytes(totalBytes) + ")";
                publish();
              });
            }
          }
          out.getFD().sync();
        }
        main.post(() -> {
          message = "Validating SGM-v2.01...";
          publish();
        });
        long test = NativeSoundFont.open(temp.getAbsolutePath());
        if (test == 0) throw new IOException("Downloaded file is not a valid SoundFont");
        NativeSoundFont.close(test);
        if (!temp.renameTo(destination)) throw new IOException("Cannot save SoundFont");
        final String licenseText = readAssetText("sgm-license.txt");
        final File licenseFile = new File(repository.media, "sgm-license.txt");
        try (java.io.FileWriter fw = new java.io.FileWriter(licenseFile)) {
          fw.write(licenseText);
        }
        main.post(() -> {
          importing = false;
          libraryImporting = false;
          message = "SGM-v2.01 downloaded successfully! Select it from SoundFonts.";
          publish();
        });
      } catch (Exception e) {
        if (temp != null) temp.delete();
        main.post(() -> {
          importing = false;
          libraryImporting = false;
          message = "Download failed: " + e.getMessage();
          publish();
        });
      }
    });
  }


  private String formatBytes(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
  }

  private String readAssetText(String assetName) {
    try (java.io.InputStream is = context.getAssets().open(assetName);
         java.io.InputStreamReader isr = new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8);
         java.io.BufferedReader br = new java.io.BufferedReader(isr)) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line).append("\\n");
      }
      return sb.toString();
    } catch (Exception e) {
      return "License file not found: " + assetName;
    }
  }

  private void copyLegacyUri(android.net.Uri uri, File target, long maxBytes) throws Exception {
    try (java.io.InputStream in = context.getContentResolver().openInputStream(uri); java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
      if (in == null) throw new IOException("Cannot open selected legacy file");
      byte[] buffer = new byte[65536]; long total = 0; int count;
      while ((count = in.read(buffer)) != -1) { if (importCancelled.get()) throw new java.io.InterruptedIOException(); total += count; if (total > maxBytes) throw new IOException("Legacy file exceeds safety limit"); out.write(buffer,0,count); }
      out.getFD().sync();
    }
  }

  String activeSoundFontFileName() {
    return selectedSoundFont == null ? "kanta-tone.sf2" : selectedSoundFont.getName();
  }


  void deleteSong(String id) {
    if (!ready || importing) return;
    io.execute(
        () -> {
          try {
            repository.deleteSong(id);
            main.post(
                () -> {
                  songs = repository.songs();
                  message = "Removed from My Set.";
                  publish();
                });
          } catch (Exception failure) {
            main.post(
                () -> {
                  message = "Could not remove that song. " + failure.getMessage();
                  publish();
                });
          }
        });
  }

  void deleteSoundFont(String fileName) {
    if (!ready || importing) return;
    io.execute(
        () -> {
          try {
            repository.deleteSoundFont(fileName);
            File bank = repository.soundFont();
            String label = repository.soundFontName();
            List<LibraryRepository.SoundFontEntry> banks = repository.soundFonts();
            main.post(
                () -> {
                  selectedSoundFont = bank;
                  soundFontName = label;
                  soundFonts = banks;
                  message = "SoundFont removed.";
                  publish();
                });
          } catch (Exception failure) {
            main.post(
                () -> {
                  message = "Could not remove that SoundFont. " + failure.getMessage();
                  publish();
                });
          }
        });
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

  void starter(String id, boolean enabled) {
    if (!ready) return;
    io.execute(
        () -> {
          try {
            repository.setStarter(id, enabled);
            List<Song> loaded = repository.songs();
            main.post(
                () -> {
                  songs = loaded;
                  message = enabled ? "Starter song restored" : "Starter song removed from My Set";
                  publish();
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  message = "Could not change starter song";
                  publish();
                });
          }
        });
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

  void scene(String name) {
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

  /** Sets a bundled BGV as the background video (used for auto-shuffle). */
  public void setBundledBgv(BundledBgvCatalog.Clip clip, java.io.File dest) {
    if (clip == null || dest == null) return;
    stageKind = "video";
    stageVideo = dest;
    visualEpoch = android.os.SystemClock.elapsedRealtime();
    visualGeneration++;
    if (stageStore != null) {
      stageStore.setVideo(dest.getName());
    }
    publish();
  }

  void sceneResource(String name, int drawableResource) {
    if (name == null || name.isEmpty() || drawableResource == 0) return;
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
