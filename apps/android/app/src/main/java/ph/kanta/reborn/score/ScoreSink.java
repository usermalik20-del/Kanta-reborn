package ph.kanta.reborn.score;

import ph.kanta.reborn.core.MelodyScoreSession;
import ph.kanta.reborn.core.Song;
import ph.kanta.reborn.core.score.ScoreResultTier;
import java.util.UUID;

/**
 * Sink for completed score results.
 *
 * <p>Receives the final score from {@link ph.kanta.reborn.core.MelodyScoreSession}
 * and persists or displays it. The existing SessionController already produces
 * {@link ph.kanta.reborn.SessionController.CompletedScore} — this contract formalizes
 * the handoff for the karaoke stage overlays.
 *
 * <p>{@link CompletedScore#overall()} is the presented score (floored at
 * {@link ScoreResultTier#FLOOR}); {@link CompletedScore#estimate} keeps the raw measurement so
 * the breakdown never overstates what the microphone heard.
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

    /** Presented overall score, 50-100. */
    public int overall() {
      return ScoreResultTier.present(estimate != null ? estimate.overall : 0);
    }

    /** The unmodified 0-100 estimate, for diagnostics and logs. */
    public int rawOverall() {
      return estimate != null ? (int) Math.round(estimate.overall) : 0;
    }

    /** Pitch accuracy 0-100. */
    public int pitch() {
      return estimate != null ? (int) (estimate.pitchAccuracy * 100) : 0;
    }

    /** Timing accuracy 0-100 (using coverage as proxy). */
    public int timing() {
      return estimate != null ? (int) (estimate.coverage * 100) : 0;
    }

    /** Bonus score (using matchedSlots as proxy). */
    public int bonus() {
      return estimate != null ? estimate.matchedSlots : 0;
    }

    /** The result band for the presented score. */
    public ScoreResultTier tier() {
      return ScoreResultTier.fromScore(overall());
    }
  }
}
