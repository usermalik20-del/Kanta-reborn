package ph.kanta.reborn.core;

import java.util.Locale;
import java.util.Objects;

public final class Song {
  public final String id, title, artist, code, fileName;
  public final boolean starter, legacySourceNumber;
  private final String searchKey;

  public Song(
      String id, String title, String artist, String code, String fileName, boolean starter) {
    this(id, title, artist, code, fileName, starter, false);
  }

  public Song(String id, String title, String artist, String code, String fileName, boolean starter,
      boolean legacySourceNumber) {
    this.id = Objects.requireNonNull(id);
    this.title = Objects.requireNonNull(title);
    this.artist = artist == null ? "" : artist;
    this.code = code == null ? "" : code;
    if (!fileName.matches("[A-Za-z0-9_.-]+") || fileName.contains(".."))
      throw new IllegalArgumentException("Unsafe media name");
    this.fileName = fileName;
    this.starter = starter;
    this.legacySourceNumber = legacySourceNumber;
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

  private static int significantStart(String digits) {
    int start = 0;
    while (start < digits.length() - 1 && digits.charAt(start) == '0') start++;
    return start;
  }

  private boolean matchesExactCode(String digits) {
    if (code.equals(digits)) return true;
    return legacySourceNumber && SongCodes.number(code) == SongCodes.number(digits);
  }

  /** Exact matches outrank partial codes in the live preview. */
  public int numericPreviewRank(String digits) {
    if (SongCodes.number(digits) < 0 || SongCodes.number(code) < 0) return -1;
    if (matchesExactCode(digits)) return 0;
    if (code.startsWith(digits)) return 1;
    int stored = significantStart(code);
    if (!legacySourceNumber) return code.startsWith(digits, stored) ? 2 : -1;
    int entered = significantStart(digits);
    return code.regionMatches(stored, digits, entered, digits.length() - entered) ? 2 : -1;
  }

  /** Exact numeric codes only; ambiguous codes must be resolved by the singer in Search. */
  public static Song uniqueCodeMatch(java.util.List<Song> songs, String entry) {
    String code = entry == null ? "" : entry.trim();
    if (SongCodes.number(code) < 0) return null;
    Song found = null;
    for (Song song : songs) {
      if (Thread.currentThread().isInterrupted()) return null;
      if (!song.matchesExactCode(code)) continue;
      if (found != null && !found.id.equals(song.id)) return null;
      found = song;
    }
    return found;
  }

  public boolean matches(String query) {
    return matchesTerms(searchTerms(query));
  }
}
