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

    def test_dock_resumes_menu_without_caching_view_children(self):
        dock = ACTIVITY.split('private void selectMainMenu(', 1)[1].split('private void openStageSearch(', 1)[0]
        page = ACTIVITY.split('private void showPage(', 1)[1].split('private void nowPlayingScreen(', 1)[0]
        self.assertIn('if (inMenu) rememberMenuRoute();', dock)
        self.assertIn('destination = menuDestination;', dock)
        self.assertIn('navigationParents.addAll(menuParents);', dock)
        self.assertIn('render();', page)
        self.assertNotIn('tabContentCache', ACTIVITY)

    def test_render_waits_for_the_window_and_runs_on_the_main_thread(self):
        render = ACTIVITY.split('private void render()', 1)[1].split('private boolean showsPrimaryStage()', 1)[0]
        build = ACTIVITY.split('private void constructWindow(', 1)[1].split('private void addStageChrome(', 1)[0]
        self.assertLess(render.index('handler.post(this::render)'), render.index('body.removeAllViews()'))
        self.assertLess(render.index('if (buildingWindow ||'), render.index('body.removeAllViews()'))
        self.assertLess(build.index('body = column();'), build.index('setContentView(root);'))

    def test_runtime_checks_cover_nested_back_and_parent_menus(self):
        runtime = (ROOT / 'androidTest/java/ph/kanta/reborn/RuntimeChecks.java').read_text()
        for expected in ('Back from Menu did not return to Stage',
                         'Queue did not return to Stage',
                         'Menu dock did not resume Settings',
                         'Stage & Visual did not return to Menu',
                         'Predictive back did not dismiss the lower utility panel and restore Stage',
                         'Stage logo still opens the removed deck'):
            self.assertIn(expected, runtime)


if __name__ == '__main__':
    unittest.main()
