from pathlib import Path
import unittest

MAIN = Path(__file__).resolve().parents[2] / 'apps/android/app/src/main'
JAVA = MAIN / 'java/ph/kanta/reborn'


def block(source, start, end):
    return source.split(start, 1)[1].split(end, 1)[0]


class AndroidPlaylistsTest(unittest.TestCase):
    def test_stage_actions_dispatch_separately(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        dispatch = block(activity, 'target.showMatches(lookupGeneration, matches,', 'if (numeric)')
        callbacks = dispatch.split('}, song -> {')
        self.assertEqual(len(callbacks), 3)
        for callback, method in zip(callbacks, ('reserveFromSearch', 'playSearchResult', 'choosePlaylistForSong')):
            self.assertIn(method + '(song', callback)
            self.assertIn('session.foreground', callback)
        home = (JAVA / 'ui/HomeScreen.java').read_text()
        self.assertIn('addMatchAction(result, R.string.stage_playlist,', home)
        self.assertIn('"playlist",', home)
        self.assertIn('generation == activeSongLookupGeneration()) addToPlaylist.accept(song)', home)

    def test_playlist_writes_do_not_dispatch_transport_or_reservations(self):
        controller = (JAVA / 'SessionController.java').read_text()
        mutation = block(controller, 'void createPlaylist(', 'void scene(')
        for required in ('repository.createPlaylist(', 'repository.addToPlaylist(', 'io.execute(', 'savedSets = updated'):
            self.assertIn(required, mutation)
        for forbidden in ('queue.', 'saveQueue(', 'reserve(', 'loadSet(', 'publish(', 'startLocal(', 'playSearchResult('):
            self.assertNotIn(forbidden, mutation)

    def test_new_and_existing_playlists_use_legacy_set_format(self):
        repo = (JAVA / 'LibraryRepository.java').read_text()
        self.assertIn('root.optJSONArray("sets")', repo)
        self.assertIn('root.put("sets", saved)', repo)
        create = block(repo, 'void createPlaylist(', 'boolean addToPlaylist(')
        self.assertIn('Collections.emptyList()', create)
        self.assertIn('Collections.singletonList(firstSongId)', create)
        append = block(repo, 'boolean addToPlaylist(', 'static final int PLAYLIST_NAME_LIMIT')
        self.assertIn('current.songs.contains(songId)', append)
        self.assertIn('new SavedSet(current.id, current.name, ids)', append)
        self.assertIn('sets.set(i, current)', append)

    def test_presets_and_stage_picker_offer_creation(self):
        activity = (JAVA / 'MainActivity.java').read_text()
        presets = block(activity, 'private void mySet()', 'private String playlistLabel(')
        self.assertIn('namePlaylist(null)', presets)
        self.assertIn('showPlaylist(set)', presets)
        self.assertIn('nameSet(false)', presets)
        self.assertIn('nameSet(true)', presets)
        picker = block(activity, 'private void choosePlaylistForSong(', 'private void namePlaylist(')
        self.assertIn('namePlaylist(song)', picker)
        self.assertIn('addPlaylistSong(sets.get(which).id, song)', picker)
        search = block(activity, 'private void searchPlaylistSongs(', 'private void showPlaylistResult(')
        self.assertIn('addPlaylistSong(set.id, shown.get(position))', search)
        self.assertNotIn('reserve(', search)
        self.assertNotIn('playSearchResult(', search)
