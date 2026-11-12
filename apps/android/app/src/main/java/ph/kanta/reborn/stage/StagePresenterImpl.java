package ph.kanta.reborn.stage;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.SessionController;
import ph.kanta.reborn.settings.SettingsStore;
import ph.kanta.reborn.BundledBgvCatalog;
import ph.kanta.reborn.FileAudioPlayback;
import ph.kanta.reborn.core.MidiLyrics;
import ph.kanta.reborn.core.RenderedPlaybackClock;
import ph.kanta.reborn.core.ReservationQueue;
import ph.kanta.reborn.core.Song;
import ph.kanta.reborn.core.catalog.SongCatalog;
import ph.kanta.reborn.core.connect.KantaConnectInfo;
import ph.kanta.reborn.core.connect.QrEncoder;
import ph.kanta.reborn.core.score.ScoreCelebrationPlan;
import ph.kanta.reborn.core.score.ScoreResultTier;
import ph.kanta.reborn.core.score.ScoreRevealPlan;
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
 *   <li>{@link QrCodeView} - Kanta-Connect pairing QR code</li>
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
  /** Karaoke standby presence (code prompt, live badge, equaliser) shown when no song is up. */
  private final StagePresenceOverlay presenceOverlay;
  private BackgroundStage.Source cameraFallbackSource = BackgroundStage.Source.SOLID;
  private boolean cameraStreaming;
  private androidx.camera.view.PreviewView cameraPreview;
  private final AdjustmentOverlay adjustmentOverlay;
  private final IntroCard introCard;
  private final SearchOverlay searchOverlay;
  private final QueueOverlay queueOverlay;
  private final RemoteStatusView remoteStatusView;
  private final Keypad keypad;

  // Kanta-Connect QR code overlay
  private final QrCodeView kantaConnectQrView;
  private final FrameLayout qrContainer;
  private StagePresenter.KantaConnectQRInfo currentQrInfo;

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
  /** A drum roll is playing; the score card is scheduled but not yet on screen. */
  private Runnable pendingReveal;
  private long revealGeneration;

  // BGV auto-shuffle state
  private final SettingsStore settingsStore;
  private long lastBgvShuffleTime = 0;
  private boolean bgvShuffleInitialized = false;

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
    this.settingsStore = new SettingsStore(context);

    setWillNotDraw(false);
    setClipChildren(false);
    setClipToPadding(false);

    // Initialize stage overlays
    lyricStage = new LyricStage(context);
    lyricStage.setPitchSource(session);
    countdownStage = new CountdownStage(context);
    scoreOverlay = new ScoreOverlay(context);
    celebration = new ScoreCelebrationView(context);
    backgroundStage = new BackgroundStage(context);
    presenceOverlay = new StagePresenceOverlay(context);
    presenceOverlay.setTag("kanta.stage.presence");
    presenceOverlay.setInteractive(false);
    backgroundStage.setVideoFrameListener(() -> {
      if (presenceOverlay.getVisibility() == View.VISIBLE) {
        presenceOverlay.recordLiveFrame();
        presenceOverlay.invalidate();
      }
    });
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

    // Initialize Kanta-Connect QR code overlay
    kantaConnectQrView = new QrCodeView(context);
    qrContainer = new FrameLayout(context);
    qrContainer.setVisibility(View.GONE);
    qrContainer.setOnClickListener(v -> hideKantaConnectQR());
        // Kanta-Connect QR code overlay initialized (doc-sync)

    // Add overlays in z-order (background first, topmost last)
    addView(backgroundStage, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    // The standby presence sits above the background video and below every functional
    // overlay, so live lyrics, countdown, score and search always win.
    addView(presenceOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
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
    // QR container on top for Kanta-Connect pairing
    addView(qrContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

    // Initialize pitch training overlays
    pitchGameOverlay = new PitchGameOverlay(context);
    pitchGameOverlay.setPitchSource(session);
    pitchGameOverlay.setListener(new PitchGameOverlay.PitchGameListener() {
      @Override public void onGameStarted() {}
      @Override public void onGameEnded(int score, int maxStreak) {}
      @Override public void onDismissed() {}
    });

    learningAssistantOverlay = new LearningAssistantOverlay(context);
    learningAssistantOverlay.setPitchSource(session);
    learningAssistantOverlay.setListener(new LearningAssistantOverlay.LearningAssistantListener() {
      @Override public void onAdviceGenerated(String advice) {}
      @Override public void onDismissed() {}
    });

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
    isPlaying = true;
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
    hideKantaConnectQR(); // Hide QR when playback prepares
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
    cancelPendingReveal();
    celebration.cancel();
    hideKantaConnectQR(); // Hide QR when playback stops
  }

  @Override
  public void showScore(ScoreResult score) {
    // A drum roll lands first, so the number arrives on the accent instead of appearing next
    // to the last chord. The plan is framework-free; this method only schedules it.
    cancelPendingReveal();
    final long generation = revealGeneration;
    final ScoreResult result = score;
    session.playEffectCue(ScoreRevealPlan.drumrollSlot());
    pendingReveal =
        () -> {
          pendingReveal = null;
          if (generation != revealGeneration) return;
          session.playEffectCue(ScoreRevealPlan.revealSlot(result.tier));
          // Deterministic per completion: the same score for the same song always plays the
          // same choreography, so the celebration is reproducible and testable.
          long seed =
              (long) result.overallScore * 31L
                  + (result.song != null && result.song.id != null
                      ? result.song.id.hashCode()
                      : 0);
          celebration.celebrate(result.overallScore, seed);
          scoreOverlay.show(result, () -> {
            // Score dismissed - the celebration has its own bounded duration.
            celebration.cancel();
          });
        };
    handler.postDelayed(pendingReveal, ScoreRevealPlan.DRUMROLL_MILLIS);
  }

  /** Drops a scheduled score card, so a stop or a second completion cannot reveal a stale one. */
  private void cancelPendingReveal() {
    revealGeneration++;
    if (pendingReveal != null) {
      handler.removeCallbacks(pendingReveal);
      pendingReveal = null;
    }
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
  public ScoreResultTier celebrationTier() {
    return celebration.currentTier();
  }

  /** Exposes the live renderer state for acceptance checks without owning session state. */
  public BackgroundStage.Source backgroundSource() {
    return backgroundStage.getSource();
  }

  /** Sets the remote connection state. */
  public void setRemoteState(RemoteStatusView.State state) {
    remoteStatusView.setState(state);
  }

  /** Sets the connected client count. */
  public void setRemoteConnectedCount(int count) {
    remoteStatusView.setConnectedCount(count);
  }

  /** Projects live search results onto the stage search overlay. */
  public void setSearchQuery(String query) {
    if (searchOverlay != null) searchOverlay.setQuery(query);
  }

  /** Shows the Kanta-Connect pairing QR code on the stage. */
  @Override
  public void showKantaConnectQR(StagePresenter.KantaConnectQRInfo info) {
    this.currentQrInfo = info;
    try {
      QrEncoder.Symbol symbol = QrEncoder.encode(info.link);
      kantaConnectQrView.setSymbol(symbol);

      // Build the QR container with the code and instructions
      qrContainer.removeAllViews();

      float density = getResources().getDisplayMetrics().density;
      LinearLayout content = new LinearLayout(getContext());
      content.setOrientation(LinearLayout.VERTICAL);
      content.setGravity(Gravity.CENTER);
      content.setPadding((int)(24 * density), (int)(24 * density), (int)(24 * density), (int)(24 * density));

      // Semi-transparent dark background
      content.setBackgroundColor(0xCC000000);

      // Title
      TextView title = new TextView(getContext());
      title.setText("Kanta-Connect");
      title.setTextSize(20);
      title.setTextColor(0xFFFFFFFF);
      title.setTypeface(null, android.graphics.Typeface.BOLD);
      title.setGravity(Gravity.CENTER);
      content.addView(title);

      // Address
      TextView address = new TextView(getContext());
      address.setText(info.host + ":" + info.port);
      address.setTextSize(14);
      address.setTextColor(0xFFFFFFFF);
      address.setGravity(Gravity.CENTER);
      content.addView(address);

      // QR code
      LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(
          (int)(232 * density), (int)(232 * density));
      qrParams.gravity = Gravity.CENTER_HORIZONTAL;
      qrParams.topMargin = (int)(16 * density);
      qrParams.bottomMargin = (int)(16 * density);
      content.addView(kantaConnectQrView, qrParams);

      // Instructions
      TextView instruction = new TextView(getContext());
      instruction.setText("Scan with Kanta-Connect app\nor tap to dismiss");
      instruction.setTextSize(12);
      instruction.setTextColor(0xFFBBBBBB);
      instruction.setGravity(Gravity.CENTER);
      content.addView(instruction);

      // Pairing code (grouped)
      TextView codeView = new TextView(getContext());
      codeView.setText(KantaConnectInfo.groupedCode(info.code));
      codeView.setTextSize(13);
      codeView.setTextColor(0xFFFFFFFF);
      codeView.setLetterSpacing(0.05f);
      codeView.setGravity(Gravity.CENTER);
      codeView.setPadding(0, (int)(8 * density), 0, 0);
      content.addView(codeView);

      FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
          LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
      containerParams.gravity = Gravity.CENTER;
      qrContainer.addView(content, containerParams);

      qrContainer.setVisibility(View.VISIBLE);
      bringChildToFront(qrContainer);
    } catch (RuntimeException e) {
      // Encoding failed (payload too long) - silently ignore
      qrContainer.setVisibility(View.GONE);
    }
  }

  /** Hides the Kanta-Connect pairing QR code from the stage. */
  @Override
  public void hideKantaConnectQR() {
    if (qrContainer.getVisibility() == View.VISIBLE) {
      qrContainer.setVisibility(View.GONE);
      qrContainer.removeAllViews();
      kantaConnectQrView.setSymbol(null);
      currentQrInfo = null;
    }
  }

  /** Returns true if the Kanta-Connect QR code is currently visible. */
  public boolean isKantaConnectQRVisible() {
    return qrContainer.getVisibility() == View.VISIBLE;
  }

  /** Refreshes all stage components. Called periodically from MainActivity. */
  public void refresh() {
    // SessionController is authoritative for visual selection; project it into
    // the presenter's existing background renderer, never into a second stage.
    // Camera opt-in is authoritative for the visible source. Scenic selection must
    // never clobber a live preview during the 100ms refresh tick.
    if ("camera".equals(session.stageKind()) || session.cameraPreviewEnabled()) {
      backgroundStage.setSource(BackgroundStage.Source.CAMERA);
    } else if (("photo".equals(session.stageKind()) || "scenic".equals(session.stageKind()))
        && session.scenicBitmap() != null) {
      backgroundStage.setPhoto(session.scenicBitmap());
    } else if ("video".equals(session.stageKind()) && session.stageVideo() != null) {
      backgroundStage.setVideo(session.stageVideo());
    } else {
      backgroundStage.setSource(BackgroundStage.Source.SOLID);
    }
    // Standby presence: shown only when no song is on the stage. A reserved song keeps
    // the lyric stage, so the prompt never covers a real performance.
    Song current = session.currentSong();
    boolean standby = current == null && !session.isPlaying();
    presenceOverlay.bind(standby ? null : current);
    lyricStage.setStandby(standby);
    // Update lyrics if playing
    if (session.isPlaying() && session.lyrics != null && !session.lyrics.isEmpty()) {
      MidiLyrics.Cue[] cues = session.lyrics.toArray(new MidiLyrics.Cue[0]);
      lyricStage.setLyrics(cues, session.getClock());
    }
    // Update remote status
    // This would be updated from CompanionControl

    // BGV auto-shuffle: periodically switch to a random bundled BGV
    maybeShuffleBgv();
  }

  /** Periodically shuffles to a random bundled BGV when auto-shuffle is enabled. */
  private void maybeShuffleBgv() {
    if (settingsStore == null) return;
    if (!settingsStore.getBgvAutoShuffle()) return;
    if (backgroundStage.getSource() != BackgroundStage.Source.BGV) return;

    long now = System.currentTimeMillis();
    int intervalMs = settingsStore.getBgvShuffleInterval() * 1000; // convert seconds to ms
    if (intervalMs <= 0) return;

    // Initialize on first call
    if (!bgvShuffleInitialized) {
      lastBgvShuffleTime = now;
      bgvShuffleInitialized = true;
      return;
    }

    if (now - lastBgvShuffleTime < intervalMs) return;

    // Pick a random bundled BGV (different from current if possible)
    BundledBgvCatalog.Clip nextClip = BundledBgvCatalog.random();
    if (nextClip == null) return;

    // Copy to stage-media directory
    java.io.File stageMediaDir = new java.io.File(backgroundStage.getContext().getFilesDir(), "stage-media");
    java.io.File dest = new java.io.File(stageMediaDir, nextClip.id + ".video");
    if (BundledBgvCatalog.copyTo(backgroundStage.getContext(), nextClip, dest)) {
      // Update session via public method
      session.setBundledBgv(nextClip, dest);
      lastBgvShuffleTime = now;
    }
  }

  /** Standby presence view; the runtime check proves it is above live video. */
  public StagePresenceOverlay presenceOverlay() { return presenceOverlay; }

  /** Sets whether the visual layer is active. */
  public void setVisualActive(boolean active) {
    backgroundStage.setVisualActive(active);
  }

  /** Starts microphone-backed pitch practice only after explicit user action. */
  public void startPitchPractice() { showPitchGame(session.practiceTargetHz()); }

  /** Starts microphone-backed voice coaching only after explicit user action. */
  public void startVoiceCoach() { showLearningAssistant(); }

  /** Reparents the original practice feature into MainActivity's lower utility panel. */
  public PitchGameOverlay detachPitchGameOverlay() {
    if (pitchGameOverlay.getParent() instanceof android.view.ViewGroup)
      ((android.view.ViewGroup) pitchGameOverlay.getParent()).removeView(pitchGameOverlay);
    return pitchGameOverlay;
  }

  /** Reparents the original coach feature into MainActivity's lower utility panel. */
  public LearningAssistantOverlay detachLearningAssistantOverlay() {
    if (learningAssistantOverlay.getParent() instanceof android.view.ViewGroup)
      ((android.view.ViewGroup) learningAssistantOverlay.getParent()).removeView(learningAssistantOverlay);
    return learningAssistantOverlay;
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
    cameraStreaming = visible;
  }

  /** Prepares and returns the primary stage's CameraX preview, preserving its prior background. */
  public androidx.camera.view.PreviewView getCameraPreview() {
    if (backgroundStage.getSource() != BackgroundStage.Source.CAMERA)
      cameraFallbackSource = backgroundStage.getSource();
    backgroundStage.setSource(BackgroundStage.Source.CAMERA);
    if (cameraPreview == null) {
      cameraPreview = new androidx.camera.view.PreviewView(getContext());
      cameraPreview.setImplementationMode(
          androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE);
      cameraPreview.setScaleType(androidx.camera.view.PreviewView.ScaleType.FILL_CENTER);
      cameraPreview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      cameraPreview.setLayoutParams(
          new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      // Keep the primary preview in the presenter hierarchy. The BackgroundStage owns the
      // selected CAMERA source, while this sibling receives the CameraX surface at full stage
      // dimensions; nested PreviewViews can remain zero-sized during workspace transitions.
      addView(cameraPreview, 1);
    }
    return cameraPreview;
  }

  /** Releases the primary preview and restores the background selected before camera opt-in. */
  public void releaseCameraPreview() {
    backgroundStage.releaseCameraPreview(cameraFallbackSource);
    if (cameraPreview != null) {
      if (cameraPreview.getParent() == this) removeView(cameraPreview);
      cameraPreview = null;
    }
    cameraStreaming = false;
    cameraFallbackSource = BackgroundStage.Source.SOLID;
  }

  /** Reports only an attached preview belonging to the active camera background. */
  public boolean isCameraVisible() {
    return cameraStreaming
        && backgroundStage.getSource() == BackgroundStage.Source.CAMERA
        && cameraPreview != null
        && cameraPreview.getParent() == this;
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    cancelPendingReveal();
    handler.removeCallbacks(frameTicker);
    countdownStage.cancel();
    pitchGameOverlay.shutdown();
    learningAssistantOverlay.shutdown();
  }
}
