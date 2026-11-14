import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
INTERLUDE = ROOT / 'apps/android/app/src/main/assets/interlude'
SCC = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn/SessionController.java'
BUNDLED = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn/BundledInterlude.java'


class InterludeAssetTests(unittest.TestCase):
    def test_bundled_standby_loop_is_packaged_and_licensed(self):
        loop = INTERLUDE / 'standby-loop.mp3'
        self.assertTrue(loop.is_file(), 'bundled standby loop missing')
        self.assertGreater(loop.stat().st_size, 50 * 1024, 'standby loop implausibly small')
        # MP3: an ID3v2 tag or a bare frame sync at the head.
        head = loop.read_bytes()[:16]
        self.assertTrue(
            head[:3] == b'ID3' or (head[0] == 0xFF and (head[1] & 0xE0) == 0xE0),
            'standby loop is not a readable MP3')
        self.assertTrue((INTERLUDE / 'INTERLUDE_LICENSE.txt').is_file(),
                        'standby interlude provenance missing')
        self.assertTrue((INTERLUDE / 'BACKING_TRACK_LICENSE.txt').is_file(),
                        'backing track license missing')

    def test_standby_loop_is_the_default_intermission_source(self):
        bundled = BUNDLED.read_text()
        scc = SCC.read_text()
        self.assertIn('interlude/standby-loop.mp3', bundled)
        self.assertIn('BundledInterlude', scc, 'SessionController does not reference the bundled interlude')
        self.assertIn('BundledInterlude.copyTo', scc)
        # The fallback must use the bundled file when no import is selected.
        self.assertIn('bundledInterludeFile', scc)

    def test_intermission_defaults_on_for_the_welcome(self):
        scc = SCC.read_text()
        # loopEnabled is initialised to true so the welcome plays without an import.
        self.assertRegex(scc, r'boolean\s+foreground\s*,\s*loopEnabled\s*=\s*true')


if __name__ == '__main__':
    unittest.main()
