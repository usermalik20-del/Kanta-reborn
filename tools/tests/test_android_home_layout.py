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

    def test_home_lookup_does_not_require_reserve(self):
        source = (JAVA / 'MainActivity.java').read_text()
        lookup = source.split('private void refreshStageEntry()', 1)[1].split('private void reserveFromSearch(', 1)[0]
        self.assertIn('song.numericPreviewRank(requested)', lookup)
        self.assertIn('song.matchesTerms(terms)', lookup)
        self.assertIn('target.showMatches(lookupGeneration, matches, song -> {', lookup)
        for fence in ('token != referenceSearchGeneration', '!isHomeLookupOpen()',
                      'homeScreen != target', 'library != session.songs'):
            self.assertIn(fence, lookup)
        self.assertNotIn('kanta.stage.search-preview', source)
        self.assertNotIn('session.reserve(', lookup)
        self.assertIn('homeScreen.showSongLookup(stagePane)', source)

    def test_publication_preserves_entry_and_focus(self):
        source = (JAVA / 'MainActivity.java').read_text()
        start = source.index('// Refresh the Home queue')
        refresh = source[start:source.index('// Now Playing is a live projection.', start)]
        self.assertIn('homeScreen.getParent() == body', refresh)
        self.assertIn('return;', refresh)
        self.assertNotIn('removeAllViews', refresh)

    def test_placeholder_is_not_submitted_as_query(self):
        source = (JAVA / 'ui/HomeScreen.java').read_text()
        self.assertIn('searchInput.setHint("Search songs, artists, or enter code")', source)
        self.assertNotIn('searchInput.setText("Search songs, artists, or enter code")', source)

    def test_home_keyboard_has_number_row_and_qwerty_without_toggle(self):
        source = (JAVA / 'ui/HomeScreen.java').read_text()
        self.assertNotIn('kanta.home.keyboard-toggle', source)
        keyboard = (JAVA / 'ui/components/InlineKeyboard.java').read_text()
        for row in ('12345', '67890', 'qwertyuiop', 'asdfghjkl', 'zxcvbnm'):
            self.assertIn(f'"{row}"', keyboard)
        strings = ROOT / 'apps/android/app/src/main/res/values/inline_keyboard_strings.xml'
        self.assertIn('<string name="keyboard_space">Space</string>', strings.read_text())
        for action in ('PLAY', 'RES', 'CLR'):
            self.assertNotIn(f'"{action}"', keyboard)
        for label in ('stage_play', 'stage_reserve', 'stage_next', 'stage_stop'):
            self.assertIn(f'addSongAction(actions, R.string.{label}', source)
        self.assertIn('searchRow.addView(clearSearch,', source)
        self.assertLess(source.index('addView(actionScroll,'), source.index('addView(searchRow,'))
        self.assertIn('addAdjustments(actions, R.string.stage_key_short', keyboard)
        self.assertIn('R.string.stage_tempo_short', keyboard)
        self.assertIn('context.getString(R.string.keyboard_space)', keyboard)
        self.assertIn('() -> listener.onText(" ")', keyboard)
        self.assertIn('addKey(row, "−"', keyboard)
        self.assertIn('addKey(row, "+"', keyboard)
        self.assertIn('listener.onBackspace()', keyboard)
        self.assertIn('clearSearchText();', source)

    def test_results_are_docked_above_stage_without_a_panel_background(self):
        screen = (JAVA / 'ui/HomeScreen.java').read_text()
        self.assertIn('stagePane.addView(queuePanel, 0,', screen)
        self.assertIn('queuePanel.setBackground(null)', screen)
        self.assertIn('lookupDocked ? dp(96)', screen)
        self.assertIn('reservedSongs.addView(result, new LayoutParams(-1, -2))', screen)
        self.assertIn('keyboardParams.weight = lookupDocked && !landscape ? 1 : 0', screen)
        self.assertIn('LANDSCAPE_KEYBOARD_MAX_WIDTH_DP = 500', screen)
        self.assertIn('landscape && paneWidth > widthCap ? widthCap : LayoutParams.MATCH_PARENT', screen)

    def test_reserve_is_async_exact_and_stale_fenced(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        start = activity.index('  private void submitHomeEntry(')
        submit = activity[start:activity.index('  private ph.kanta.reborn.ui.GroupScreen', start)]
        self.assertIn('searchWorker.execute(', submit)
        self.assertIn('Song.uniqueCodeMatch(library, requested)', submit)
        self.assertIn('source != homeScreen', submit)
        self.assertIn('token != homeSubmitGeneration', submit)
        self.assertIn('!session.foreground', submit)
        self.assertIn('library == session.songs', submit)
        self.assertIn('!entered.equals(source.getSearchText())', submit)
        self.assertIn('session.reserve(match, singer)', submit)
        self.assertNotIn('playSearchResult(', submit)
        self.assertIn('refreshStageEntry();', submit)
        self.assertIn('source.revealSongMatches();', submit)
        self.assertNotIn('openPage(', submit)
        song = (JAVA / 'core/Song.java').read_text()
        self.assertIn('song.matchesExactCode(code)', song)
        self.assertIn('!found.id.equals(song.id)', song)

    def test_stage_transport_and_spacebar_safety(self):
        screen = (JAVA / 'ui/HomeScreen.java').read_text()
        for callback in ('onPauseResumeSong', 'onNextSong', 'onStopSong'):
            self.assertIn(f'listener.{callback}()', screen)
        activity = (JAVA / 'MainActivity.java').read_text()
        transport = activity.split('void onPauseResumeSong()', 1)[1].split('}', 1)[0]
        self.assertIn('playWithCountdown();', transport)
        self.assertIn('homeSubmitGeneration++;\n                session.stop();', activity)
        keyboard = (JAVA / 'ui/components/InlineKeyboard.java').read_text()
        self.assertIn('"key", true', keyboard)
        self.assertIn('"tempo", false', keyboard)

    def test_home_window_cannot_auto_show_system_keyboard_on_launch(self):
        manifest = (ROOT / 'apps/android/app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('android:windowSoftInputMode="stateAlwaysHidden|adjustResize"', manifest)
        runtime = (ROOT / 'apps/android/app/src/androidTest/java/ph/kanta/reborn/RuntimeChecks.java').read_text()
        self.assertIn('Home inline keyboard unexpectedly opened the system IME', runtime)
        self.assertIn('!insets.isVisible(android.view.WindowInsets.Type.ime())', runtime)

    def test_stage_search_reuses_the_existing_input_and_query(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        screen = (JAVA / 'ui/HomeScreen.java').read_text()
        route = activity.split('private void openStageSearch()', 1)[1].split('\n  }', 1)[0]
        self.assertIn('selectMainMenu("Home");', route)
        self.assertIn('homeScreen.focusSongSearch();', route)
        self.assertNotIn('homeEntry =', route)
        self.assertIn('homeScreen.restoreSearchText(homeEntry);', activity)
        self.assertIn('homeEntry = typed;', activity)
        self.assertIn('searchInput.setTag("kanta.home.search")', screen)
        focus = screen.split('public void focusSongSearch()', 1)[1].split('\n  }', 1)[0]
        self.assertIn('searchInput.requestFocus();', focus)
        self.assertNotIn('setText(', focus)
        self.assertIn('reservedSongs.setTag("kanta.stage.search-results")', screen)

    def test_first_rotation_rebuilds_layout_and_rebinds_primary_camera(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        on_create = activity[activity.index('  public void onCreate('):activity.index('  @Override', activity.index('  public void onCreate('))]
        self.assertIn('lastOrientation = getResources().getConfiguration().orientation;', on_create)
        callback_start = activity.index('  public void onConfigurationChanged(')
        callback = activity[callback_start:activity.index('  @Override', callback_start)]
        self.assertIn('buildWindow(newOrient)', callback)
        self.assertIn('camera.ownsSurface(stagePresenter)', callback)
        self.assertIn('cameraWasOnPrimaryStage', callback)
        self.assertIn('if (cameraWasOnPrimaryStage && stagePresenter != null) camera.moveTo(stagePresenter);', callback)
        build_start = activity.index('  private void buildWindow(int orientation)')
        build_window = activity[build_start:activity.index('  private void addStageChrome(', build_start)]
        self.assertIn('orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE', build_window)

        runtime = (ROOT / 'apps/android/app/src/androidTest/java/ph/kanta/reborn/RuntimeChecks.java').read_text()
        self.assertIn('First orientation change did not rebuild the primary stage', runtime)
        self.assertIn('Primary camera preview was not rebound after orientation change', runtime)
        self.assertIn('Superseded primary stage retained the camera preview', runtime)
        self.assertIn('persistentStageOrientation', runtime)
        self.assertIn('Primary rotation stole the camera preview from a non-primary stage', runtime)
        self.assertIn('cameraStage().ownsSurface(nonPrimaryStage)', runtime)

    def test_runtime_covers_sizes_and_typed_entry(self):
        source = (ROOT / 'apps/android/app/src/androidTest/java/ph/kanta/reborn/RuntimeChecks.java').read_text()
        self.assertIn('homeLayoutChecks();', source)
        self.assertIn('new int[] {320, 411, 720}', source)
        self.assertIn('new int[] {320, 620}', source)
        self.assertIn('Queue refresh discarded in-progress Home entry', source)


if __name__ == '__main__':
    unittest.main()
