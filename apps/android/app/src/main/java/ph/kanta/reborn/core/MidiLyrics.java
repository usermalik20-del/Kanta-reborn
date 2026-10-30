package ph.kanta.reborn.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded SMF 0/1 parser. Tempo events from all tracks share one tick timeline. */
public final class MidiLyrics {
  public static final class Cue {
    public final long milliseconds;
    public final String line;
    public final List<Fragment> fragments;

    Cue(long ms, String text, List<Fragment> source) {
      milliseconds = ms;
      int start = 0, end = text.length();
      while (start < end && Character.isWhitespace(text.charAt(start))) start++;
      while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
      line = text.substring(start, end);
      ArrayList<Fragment> values = new ArrayList<>();
      for (Fragment fragment : source)
        values.add(
            new Fragment(
                fragment.milliseconds,
                Math.max(0, Math.min(line.length(), fragment.endCharacters - start))));
      fragments = Collections.unmodifiableList(values);
    }

    public int highlightedCharacters(long position) {
      int end = 0;
      for (Fragment fragment : fragments) {
        if (fragment.milliseconds > position) break;
        end = fragment.endCharacters;
      }
      return end;
    }
  }

  public static final class Fragment {
    public final long milliseconds;
    public final int endCharacters;

    Fragment(long milliseconds, int endCharacters) {
      this.milliseconds = milliseconds;
      this.endCharacters = endCharacters;
    }
  }

  public static final class Message {
    public final long milliseconds;
    public final int status, a, b, track;

    Message(long ms, int status, int a, int b, int track) {
      milliseconds = ms;
      this.status = status;
      this.a = a;
      this.b = b;
      this.track = track;
    }
  }

  public static final class Track {
    public final int index, noteChannelMask;
    public final String name, instrumentName;
    public final boolean hasLyrics;

    Track(int index, int mask, String name, String instrumentName, boolean hasLyrics) {
      this.index = index;
      noteChannelMask = mask;
      this.name = name;
      this.instrumentName = instrumentName;
      this.hasLyrics = hasLyrics;
    }
  }

  public static final class Document {
    public final List<Cue> cues;
    public final List<Message> messages;
    public final List<Track> tracks;
    public final long durationMilliseconds;

    /** True when the file carried a key-signature meta event. */
    public final boolean keyPresent;

    /** Signed sharps (positive) or flats (negative) count, -7 to +7. */
    public final int keySharpsFlats;

    /** True when the key signature is minor. */
    public final boolean keyMinor;

    Document(
        List<Cue> cues,
        List<Message> messages,
        List<Track> tracks,
        long duration,
        boolean keyPresent,
        int keySharpsFlats,
        boolean keyMinor) {
      this.cues = Collections.unmodifiableList(cues);
      this.messages = Collections.unmodifiableList(messages);
      this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
      durationMilliseconds = duration;
      this.keyPresent = keyPresent;
      this.keySharpsFlats = keySharpsFlats;
      this.keyMinor = keyMinor;
    }
  }

  private static final class Event {
    long tick;
    int tempo, type, status, a, b;
    String text;
    int sequence, track;

    Event(long t, int tempo, int type, String text, int seq, int track) {
      tick = t;
      this.tempo = tempo;
      this.type = type;
      this.text = text;
      sequence = seq;
      this.track = track;
    }
  }

  private static final class Reader {
    final byte[] b;
    int p, end;

    Reader(byte[] b, int p, int end) {
      this.b = b;
      this.p = p;
      this.end = end;
    }

    int u8() throws IOException {
      if (p >= end) throw new IOException("Truncated MIDI");
      return b[p++] & 255;
    }

    int u16() throws IOException {
      return (u8() << 8) | u8();
    }

    long u32() throws IOException {
      return ((long) u16() << 16) | u16();
    }

    long vlq() throws IOException {
      long n = 0;
      for (int i = 0; i < 4; i++) {
        int v = u8();
        n = (n << 7) | (v & 127);
        if ((v & 128) == 0) return n;
      }
      throw new IOException("Invalid MIDI length");
    }

