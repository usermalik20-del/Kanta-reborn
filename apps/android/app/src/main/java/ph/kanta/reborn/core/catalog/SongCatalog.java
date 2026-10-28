package ph.kanta.reborn.core.catalog;

import ph.kanta.reborn.core.Song;
import java.util.List;

/**
 * Contract for song catalog queries.
 *
 * <p>Implementations may back the catalog by a local file scan, a legacy index container,
 * or a SQLite cache. The interface isolates callers from the source.
 */
public interface SongCatalog {

  /** Returns the total number of songs in the catalog. */
  int count();

  /** Returns all songs (bounded for memory safety). */
  List<Song> all(int limit);

  /** Searches songs by query string (code, title, artist). */
  List<Song> search(String query, int limit);

  /** Returns songs by artist. */
  List<Song> byArtist(String artist, int limit);

  /** Returns songs by volume/folder. */
  List<Song> byVolume(String volume, int limit);

  /** Returns distinct artists. */
  List<String> artists(int limit);

  /** Returns distinct volumes. */
  List<String> volumes(int limit);

  /** Finds a song by its stable ID. */
  Song findById(String id);

  /** Finds a song by its numeric code. */
  Song findByCode(String code);

  /** Rebuilds the catalog from source (called on IO thread). */
  void rebuild() throws Exception;

  /** Closes any resources. */
  void close();
}
