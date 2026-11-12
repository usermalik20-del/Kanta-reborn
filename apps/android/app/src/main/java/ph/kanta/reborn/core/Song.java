package ph.kanta.reborn.core;

import java.util.Locale;
import java.util.Objects;

public final class Song {
  public final String id, title, artist, code, fileName;
  public final boolean starter;
  private final String searchKey;

  public Song(
      String id, String title, String artist, String code, String fileName, boolean starter) {
    this.id = Objects.requireNonNull(id);
    this.title = Objects.requireNonNull(title);
    this.artist = artist == null ? "" : artist;
    this.code = code == null ? "" : code;
    if (!fileName.matches("[A-Za-z0-9_.-]+") || fileName.contains(".."))
      throw new IllegalArgumentException("Unsafe media name");
    this.fileName = fileName;
    this.starter = starter;
    this.searchKey = normalize(this.code + " " + this.title + " " + this.artist);
  }

  private static String normalize(String value) {
    return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }

  public static String[] searchTerms(String query) {
    return normalize(query).trim().split("\\s+");
  }

  public boolean matchesTerms(String[] terms) {
    for (String term : terms) if (!searchKey.contains(term)) return false;
    return true;
  }

  /** Exact numeric codes only; ambiguous codes must be resolved by the singer in Search. */
  public static Song uniqueCodeMatch(java.util.List<Song> songs, String entry) {
    String code = entry == null ? "" : entry.trim();
    if (!code.matches("[0-9]+")) return null;
    Song found = null;
    for (Song song : songs) {
      if (Thread.currentThread().isInterrupted()) return null;
      if (!code.equals(song.code)) continue;
      if (found != null && !found.id.equals(song.id)) return null;
      found = song;
    }
    return found;
  }

  public boolean matches(String query) {
    return matchesTerms(searchTerms(query));
  }
}