    void skip(long n) throws IOException {
      if (n < 0 || n > end - p) throw new IOException("Invalid MIDI chunk");
      p += (int) n;
    }

    String text(int n) throws IOException {
      if (n < 0 || n > end - p || n > 65536) throw new IOException("Invalid MIDI text");
      String s = new String(b, p, n, StandardCharsets.UTF_8);
      p += n;
      return s;
    }

    boolean magic(String s) throws IOException {
      return text(4).equals(s);
    }
  }

  public static List<Cue> parse(byte[] data) throws IOException {
    return decode(data).cues;
  }

  public static Document decode(byte[] data) throws IOException {
    if (data.length > 16 * 1024 * 1024) throw new IOException("MIDI too large");
    Reader r = new Reader(data, 0, data.length);
    if (!r.magic("MThd")) throw new IOException("Not MIDI");
    long header = r.u32();
    if (header < 6 || header > 1024) throw new IOException("Invalid MIDI header");
    int format = r.u16(), tracks = r.u16(), ppq = r.u16();
    if (format > 1
        || tracks < 1
        || tracks > 256
        || ppq == 0
        || (ppq & 0x8000) != 0
        || (format == 0 && tracks != 1)) throw new IOException("Unsupported MIDI timing");
    r.skip(header - 6);
    ArrayList<Event> events = new ArrayList<>();
    int seq = 0, total = 0;
    boolean keyFound = false, keyMinor = false;
    int keySharpsFlats = 0;
    ArrayList<Track> trackMetadata = new ArrayList<>();
    for (int track = 0; track < tracks; track++) {
      if (!r.magic("MTrk")) throw new IOException("Missing MIDI track");
      long length = r.u32();
      if (length > r.end - r.p) throw new IOException("Truncated MIDI track");
      Reader t = new Reader(data, r.p, r.p + (int) length);
      r.skip(length);
      long tick = 0;
      int running = 0, channelMask = 0;
      String trackName = "", instrumentName = "";
      boolean trackLyrics = false;
      while (t.p < t.end) {
        if (++total > 250000) throw new IOException("Too many MIDI events");
        tick += t.vlq();
        if (tick > Integer.MAX_VALUE * 16L) throw new IOException("MIDI timeline too long");
        int status = t.u8();
        if (status < 128) {
          if (running == 0) throw new IOException("Invalid running status");
          t.p--;
          status = running;
        }
        if (status == 255) {
          running = 0;
          int type = t.u8();
          long n = t.vlq();
          if (n > t.end - t.p) throw new IOException("Truncated MIDI event");
          if (type == 0x51) {
            if (n != 3) throw new IOException("Invalid tempo");
            int tempo = (t.u8() << 16) | (t.u8() << 8) | t.u8();
            if (tempo <= 0) throw new IOException("Invalid tempo");
            events.add(new Event(tick, tempo, type, null, seq++, track));
          } else if (type == 1 || type == 5) {
            String text = t.text((int) n);
            if (!text.startsWith("@") && !text.isEmpty()) {
              events.add(new Event(tick, 0, type, text, seq++, track));
              if (type == 5 && !text.replace('/', ' ').replace('\\', ' ').trim().isEmpty())
                trackLyrics = true;
            }
          } else if (type == 3 || type == 4) {
            // Oversized optional labels must not break otherwise playable MIDI.
            if (n > 1024) t.skip(n);
            else {
              String name = t.text((int) n).trim();
              if (name.length() > 256) name = "";
              if (type == 3 && trackName.isEmpty()) trackName = name;
              if (type == 4 && instrumentName.isEmpty()) instrumentName = name;
            }
          } else if (type == 0x59) {
            // Key signature: a signed sharps/flats count and a mode flag. Every track tends to
            // repeat the same key, so the first one wins.
            if (n != 2) t.skip(n);
            else {
              int sharpsFlats = (byte) t.u8();
              int mode = t.u8();
              if (!keyFound) {
                keyFound = true;
                keySharpsFlats = sharpsFlats;
                keyMinor = mode != 0;
              }
            }
          } else t.skip(n);
          if (type == 0x2f) {
            events.add(new Event(tick, 0, type, null, seq++, track));
            break;
          }
        } else if (status == 0xf0 || status == 0xf7) {
          running = 0;
          t.skip(t.vlq());
        } else if (status >= 0x80 && status <= 0xef) {
          running = status;
          int n = (status & 0xe0) == 0xc0 ? 1 : 2;
          int a = t.u8(), b = n == 2 ? t.u8() : 0;
          if (a > 127 || b > 127) throw new IOException("Invalid MIDI data");
          Event message = new Event(tick, 0, 0, null, seq++, track);
          message.status = status;
          message.a = a;
          message.b = b;
          if ((status & 0xf0) == 0x90 && b > 0) channelMask |= 1 << (status & 15);
          events.add(message);
        } else throw new IOException("Unsupported MIDI status");
      }
      trackMetadata.add(new Track(track, channelMask, trackName, instrumentName, trackLyrics));
    }
    events.sort(Comparator.comparingLong((Event e) -> e.tick).thenComparingInt(e -> e.sequence));
    boolean lyrics = events.stream().anyMatch(e -> e.type == 5);
    ArrayList<Cue> cues = new ArrayList<>();
    ArrayList<Message> messages = new ArrayList<>();
    long last = 0;
    double micros = 0;
    int tempo = 500000;
    StringBuilder line = new StringBuilder();
    long lineStart = 0;
    ArrayList<Fragment> fragments = new ArrayList<>();
    final int lyricType = lyrics ? 5 : 1;
    boolean marked =
        events.stream()
            .filter(e -> e.type == lyricType && e.text != null)
            .anyMatch(
                e ->
                    e.text.indexOf('/') >= 0
                        || e.text.indexOf('\\') >= 0
                        || e.text.indexOf('\n') >= 0
                        || e.text.indexOf('\r') >= 0);
    for (Event e : events) {
      micros += (e.tick - last) * (double) tempo / ppq;
      last = e.tick;
      if (micros > 6L * 60 * 60 * 1000000) throw new IOException("MIDI exceeds six hours");
      if (e.status != 0) {
        messages.add(new Message((long) (micros / 1000), e.status, e.a, e.b, e.track));
        continue;
      }
      if (e.tempo > 0) {
        tempo = e.tempo;
        continue;
      }
      if (e.type != (lyrics ? 5 : 1)) continue;
      String raw = e.text;
      if (!marked && line.length() > 0) {
        cues.add(new Cue(lineStart, line.toString(), fragments));
        line.setLength(0);
        fragments.clear();
      }
      long time = (long) (micros / 1000);
      for (int i = 0; i < raw.length(); ) {
        char value = raw.charAt(i);
        if (value == '/' || value == '\\' || value == '\n' || value == '\r') {
          if (line.length() > 0 && !line.toString().trim().isEmpty())
            cues.add(new Cue(lineStart, line.toString(), fragments));
          line.setLength(0);
          fragments.clear();
          i++;
        } else {
          int end = i + 1;
          while (end < raw.length()
              && raw.charAt(end) != '/'
              && raw.charAt(end) != '\\'
              && raw.charAt(end) != '\n'
              && raw.charAt(end) != '\r') end++;
          if (line.length() == 0) lineStart = time;
          if (line.length() + end - i > 4096) throw new IOException("Lyric line too long");
          line.append(raw, i, end);
          fragments.add(new Fragment(time, line.length()));
          i = end;
        }
      }
    }
    if (line.length() > 0 && !line.toString().trim().isEmpty())
      cues.add(new Cue(lineStart, line.toString(), fragments));
    return new Document(
        cues, messages, trackMetadata, (long) (micros / 1000), keyFound, keySharpsFlats, keyMinor);
  }

  public static int activeIndex(List<Cue> cues, long milliseconds) {
    int low = 0, high = cues.size() - 1, result = -1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (cues.get(mid).milliseconds <= milliseconds) {
        result = mid;
        low = mid + 1;
      } else high = mid - 1;
    }
    return result;
  }
}
