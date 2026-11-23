from pathlib import Path
import subprocess
import tempfile
import unittest

JAVA = (Path(__file__).resolve().parents[2]
        / 'apps/android/app/src/main/java/ph/kanta/reborn')


class StageVisualShuffleTest(unittest.TestCase):
    def test_controller_projects_only_at_successful_song_boundaries(self):
        session = (JAVA / 'SessionController.java').read_text()
        prep = session.split('private void prepareStageShuffle()', 1)[1].split(
            'private void applyStageShuffleBoundary', 1)[0]
        self.assertIn('visualShuffle.isCurrent(request)', prep)
        self.assertIn('photo.recycle()', prep)
        self.assertNotIn('selectBundledVideo', prep)
        self.assertNotIn('selectImport', prep)
        self.assertEqual(session.count('applyStageShuffleBoundary(true)'), 1)
        self.assertIn('loading = false;\n                          applyStageShuffleBoundary(true);', session)
        for renderer in ['stage/StagePresenterImpl.java', 'StageSurface.java']:
            source = (JAVA / renderer).read_text()
            self.assertIn('session.stageVideo()', source)
            self.assertNotIn('maybeShuffleBgv', source)

    def test_independent_settings_default_off(self):
        settings = (JAVA / 'settings/SettingsStore.java').read_text()
        for kind in ('BGV', 'BGP'):
            self.assertIn(f'getBoolean(KEY_{kind}_AUTO_SHUFFLE, false)', settings)
        self.assertNotIn('getBgvShuffleInterval', settings)

    def test_random_alternatives_and_stale_result_fence(self):
        java_test = '''package ph.kanta.reborn;
import java.util.*;
class StageVisualShuffleCheck {
    public static void main(String[] args) {
        var state = new StageVisualShuffle<String>();
        long stale = state.beginPreparation();
        assert !state.needsPreparation();
        assert state.takeAtSongBoundary() == null;
        state.invalidate();
        long live = state.beginPreparation();
        state.prepared(stale, "old");
        assert state.takeAtSongBoundary() == null;
        state.prepared(live, "new");
        assert state.takeAtSongBoundary().equals("new");
        assert state.takeAtSongBoundary() == null && state.needsPreparation();
        state.prepared(live, "duplicate");
        assert state.takeAtSongBoundary() == null;
        var random = new Random(42);
        assert StageVisualShuffle.alternatives(List.of("a"), "a", random).isEmpty();
        assert StageVisualShuffle.alternatives(List.of("a", "b"), "a", random).equals(List.of("b"));
        assert new HashSet<>(StageVisualShuffle.alternatives(List.of("a", "b", "c"), "b", random))
            .equals(Set.of("a", "c"));
    }
}
'''
        with tempfile.TemporaryDirectory() as directory:
            harness = Path(directory) / 'StageVisualShuffleCheck.java'
            harness.write_text(java_test)
            subprocess.run(['javac', '-d', directory, JAVA / 'StageVisualShuffle.java', harness], check=True)
            subprocess.run(['java', '-ea', '-cp', directory, 'ph.kanta.reborn.StageVisualShuffleCheck'], check=True)


if __name__ == '__main__':
    unittest.main()
