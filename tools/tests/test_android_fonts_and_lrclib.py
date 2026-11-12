"""Source guards only. Decoder, JSON and LRC execution require Android/Java runners."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class FontContractTests(unittest.TestCase):
    def test_loading_is_bounded_and_falls_back(self):
        source = (JAVA / 'font/FontStore.java').read_text()
        self.assertIn('new ArrayBlockingQueue<>(1)', source)
        self.assertIn('copied > MAX_FONT_BYTES', source)
        self.assertIn('java.io.IOException | RuntimeException', source)
        self.assertIn('if (temporary != null) temporary.delete()', source)
        self.assertIn('generation != selectionGeneration', source)
        self.assertIn('String saved = settings.getLyricFont()', source)
        self.assertIn('settings.setLyricFont(', source)

    def test_both_lyric_renderers_consume_cached_face(self):
        for name in ('StageView.java', 'stage/LyricStage.java'):
            source = (JAVA / name).read_text()
            with self.subTest(name=name):
                self.assertIn('Typeface selected = fontStore.typeface()', source)
                self.assertNotIn('createFromFile', source)
                self.assertNotIn('openInputStream', source)
        self.assertIn('cachedWidth = -1', (JAVA / 'StageView.java').read_text())
        source = (JAVA / 'stage/LyricStage.java').read_text()
        for paint in ('activePaint', 'highlightPaint', 'inactivePaint', 'upcomingPaint', 'echoPaint', 'pitchWavePaint'):
            self.assertIn(f'{paint}.setTypeface(selected)', source)

    def test_saf_traversal_and_picker_permission(self):
        scanner = (JAVA / 'content/ContentScanner.java').read_text()
        self.assertIn('public List<Uri> scanFonts(', scanner)
        self.assertIn('DocumentsContract.getDocumentId(parentUri)', scanner)
        self.assertIn('DocumentsContract.getDocumentId(dirUri)', scanner)
        activity = (JAVA / 'MainActivity.java').read_text()
        self.assertIn('takePersistableUriPermission(root, grants)', activity)
        self.assertIn('fonts.discover(root, entries ->', activity)
        self.assertIn('button("Lyric font", this::chooseLyricFont)', activity)


class LrclibContractTests(unittest.TestCase):
    def test_all_requests_are_identified_bounded_and_https(self):
        client = (JAVA / 'lyrics/LrclibClient.java').read_text()
        self.assertEqual(client.count('.openConnection()'), 1)
        self.assertIn('"https://lrclib.net/api/search?"', client)
        self.assertIn('setRequestProperty("Lrclib-App-Id", APPLICATION_ID)', client)
        self.assertIn('setRequestProperty("User-Agent", userAgent)', client)
        self.assertIn('setInstanceFollowRedirects(false)', client)
        self.assertIn('setConnectTimeout(8000)', client)
        self.assertIn('setReadTimeout(12000)', client)
        self.assertIn('bytes.size() + count > MAX_RESPONSE_BYTES', client)
        self.assertIn('connection.disconnect()', client)
        self.assertNotIn('Lrclib-Api-Key', client)

    def test_fetch_is_user_initiated_and_playback_fenced(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        self.assertIn('button("Find lyrics on LRCLIB", this::findOnlineLyrics)', activity)
        self.assertIn('.setPositiveButton("Search"', activity)
        self.assertIn('to lrclib.net. No audio, singer names or library files', activity)
        session = (JAVA / 'SessionController.java').read_text()
        self.assertIn('request != lyricLookupGeneration', session)
        self.assertIn('!identity.equals(playbackIdentity) || !lyrics.isEmpty()', session)
        self.assertIn('if (!visible) {\n      ++lyricLookupGeneration;', session)
        self.assertIn('lyrics = timed;', session)
        self.assertIn('timed = result.timedCues', session)
        self.assertIn('LrcLyrics.parse(syncedLyrics)', (JAVA / 'lyrics/LrclibClient.java').read_text())
        self.assertIn('if (status == 429)', (JAVA / 'lyrics/LrclibClient.java').read_text())

    def test_java_and_device_checks_are_registered(self):
        runner = (ROOT / 'apps/android/app/src/test/java/ph/kanta/reborn/core/CoreRegressionTests.java').read_text()
        self.assertIn('checks += LrcLyricsRegressionTests.run();', runner)
        runtime = (ROOT / 'apps/android/app/src/androidTest/java/ph/kanta/reborn/RuntimeChecks.java').read_text()
        self.assertIn('FontStoreChecks.run(getTargetContext())', runtime)
        self.assertIn('LrclibClientChecks.run()', runtime)


if __name__ == '__main__':
    unittest.main()
