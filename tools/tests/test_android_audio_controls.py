"""D8: unsupported per-part sliders must not masquerade as working controls."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class AudioControlContractTests(unittest.TestCase):
    def test_adjustments_only_offer_supported_parts(self):
        overlay = (JAVA / 'stage/AdjustmentOverlay.java').read_text()
        self.assertNotIn('"Melody"', overlay)
        self.assertNotIn('"Guide"', overlay)
        self.assertNotIn('onMelodyVolumeChange', overlay)
        self.assertNotIn('onGuideVolumeChange', overlay)
        self.assertIn('addAdjustmentRow(container, "Key"', overlay)
        self.assertIn('addAdjustmentRow(container, "Tempo"', overlay)

    def test_removed_callbacks_have_no_orphan_wiring(self):
        for name in ('StagePresenter.java', 'StagePresenterImpl.java'):
            with self.subTest(name=name):
                source = (JAVA / 'stage' / name).read_text()
                self.assertNotIn('onMelodyVolumeChange', source)
                self.assertNotIn('onGuideVolumeChange', source)
        presenter = (JAVA / 'stage/StagePresenterImpl.java').read_text()
        self.assertIn('session.setPitch(semitones)', presenter)
        self.assertIn('session.setTempo(factor)', presenter)

    def test_master_volume_remains_connected_to_controller(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        self.assertIn('session.setVolume(', activity)
        self.assertIn('deck.setVolume(session.volume)', activity)
        controller = (JAVA / 'SessionController.java').read_text()
        self.assertIn('settings.setMasterVolume(', controller)


if __name__ == '__main__':
    unittest.main()
