"""G4: interlude timer — auto-start the next reserved song with a visible countdown."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class InterludeTimerG4(unittest.TestCase):
    def test_settings_key_and_session_accessors(self):
        settings = (JAVA / 'settings/SettingsStore.java').read_text()
        self.assertIn('KEY_INTERLUDE_TIMER_SECONDS = "interlude.timerSeconds"', settings)
        self.assertIn('getInterludeTimerSeconds()', settings)
        self.assertIn('setInterludeTimerSeconds(', settings)
        session = (JAVA / 'SessionController.java').read_text()
        for m in ('interludeTimerSeconds()', 'setInterludeTimerSeconds(',
                  'interludeTimerRemainingMs()', 'startNextNow()'):
            self.assertIn(m, session)
        # Only the four armed durations are accepted; anything else arms Off.
        self.assertIn('seconds == 5 || seconds == 10 || seconds == 15 || seconds == 30', session)

    def test_timer_branch_replaces_fixed_delay(self):
        session = (JAVA / 'SessionController.java').read_text()
        self.assertIn('int timerSeconds = settings.getInterludeTimerSeconds();', session)
        self.assertIn('if (timerSeconds > 0) {', session)
        # The legacy 3 s auto-advance stays as the Off path.
        self.assertIn('main.postDelayed(autoAdvance, tier == null ? 3000L : reveal + 5000L);',
                      session)
        # Cancellation (auto-advance clear) also clears the countdown clock.
        self.assertGreaterEqual(session.count('interludeTimerEndsAt = -1;'), 2)
        # Manual override guards playback state before calling the manual path.
        self.assertIn('if (!foreground || loading || current != null || queue.peek() == null) return;\n    next();',
                      session)

    def test_stage_hud_bar_and_start_now(self):
        main = (JAVA / 'MainActivity.java').read_text()
        for m in ('"kanta.stage.timer"', '"kanta.stage.timer.start"', '"kanta.stage.timer.bar"',
                  'session.startNextNow()', 'session.interludeTimerRemainingMs()',
                  'refreshStageTimerPill()', 'R.string.stage_timer_start_now',
                  'R.string.stage_timer_label'):
            self.assertIn(m, main)
        # The bar refresh rides the 100 ms stage tick, next to the playback time.
        self.assertIn('refreshStagePlaybackTime();\n          refreshStageTimerPill();', main)

    def test_editor_pills_and_strings(self):
        main = (JAVA / 'MainActivity.java').read_text()
        for m in ('"kanta.interlude.timer"', 'R.string.interlude_timer_heading',
                  'new int[] { 0, 5, 10, 15, 30 }[index]'):
            self.assertIn(m, main)
        res = (ROOT / 'apps/android/app/src/main/res/values/lyrics_style_strings.xml').read_text()
        for s in ('interlude_timer_heading', 'interlude_timer_off', 'interlude_timer_5s',
                  'interlude_timer_10s', 'interlude_timer_15s', 'interlude_timer_30s',
                  'interlude_timer_help', 'stage_timer_start_now', 'stage_timer_label'):
            self.assertIn(f'name="{s}"', res)
