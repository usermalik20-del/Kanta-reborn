"""Ticker source contracts; measured behavior belongs to Android RuntimeChecks."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class UpNextTickerTests(unittest.TestCase):
    def test_bounded_native_marquee_with_accessible_text(self):
        source = (JAVA / 'ui/components/UpNextTicker.java').read_text()
        for marker in ('TextUtils.TruncateAt.MARQUEE', 'setMarqueeRepeatLimit(-1)',
                       'Math.min(MAX_ITEMS, entries.size())', 'bounded(entry.song.title, 60)',
                       'bounded(entry.singer, 30)', 'entries.size() - MAX_ITEMS',
                       'Up next · ', 'setFocusable(false)', 'setClickable(false)'):
            self.assertIn(marker, source)
        self.assertIn('TextUtils.equals(text.getText(), next)', source)
        for forbidden in ('new Handler', 'new Timer', 'postDelayed', 'requestFocus'):
            self.assertNotIn(forbidden, source)

    def test_stops_for_host_visibility_and_detach(self):
        source = (JAVA / 'ui/components/UpNextTicker.java').read_text()
        for marker in ('setVisualActive(boolean active)', 'visualActive && isAttachedToWindow() && isShown()',
                       'onDetachedFromWindow()', 'text.setSelected(false)',
                       'onVisibilityChanged(View changedView, int visibility)',
                       'onWindowVisibilityChanged(int visibility)'):
            self.assertIn(marker, source)

    def test_stage_renderers_project_fifo_without_removing_repeats(self):
        for filename, snapshot in [('stage/StagePresenterImpl.java', 'queue.snapshot()'),
                                   ('StageSurface.java', 'session.queue.snapshot()')]:
            with self.subTest(filename=filename):
                source = (JAVA / filename).read_text()
                self.assertIn('upNextTicker.updateFromSnapshot(' + snapshot + ')', source)
                self.assertIn('tickerParams.gravity = ', source)
                self.assertIn('Gravity.BOTTOM', source)
                self.assertIn('params.bottomMargin = inset', source)
                self.assertIn('upNextTicker.setVisualActive(', source)
                self.assertNotIn('queueSnapshot.subList(1', source)
                self.assertNotIn('first.song.equals(session.current', source)
        primary = (JAVA / 'stage/StagePresenterImpl.java').read_text()
        self.assertLess(primary.index('addView(upNextTicker,'), primary.index('addView(countdownStage,'))
        fullscreen = (JAVA / 'StageSurface.java').read_text()
        self.assertIn('standby && upNextTicker.getVisibility() != VISIBLE', fullscreen)

    def test_runtime_checks_ticker_updates_and_lifecycle(self):
        source = (ROOT / 'apps/android/app/src/androidTest/java/ph/kanta/reborn/RuntimeChecks.java').read_text()
        self.assertIn('upNextTickerChecks();', source)
        self.assertIn('Intentional repeated song disappeared', source)
        self.assertIn('Ticker did not stop for inactive host', source)
        self.assertIn('Ticker empty queue is still visible', source)


if __name__ == '__main__':
    unittest.main()
