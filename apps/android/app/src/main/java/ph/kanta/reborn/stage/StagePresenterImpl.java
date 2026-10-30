package ph.kanta.reborn.stage;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import ph.kanta.reborn.SessionController;
import ph.kanta.reborn.FileAudioPlayback;
import ph.kanta.reborn.core.MidiLyrics;
import ph.kanta.reborn.core.RenderedPlaybackClock;
import ph.kanta.reborn.core.ReservationQueue;
import ph.kanta.reborn.core.Song;
import ph.kanta.reborn.core.catalog.SongCatalog;
import ph.kanta.reborn.core.score.ScoreCelebrationPlan;
import ph.kanta.reborn.score.ScoreSink;

/**
 * Implementation of {@link StagePresenter} that binds the karaoke stage overlays
 * to the existing {@link SessionController}.
 *
 * <p>This is the single presentation owner for the karaoke stage. It manages:
 * <ul>
 *   <li>{@link LyricStage} - scrolling timed lyrics synced to playback</li>
 *   <li>{@link CountdownStage} - 3-2-1-GO countdown before playback</li>
 *   <li>{@link ScoreOverlay} - post-song score breakdown</li>
 *   <li>{@link BackgroundStage} - solid/photo/camera/BGV background</li>
 *   <li>{@link AdjustmentOverlay} - real-time key/tempo/melody/guide adjustments</li>
 *   <li>{@link IntroCard} - song metadata card shown before countdown</li>
 *   <li>{@link SearchOverlay} - type-ahead search with reserve action</li>
 *   <li>{@link QueueOverlay} - read-only projection of ReservationQueue</li>
 *   <li>{@link RemoteStatusView} - Kanta-Connect connection indicator</li>
 *   <li>{@link Keypad} - numeric entry for song codes</li>
 * </ul>
 *
 * <p>Implements {@link ScoreSink} to receive completed scores from
 * {@link SessionController} and display them via {@link ScoreOverlay}.
 */
