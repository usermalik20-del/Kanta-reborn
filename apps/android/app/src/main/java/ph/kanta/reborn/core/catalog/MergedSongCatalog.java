package ph.kanta.reborn.core.catalog;

import ph.kanta.reborn.core.Song;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Merges two catalogs (for example the SQLite legacy index and the live
 * library) with identity dedupe; the first catalog wins for a repeated id,
 * matching active-library precedence over a dormant index.
 */
public final class MergedSongCatalog implements SongCatalog {

  private final SongCatalog first, second;

  public MergedSongCatalog(SongCatalog first, SongCatalog second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public int count() {
    return first.count() + second.count();
  }

  @Override
  public List<Song> all(int limit) {
    if (limit <= 0) return new ArrayList<>();
    ArrayList<Song> merged = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    addAll(first.all(limit), merged, ids);
    addAll(second.all(limit), merged, ids);
    return merged.size() > limit ? new ArrayList<>(merged.subList(0, limit)) : merged;
  }

  @Override
  public List<Song> search(String query, int limit) {
    if (limit <= 0) return new ArrayList<>();
    ArrayList<Song> merged = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    addAll(first.search(query, limit), merged, ids);
    addAll(second.search(query, limit), merged, ids);
    return merged.size() > limit ? new ArrayList<>(merged.subList(0, limit)) : merged;
  }

  @Override
  public List<Song> byArtist(String artist, int limit) {
    if (limit <= 0) return new ArrayList<>();
    ArrayList<Song> merged = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    addAll(first.byArtist(artist, limit), merged, ids);
    addAll(second.byArtist(artist, limit), merged, ids);
    return merged.size() > limit ? new ArrayList<>(merged.subList(0, limit)) : merged;
  }

  @Override
  public List<Song> byVolume(String volume, int limit) {
    if (limit <= 0) return new ArrayList<>();
    ArrayList<Song> merged = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    addAll(first.byVolume(volume, limit), merged, ids);
    addAll(second.byVolume(volume, limit), merged, ids);
    return merged.size() > limit ? new ArrayList<>(merged.subList(0, limit)) : merged;
  }

  @Override
  public List<String> artists(int limit) {
    ArrayList<String> merged = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String artist : first.artists(limit)) if (seen.add(artist)) merged.add(artist);
    for (String artist : second.artists(limit)) if (seen.add(artist)) merged.add(artist);
    return merged.size() > limit ? new ArrayList<>(merged.subList(0, limit)) : merged;
  }

  @Override
  public List<String> volumes(int limit) {
    ArrayList<String> merged = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String volume : first.volumes(limit)) if (seen.add(volume)) merged.add(volume);
    for (String volume : second.volumes(limit)) if (seen.add(volume)) merged.add(volume);
    return merged.size() > limit ? new ArrayList<>(merged.subList(0, limit)) : merged;
  }

  @Override
  public Song findById(String id) {
    Song song = first.findById(id);
    return song != null ? song : second.findById(id);
  }

  @Override
  public Song findByCode(String code) {
    Song song = first.findByCode(code);
    return song != null ? song : second.findByCode(code);
  }

  @Override
  public void rebuild() throws Exception {
    first.rebuild();
    second.rebuild();
  }

  @Override
  public void close() {
    first.close();
    second.close();
  }

  private static void addAll(List<Song> source, ArrayList<Song> merged, Set<String> ids) {
    if (source == null) return;
    for (Song song : source) if (ids.add(song.id)) merged.add(song);
  }
}