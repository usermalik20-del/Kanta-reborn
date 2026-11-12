package ph.kanta.reborn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded line-timed LRC parser. Never fabricates word timings from plain text. */
public final class LrcLyrics {
  private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,4}):(\\d{2})(?:[.](\\d{1,3}))?\\]");
  private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset:([+-]?\\d{1,7})\\]\\s*$");
  private LrcLyrics() {}

  public static List<MidiLyrics.Cue> parse(String lrc) {
    if (lrc == null || lrc.length() > 128 * 1024) return Collections.emptyList();
    long offset = 0;
    Matcher adjustment = OFFSET.matcher(lrc);
    while (adjustment.find()) offset = Long.parseLong(adjustment.group(1));
    if (Math.abs(offset) > 600000) return Collections.emptyList();
    List<MidiLyrics.Cue> cues = new ArrayList<>();
    String[] lines = lrc.split("\\r?\\n", -1);
    if (lines.length > 1000) return Collections.emptyList();
    for (String raw : lines) {
      String line = raw.trim();
      if (line.length() > 4096) return Collections.emptyList();
      Matcher stamp = STAMP.matcher(line);
      List<Long> times = new ArrayList<>();
      int end = 0;
      while (stamp.find(end) && stamp.start() == end) {
        long minutes = Long.parseLong(stamp.group(1));
        int seconds = Integer.parseInt(stamp.group(2));
        if (seconds >= 60) break;
        String fraction = stamp.group(3);
        long millis = fraction == null ? 0 : Integer.parseInt(fraction)
            * (fraction.length() == 1 ? 100 : fraction.length() == 2 ? 10 : 1);
        long time = minutes * 60000 + seconds * 1000 + millis + offset;
        if (time > 24 * 60 * 60 * 1000L) break;
        times.add(Math.max(0, time));
        end = stamp.end();
      }
      if (times.isEmpty()) continue;
      String text = line.substring(end).trim();
      for (long time : times) {
        cues.add(new MidiLyrics.Cue(time, text,
            Collections.singletonList(new MidiLyrics.Fragment(time, text.length()))));
        if (cues.size() > 1000) return Collections.emptyList();
      }
    }
    cues.sort(Comparator.comparingLong(cue -> cue.milliseconds));
    return Collections.unmodifiableList(cues);
  }
}