public final class StagePresenterImpl extends FrameLayout
    implements StagePresenter, ScoreSink {

  // Stage overlays
  private final LyricStage lyricStage;
  private final CountdownStage countdownStage;
  private final ScoreOverlay scoreOverlay;
  private final ScoreCelebrationView celebration;
  private final BackgroundStage backgroundStage;
  private final AdjustmentOverlay adjustmentOverlay;
  private final IntroCard introCard;
  private final SearchOverlay searchOverlay;
  private final QueueOverlay queueOverlay;
  private final RemoteStatusView remoteStatusView;
  private final Keypad keypad;

  // Pitch training overlays
  private final PitchGameOverlay pitchGameOverlay;
  private final LearningAssistantOverlay learningAssistantOverlay;

  // Dependencies
  private final SessionController session;
  private final SongCatalog catalog;
  private final ReservationQueue queue;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable frameTicker;

  // State
  private StagePresenter.CountdownListener pendingCountdownListener;
  private int pendingCountdownSeconds;
  private boolean countdownActive = false;
  private boolean isPlaying = false;

  public StagePresenterImpl(Context context, SessionController session,
      SongCatalog catalog, ReservationQueue queue) {
    this(context, null, session, catalog, queue);
  }

  public StagePresenterImpl(Context context, AttributeSet attrs,
      SessionController session, SongCatalog catalog, ReservationQueue queue) {
    super(context, attrs);
    this.session = session;
    this.catalog = catalog;
    this.queue = queue;

    setWillNotDraw(false);
    setClipChildren(false);
    setClipToPadding(false);

    // Initialize stage overlays
    lyricStage = new LyricStage(context);
    countdownStage = new CountdownStage(context);
    scoreOverlay = new ScoreOverlay(context);
    celebration = new ScoreCelebrationView(context);
    backgroundStage = new BackgroundStage(context);
    adjustmentOverlay = new AdjustmentOverlay(context);
    introCard = new IntroCard(context);
    searchOverlay = new SearchOverlay(context);
    queueOverlay = new QueueOverlay(context);
    remoteStatusView = new RemoteStatusView(context);
    keypad = new Keypad(context, null, new Keypad.KeypadListener() {
      @Override public void onKey(String digit) { /* handled by SearchOverlay */ }
      @Override public void onBackspace() { /* handled by SearchOverlay */ }
      @Override public void onSubmit() { /* handled by SearchOverlay */ }
      @Override public void onDismiss() { keypad.hide(); }
    });

    // Add overlays in z-order (background first, topmost last)
    addView(backgroundStage, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(lyricStage, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(countdownStage, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(scoreOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(celebration, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(adjustmentOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(introCard, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(searchOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(queueOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(remoteStatusView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    addView(keypad, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

    // Initialize pitch training overlays
    pitchGameOverlay = new PitchGameOverlay(context);
    pitchGameOverlay.setPitchSource(session);
    pitchGameOverlay.setListener(new PitchGameOverlay.PitchGameListener() {
      @Override public void onGameStarted() {}
      @Override public void onGameEnded(int score, int maxStreak) {}
      @Override public void onDismissed() {}
    });
    addView(pitchGameOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

    learningAssistantOverlay = new LearningAssistantOverlay(context);
    learningAssistantOverlay.setPitchSource(session);
    learningAssistantOverlay.setListener(new LearningAssistantOverlay.LearningAssistantListener() {
      @Override public void onAdviceGenerated(String advice) {}
      @Override public void onDismissed() {}
    });
    addView(learningAssistantOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

    // Configure search overlay
    searchOverlay.setCatalog(catalog, queue);
    searchOverlay.setListener(song -> {
      queue.reserve(java.util.UUID.randomUUID(), song, session.singer);
      session.saveQueue();
      searchOverlay.hide();
    });

    // Configure queue overlay
    queueOverlay.setQueue(queue);
    queueOverlay.setListener(id -> queue.remove(id));

    // Configure adjustment overlay
    adjustmentOverlay.setListener(new AdjustmentOverlay.AdjustmentListener() {
      @Override public void onKeyChange(int semitones) {
        session.setPitch(semitones);
      }
      @Override public void onTempoChange(float factor) {
        session.setTempo(factor);
      }
      @Override public void onMelodyVolumeChange(float volume) {
        // Melody volume handled via SoundFontPlayback if needed
      }
      @Override public void onGuideVolumeChange(float volume) {
        // Guide volume handled via SoundFontPlayback if needed
      }
    });

    // Frame ticker to keep lyrics synced to rendered output frames
    frameTicker = new Runnable() {
      @Override public void run() {
        if (isPlaying) {
          RenderedPlaybackClock clock = session.getClock();
          if (clock != null) {
            // For FileAudioPlayback, we need to update its clock approximation
            if (session.player instanceof FileAudioPlayback) {
              ((FileAudioPlayback) session.player).updateClock();
            }
            // Supply monotonically increasing rendered frame count to LyricStage
            // This must come from the same output clock used by RenderedPlaybackClock
            long frames = session.position() * 44100 / 1000; // 44.1kHz sample rate
            lyricStage.setPlayedFrames(frames);
          }
        }
        handler.postDelayed(this, 16); // ~60fps
      }
    };

    // Initially hide all overlays except background and lyrics
    countdownStage.setVisibility(GONE);
    scoreOverlay.setVisibility(GONE);
    celebration.setVisibility(GONE);
    adjustmentOverlay.setVisibility(GONE);
    introCard.setVisibility(GONE);
    searchOverlay.setVisibility(GONE);
    queueOverlay.setVisibility(GONE);
    keypad.setVisibility(GONE);
    remoteStatusView.setVisibility(VISIBLE);
  }

  @Override
  public void prepare(Song song, ReservationQueue.Entry reservation) {
    // Set up lyrics and background for the upcoming song
    if (song != null && session.lyrics != null) {
      MidiLyrics.Cue[] cues = session.lyrics.toArray(new MidiLyrics.Cue[0]);
      lyricStage.setLyrics(cues, session.getClock());
    }

    // Show intro card if enabled
    if (session.options.getBoolean("showIntroCard", true)) {
      showIntro(new IntroCardData(
          song != null ? song.title : "Unknown",
          song != null ? song.artist : "Unknown Artist",
          song != null ? formatDuration(180000) : "0:00", // default 3 min
          "C", // default key
          120, // default tempo
          "Karaoke" // default genre
      ));
    }
  }

  private String formatDuration(long millis) {
    long seconds = millis / 1000;
    long minutes = seconds / 60;
    seconds = seconds % 60;
    return String.format("%d:%02d", minutes, seconds);
  }

  @Override
  public void startCountdown(int countdownSeconds, CountdownListener listener) {
    this.pendingCountdownListener = listener;
    this.pendingCountdownSeconds = countdownSeconds;
    this.countdownActive = true;

    // Hide intro card if showing
    introCard.dismiss();

    // Start countdown
    countdownStage.start(countdownSeconds, new CountdownListener() {
      @Override public void onTick(int remaining) {
        if (pendingCountdownListener != null) pendingCountdownListener.onTick(remaining);
      }
      @Override public void onComplete() {
        countdownActive = false;
        if (pendingCountdownListener != null) pendingCountdownListener.onComplete();
        pendingCountdownListener = null;
      }
    });
  }

  @Override
  public void startPlayback(RenderedPlaybackClock clock, MidiLyrics.Cue[] lyrics) {
    isPlaying = true;
    countdownActive = false;

    // Update lyrics with the new clock and cues
    if (lyrics != null && lyrics.length > 0) {
      lyricStage.setLyrics(lyrics, clock);
    }

    // Start frame ticker
    handler.post(frameTicker);

    // Ensure background is set to BGV if available
    backgroundStage.setSource(BackgroundStage.Source.BGV);
  }

  @Override
  public void pausePlayback() {
    isPlaying = false;
    handler.removeCallbacks(frameTicker);
  }

  @Override
  public void resumePlayback() {
    isPlaying = true;
    handler.post(frameTicker);
  }

  @Override
  public void stopPlayback() {
    isPlaying = false;
    countdownActive = false;
    handler.removeCallbacks(frameTicker);

    // Reset lyrics
    lyricStage.setLyrics(new MidiLyrics.Cue[0], null);

    // Reset background to solid
    backgroundStage.setSource(BackgroundStage.Source.SOLID);

    // Hide any active overlays
    adjustmentOverlay.hide();
    introCard.dismiss();
    celebration.cancel();
  }

  @Override
  public void showScore(ScoreResult score) {
    // Deterministic per completion: the same score for the same song always plays the
    // same choreography, so the celebration is reproducible and testable.
    long seed =
        (long) score.overallScore * 31L
            + (score.song != null && score.song.id != null ? score.song.id.hashCode() : 0);
    celebration.celebrate(score.overallScore, seed);
    scoreOverlay.show(score, () -> {
      // Score dismissed - the celebration has its own bounded duration.
      celebration.cancel();
    });
  }

  @Override
  public void setBackground(BackgroundSource source) {
    switch (source) {
      case SOLID:
        backgroundStage.setSource(BackgroundStage.Source.SOLID);
        break;
      case PHOTO:
        backgroundStage.setSource(BackgroundStage.Source.PHOTO);
        break;
      case CAMERA:
        backgroundStage.setSource(BackgroundStage.Source.CAMERA);
        break;
      case BGV:
        backgroundStage.setSource(BackgroundStage.Source.BGV);
        break;
    }
  }

  @Override
  public void showAdjustments(AdjustmentListener listener) {
    adjustmentOverlay.setListener(new AdjustmentOverlay.AdjustmentListener() {
      @Override public void onKeyChange(int semitones) {
        listener.onKeyChange(semitones);
      }
      @Override public void onTempoChange(float factor) {
        listener.onTempoChange(factor);
      }
      @Override public void onMelodyVolumeChange(float volume) {
        listener.onMelodyVolumeChange(volume);
      }
      @Override public void onGuideVolumeChange(float volume) {
        listener.onGuideVolumeChange(volume);
      }
    });
    adjustmentOverlay.show();
  }

  @Override
  public void hideAdjustments() {
    adjustmentOverlay.hide();
  }

  @Override
  public void showIntro(IntroCardData data) {
    introCard.show(data, () -> {
      // Intro dismissed - if countdown is pending, start it
      if (pendingCountdownListener != null && pendingCountdownSeconds > 0) {
        startCountdown(pendingCountdownSeconds, pendingCountdownListener);
      }
    });
  }

  @Override
  public void hideIntro() {
    introCard.dismiss();
  }

  @Override
  public void showPitchGame(float targetPitchHz) {
    pitchGameOverlay.startGame(targetPitchHz);
  }

  @Override
  public void hidePitchGame() {
    pitchGameOverlay.dismiss();
  }

  @Override
  public void showLearningAssistant() {
    learningAssistantOverlay.setSongKey(session.practiceKeyName());
    learningAssistantOverlay.startSession();
  }

  @Override
  public void hideLearningAssistant() {
    learningAssistantOverlay.dismiss();
  }

  // ScoreSink implementation
  @Override
  public void onScoreCompleted(CompletedScore score) {
    // Convert SessionController.CompletedScore to StagePresenter.ScoreResult
    StagePresenter.ScoreResult result = new StagePresenter.ScoreResult(
        score.overall(),
        score.pitch(),
        score.timing(),
        score.bonus(),
        score.tier(),
        findSongByTitle(score.title),
        score.singer
    );
    showScore(result);
  }

  @Override
  public void onScoreCancelled(java.util.UUID playbackIdentity) {
    // Score cancelled - nothing to show
  }

  private Song findSongByTitle(String title) {
    for (Song s : session.songs) {
      if (s.title.equals(title)) return s;
    }
    return null;
  }

  /** Shows the search accessory. */
  public void showSearch() {
    searchOverlay.show();
  }

  /** Hides the search accessory. */
  public void hideSearch() {
    searchOverlay.hide();
  }

  /** Shows the queue accessory. */
  public void showQueue() {
    queueOverlay.update();
    queueOverlay.show();
  }

  /** Hides the queue accessory. */
  public void hideQueue() {
    queueOverlay.hide();
  }

  /** Shows the keypad accessory. */
  public void showKeypad() {
    keypad.show();
  }

  /** Hides the keypad accessory. */
  public void hideKeypad() {
    keypad.hide();
  }

  /** True while a celebration is on screen. */
  public boolean isCelebrating() {
    return celebration.isCelebrating();
  }

  /** The tier currently being celebrated, or null. */
  public ScoreCelebrationPlan.Tier celebrationTier() {
    return celebration.currentTier();
  }

  /** Sets the remote connection state. */
  public void setRemoteState(RemoteStatusView.State state) {
    remoteStatusView.setState(state);
  }

  /** Sets the connected client count. */
  public void setRemoteConnectedCount(int count) {
    remoteStatusView.setConnectedCount(count);
  }

  /** Refreshes all stage components. Called periodically from MainActivity. */
  public void refresh() {
    // Update lyrics if playing
    if (isPlaying && session.lyrics != null && !session.lyrics.isEmpty()) {
      MidiLyrics.Cue[] cues = session.lyrics.toArray(new MidiLyrics.Cue[0]);
      lyricStage.setLyrics(cues, session.getClock());
    }
    // Update remote status
    // This would be updated from CompanionControl
  }

  /** Sets whether the visual layer is active. */
  public void setVisualActive(boolean active) {
    // BackgroundStage handles its own visibility
    // LyricStage is always drawn
  }

  /** Delegates to BackgroundStage for camera mounting. */
  public void mountCamera(android.view.View preview) {
    // BackgroundStage manages its own PreviewView for camera
    // This is called from CameraStage when moving camera to this presenter
  }

  /** Delegates to BackgroundStage for camera unmounting. */
  public void unmountCamera(android.view.View preview) {
    // BackgroundStage handles this internally
  }

  /** Delegates to BackgroundStage for camera frame visibility. */
  public void cameraFrame(boolean visible) {
    // BackgroundStage doesn't have this, but we can track it
  }

  /** Returns the camera PreviewView from BackgroundStage. */
  public androidx.camera.view.PreviewView getCameraPreview() {
    return backgroundStage.getCameraPreview();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    handler.removeCallbacks(frameTicker);
    countdownStage.cancel();
    pitchGameOverlay.shutdown();
    learningAssistantOverlay.shutdown();
  }
}
