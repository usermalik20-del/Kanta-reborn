"""Source routing guards; RuntimeChecks exercises actual views and the dispatcher."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / 'apps/android/app/src'
ACTIVITY = (ROOT / 'main/java/ph/kanta/reborn/MainActivity.java').read_text()


class AndroidBackNavigationTests(unittest.TestCase):
    def test_system_and_page_headers_share_the_same_back_action(self):
        callback = ACTIVITY.split('public void handleOnBackPressed()', 1)[1].split('};', 1)[0]
        self.assertIn('navigateBack();', callback)
        for line in ACTIVITY.splitlines():
            if 'void onBack()' in line:
                self.assertIn('navigateBack();', line)
        self.assertNotIn('navigateHome()', ACTIVITY)

    def test_forward_and_back_have_one_history_owner(self):
        forward = ACTIVITY.split('private void openPage(', 1)[1].split('private void navigateBack(', 1)[0]
        back = ACTIVITY.split('private void navigateBack(', 1)[1].split('private void showPage(', 1)[0]
        self.assertIn('navigationParents.add(', forward)
        self.assertIn('navigationParents.remove(navigationParents.size() - 1)', back)
        self.assertNotIn('openPage(', back)
        self.assertLess(back.index('dismissAccessory();'), back.index('navigationParents.remove('))
        self.assertIn('putStringArrayList("navigationParents"', ACTIVITY)
        self.assertIn('getStringArrayList("navigationParents"', ACTIVITY)

    def test_runtime_checks_cover_nested_back_and_parent_menus(self):
        runtime = (ROOT / 'androidTest/java/ph/kanta/reborn/RuntimeChecks.java').read_text()
        for expected in ('Back from Search did not return to Queue',
                         'Queue did not return to its parent menu',
                         'Pitch & Tempo did not return to its parent menu',
                         'Visuals did not return to its parent menu',
                         'Back from Search did not restore the control deck'):
            self.assertIn(expected, runtime)


if __name__ == '__main__':
    unittest.main()
