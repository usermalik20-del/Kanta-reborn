package ph.kanta.reborn.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.util.*;

/**
 * SMF 0/1 parser without fixed file-size, event-count or duration caps, so
 * arbitrarily large MIDI and KAR files can be imported and played when the
 * device has memory to process them. Structural validity is still enforced
 * (SMF format 0/1, 1-256 tracks, nonzero PPQ, well-formed events); only
 * individual text events are memory-bounded.
 *
 * <p>Karaoke extensions: lyric/text encoding detection (strict UTF-8, then
 * Shift_JIS / GBK / Big5 by CJK density, then windows-1252), KAR metadata
 * tags ({@code @T} title, {@code @A} artist, {@code @C} copyright),
 * {@code @P} lyric pause codes (standalone or inline) and backslash
 * {@code \} echo (backing) lines surfaced on {@link Cue}s.
 */
public final class MidiLyrics {
  /** Per-text-event memory bound (not a file cap); prevents one runaway string. */
  private static final int MAX_TEXT_EVENT_BYTES = 16 * 1024 * 1024;

  public static final class Cue {
    public final long milliseconds;
    public final String line;
    public final List<Fragment> fragments;

    /** True when the KAR backslash convention marks this as an echo/backing line. */
    public final boolean echo;

    Cue(long ms, String text, List<Fragment> source) {
      this(ms, text, source, false);
    }

    Cue(long ms, String text, List<Fragment> source, boolean echo) {
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
      this.echo = echo;
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

    /** KAR {@code @T} metadata when present, otherwise empty. */
    public final String title;

    /** KAR {@code @A} metadata when present, otherwise empty. */
    public final String artist;

    /** KAR {@code @C} metadata when present, otherwise empty. */
    public final String copyright;

    Document(
        List<Cue> cues,
        List<Message> messages,
        List<Track> tracks,
        long duration,
        boolean keyPresent,
        int keySharpsFlats,
        boolean keyMinor) {
      this(
          cues, messages, tracks, duration, keyPresent, keySharpsFlats, keyMinor, "", "", "");
    }

    Document(
        List<Cue> cues,
        List<Message> messages,
        List<Track> tracks,
        long duration,
        boolean keyPresent,
        int keySharpsFlats,
        boolean keyMinor,
        String title,
        String artist,
        String copyright) {
      this.cues = Collections.unmodifiableList(cues);
      this.messages = Collections.unmodifiableList(messages);
      this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
      durationMilliseconds = duration;
      this.keyPresent = keyPresent;
      this.keySharpsFlats = keySharpsFlats;
      this.keyMinor = keyMinor;
      this.title = title == null ? "" : title;
      this.artist = artist == null ? "" : artist;
      this.copyright = copyright == null ? "" : copyright;
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
  }/** Buffered sequential reader over a slice; one TextCharset per document. */
  private static final class Reader {
    final ByteBuffer b;
    final TextCharset textCharset;

    Reader(ByteBuffer buffer, TextCharset textCharset) {
      b = buffer;
      this.textCharset = textCharset;
    }

    int remaining() {
      return b.remaining();
    }

    int u8() throws IOException {
      if (!b.hasRemaining()) throw new IOException("Truncated MIDI");
      return b.get() & 255;
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
      if (n < 0 || n > b.remaining()) throw new IOException("Invalid MIDI chunk");
      b.position(b.position() + (int) n);
    }

    String text(int n) throws IOException {
      if (n < 0 || n > b.remaining() || n > MAX_TEXT_EVENT_BYTES)
        throw new IOException("Invalid MIDI text");
      byte[] bytes = new byte[n];
      b.get(bytes);
      return textCharset.decode(bytes);
    }

    boolean magic(String expect) throws IOException {
      if (expect.length() > b.remaining()) return false;
      for (int i = 0; i < expect.length(); i++) if (b.get() != expect.charAt(i)) return false;
      return true;
    }
  }

  /**
   * Per-document lyric/text charset: strict UTF-8 first; when that fails the
   * CJK candidates are scored by decoded CJK/Hangul density; anything else
   * falls back to windows-1252. The first text event selects the charset for
   * the whole document, which matches how KAR files are written.
   */
  private static final class TextCharset {
    private Charset active;

    String decode(byte[] bytes) {
      return new String(bytes, charsetFor(bytes));
    }

    private Charset charsetFor(byte[] bytes) {
      if (active != null) return active;
      Charset chosen = null;
      if (strict(StandardCharsets.UTF_8, bytes)) {
        chosen = StandardCharsets.UTF_8;
      } else {
        int best = 0;
        for (String name : new String[] {"Shift_JIS", "GBK", "Big5", "CP949"}) {
          Charset candidate;
          try {
            candidate = Charset.forName(name);
          } catch (Exception unavailable) {
            continue;
          }
          if (!strict(candidate, bytes)) continue;
          int score = cjkScore(candidate, bytes);
          if (score > best) {
            best = score;
            chosen = candidate;
          }
        }
      }
      if (chosen == null) chosen = Charset.forName("windows-1252");
      active = chosen;
      return chosen;
    }

    private static boolean strict(Charset charset, byte[] bytes) {
      try {
        charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes));
        return true;
      } catch (CharacterCodingException e) {
        return false;
      }
    }

