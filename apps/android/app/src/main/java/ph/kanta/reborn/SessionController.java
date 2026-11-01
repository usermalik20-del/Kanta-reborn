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
  final MicrophoneInput microphone;
  final MicrophoneMonitor monitor;
  public final android.content.SharedPreferences options;
  boolean foreground, loopEnabled, auxiliaryInterrupted, assigningPad;
  String loopSongId = "";
  final String[] customPads = new String[8];
  private final long[] padGeneration = new long[8];
  String soundFontName = "KANTA original tone";
  private long generation = 0;
  private final AudioManager audio;
  private final AudioFocusRequest focus;
  private UUID onlineAudioOwner;
  private Runnable retireOnlineAudio;

  SessionController(Context context) {
    this.context = context;
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
            List<ReservationQueue.Entry> restored = repository.queue();
            main.post(
                () -> {
                  songs = loaded;
                  savedSets = sets;
                  queue.restore(restored);
                  selectedSoundFont = bank;
                  soundFontName = bankLabel;
                  ready = true;
                  preparePads();
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
            main.post(
                () -> {
                  stageReady = true;
                  applyStage(selection);
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
                stream.write(PartySounds.wave(i));
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
    party.loop(
        selected == null || repository == null
            ? null
            : new File(repository.media, selected.fileName),
        allowed);
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
                    ? MidiLyrics.decode(java.nio.file.Files.readAllBytes(file.toPath()))
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
                            candidate.controls(volume, tempo, pitch);
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
      if (player != null) player.controls(volume, tempo, pitch);
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

  void stop() {
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

  public RenderedPlaybackClock getClock() {
    if (player != null) return player.getClock();
    return currentClock;
  }

  void importSongs(List<Uri> files) {
    if (!ready || importing) return;
    importCancelled.set(false);
    importing = true;
    message = "Importing your selected songs…";
    publish();
    io.execute(
        () -> {
          int good = 0, failed = 0;
          try {
            int[] counts = repository.importBatch(files, importCancelled::get);
            good = counts[0];
            failed = counts[1];
          } catch (Exception e) {
            failed = files.size();
          }
          List<Song> loaded = repository.songs();
          File bank = repository.soundFont();
          String bankLabel = repository.soundFontName();
          String result =
              "Imported "
                  + good
                  + " file(s)"
                  + (failed > 0
                      ? ". " + failed + " unsupported or unreadable file(s) were skipped."
                      : ".");
          main.post(
              () -> {
                songs = loaded;
                selectedSoundFont = bank;
                soundFontName = bankLabel;
                importing = false;
                message = result;
                publish();
              });
        });
  }

  void setImportProgressListener(LibraryRepository.ImportProgressListener listener) {
    repository.setImportProgressListener(listener);
  }

  void cancelImports() {
    importCancelled.set(true);
  }

  void importFolder(Uri tree) {
    if (!ready || importing) return;
    importing = true;
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
                    message = "Folder import cancelled.";
                    publish();
                    return;
                  }
                  importSongs(files);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  importing = false;
                  message = "The folder is unavailable, cancelled or exceeds the scan limit.";
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
