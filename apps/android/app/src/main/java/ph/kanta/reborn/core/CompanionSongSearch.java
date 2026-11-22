package ph.kanta.reborn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Search only the host's playable catalog snapshot, never its queue or current input. */
public final class CompanionSongSearch {
  private static final int RESULTS = 10;
  private static final int TEXT_SCAN_LIMIT = 10_000;

  private CompanionSongSearch() {}

  /** Returns submitted ASCII song digits, preserving leading zeros; never displays free text. */
  public static String displayDigits(String query) {
    String value = query.trim();
    return SongCodes.number(value) >= 0 ? value : "";
  }

  public static List<Song> find(List<Song> catalog, String query) {
    String value = query.trim();
    if (value.isEmpty() || SongCodes.isOverlongNumericQuery(value)) return Collections.emptyList();
    List<Song> matches = new ArrayList<>(RESULTS);
    if (!SongCodes.isDigits(value)) {
      String[] terms = Song.searchTerms(value);
      int examined = 0;
      for (Song song : catalog) {
        if (++examined > TEXT_SCAN_LIMIT || matches.size() == RESULTS) break;
        if (song.matchesTerms(terms)) matches.add(song);
      }
      return matches;
    }

    // Scan the full snapshot: a later exact number must beat early partial matches.
    int[] ranks = new int[RESULTS];
    for (Song song : catalog) {
      int rank = song.numericPreviewRank(value);
      if (rank < 0) continue;
      int index = matches.size();
      while (index > 0 && ranks[index - 1] > rank) index--;
      if (index >= RESULTS) continue;
      if (matches.size() == RESULTS) matches.remove(RESULTS - 1);
      for (int i = matches.size(); i > index; i--) ranks[i] = ranks[i - 1];
      ranks[index] = rank;
      matches.add(index, song);
      if (matches.size() == RESULTS && ranks[RESULTS - 1] == 0) break;
    }
    return matches;
  }
}