    private static int cjkScore(Charset charset, byte[] bytes) {
      CharBuffer decoded = charset.decode(ByteBuffer.wrap(bytes));
      int score = 0;
      for (int i = 0; i < decoded.length(); i++) {
        char c = decoded.charAt(i);
        if ((c >= 0x2E80 && c <= 0x9FFF) || (c >= 0xAC00 && c <= 0xD7AF)) score++;
      }
      return score;
    }
  }

  /**
   * KAR metadata accumulator: {@code @T} title, {@code @A} artist,
   * {@code @C} copyright and {@code @P} lyric pause codes. A tag is a text
   * segment starting with {@code @}, tagged either {@code @T value} or
   * {@code @T:value}. Pause values without a decimal point are hundredths of
   * a second; with a decimal point they are seconds. Pauses shift subsequent
   * lyric cues only (the music timeline is untouched).
   */
  private static final class KaraokeMeta {
    String title = "", artist = "", copyright = "";
    long pauseMillis;

    boolean consume(String text) {
      if (text == null || text.isEmpty() || text.charAt(0) != '@') return false;
      String tag = text.substring(1);
      int space = tag.indexOf(' ');
      int colon = tag.indexOf(':');
      int cut = Math.min(
          space < 0 ? Integer.MAX_VALUE : space, colon < 0 ? Integer.MAX_VALUE : colon);
      String key, value;
      if (cut == Integer.MAX_VALUE) {
        key = tag;
        value = "";
      } else {
        key = tag.substring(0, cut);
        value = tag.substring(cut + 1).trim();
      }
      if (key.equals("T")) {
        if (title.isEmpty()) title = value;
      } else if (key.equals("A")) {
        if (artist.isEmpty()) artist = value;
      } else if (key.equals("C")) {
        if (copyright.isEmpty()) copyright = value;
      } else if (!key.isEmpty() && (key.charAt(0) == 'P' || key.charAt(0) == 'p')) {
        // @Pnn, @P nn, @P:nn and @P0.5 forms are all supported.
        parsePause(key.substring(1));
      }
      // @L/@k language, @W writer and @V version are recognized and ignored.
      return true;
    }

    private void parsePause(String value) {
      if (value.isEmpty()) return;
      // Tolerate trailing non-numeric text by parsing the leading number only.
      int end = 0;
      while (end < value.length()
          && (Character.isDigit(value.charAt(end)) || value.charAt(end) == '.')) end++;
      String number = value.substring(0, end);
      if (number.isEmpty()) return;
      double seconds;
      try {
        if (number.indexOf('.') >= 0) {
          seconds = Double.parseDouble(number);
        } else {
          seconds = Long.parseLong(number) / 100.0;
        }
      } catch (NumberFormatException malformed) {
        return;
      }
      if (!(seconds >= 0) || seconds > 60) return;
      pauseMillis += (long) (seconds * 1000);
    }
  }

  /** Parses in-memory MIDI bytes. */
  public static List<Cue> parse(byte[] data) throws IOException {
    return decode(data).cues;
  }

  /** Parses a MIDI/KAR file. */
  public static List<Cue> parse(File file) throws IOException {
    return decode(file).cues;
  }

  /** Decodes in-memory MIDI bytes. */
  public static Document decode(byte[] data) throws IOException {
    return decode(ByteBuffer.wrap(data));
  }

  /**
   * Decodes a MIDI/KAR file without materializing the whole file in the heap:
   * large files are mapped read-only, so a multi-hundred-megabyte import only
   * consumes address space plus the bounded events/strings it actually has.
   */
  public static Document decode(File file) throws IOException {
    try (FileChannel channel = new FileInputStream(file).getChannel()) {
      long size = channel.size();
      if (size > Integer.MAX_VALUE)
        throw new IOException("MIDI too large to map on this device");
      return decode(channel.map(FileChannel.MapMode.READ_ONLY, 0, size));
    }
  }

