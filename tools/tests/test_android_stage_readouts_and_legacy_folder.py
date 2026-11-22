from pathlib import Path
import unittest


JAVA = (Path(__file__).resolve().parents[2]
        / 'apps/android/app/src/main/java/ph/kanta/reborn')


class StageReadoutsAndLegacyFolderTest(unittest.TestCase):
    def test_docked_readout_precedes_visual_layer_and_both_modes_refresh(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        self.assertLess(activity.index('stagePane.addView(stageAdjustments,'),
                        activity.index('stagePane.addView(stageLayers,'))
        self.assertIn('header.addView(fullscreenAdjustments,', activity)
        self.assertIn('refreshStageAdjustments(stageAdjustments);', activity)
        self.assertIn('refreshStageAdjustments(fullscreenAdjustments);', activity)
        readout = activity.split('private void refreshStageAdjustments(', 1)[1].split('\n  }', 1)[0]
        self.assertIn('session.effectivePitch()', readout)
        self.assertIn('session.effectiveTempo()', readout)
        self.assertNotIn('View.GONE', readout)

    def test_folder_scan_is_bounded_and_routes_through_paired_import(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        picker = (JAVA / 'LegacyFolderPicker.java').read_text()
        self.assertIn('new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), LEGACY_FOLDER', activity)
        self.assertIn('importLegacyFolder(data.getData());', activity)
        action = activity.split('private void importLegacyFolder(', 1)[1].split('\n  }', 1)[0]
        self.assertIn('session.io.execute(', action)
        self.assertIn('startLegacyPair(selected[0], selected[1]);', action)
        self.assertIn('this::pickLegacyIndex', activity)
        self.assertEqual(picker.count('resolver.query('), 1)
        self.assertIn('buildChildDocumentsUriUsingTree(tree,', picker)
        self.assertIn('MIME_TYPE_DIR.equals', picker)
        self.assertIn('++count > 10000', picker)
        self.assertIn('pair[role] != null) return null', picker)
        self.assertIn('pair[0] != null && pair[1] != null', picker)
