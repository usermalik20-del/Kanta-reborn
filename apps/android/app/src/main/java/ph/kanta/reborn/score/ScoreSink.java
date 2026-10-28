package ph.kanta.reborn.score;

import ph.kanta.reborn.core.Song;
import java.util.UUID;

/**
 * Sink for completed score results.
 *
 * <p>Receives the final score from {@link ph.kanta.reborn.core.MelodyScoreSession}
 * and persists or displays it. The existing SessionController already produces
 * {@link ph.kanta.reborn.SessionController.CompletedScore} — this contract formalizes
 * the handoff for the karaoke stage overlays.
 */
public interface ScoreSink {

  /** Called when a song completes with a score estimate. */
  void onScoreCompleted(CompletedScore score);

  /** Called when scoring is cancelled or fails. */
  void onScoreCancelled(UUID playbackIdentity);

  /** Completed score data (mirrors SessionController.CompletedScore). */
  final class CompletedScore {
    public final UUID playbackIdentity;
    public final String title;
    public final String singer;
    public final MelodyScoreSession.Estimate estimate;

    public CompletedScore(UUID playbackIdentity, String title, String singer,
        MelodyScoreSession.Estimate estimate) {
      this.playbackIdentity = playbackIdentity;
      this.title = title;
      this.singer = singer;
      this.estimate = estimate;
    }

    /** Overall score 0-100. */
    public int overall() {
      return estimate != null ? estimate.overall : 0;
    }

    /** Pitch accuracy 0-100. */
    public int pitch() {
      return estimate != null ? estimate.pitch : 0;
    }

    /** Timing accuracy 0-100. */
    public int timing() {
      return estimate != null ? estimate.timing : 0;
    }

    /** Bonus score. */
    public int bonus() {
      return estimate != null ? estimate.bonus : 0;
    }

    /** Tier label. */
    public String tier() {
      int o = overall();
      if (o >= 95) return "Perfect";
      if (o >= 85) return "Great";
      if (o >= 70) return "Good";
      return "Miss";
    }
  }
}