  private static Document decode(ByteBuffer data) throws IOException {
    Reader r = new Reader(data, new TextCharset());
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
    int seq = 0;
    boolean keyFound = false, keyMinor = false;
    int keySharpsFlats = 0;
    ArrayList<Track> trackMetadata = new ArrayList<>();
    for (int track = 0; track < tracks; track++) {
      if (!r.magic("MTrk")) throw new IOException("Missing MIDI track");
      long length = r.u32();
      if (length > r.remaining()) throw new IOException("Truncated MIDI track");
      // A sub-reader over exactly this track's bytes keeps the master reader aligned.
      ByteBuffer trackBytes = data.duplicate();
      trackBytes.position(data.position());
      trackBytes.limit(data.position() + (int) length);
      Reader t = new Reader(trackBytes, r.textCharset);
      r.skip(length);
      long tick = 0;
      int running = 0, channelMask = 0;
      String trackName = "", instrumentName = "";
      boolean trackLyrics = false;
      while (t.b.hasRemaining()) {
        tick += t.vlq();
        int status = t.u8();
        if (status < 128) {
          if (running == 0) throw new IOException("Invalid running status");
          t.b.position(t.b.position() - 1);
          status = running;
        }
        if (status == 255) {
          running = 0;
          int type = t.u8();
          long n = t.vlq();
          if (n > t.remaining()) throw new IOException("Truncated MIDI event");
          if (type == 0x51) {
            if (n != 3) throw new IOException("Invalid tempo");
            int tempo = (t.u8() << 16) | (t.u8() << 8) | t.u8();
            if (tempo <= 0) throw new IOException("Invalid tempo");
            events.add(new Event(tick, tempo, type, null, seq++, track));
          } else if (type == 1 || type == 5) {
            String text = t.text((int) n);
            if (!text.isEmpty()) {
              events.add(new Event(tick, 0, type, text, seq++, track));
              if (type == 5
                  && !text.startsWith("@")
                  && !text.replace('/', ' ').replace('\\', ' ').trim().isEmpty())
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
    boolean lyrics =
        events.stream()
            .anyMatch(e -> e.type == 5 && (e.text == null || !e.text.startsWith("@")));
    KaraokeMeta meta = new KaraokeMeta();
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
            .filter(e -> e.type == lyricType && e.text != null && !e.text.startsWith("@"))
            .anyMatch(
                e ->
                    e.text.indexOf('/') >= 0
                        || e.text.indexOf('\\') >= 0
                        || e.text.indexOf('\n') >= 0
                        || e.text.indexOf('\r') >= 0);
    boolean lineEcho = false;
    for (Event e : events) {
      micros += (e.tick - last) * (double) tempo / ppq;
      last = e.tick;
      if (e.status != 0) {
        messages.add(new Message((long) (micros / 1000), e.status, e.a, e.b, e.track));
        continue;
      }
      if (e.tempo > 0) {
        tempo = e.tempo;
        continue;
      }
      if (e.type != lyricType || e.text == null || e.text.isEmpty()) continue;
      String raw = e.text;
      if (!marked && line.length() > 0) {
        cues.add(new Cue(lineStart, line.toString(), fragments, lineEcho));
        line.setLength(0);
        fragments.clear();
        lineEcho = false;
      }
      for (int i = 0; i < raw.length(); ) {
        char value = raw.charAt(i);
        if (value == '/' || value == '\\' || value == '\n' || value == '\r') {
          if (line.length() > 0 && !line.toString().trim().isEmpty())
            cues.add(new Cue(lineStart, line.toString(), fragments, lineEcho));
          line.setLength(0);
          fragments.clear();
          lineEcho = value == '\\';
          i++;
        } else if (value == '@') {
          // Inline KAR tag: consumed to the next marker or the end of the event.
          int end = i + 1;
          while (end < raw.length()
              && raw.charAt(end) != '/'
              && raw.charAt(end) != '\\'
              && raw.charAt(end) != '\n'
              && raw.charAt(end) != '\r') end++;
          meta.consume(raw.substring(i, end));
          i = end;
        } else {
          int end = i + 1;
          while (end < raw.length()
              && raw.charAt(end) != '/'
              && raw.charAt(end) != '\\'
              && raw.charAt(end) != '\n'
              && raw.charAt(end) != '\r'
              && raw.charAt(end) != '@') end++;
          if (line.length() == 0) lineStart = (long) (micros / 1000) + meta.pauseMillis;
          line.append(raw, i, end);
          fragments.add(new Fragment((long) (micros / 1000) + meta.pauseMillis, line.length()));
          i = end;
        }
      }
    }
    if (line.length() > 0 && !line.toString().trim().isEmpty())
      cues.add(new Cue(lineStart, line.toString(), fragments, lineEcho));
    return new Document(
        cues,
        messages,
        trackMetadata,
        (long) (micros / 1000),
        keyFound,
        keySharpsFlats,
        keyMinor,
        meta.title,
        meta.artist,
        meta.copyright);
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