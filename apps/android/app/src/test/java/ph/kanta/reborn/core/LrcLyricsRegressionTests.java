package ph.kanta.reborn.core;

import java.util.List;

/** Synthetic LRC only; no network and no user lyrics. */
public final class LrcLyricsRegressionTests {
  private static int checks;
  private static void check(boolean valid, String message) {
    checks++; if (!valid) throw new AssertionError(message);
  }
  public static int run() {
    checks = 0;
    List<MidiLyrics.Cue> cues = LrcLyrics.parse("[ar:Fixture]\n[00:02.50]Second\n[00:01.2][00:03.123]First\n[00:04.00]\n");
    check(cues.size() == 4, "Multiple timestamps and blank instrumental cue preserved");
    check(cues.get(0).milliseconds == 1200 && cues.get(0).line.equals("First"), "Fraction and sort");
    check(cues.get(1).milliseconds == 2500, "Centiseconds");
    check(cues.get(2).milliseconds == 3123, "Milliseconds");
    check(cues.get(3).line.isEmpty(), "Blank cue clears previous lyric");
    check(cues.get(0).fragments.size() == 1, "Line timing, not fabricated word timing");
    check(cues.get(0).highlightedCharacters(1199) == 0, "No highlight before timestamp");
    check(cues.get(0).highlightedCharacters(1200) == 5, "Full line at timestamp");
    check(LrcLyrics.parse("[offset:-2000]\n[00:01.00]A").get(0).milliseconds == 0, "Negative offset clamped");
    check(LrcLyrics.parse("[offset:500]\n[00:01]A").get(0).milliseconds == 1500, "Positive offset");
    check(LrcLyrics.parse("plain text without timing").isEmpty(), "Plain text never gets fake timestamps");
    check(LrcLyrics.parse("[00:60.00]bad\n[9999:00]bad").isEmpty(), "Invalid times ignored");
    check(LrcLyrics.parse(null).isEmpty(), "Null LRC safe");
    check(LrcLyrics.parse("[offset:9999999]\n[00:01]bad").isEmpty(), "Offset bound");
    check(LrcLyrics.parse("x".repeat(512 * 1024 + 1)).isEmpty(), "Input size bounded");
    check(LrcLyrics.parse("[00:01]" + "x".repeat(4097)).isEmpty(), "Line size bounded");
    check(LrcLyrics.parse("[00:01]x\n".repeat(10001)).isEmpty(), "Cue count bounded");
    boolean immutable = false;
    try { cues.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
    check(immutable, "Published cue list immutable");
    return checks;
  }
}
