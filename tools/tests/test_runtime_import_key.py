"""Source contracts for the bundled legacy key library; not Android execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'
ASSET = ROOT / 'apps/android/app/src/main/assets/legacy-import-keys.txt'


def table_entries():
    entries = []
    for block in ASSET.read_text().split('\n\n'):
        fields = {}
        for line in block.split('\n'):
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            name, _, value = line.partition('=')
            fields[name] = value
        if fields:
            entries.append(fields)
    return entries


class RuntimeImportKeyContractTests(unittest.TestCase):
    def test_importer_has_no_embedded_material_or_fallback(self):
        source = (JAVA / 'core/legacy/LegacyFileContainerImporter.java').read_text()
        self.assertNotRegex(source, r'byte\[\]\s+(KEY|IV)\s*=')
        self.assertIn('LegacyImportKey credentials', source)
        self.assertIn('try (LegacyImportKey owned = credentials)', source)
        self.assertIn('owned.key(), owned.iv()', source)
        self.assertNotIn('hex(', source)
        # The probe is non-consuming: it borrows the material and never closes it.
        probe = source.split('public static boolean probeKey(', 1)[1].split(
            'private static ImportResult importWithKey(', 1)[0]
        self.assertIn('catch (Exception unable)', probe)
        self.assertNotIn('close()', probe)

    def test_prompt_is_transient_masked_and_not_autofilled(self):
        source = (JAVA / 'MainActivity.java').read_text()
        prompt = source.split('private EditText legacyKeyInput(', 1)[1].split(
            'private void chooseAudio(', 1)[0]
        for requirement in ('TYPE_TEXT_VARIATION_PASSWORD', 'setSaveEnabled(false)',
                            'setSaveFromParentEnabled(false)',
                            'IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS',
                            'IME_FLAG_NO_PERSONALIZED_LEARNING', 'IME_FLAG_NO_EXTRACT_UI',
                            'FLAG_SECURE', 'setOnDismissListener',
                            'clearLegacyKeyInput(keyInput)', 'clearLegacyKeyInput(ivInput)'):
            self.assertIn(requirement, prompt)
        submit = prompt.split('dialog.getButton(AlertDialog.BUTTON_POSITIVE)', 1)[1]
        self.assertLess(submit.index('clearLegacyKeyInput(keyInput)'),
                        submit.index('dialog.dismiss()'))
        self.assertLess(submit.index('clearLegacyKeyInput(ivInput)'),
                        submit.index('submitLegacyPair('))
        self.assertNotIn('getText().toString()', prompt)
        self.assertIn('if (legacyKeyDialog != null) legacyKeyDialog.dismiss();',
                      source.split('protected void onStop()', 1)[1].split(
                          'protected void onDestroy()', 1)[0])

    def test_key_table_is_bundled_and_extensible(self):
        self.assertTrue(ASSET.is_file(), 'The key table ships in main assets')
        entries = table_entries()
        self.assertGreaterEqual(len(entries), 1)
        self.assertLessEqual(len(entries), 32)
        real = [entry for entry in entries if entry.get('key') != 'PLACEHOLDER']
        reserved = [entry for entry in entries if entry.get('key') == 'PLACEHOLDER']
        self.assertGreaterEqual(len(real), 1,
                                'At least one real entry unlocks the distributed containers')
        self.assertGreaterEqual(len(reserved), 1,
                                'A placeholder slot is reserved for future container versions')
        for entry in entries:
            self.assertEqual(set(entry), {'label', 'key', 'iv'})
            self.assertTrue(entry['label'])
            self.assertLessEqual(len(entry['label']), 64)
            self.assertRegex(entry['iv'], r'^[0-9a-fA-F]{32}$')
            if entry['key'] != 'PLACEHOLDER':
                self.assertRegex(entry['key'], r'^[0-9a-fA-F]{64}$')

    def test_private_build_variant_stays_local(self):
        gradle = (ROOT / 'apps/android/app/build.gradle').read_text()
        for requirement in ("'PRIVATE_LEGACY_IMPORT', 'false'", "'PRIVATE_LEGACY_IMPORT', 'true'",
                            "sourceSets.getByName('privateLegacy').assets.srcDir(privateLegacyAssets)",
                            "applicationIdSuffix '.privatelegacy'", 'Private legacy builds must not run in CI'):
            self.assertIn(requirement, gradle)
        activity = (JAVA / 'MainActivity.java').read_text()
        self.assertIn('session.importKaraokeFolder(tree);', activity)
        self.assertNotIn('legacy-import-key.bin', activity)
        self.assertNotIn('fromPrivateAsset', activity)

    def test_controller_owns_every_candidate(self):
        source = (JAVA / 'SessionController.java').read_text()
        flow = source.split('boolean importLegacyPair(', 1)[1].split(
            'void downloadFluidR3GM()', 1)[0]
        self.assertIn('java.util.List<ph.kanta.reborn.core.legacy.LegacyImportKey> candidates', flow)
        self.assertIn(
            'for (ph.kanta.reborn.core.legacy.LegacyImportKey credential : candidates) credential.close();',
            flow)
        self.assertIn('probeKey(index, blob, candidate)', flow)
        self.assertIn('legacyManualKeyOffer = candidates.size() > 1;', flow)
        self.assertIn('if (credential != chosen) credential.close();', flow)
        self.assertIn('"legacy-import"), chosen, importCancelled::get,', flow)
        self.assertIn('importProgressDispatcher.onProgress(total, done,', flow)
        self.assertIn('Adding songs to your library', flow)
        self.assertIn('Reading legacy songs: ', flow)
        for forbidden in ('SharedPreferences', 'Log.', 'println', 'getBytes(', 'toString('):
            self.assertNotIn(forbidden, flow)
        self.assertIn('boolean drainLegacyManualKeyOffer()', source)

    def test_activity_tries_table_then_manual(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        controller = (JAVA / 'SessionController.java').read_text()
        # The bundled table is loaded once and probed before any manual offer can appear.
        table = controller.split('legacyTableCandidates() {', 1)[1].split(
            'private void karaokeFinished', 1)[0]
        self.assertIn('getAssets().open("legacy-import-keys.txt")', table)
        self.assertIn('LegacyKeyTable.fromStream(asset)', table)
        self.assertIn('entry.placeholder', table)
        self.assertIn('importLegacyPair(found.legacyIndex, found.legacyBlob, legacyTableCandidates());',
                      controller)
        # When the table fails, the manual dialog receives the pair the folder scan found.
        self.assertIn('requestLegacyKey(', activity)
        self.assertIn('session.pendingLegacyIndexUri()', activity)
        self.assertIn('session.pendingLegacyBlobUri()', activity)
        self.assertIn('java.util.Collections.singletonList(credentials)', activity)
        self.assertIn('session.drainLegacyManualKeyOffer()', activity)
        self.assertIn('offerManualLegacyKey()', activity)

    def test_large_import_reports_progress_and_validates_file_roles(self):
        importer = (JAVA / 'core/legacy/LegacyFileContainerImporter.java').read_text()
        for requirement in ('BiConsumer<Integer, Integer>', 'progress.accept(i, titles.size())',
                            'progress.accept(titles.size(), titles.size())'):
            self.assertIn(requirement, importer)
        controller = (JAVA / 'SessionController.java').read_text()
        for requirement in ('copyLegacyUri(indexUri, index, 16L * 1024 * 1024, "IDX", "16 MiB", "first")',
                            'copyLegacyUri(blobUri, blob, 1024L * 1024 * 1024L, "BLOB", "1 GiB", "second")',
                            'That looks like the BLOB file. Select the IDX file first.',
                            'if ("IDX".equals(role) && in.read()'):
            self.assertIn(requirement, controller)
        activity = (JAVA / 'MainActivity.java').read_text()
        self.assertIn('offerManualLegacyKey()', activity)
        self.assertIn('requestLegacyKey(', activity)

    def test_synthetic_regressions_do_not_reflect_real_constants(self):
        source = (ROOT / 'apps/android/app/src/test/java/ph/kanta/reborn/core/legacy/'
                  'LegacyFileContainerImporterRegressionTests.java').read_text()
        self.assertNotIn('getDeclaredField', source)
        for requirement in ('runtimeKeyLifecycle();', 'checkCleared(', 'wrongCredentials',
                            'new LegacyImportKey(TEST_KEY, TEST_IV)', 'probeKeySelection();'):
            self.assertIn(requirement, source)
        table = (ROOT / 'apps/android/app/src/test/java/ph/kanta/reborn/core/legacy/'
                 'LegacyKeyTableRegressionTests.java').read_text()
        for requirement in ('PLACEHOLDER', '32-entry limit', 'entryValues();',
                            'multiEntryAndPlaceholder();', 'rejectsMalformedTables();'):
            self.assertIn(requirement, table)
        # The committed table is the only real material; no test source may echo it.
        real_key = next(entry['key'] for entry in table_entries()
                        if entry['key'] != 'PLACEHOLDER')
        for path in (ROOT / 'apps/android/app/src/test/java').rglob('*.java'):
            self.assertNotIn(real_key, path.read_text(), path.name)


if __name__ == '__main__':
    unittest.main()
