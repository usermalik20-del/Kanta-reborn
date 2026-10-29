package ph.kanta.reborn.stage;

import ph.kanta.reborn.core.Song;
import ph.kanta.reborn.core.ReservationQueue;
import ph.kanta.reborn.core.MidiLyrics;
import ph.kanta.reborn.core.RenderedPlaybackClock;
import android.graphics.Bitmap;

/**
 * Contract for the karaoke stage presentation layer.
 *
 * <p>This interfaces with the existing SessionController (the single playback/queue owner)
 * and provides the stage overlays: lyrics, countdown, score, background, adjustments, intro.
 */
public interface StagePresenter {

  /** Called when the stage should prepare for a song. */
  void prepare(Song song, ReservationQueue.Entry reservation);

  /** Called when countdown starts (3-2-1-GO). */
  void startCountdown(int countdownSeconds, CountdownListener listener);

  /** Called when playback starts. */
  void startPlayback(RenderedPlaybackClock clock, MidiLyrics.Cue[] lyrics);

  /** Called when playback pauses. */
  void pausePlayback();

  /** Called when playback resumes. */
  void resumePlayback();

  /** Called when playback stops. */
  void stopPlayback();

  /** Called when song completes and score is available. */
  void showScore(ScoreResult score);

  /** Called to update the background (solid, photo, camera, BGV). */
  void setBackground(BackgroundSource source);

  /** Called to show the adjustment overlay (key/tempo/melody/volume). */
  void showAdjustments(AdjustmentListener listener);

  /** Called to hide the adjustment overlay. */
  void hideAdjustments();

  /** Called to show the intro card. */
  void showIntro(IntroCardData data);

  /** Called to hide the intro card. */
  void hideIntro();
  /** Shows the pitch meter game overlay for pitch practice. */
  void showPitchGame(float targetPitchHz);

  /** Hides the pitch meter game overlay. */
  void hidePitchGame();

  /** Shows the learning assistant overlay for real-time coaching. */
  void showLearningAssistant();

  /** Hides the learning assistant overlay. */
  void hideLearningAssistant();

  /** Listener for countdown events. */
  interface CountdownListener {
    void onTick(int remaining);
    void onComplete();
  }

  /** Score result data. */
  final class ScoreResult {
    public final int overallScore; // 0-100
    public final int pitchScore;
    public final int timingScore;
    public final int bonusScore;
    public final String tier; // "Perfect", "Great", "Good", "Miss"
    public final Song song;
    public final String singer;

    public ScoreResult(int overall, int pitch, int timing, int bonus, String tier, Song song, String singer) {
      this.overallScore = overall;
      this.pitchScore = pitch;
      this.timingScore = timing;
      this.bonusScore = bonus;
      this.tier = tier;
      this.song = song;
      this.singer = singer;
    }
  }

  /** Background source types. */
  enum BackgroundSource {
    SOLID,
    PHOTO,
    CAMERA,
    BGV
  }

  /** Adjustment listener for real-time parameter changes. */
  interface AdjustmentListener {
    void onKeyChange(int semitones);
    void onTempoChange(float factor);
    void onMelodyVolumeChange(float volume);
    void onGuideVolumeChange(float volume);
  }

  /** Intro card data. */
  final class IntroCardData {
    public final String title;
    public final String artist;
    public final String duration;
    public final String originalKey;
    public final int tempo;
    public final String genre;

    public IntroCardData(String title, String artist, String duration, String originalKey, int tempo, String genre) {
      this.title = title;
      this.artist = artist;
      this.duration = duration;
      this.originalKey = originalKey;
      this.tempo = tempo;
      this.genre = genre;
    }
  }
}
