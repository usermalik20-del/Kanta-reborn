"""Source contracts for runtime-only legacy import material; not Android execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class RuntimeImportKeyContractTests(unittest.TestCase):
    def test_importer_has_no_embedded_material_or_fallback(self):
        source = (JAVA / 'core/legacy/LegacyFileContainerImporter.java').read_text()
        self.assertNotRegex(source, r'byte\[\]\s+(KEY|IV)\s*=')
        self.assertIn('LegacyImportKey credentials', source)
        self.assertIn('try (LegacyImportKey owned = credentials)', source)
        self.assertIn('owned.key(), owned.iv()', source)
        self.assertNotIn('hex(', source)

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
                        submit.index('session.importLegacyPair('))
        self.assertNotIn('getText().toString()', prompt)
        self.assertIn('if (legacyKeyDialog != null) legacyKeyDialog.dismiss();',
                      source.split('protected void onStop()', 1)[1].split(
                          'protected void onDestroy()', 1)[0])

    def test_controller_clears_rejected_and_failed_imports(self):
        source = (JAVA / 'SessionController.java').read_text()
        flow = source.split('boolean importLegacyPair(', 1)[1].split(
            'void downloadFluidR3GM()', 1)[0]
        self.assertIn('credentials.close();\n      return false;', flow)
        self.assertIn('LegacyImportKey owned = credentials)', flow)
        self.assertIn('RejectedExecutionException', flow)
        self.assertIn('"legacy-import"), owned)', flow)
        for forbidden in ('SharedPreferences', 'Log.', 'println', 'getBytes(', 'toString('):
            self.assertNotIn(forbidden, flow)

    def test_synthetic_regressions_do_not_reflect_real_constants(self):
        source = (ROOT / 'apps/android/app/src/test/java/ph/kanta/reborn/core/legacy/'
                  'LegacyFileContainerImporterRegressionTests.java').read_text()
        self.assertNotIn('getDeclaredField', source)
        for requirement in ('runtimeKeyLifecycle();', 'checkCleared(', 'wrongCredentials',
                            'new LegacyImportKey(TEST_KEY, TEST_IV)'):
            self.assertIn(requirement, source)


if __name__ == '__main__':
    unittest.main()
