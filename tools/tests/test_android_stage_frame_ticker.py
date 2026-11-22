"""Source contracts for the embedded karaoke stage's rendered-frame ticker.

The embedded ``StagePresenterImpl`` is the only consumer that feeds
``LyricStage.setPlayedFrames``. If its ticker never runs, ``LyricStage`` renders
``clock.position(0)`` and the embedded lyrics stay frozen on the first cue while the
fullscreen renderer, which reads ``session.position()`` directly, keeps moving.

Measured behavior belongs to the Android ``RuntimeChecks``; these assertions guard the
source-level wiring that makes that behavior reachable.
"""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class StageFrameTickerTests(unittest.TestCase):
    def setUp(self):
        self.presenter = (JAVA / 'stage/StagePresenterImpl.java').read_text()

    def test_ticker_start_is_idempotent(self):
        start = self.presenter.split('private void startFrameTicker()', 1)[1].split('\n  }', 1)[0]
        # A second start while the ticker is live would double the 60fps callback chain.
        self.assertIn('if (tickerRunning) return;', start)
        self.assertIn('tickerRunning = true;', start)
        self.assertIn('handler.post(frameTicker);', start)

    def test_stop_clears_running_state_and_callbacks(self):
        stop = self.presenter.split('private void stopFrameTicker()', 1)[1].split('\n  }', 1)[0]
        self.assertIn('tickerRunning = false;', stop)
        self.assertIn('handler.removeCallbacks(frameTicker);', stop)

    def test_lifecycle_entry_points_use_the_guarded_helpers(self):
        for method in ('startPlayback', 'pausePlayback', 'resumePlayback', 'stopPlayback'):
            body = self.presenter.split(f'public void {method}(', 1)[1].split('\n  }', 1)[0]
            self.assertNotIn('handler.removeCallbacks(frameTicker)', body, method)
            self.assertNotIn('handler.post(frameTicker)', body, method)
        self.assertIn('startFrameTicker();', self.presenter.split('public void startPlayback(', 1)[1].split('\n  }', 1)[0])
        self.assertIn('stopFrameTicker();', self.presenter.split('public void pausePlayback(', 1)[1].split('\n  }', 1)[0])
        self.assertIn('startFrameTicker();', self.presenter.split('public void resumePlayback(', 1)[1].split('\n  }', 1)[0])

    def test_refresh_drives_the_ticker_from_session_state(self):
        """MainActivity never calls the presenter's playback lifecycle methods."""
        refresh = self.presenter.split('public void refresh()', 1)[1].split('\n  }', 1)[0]
        self.assertIn('isPlaying = session.isPlaying();', refresh)
        # A detached stage must not hold a 60fps callback chain.
        self.assertIn('if (isPlaying && isAttachedToWindow()) startFrameTicker();', refresh)
        self.assertIn('else stopFrameTicker();', refresh)

    def test_attach_and_detach_bound_the_ticker_lifecycle(self):
        attach = self.presenter.split('protected void onAttachedToWindow()', 1)[1] \
            .split('\n  }', 1)[0]
        self.assertIn('if (isPlaying) startFrameTicker();', attach)
        detach = self.presenter.split('protected void onDetachedFromWindow()', 1)[1] \
            .split('\n  }', 1)[0]
        self.assertIn('stopFrameTicker();', detach)

    def test_ticker_self_terminates_and_resets_its_flag(self):
        ticker = self.presenter.split('frameTicker = new Runnable()', 1)[1].split('};', 1)[0]
        self.assertIn('if (!isPlaying) {', ticker)
        self.assertIn('tickerRunning = false;', ticker)
        self.assertIn('return;', ticker)
        # The re-arm must stay inside the running branch so a stopped ticker stays stopped.
        running = ticker.split('if (!isPlaying) {', 1)[1].split('\n        }', 1)[1]
        self.assertIn('handler.postDelayed(this, 16);', running)

    def test_detach_stops_the_ticker(self):
        detach = self.presenter.split('protected void onDetachedFromWindow()', 1)[1]
        self.assertIn('stopFrameTicker();', detach)

    def test_played_frames_matches_the_clock_sample_rate(self):
        """LyricStage feeds these frames to RenderedPlaybackClock.position()."""
        playback = (JAVA / 'SoundFontPlayback.java').read_text()
        self.assertIn('RATE = 44100', playback)
        self.assertIn('session.position() * 44100 / 1000', self.presenter)


if __name__ == '__main__':
    unittest.main()
