import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/android/app/src'
MAIN = APP / 'main'
JAVA = MAIN / 'java/ph/kanta/reborn'


class SettingsBackupTests(unittest.TestCase):
    def test_picker_integration_remains_opt_in_and_document_scoped(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        actions = (JAVA / 'SettingsBackupActions.java').read_text()
        manifest = ElementTree.parse(MAIN / 'AndroidManifest.xml')
        app = manifest.find('application')
        self.assertEqual(app.get('{http://schemas.android.com/apk/res/android}allowBackup'), 'false')
        self.assertIn('case "backup":\n        settingsBackup.show();', activity)
        self.assertIn('settingsBackup = new SettingsBackupActions(this, session);', activity)
        self.assertIn('settingsBackup.close();', activity)
        for token in ('Intent.ACTION_CREATE_DOCUMENT', 'Intent.ACTION_OPEN_DOCUMENT',
                      'Intent.CATEGORY_OPENABLE', 'ContentResolver.SCHEME_CONTENT',
                      'resolver.openInputStream(uri)', 'resolver.openOutputStream(uri, "wt")'):
            self.assertIn(token, actions)
        for token in ('ACTION_OPEN_DOCUMENT_TREE', 'takePersistableUriPermission', 'AccountManager',
                      'HttpURLConnection', 'getSharedPreferences'):
            self.assertNotIn(token, actions)
        strings = ElementTree.parse(MAIN / 'res/values/settings_backup_strings.xml')
        self.assertEqual(set(re.findall(r'R\.string\.(settings_backup_\w+)', activity + actions)),
                         {node.get('name') for node in strings.getroot()})

    def test_restore_is_confirmed_idle_and_limited_to_three_keys(self):
        actions = (JAVA / 'SettingsBackupActions.java').read_text()
        controller = (JAVA / 'SessionController.java').read_text()
        store = (JAVA / 'settings/SettingsStore.java').read_text()
        self.assertLess(actions.index('PlaybackSettingsBackup.read(in)'),
                        actions.index('confirmRestore(backup)'))
        confirm = actions.split('private void confirmRestore(', 1)[1].split('private boolean visible', 1)[0]
        self.assertLess(confirm.index('.setPositiveButton('),
                        confirm.index('session.restorePlaybackSettings(backup)'))
        guard = controller.split('boolean canRestorePlaybackSettings() {', 1)[1].split('}', 1)[0]
        for state in ('!playing', '!loading', '!resuming', 'current == null', 'player == null',
                      'pending == null', 'countingEntry == null'):
            self.assertIn(state, guard)
        restore = controller.split('boolean restorePlaybackSettings(', 1)[1].split('}', 1)[0]
        self.assertLess(restore.index('!foreground || !canRestorePlaybackSettings()'),
                        restore.index('settings.restorePlaybackDefaults(backup)'))
        for effect in ('volume = backup.volume', 'tempo = backup.tempo', 'pitch = backup.pitch', 'publish();'):
            self.assertIn(effect, restore)
        self.assertNotIn('controls(', restore)
        persistence = store.split('void restorePlaybackDefaults(', 1)[1].split('}', 1)[0]
        self.assertEqual(re.findall(r'\.put\w+\((KEY_\w+),', persistence),
                         ['KEY_MASTER_VOLUME', 'KEY_TEMPO', 'KEY_PITCH'])
        self.assertEqual(persistence.count('prefs.edit()'), 1)
        self.assertEqual(persistence.count('.apply()'), 1)
        self.assertNotIn('.clear()', persistence)

    @unittest.skipUnless(shutil.which('java') and shutil.which('javac'), 'JDK required')
    def test_round_trip_and_hostile_documents(self):
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(['javac', '-d', tmp, str(JAVA / 'core/PlaybackSettingsBackup.java'),
                            str(APP / 'test/java/ph/kanta/reborn/core/PlaybackSettingsBackupChecks.java')],
                           check=True)
            subprocess.run(['java', '-cp', tmp, 'ph.kanta.reborn.core.PlaybackSettingsBackupChecks'],
                           check=True)


if __name__ == '__main__':
    unittest.main()
