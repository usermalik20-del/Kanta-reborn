"""Home queue projection and focus-preserving layout source contracts.

These guards complement RuntimeChecks; they do not prove on-device geometry.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class HomeLayoutTests(unittest.TestCase):
    def test_queue_fills_space_before_keypad(self):
        source = (JAVA / 'ui/HomeScreen.java').read_text()
        self.assertLess(source.index('addView(queuePanel, queueParams)'), source.index('addView(keyboard,'))
        self.assertIn('LayoutParams.WRAP_CONTENT, 1)', source)
        self.assertIn('queuePanel.setMinimumHeight(dp(112))', source)
        self.assertIn('kanta.home.queue.empty', source)
        self.assertIn('listener.onQueue()', source)

    def test_queue_comes_from_authoritative_snapshot(self):
        source = (JAVA / 'MainActivity.java').read_text()
        start = source.index('  private void refreshHomeReservations()')
        projection = source[start:source.index('  private ph.kanta.reborn.ui.GroupScreen', start)]
        self.assertIn('session.queue.snapshot()', projection)
        self.assertIn('Math.min(3, entries.size())', projection)
        self.assertIn('homeScreen.setReservedSongs(songs, entries.size())', projection)
        self.assertNotIn('session.queue.reserve', projection)

    def test_publication_preserves_entry_and_focus(self):
        source = (JAVA / 'MainActivity.java').read_text()
        start = source.index('// Refresh the Home queue')
        refresh = source[start:source.index('// Now Playing is a live projection.', start)]
        self.assertIn('!controlDeckVisible', refresh)
        self.assertIn('homeScreen.getParent() == body', refresh)
        self.assertIn('refreshHomeReservations()', refresh)
        self.assertIn('return;', refresh)
        self.assertNotIn('removeAllViews', refresh)

    def test_placeholder_is_not_submitted_as_query(self):
        source = (JAVA / 'ui/HomeScreen.java').read_text()
        self.assertIn('searchInput.setHint("Search songs, artists, or enter code")', source)
        self.assertNotIn('searchInput.setText("Search songs, artists, or enter code")', source)

    def test_home_keyboard_has_one_toggle_and_reserve_action(self):
        source = (JAVA / 'ui/HomeScreen.java').read_text()
        self.assertIn('kanta.home.keyboard-toggle', source)
        self.assertNotIn('ImageView code', source)
        self.assertIn('keyboard.setSubmitLabel("RES")', source)
        keyboard = (JAVA / 'ui/components/InlineKeyboard.java').read_text()
        self.assertIn('BACKSPACE = "DEL"', keyboard)
        self.assertIn('listener.onBackspace()', keyboard)
        self.assertIn('"OK".equals(value) ? submitLabel : value', keyboard)

    def test_reserve_is_async_exact_and_stale_fenced(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        start = activity.index('  private void submitHomeEntry(')
        submit = activity[start:activity.index('  private void refreshHomeReservations()', start)]
        self.assertIn('searchWorker.execute(', submit)
        self.assertIn('Song.uniqueCodeMatch(library, requested)', submit)
        self.assertIn('source != homeScreen', submit)
        self.assertIn('token != homeSubmitGeneration', submit)
        self.assertIn('!session.foreground', submit)
        self.assertIn('library == session.songs', submit)
        self.assertIn('!entered.equals(source.getSearchText())', submit)
        self.assertIn('session.reserve(match, singer)', submit)
        self.assertIn('tab = "Search"', submit)
        song = (JAVA / 'core/Song.java').read_text()
        self.assertIn('code.equals(song.code)', song)
        self.assertIn('!found.id.equals(song.id)', song)

    def test_search_prefill_is_not_cleared_after_text_watcher(self):
        source = (JAVA / 'MainActivity.java').read_text()
        start = source.index('    String committedQuery = query;')
        prefill = source[start:source.index('    renderSearchResults(screen, visibleSongs);', start)]
        self.assertLess(prefill.index('query = ""'), prefill.index('screen.setQuery(committedQuery)'))

    def test_runtime_covers_sizes_and_typed_entry(self):
        source = (ROOT / 'apps/android/app/src/androidTest/java/ph/kanta/reborn/RuntimeChecks.java').read_text()
        self.assertIn('homeLayoutChecks();', source)
        self.assertIn('new int[] {320, 411, 720}', source)
        self.assertIn('new int[] {320, 620}', source)
        self.assertIn('Queue refresh discarded in-progress Home entry', source)


if __name__ == '__main__':
    unittest.main()
