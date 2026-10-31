package ph.kanta.reborn.core;

import java.util.List;

/**
 * Pure lyric-animation math shared by the Android stage views.
 *
 * <p>Highlight progression is derived from the source MIDI lyric fragments (no word timing is
 * invented): a fragment boundary is a hard step, and the span between two boundaries is
 * interpolated so a long syllable fills smoothly instead of popping as a whole word.
 *
 * <p>Pitch energy is only a visual energy signal. It never changes the lyric timeline, the
 * playback clock, or scoring; an absent/stale estimate decays to zero so the stage still renders
 * exactly on the source cues when the microphone is off.
 */
public final class LyricWordAnimation {

  private LyricWordAnimation() {}

  /** Number of source-timed characters highlighted at {@code positionMilliseconds}. */
  public static float highlightedCharacters(MidiLyrics.Cue cue, long positionMilliseconds) {
    if (cue == null) return 0f;
    List<MidiLyrics.Fragment> fragments = cue.fragments;
    if (fragments == null || fragments.isEmpty()) return 0f;
    int total = cue.line.length();
    int lastCharacters = 0;
    long lastMilliseconds = cue.milliseconds;
    for (int index = 0; index < fragments.size(); index++) {
      MidiLyrics.Fragment fragment = fragments.get(index);
      int endCharacters = clamp(fragment.endCharacters, 0, total);
      if (fragment.milliseconds <= positionMilliseconds) {
        lastCharacters = endCharacters;
        lastMilliseconds = fragment.milliseconds;
        continue;
      }
      long span = fragment.milliseconds - lastMilliseconds;
      if (span <= 0) return lastCharacters;
      float fraction =
          Math.max(0f, Math.min(1f, (positionMilliseconds - lastMilliseconds) / (float) span));
      return lastCharacters + fraction * (endCharacters - lastCharacters);
    }
    return lastCharacters;
  }

  /**
   * Character range of the fragment currently being sung, as {start, endExclusive}.
   * Returns an empty range when there is no fragment at or before the position.
   */
  public static int[] activeFragment(MidiLyrics.Cue cue, long positionMilliseconds) {
    if (cue == null) return new int[] {0, 0};
    List<MidiLyrics.Fragment> fragments = cue.fragments;
    if (fragments == null || fragments.isEmpty()) return new int[] {0, 0};
    int total = cue.line.length();
    int start = 0;
    int end = 0;
    for (int index = 0; index < fragments.size(); index++) {
      MidiLyrics.Fragment fragment = fragments.get(index);
      if (fragment.milliseconds > positionMilliseconds) break;
      start = end;
      end = clamp(fragment.endCharacters, 0, total);
    }
    return new int[] {start, end};
  }

  /**
   * Visual pitch energy in [0, 1] for a recent microphone estimate.
   *
   * @param pitchHz current estimate, 0 when unavailable
   * @param measuredAtMs elapsed-realtime timestamp of the estimate, 0 when unknown
   * @param nowMs elapsed-realtime now
   */
  public static float pitchEnergy(float pitchHz, long measuredAtMs, long nowMs) {
    if (pitchHz <= 40f || pitchHz > 1500f || measuredAtMs <= 0L) return 0f;
    long age = nowMs - measuredAtMs;
    if (age < 0L || age > 900L) return 0f;
    float freshness = 1f - Math.min(1f, age / 900f);
    float band =
        (float) (Math.log(pitchHz / 80.0) / Math.log(1000.0 / 80.0));
    return Math.max(0f, Math.min(1f, band)) * freshness;
  }

  private static int clamp(int value, int min, int max) {
    return value < min ? min : Math.min(value, max);
  }
}
