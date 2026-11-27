"""Batch G: interlude & stage overlays (up-next styles, visualizer modes, BGM, chime)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class InterludeOverlaysBatchG(unittest.TestCase):
    def test_settings_keys_and_session_accessors(self):
        settings = (JAVA / 'settings/SettingsStore.java').read_text()
        for key in ('KEY_INTERLUDE_VISUALIZER_MODE', 'KEY_INTERLUDE_VISUALIZER_SENSITIVITY',
                    'KEY_INTERLUDE_UP_NEXT_STYLE', 'KEY_INTERLUDE_UP_NEXT_COUNT',
                    'KEY_INTERLUDE_BGM', 'KEY_INTERLUDE_CHIME'):
            self.assertIn(key, settings)
        session = (JAVA / 'SessionController.java').read_text()
        for m in ('interludeVisualizerMode()', 'interludeVisualizerSensitivity()',
                  'interludeUpNextStyle()', 'interludeUpNextCount()',
                  'interludeBgmEnabled()', 'interludeChimeEnabled()',
                  'setInterludeBgm(', 'setInterludeChime('):
            self.assertIn(m, session)

    def test_visualizer_modes_and_sensitivity_in_stageview(self):
        view = (JAVA / 'StageView.java').read_text()
        for m in ('"ring".equals(mode)', '"wave".equals(mode)',
                  'session.interludeVisualizerMode()',
                  'session.interludeVisualizerSensitivity()',
                  'paintPath(canvas,'):
            self.assertIn(m, view)
        # Classic bars branch retained alongside the new modes.
        self.assertIn('Math.sin(t * 3.1 + b * .83)', view)

    def test_up_next_style_and_count_flow(self):
        ticker = (JAVA / 'ui/components/UpNextTicker.java').read_text()
        for m in ('setStyle(String s)', 'setShowCount(boolean v)',
                  'style.equals("card")', 'style.equals("badge")',
                  'in queue'):
            self.assertIn(m, ticker)
        presenter = (JAVA / 'stage/StagePresenterImpl.java').read_text()
        self.assertIn('upNextTicker.setStyle(session.interludeUpNextStyle())', presenter)
        self.assertIn('upNextTicker.setShowCount(session.interludeUpNextCount())', presenter)

    def test_bgm_gate_and_transition_chime(self):
        session = (JAVA / 'SessionController.java').read_text()
        # BGM only plays when explicitly enabled (gated on the existing standby conditions).
        self.assertIn('&& settings.getInterludeBgm();', session)
        self.assertIn('private void playTransitionChime()', session)
        self.assertIn('party.trigger(PartySounds.DING)', session)
        # Chime fires at both countdown entry points (legacy + known-melody).
        self.assertGreaterEqual(session.count('playTransitionChime();'), 2)

    def test_ui_exposes_controls(self):
        main = (JAVA / 'MainActivity.java').read_text()
        for m in ('kanta.interlude.upnext.style', 'kanta.interlude.visualizer.mode',
                  'kanta.interlude.bgm', 'kanta.interlude.chime',
                  'R.string.visualizer_mode_ring', 'R.string.upnext_style_card'):
            self.assertIn(m, main)
