package ph.kanta.reborn.core.catalog;

import ph.kanta.reborn.core.Song;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A {@link SongCatalog} view over the live library snapshot (imported songs,
 * starters and persistent metadata) so stage search finds everything the
 * active player can actually play instead of only a dormant index.
 */
public final class LibrarySongCatalog implements SongCatalog {

  private final Supplier<List<Song>> library;

  public LibrarySongCatalog(Supplier<List<Song>> library) {
    this.library = library;
  }

  private List<Song> snapshot() {
    List<Song> source = library.get();
    return source == null ? new ArrayList<>() : source;
  }

  @Override
  public int count() {
    return snapshot().size();
  }

  @Override
  public List<Song> all(int limit) {
    List<Song> source = snapshot();
    return limit >= source.size() || limit < 0
        ? source
        : new ArrayList<>(source.subList(0, limit));
  }

  @Override
  public List<Song> search(String query, int limit) {
    if (query == null || limit <= 0) return new ArrayList<>();
    String[] terms = Song.searchTerms(query);
    if (terms.length == 0) return new ArrayList<>();
    ArrayList<Song> matches = new ArrayList<>();
    for (Song song : snapshot()) if (song.matchesTerms(terms)) matches.add(song);
    return matches.size() > limit ? new ArrayList<>(matches.subList(0, limit)) : matches;
  }

  @Override
  public List<Song> byArtist(String artist, int limit) {
    if (artist == null || artist.isEmpty() || limit <= 0) return new ArrayList<>();
    ArrayList<Song> matches = new ArrayList<>();
    for (Song song : snapshot())
      if (song.artist.equalsIgnoreCase(artist)) matches.add(song);
    return matches.size() > limit ? new ArrayList<>(matches.subList(0, limit)) : matches;
  }

  @Override
  public List<Song> byVolume(String volume, int limit) {
    return new ArrayList<>();
  }

  @Override
  public List<String> artists(int limit) {
    ArrayList<String> values = new ArrayList<>();
    for (Song song : snapshot())
      if (!song.artist.isEmpty() && !values.contains(song.artist)) values.add(song.artist);
    return limit < 0 || values.size() <= limit
        ? values
        : new ArrayList<>(values.subList(0, limit));
  }

  @Override
  public List<String> volumes(int limit) {
    return new ArrayList<>();
  }

  @Override
  public Song findById(String id) {
    if (id == null) return null;
    for (Song song : snapshot()) if (song.id.equals(id)) return song;
    return null;
  }

  @Override
  public Song findByCode(String code) {
    if (code == null) return null;
    for (Song song : snapshot()) if (song.code.equals(code)) return song;
    return null;
  }

  @Override
  public void rebuild() {
    // The live library is the source of truth; there is nothing to scan.
  }

  @Override
  public void close() {
  }
}