"""Source contracts for v31 media libraries; Android behavior needs RuntimeChecks."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'apps/android/app/src/main'
JAVA = MAIN / 'java/ph/kanta/reborn'
ACTIVITY = (JAVA / 'MainActivity.java').read_text()
STORE = (JAVA / 'StageMediaStore.java').read_text()


def method(source, name, next_method):
    return source.split(name, 1)[1].split(next_method, 1)[0]


class AndroidMediaLibraryV31(unittest.TestCase):
    def test_existing_music_catalog_and_safe_removal_remain_the_owners(self):
        loop = method(ACTIVITY, 'private void loop()', 'private void showIntermissionGuide()')
        self.assertIn('SessionController.isAudio(song)', loop)
        self.assertIn('track.id.equals(session.loopSongId)', loop)
        self.assertIn('session.chooseIntermission(track)', loop)
        self.assertIn('session.deleteSong(track.id,', loop)
        self.assertIn('Arrays.asList(session.customPads).contains(track.id)', loop)
        self.assertIn('session.useBundledInterlude', loop.replace('session::', 'session.'))
        self.assertIn('session.importSongs(uris)', ACTIVITY)
        self.assertNotIn('persistableUriPermission', loop)

    def test_pickers_accept_clipdata_and_single_documents_read_only(self):
        for picker, next_method in [('chooseStageMedia', 'addCompactMediaList'), ('chooseMusic', 'chooseAudio')]:
            with self.subTest(picker=picker):
                body = method(ACTIVITY, f'private void {picker}(', f'private void {next_method}(')
                self.assertIn('Intent.ACTION_OPEN_DOCUMENT', body)
                self.assertIn('Intent.CATEGORY_OPENABLE', body)
                self.assertIn('Intent.EXTRA_ALLOW_MULTIPLE, true', body)
                self.assertNotIn('Intent.FLAG_GRANT_WRITE_URI_PERMISSION', body)
        result = method(ACTIVITY, 'List<Uri> pickedDocuments(', 'protected void onActivityResult(')
        for guard in ('getClipData()', 'clip.getItemAt(i).getUri()', 'data.getData()', 'LinkedHashSet'):
            self.assertIn(guard, result)
        self.assertIn('session.importStageBatch(sources, request == VIDEO)', ACTIVITY)

    def test_shared_list_caps_both_pages_at_three_rows_and_virtualizes(self):
        body = method(ACTIVITY, 'private void addCompactMediaList(', 'private View compactMediaRow(')
        self.assertIn('new RecyclerView(this)', body)
        self.assertIn('rowHeight * Math.min(3, count)', body)
        self.assertIn('layout.scrollToPositionWithOffset(', body)
        self.assertIn('new NestedScrollView(this)', ACTIVITY)
        self.assertIn('kanta.visuals.imported.photos', ACTIVITY)
        self.assertIn('kanta.visuals.imported.videos', ACTIVITY)
        self.assertIn('kanta.intermissions.tracks', ACTIVITY)
        self.assertIn('getScaledFrameAtTime(', STORE)

    def test_guide_has_live_status_separate_instructions_and_valid_resources(self):
        guide = method(ACTIVITY, 'private void showIntermissionGuide()', 'private void chooseFontFolder()')
        self.assertIn('intermission_music_guide', guide)
        self.assertNotIn('intermission_pads_guide', guide)
        pads = method(ACTIVITY, 'private void partyPadsPage()', 'private Button partyPadTrigger(')
        self.assertIn('intermission_pads_guide', pads)
        self.assertIn('intermission_remove_guide', guide)
        self.assertIn('session.party.loopPlaying()', guide)
        self.assertIn('session.loopEnabled', guide)
        self.assertIn('intermissionGuideState.setText(status)', guide)
        values = ET.parse(MAIN / 'res/values/media_library_strings.xml').getroot()
        strings = {item.attrib['name'] for item in values.findall('string')}
        refs = re.findall(r'R\.string\.((?:media_library|intermission)_\w+)', ACTIVITY)
        self.assertEqual(set(refs) - strings, set())

    def test_versioned_visual_storage_protects_existing_selection_and_originals(self):
        load = method(STORE, 'synchronized Selection load()', 'private void addLegacyImport')
        self.assertIn('version != 1 && version != 2', load)
        for previous in ('savedPhoto, false', 'savedVideo, true', 'savedRetained, true'):
            self.assertIn(previous, load)
        batch = method(STORE, 'ImportResult importBatch(', 'private synchronized boolean saveImport(')
        self.assertIn('saveImport(source, isVideo, cancelled, false)', batch)
        delete = method(STORE, 'boolean removeImport(', 'synchronized Selection importMedia(')
        self.assertLess(delete.index('changeSelection('), delete.index('Files.deleteIfExists(saved.file.toPath())'))
        self.assertNotIn('openOutputStream', STORE)
        self.assertIn('OpenableColumns.DISPLAY_NAME', STORE)
        self.assertIn('new AtomicFile(', STORE)


if __name__ == '__main__':
    unittest.main()
