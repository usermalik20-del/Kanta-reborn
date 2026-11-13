"""Static guards for reviewed lint/security boundaries; not device acceptance."""
from pathlib import Path
import json
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'apps/android/app/src/main'
JAVA = MAIN / 'java/ph/kanta/reborn'

class AndroidLintSecurityTests(unittest.TestCase):
    def test_scenic_refresh_does_not_replay_untrusted_launch_intent(self):
        source = (JAVA / 'MainActivity.java').read_text()
        self.assertNotIn('startActivity(getIntent())', source)
        delete = source.split('session.deleteScenic(id);', 1)[1].split('});', 1)[0]
        self.assertIn('recreate();', delete)

    def test_media_files_are_atomically_owner_only_including_rename_fallback(self):
        source = (JAVA / 'core/legacy/MegContainerImporter.java').read_text()
        self.assertNotIn('setWritable(true, false)', source)
        self.assertIn('Files.createTempFile(dir.toPath()', source)
        self.assertIn('Files.createFile(dst.toPath()', source)
        self.assertEqual(source.count('PosixFilePermissions.fromString("rw-------")'), 2)
        self.assertIn('if (!temp.renameTo(finalFile))', source)

    def test_tls_exception_is_exact_leaf_pinning_not_trust_all(self):
        source = (JAVA / 'PinnedBrowserTransport.java').read_text()
        validator = source.split('public void checkServerTrusted(', 1)[1].split('}},new SecureRandom()', 1)[0]
        for guard in ('chain==null||chain.length==0', 'chain[0].checkValidity()',
                      'MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded())',
                      'MessageDigest.isEqual(', 'fingerprint.getBytes(StandardCharsets.US_ASCII)',
                      'throw new CertificateException("Host fingerprint mismatch")'):
            self.assertIn(guard, validator)
        self.assertIn('connection.setInstanceFollowRedirects(false)', source)
        self.assertNotIn('setHostnameVerifier', source)
        self.assertIn('throw new CertificateException("Client certificates unsupported")', source)

    def test_font_singleton_cannot_capture_activity_context(self):
        source = (JAVA / 'font/FontStore.java').read_text()
        self.assertIn('private FontStore(Context context)', source)
        self.assertIn('new FontStore(context.getApplicationContext())', source)
        self.assertNotIn('new FontStore(context);', source)

    def test_javascript_exceptions_retain_isolation(self):
        remote = (JAVA / 'BrowserCompanionDialog.java').read_text()
        for guard in ('settings.setAllowFileAccess(false)', 'settings.setAllowContentAccess(false)',
                      'WebSettings.MIXED_CONTENT_NEVER_ALLOW', 'return transport.resource(request)',
                      'return !transport.sameOrigin(request.getUrl())', 'request.deny()'):
            self.assertIn(guard, remote)
        youtube = (JAVA / 'YouTubeEmbeddedPlayer.java').read_text()
        for guard in ('setAllowFileAccess(false)', 'setAllowContentAccess(false)',
                      'setJavaScriptCanOpenWindowsAutomatically(false)', 'setSupportMultipleWindows(false)',
                      'return released || !isAllowed(', '"https".equalsIgnoreCase(url.getScheme())'):
            self.assertIn(guard, youtube)
        self.assertNotIn('addJavascriptInterface', youtube)

    def test_cloud_and_device_transfer_exclude_all_app_storage(self):
        ns = '{http://schemas.android.com/apk/res/android}'
        app = ET.parse(MAIN / 'AndroidManifest.xml').getroot().find('application')
        self.assertEqual(app.get(ns + 'allowBackup'), 'false')
        self.assertEqual(app.get(ns + 'dataExtractionRules'), '@xml/data_extraction_rules')
        self.assertEqual(app.get(ns + 'fullBackupContent'), '@xml/full_backup_content')
        legacy = ET.parse(MAIN / 'res/xml/full_backup_content.xml').getroot().findall('exclude')
        self.assertEqual({item.get('domain') for item in legacy},
                         {'root', 'file', 'database', 'sharedpref', 'external'})
        self.assertTrue(all(item.get('path') == '.' for item in legacy))
        rules = ET.parse(MAIN / 'res/xml/data_extraction_rules.xml').getroot()
        domains = {'root', 'file', 'database', 'sharedpref', 'external', 'device_root',
                   'device_file', 'device_database', 'device_sharedpref'}
        for mode in ('cloud-backup', 'device-transfer'):
            exclusions = rules.find(mode).findall('exclude')
            self.assertEqual({item.get('domain') for item in exclusions}, domains)
            self.assertTrue(all(item.get('path') == '.' for item in exclusions))

    def test_localized_status_count_uses_plural_and_ellipsis_glyphs(self):
        plurals = ET.parse(MAIN / 'res/values/plurals.xml').getroot().find('plurals')
        self.assertEqual({item.get('quantity') for item in plurals}, {'one', 'other'})
        source = (JAVA / 'stage/RemoteStatusView.java').read_text()
        self.assertIn('getQuantityString(', source)
        strings = (MAIN / 'res/values/strings.xml').read_text()
        self.assertNotIn('...', strings)

    def test_only_reviewed_lint_exceptions_are_annotated_and_narrow(self):
        budget = json.loads((ROOT / 'tools/android-lint-debt.json').read_text())
        self.assertEqual(budget['findings'], [])
        expected = {
            'PinnedBrowserTransport.java': 'CustomX509TrustManager',
            'BrowserCompanionDialog.java': 'SetJavaScriptEnabled',
            'YouTubeEmbeddedPlayer.java': 'SetJavaScriptEnabled',
            'font/FontStore.java': 'StaticFieldLeak',
        }
        all_source = ''.join(path.read_text() for path in JAVA.rglob('*.java'))
        for suffix, issue in expected.items():
            path = next(JAVA.rglob(suffix))
            source = path.read_text()
            self.assertIn(f'@android.annotation.SuppressLint("{issue}")', source)
            # Exactly one justification per intentional finding, not package-wide disabling.
            self.assertEqual(source.count(f'SuppressLint("{issue}")'), 1)
        for forbidden in ('lint { disable', 'lintOptions { disable',
                          'disable "SetJavaScriptEnabled"', 'warningsAsErrors = false'):
            self.assertNotIn(forbidden, all_source)

    def test_programmatic_only_custom_view_suppressions_match_xml_absence(self):
        modules = (
            'ui/GroupScreen', 'ui/HomeScreen', 'ui/components/InlineKeyboard',
            'ui/KantaControlDeck', 'ui/KantaFullscreenStage', 'ui/components/KantaImageButton',
            'ui/KantaMicVoiceScreen', 'ui/KantaNowPlayingScreen', 'ui/KantaPitchTempoScreen',
            'ui/KantaQueueScreen', 'ui/components/KantaScreenHeader', 'ui/KantaSearchScreen',
            'ui/components/KantaTransportControls', 'ui/KantaVisualsScreen',
            'stage/StagePresenterImpl', 'StageSurface', 'StageView', 'YouTubeEmbeddedPlayer',
        )
        xml_files = list((MAIN / 'res').rglob('*.xml'))
        for module in modules:
            name = module.rsplit('/', 1)[-1]
            source = (JAVA / (module + '.java')).read_text()
            with self.subTest(module=module):
                self.assertIn('@android.annotation.SuppressLint("ViewConstructor")', source)
                self.assertRegex(source, rf'class {name}\b')
                for xml in xml_files:
                    text = xml.read_text()
                    self.assertNotRegex(text, rf'(?<![A-Za-z0-9_]){name}(?![A-Za-z0-9_])')

    def test_debug_and_release_builds_require_zero_debt_and_native_checks(self):
        gradle = (ROOT / 'apps/android/app/build.gradle').read_text()
        workflow = (ROOT / '.github/workflows/android-validation.yml').read_text()
        for variant in ('Debug', 'Release'):
            self.assertIn(f'dependsOn "lint${{variant}}"', gradle)
            self.assertIn('task.name == "assemble${variant}" || task.name == "bundle${variant}"', gradle)
            self.assertIn('verify${variant}LintDebt', gradle)
        self.assertIn('verify-android-native-alignment.py', gradle)
        self.assertIn(':app:assembleRelease :app:lintRelease', workflow)
        self.assertIn("python3 -m unittest discover -s tools/tests -p 'test_android_lint*.py'", workflow)
        self.assertIn('reports/lint-results-release.*', workflow)

if __name__ == '__main__':
    unittest.main()
