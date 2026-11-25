import unittest
from pathlib import Path

MAIN = Path(__file__).resolve().parents[2] / 'apps/android/app/src/main'
JAVA = MAIN / 'java/ph/kanta/reborn'


class MenuFinishTest(unittest.TestCase):
    def test_help_and_legal_are_menu_pages(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        for route, method in [('MENU_HELP', 'helpPage'), ('MENU_LEGAL', 'libraryNoticesPage')]:
            self.assertIn(f'openPage({route});', activity)
            self.assertIn(f'case {route}:\n        {method}();', activity)
        # Help is an illustrated step-by-step guide, not a plain section list.
        self.assertIn('addHelpStep(1, R.drawable.kanta_submenu_folder', activity)
        self.assertIn('"kanta.help.step."', activity)

    def test_stage_chrome_uses_icon_actions_and_camera_state(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        self.assertIn('stageChromeButton(R.string.stage_fullscreen_label', activity)
        # Camera and fullscreen are icon-only; the camera keeps its status dot.
        self.assertIn('refreshCameraIndicator()', activity)
        self.assertIn('R.string.stage_state_on : R.string.stage_state_off', activity)
        # Fullscreen sits at the lower right stage edge, not the header row.
        self.assertIn('Gravity.BOTTOM | Gravity.END', activity)

    def test_menu_destinations_are_direct_and_settings_stay_separate(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        menu = activity.split('private void moreGroup()', 1)[1].split('private void addMenuRow(', 1)[0]
        settings = activity.split('private void control()', 1)[1].split('private void queue()', 1)[0]
        presets = activity.split('private void mySet()', 1)[1].split('private void songbookExports(', 1)[0]
        for item in ('stage-visual', 'intermission', 'party-pads', 'voice-scoring'):
            self.assertEqual(1, menu.count(f'"{item}"'))
            self.assertNotIn(f'"{item}"', settings)
        for item in ('stage', 'visual', 'voice', 'scoring'):
            self.assertNotIn(f'"{item}"', menu)
        for removed in ('kanta.voice.settings', 'kanta.settings.intermission', 'menu_visuals_summary'):
            self.assertNotIn(removed, settings)
        self.assertNotIn('library_notices', presets)
        self.assertIn('R.string.library_notices', activity.split('private void libraryNoticesPage()', 1)[1])

    def test_menu_glyphs_are_independent_vectors_with_existing_tint(self):
        import re
        from xml.etree import ElementTree
        activity = (JAVA / 'MainActivity.java').read_text()
        menu = activity.split('private void moreGroup()', 1)[1].split('private void addMenuRow(', 1)[0]
        glyphs = re.findall(r'R.drawable.(kanta_menu_\w+)', menu)
        self.assertEqual(menu.count('addMenuRow('), len(glyphs))
        self.assertEqual(len(set(glyphs)), len(glyphs))
        for glyph in glyphs:
            self.assertEqual('vector', ElementTree.parse(MAIN / f'res/drawable/{glyph}.xml').getroot().tag)
        surface = (JAVA / 'ui/MenuSurface.java').read_text()
        self.assertIn('KantaTheme.PRIMARY_CYAN', surface)
        self.assertIn('icon.setColorFilter(ACCENT)', surface)
        self.assertIn('row.setBackground(ripple', surface)

    def test_party_pads_share_one_panel_and_existing_owner(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        intermission = activity.split('private void loop()', 1)[1].split('private void partyPadsPage()', 1)[0]
        self.assertNotIn('session.triggerPad(', intermission)
        controls = activity.split('private StageControlsPanel makeStageControls(', 1)[1].split('private Button addStageControl(', 1)[0]
        self.assertIn('partyPadTrigger(slot, result.tagPrefix + ".pad.")', controls)
        self.assertIn('session::stopParty', controls)
        self.assertIn('case "party-pads":\n        openPage(MENU_PARTY_PADS);', activity)
        pads = activity.split('private void partyPadsPage()', 1)[1].split('private void refreshPartyPadControls()', 1)[0]
        for call in ('session.triggerPad(slot)', 'session.assignPad(slot, song)', 'session.resetPad(slot)',
                     'session.partyVolumes('):
            self.assertIn(call, pads)
