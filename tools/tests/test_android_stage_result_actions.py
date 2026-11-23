from pathlib import Path
import unittest
from xml.etree import ElementTree

MAIN = Path(__file__).resolve().parents[2] / 'apps/android/app/src/main'
JAVA = MAIN / 'java/ph/kanta/reborn'


class StageResultActionsTest(unittest.TestCase):
    def test_direct_play_does_not_change_up_next(self):
        controller = (JAVA / 'SessionController.java').read_text()
        direct = controller.split('void playSearchResult(', 1)[1].split('\n  }', 1)[0]
        self.assertIn('startLocal(new ReservationQueue.Entry(UUID.randomUUID(), song, singer), false);', direct)
        self.assertNotIn('queue.', direct)
        prepare = controller.split('private void prepare(', 1)[1]
        self.assertIn('if (queued) queue.remove(entry.id);', prepare)
        self.assertIn('if (queued) saveQueue();', prepare)
        home = (JAVA / 'ui/HomeScreen.java').read_text()
        self.assertIn('addMatchAction(result, R.string.stage_reserve', home)
        self.assertIn('addMatchAction(result, R.string.stage_play', home)

    def test_inline_keyboard_keeps_space_and_adjustment_actions(self):
        keyboard = (JAVA / 'ui/components/InlineKeyboard.java').read_text()
        self.assertIn('addAdjustments(actions, R.string.stage_key_short', keyboard)
        self.assertIn('R.string.stage_tempo_short', keyboard)
        self.assertIn('R.string.keyboard_space', keyboard)
        self.assertIn('addKey(row, "−"', keyboard)
        self.assertIn('addKey(row, "+"', keyboard)
        self.assertIn('R.string.keyboard_delete_short', keyboard)
        home = (JAVA / 'ui/HomeScreen.java').read_text()
        reserve = home.split('addSongAction(actions, R.string.stage_reserve', 1)[1].split(');', 1)[0]
        self.assertIn('this::submitEntry', reserve)

    def test_stage_result_deadline_rejects_stale_generations(self):
        home = (JAVA / 'ui/HomeScreen.java').read_text()
        self.assertIn('SONG_LOOKUP_TIMEOUT_MS = 8_000L', home)
        self.assertIn('lookupHandler.postAtTime(expireSongLookup, songLookupDeadline)', home)
        self.assertIn('SystemClock.uptimeMillis() >= songLookupDeadline', home)
        dismiss = home.split('public void dismissSongLookup()', 1)[1].split('\n  }', 1)[0]
        self.assertIn('lookupHandler.removeCallbacks(expireSongLookup)', dismiss)
        self.assertIn('setLiveMatchesVisible(false)', dismiss)
        self.assertIn('reservedSongs.removeAllViews()', dismiss)
        self.assertNotIn('SearchText(', dismiss)
        show = home.split('public void showMatches(', 1)[1]
        self.assertLess(show.index('generation != activeSongLookupGeneration()'),
                        show.index('reservedSongs.removeAllViews()'))

    def test_stage_controls_have_text_and_distinct_accessibility_labels(self):
        home = (JAVA / 'ui/HomeScreen.java').read_text()
        self.assertIn('button.setText(label);', home)
        self.assertNotIn('ImageButton', home)
        self.assertNotIn('playSearch =', home)
        self.assertIn('playing ? R.string.stage_pause : R.string.stage_play', home)
        self.assertIn('button.setContentDescription(context.getString(description))', home)
        self.assertIn('R.string.stage_pause_playback', home)
        self.assertIn('R.string.stage_resume_playback', home)
        self.assertIn('R.string.stage_clear_search', home)
        self.assertIn('clearSearch.setVisibility(e.length() == 0 ? GONE : VISIBLE)', home)
        labels = ElementTree.parse(MAIN / 'res/values/stage_transport_strings.xml').getroot()
        self.assertEqual('Pause playback', labels.find("string[@name='stage_pause_playback']").text)
        self.assertEqual('Resume playback', labels.find("string[@name='stage_resume_playback']").text)
