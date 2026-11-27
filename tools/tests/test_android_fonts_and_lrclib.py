"""Source guards only. Decoder, JSON and LRC execution require Android/Java runners."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class FontContractTests(unittest.TestCase):
    def test_loading_is_bounded_and_falls_back(self):
        source = (JAVA / 'font/FontStore.java').read_text()
        # One serial worker keeps provider I/O ordered; the queue must be
        # unbounded so a user select() is never rejected while previews are
        # pending (a rejected select silently reset the saved font and left
        # the stage preview unchanged).
        self.assertIn('new LinkedBlockingQueue<Runnable>()', source)
        self.assertNotIn('ArrayBlockingQueue', source)
        self.assertIn('new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,', source)
        # Bundled faces are cached so chips, picker rows and the renderers
        # share one decoded instance instead of re-reading assets.
        self.assertIn('BUNDLED_CACHE', source)
        self.assertIn('synchronized (BUNDLED_CACHE)', source)
        self.assertEqual(source.count('Typeface cached = cachedBundled(uri)'), 2)
        self.assertIn('copied > MAX_FONT_BYTES', source)
        self.assertIn('java.io.IOException | RuntimeException', source)
        self.assertIn('if (temporary != null) temporary.delete()', source)
        self.assertIn('generation != selectionGeneration', source)
        self.assertIn('String saved = settings.getLyricFont()', source)
        self.assertIn('new Typeface.Builder(temporary).build()', source)
        self.assertNotIn('Typeface.createFromFile(', source)
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
        # The renderer derives every face from the single FontStore-cached
        # typeface (bold via create), never a per-line file decode.
        self.assertIn('Typeface selected = fontStore.typeface();', source)
        self.assertIn('activePaint.setTypeface(face)', source)
        self.assertIn('upcomingPaint.setTypeface(face)', source)

    def test_picker_renders_actual_faces_and_restores_bundled_options(self):
        import json
        import re
        picker = (JAVA / 'ui/components/KantaFontPicker.java').read_text()
        # Every built-in row requests its real face and applies it to the sample.
        self.assertIn('fonts.preview(entry.uri, face -> row.sample(face))', picker)
        self.assertIn('sampleView.setTypeface(face)', picker)
        self.assertIn('kanta.font.picker.row.', picker)
        # Rows are real touch targets with accessible names.
        self.assertIn('setMinimumHeight(dp(56))', picker)
        self.assertIn('setContentDescription(name', picker)
        activity = (JAVA / 'MainActivity.java').read_text()
        # Both editors open the same picker: lyrics chip row + interlude button.
        self.assertIn('new ph.kanta.reborn.ui.components.KantaFontPicker', activity)
        self.assertIn('selectBundledFont(fonts, uri, pickerView)', activity)
        self.assertIn('previewLyricFont(entry.uri, entry.name, pickerView)', activity)
        self.assertIn('fonts.bundledFonts(), true', activity)
        # The interlude font control and the lyrics chip row both reach the picker.
        self.assertIn('() -> chooseLyricFont()', activity)
        self.assertIn('kanta.visuals.interlude.font', activity)
        # The selection flow still runs the legacy two-step confirm for custom fonts.
        self.assertIn('setPositiveButton("Use font"', activity)
        # Selecting a font refreshes the shared stage preview immediately.
        note = activity.split('private void noteLyricsFontChanged()', 1)[1].split('\n  }', 1)[0]
        self.assertIn('refreshVisualsPreview()', note)
        # The mini preview must reset to the built-in face on a default
        # selection instead of keeping the previous font (stale preview).
        preview = (JAVA / 'ui/MiniStagePreview.java').read_text()
        self.assertEqual(
            preview.count('baseFace = typeface == null ? Typeface.DEFAULT_BOLD : typeface'), 2)
        # Restoration guard: every PROVENANCE.json family ships its ttf + OFL
        # license, and every bundled option in code resolves to a real asset.
        provenance = json.loads(
            (ROOT / 'apps/android/app/src/main/assets/lyric-fonts/PROVENANCE.json').read_text())
        bundled = (JAVA / 'font/BundledLyricFonts.java').read_text()
        families = re.findall(r'new Option\("[a-z0-9-]+", "[^"]+", "([a-z0-9]+)"', bundled)
        self.assertTrue(families, "Bundled options must list font families")
        self.assertGreaterEqual(len(families), 30,
            "Every previously bundled option must remain selectable")
        for family in set(families):
            assets = provenance['families'].get(family, {})
            self.assertIn(family + '.ttf', assets, family + ' missing from provenance')
            self.assertIn(family + '.OFL.txt', assets, family + ' missing its OFL license')
            self.assertTrue(
                (ROOT / 'apps/android/app/src/main/assets/lyric-fonts' / (family + '.ttf'))
                .exists(), family + ' ttf missing from assets')

    def test_saf_traversal_and_picker_permission(self):
        scanner = (JAVA / 'content/ContentScanner.java').read_text()
        self.assertIn('public List<Uri> scanFonts(', scanner)
        self.assertIn('DocumentsContract.getDocumentId(parentUri)', scanner)
        self.assertIn('DocumentsContract.getDocumentId(dirUri)', scanner)
        activity = (JAVA / 'MainActivity.java').read_text()
        permission = activity.split('else if (request == LYRIC_FONT_FOLDER', 1)[1].split('else if (request == VOICE)', 1)[0]
        self.assertIn('data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION', permission)
        self.assertIn('if (grants == 0) throw new SecurityException()', permission)
        self.assertIn('takePersistableUriPermission(root, Intent.FLAG_GRANT_READ_URI_PERMISSION)', permission)
        self.assertLess(permission.index('if (grants == 0)'), permission.index('takePersistableUriPermission'))
        self.assertNotIn('FLAG_GRANT_WRITE_URI_PERMISSION', permission)
        self.assertIn('fonts.discover(root, entries ->', activity)
        my_set = activity.split('private void mySet()', 1)[1].split('\n  }', 1)[0]
        visuals = activity.split('private void addStageVisualControls(', 1)[1].split('\n  }', 1)[0]
        settings = activity.split('private void control()', 1)[1].split('\n  }', 1)[0]
        lyrics_editor = activity.split('private void addLyricsVisualControls(', 1)[1].split('\n  }', 1)[0]
        font_chips = activity.split('private void addLyricsFontChips(', 1)[1].split('\n  }', 1)[0]
        # The font picker moved from Settings to the Intermission lyrics editor,
        # where the chip row's custom-import action drives the SAF discovery chain.
        self.assertIn('addLyricsFontChips(target)', lyrics_editor)
        self.assertIn('chooseLyricFont', font_chips)
        self.assertNotIn('this::chooseLyricFont', settings)
        self.assertNotIn('this::chooseLyricFont', visuals)
        self.assertNotIn('this::chooseLyricFont', my_set)


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
        self.assertRegex(session, r'if \(!visible\) \{(?:\n      [^{}\n]+)*\n      \+\+lyricLookupGeneration;')
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
