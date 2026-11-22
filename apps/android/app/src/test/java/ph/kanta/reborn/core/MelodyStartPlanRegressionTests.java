package ph.kanta.reborn.core;

/** Host-JVM checks for melody-aligned countdown scheduling. */
public final class MelodyStartPlanRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  public static int run() {
    int before = checks;
    MelodyStartPlan.Schedule legacy = MelodyStartPlan.at(0, 3, 5000);
    check(!legacy.startAudioImmediately, "Unknown melody must retain countdown-first start");
    check(legacy.showCountdown && legacy.countdownAtMillis == 5000,
        "Unknown melody must retain the immediate legacy countdown");

    MelodyStartPlan.Schedule longIntro = MelodyStartPlan.at(30000, 3, 5000);
    check(longIntro.startAudioImmediately, "A long MIDI intro must start at readiness");
    check(longIntro.showCountdown, "A long intro needs its melody countdown");
    check(longIntro.countdownAtMillis == 32000,
        "GO is scheduled three seconds before the 30-second melody");

    MelodyStartPlan.Schedule shortIntro = MelodyStartPlan.at(3000, 3, 5000);
    check(shortIntro.startAudioImmediately && !shortIntro.showCountdown,
        "A countdown-length intro skips the countdown");
    MelodyStartPlan.Schedule veryShortIntro = MelodyStartPlan.at(1200, 3, 5000);
    check(veryShortIntro.startAudioImmediately && !veryShortIntro.showCountdown,
        "A short intro skips the countdown and uses the playback clock for its title");

    MelodyStartPlan.Schedule instant = MelodyStartPlan.at(30000, 0, 5000);
    check(instant.startAudioImmediately && !instant.showCountdown,
        "Zero countdown length keeps instant audio without a countdown");
    check(!MelodyStartPlan.at(-1, 3, 5000).startAudioImmediately,
        "An invalid negative melody offset is treated as unknown");
    return checks - before;
  }

  private MelodyStartPlanRegressionTests() {}
}
