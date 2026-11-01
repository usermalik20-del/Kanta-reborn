package ph.kanta.reborn.core;

import java.util.ArrayList;
import java.util.List;

/** Host-JVM checks for the shared lyric-animation math. No Android framework dependency. */
public final class LyricWordAnimationRegressionTests {

  private LyricWordAnimationRegressionTests() {}

  private static void check(boolean yes, String message) {
    if (!yes) throw new AssertionError(message);
  }

  public static int run() {
    int checks = 0;

    List<MidiLyrics.Fragment> fragments = new ArrayList<>();
    fragments.add(new MidiLyrics.Fragment(0L, 2));
    fragments.add(new MidiLyrics.Fragment(1000L, 5));
    MidiLyrics.Cue cue = new MidiLyrics.Cue(0L, "Hello", fragments);

    check(LyricWordAnimation.highlightedCharacters(null, 0L) == 0f, "Null cue highlight");
    checks++;
    check(
        Math.abs(LyricWordAnimation.highlightedCharacters(cue, -10L) - 0f) < 1e-4f,
        "Pre-cue highlight");
    checks++;
    check(
        Math.abs(LyricWordAnimation.highlightedCharacters(cue, 0L) - 2f) < 1e-4f,
        "First fragment is source-timed");
    checks++;
    check(
        Math.abs(LyricWordAnimation.highlightedCharacters(cue, 500L) - 3.5f) < 1e-4f,
        "Long fragment fills smoothly instead of popping");
    checks++;
    check(
        Math.abs(LyricWordAnimation.highlightedCharacters(cue, 1000L) - 5f) < 1e-4f,
        "Second fragment completes");
    checks++;
    check(
        Math.abs(LyricWordAnimation.highlightedCharacters(cue, 6000L) - 5f) < 1e-4f,
        "Highlight cannot exceed the line");
    checks++;

    int[] first = LyricWordAnimation.activeFragment(cue, 500L);
    check(first[0] == 0 && first[1] == 2, "Active fragment is the first source fragment");
    checks++;
    int[] second = LyricWordAnimation.activeFragment(cue, 1500L);
    check(second[0] == 2 && second[1] == 5, "Active fragment advances at the source boundary");
    checks++;
    int[] none = LyricWordAnimation.activeFragment(cue, -5L);
    check(none[0] == 0 && none[1] == 0, "No active fragment before the line");
    checks++;

    MidiLyrics.Cue plain = new MidiLyrics.Cue(0L, "No fragments", new ArrayList<>());
    check(LyricWordAnimation.highlightedCharacters(plain, 0L) == 0f, "No-fragment line");
    checks++;

    long now = 1_000_000L;
    check(LyricWordAnimation.pitchEnergy(0f, now, now) == 0f, "No pitch estimate");
    checks++;
    check(
        LyricWordAnimation.pitchEnergy(440f, now - 2000L, now) == 0f,
        "Stale pitch estimate decays to zero");
    checks++;
    check(
        LyricWordAnimation.pitchEnergy(5000f, now, now) == 0f,
        "Out-of-band pitch cannot drive energy");
    checks++;
    check(
        LyricWordAnimation.pitchEnergy(440f, now, now) > 0.5f,
        "Recent vocal pitch produces energy");
    checks++;

    return checks;
  }
}
