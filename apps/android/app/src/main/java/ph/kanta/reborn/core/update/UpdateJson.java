package ph.kanta.reborn.core.update;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict, bounded JSON reader for the update feed. Framework-free: it compiles and runs on an
 * empty classpath so the parser is exercised by {@code coreRegression} before any emulator time.
 *
 * <p>The feed is fetched over the network and is therefore untrusted input that the app will act
 * on. Every documented limit exists to bound a hostile or truncated document rather than to
 * reject a plausible one:
 *
 * <ul>
 *   <li>input size and nesting depth are capped, so a decompression bomb or a pathological file
 *       cannot exhaust memory or the call stack;
 *   <li>duplicate object members are rejected — two {@code versionCode} keys make the effective
 *       update policy ambiguous, and the first/last winner differs between parsers;
 *   <li>numbers are kept as {@link BigDecimal} literals, so a {@code versionCode} or
 *       {@code apkBytes} that silently loses precision as a {@code double} cannot be laundered;
 *   <li>raw control characters, malformed UTF-8, unpaired surrogate code units and non-finite
 *       numbers are rejected, because they end up in on-screen release notes;
 *   <li>trailing content after one document is rejected.
 * </ul>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8259">RFC 8259</a>
 */
public final class UpdateJson {

  /** Largest accepted document. The published feed is well under one kilobyte. */
  public static final int MAX_BYTES = 256 * 1024;

  /** Deepest accepted container. The published feed is two levels deep. */
  public static final int MAX_DEPTH = 8;

  /** Largest accepted object or array, so one document cannot exhaust memory. */
  public static final int MAX_MEMBERS = 512;

  /** Signals a document that is not valid JSON within the limits above. */
  public static final class Malformed extends Exception {
    private static final long serialVersionUID = 1L;

    Malformed(String message) {
      super(message);
    }
  }

  private final String text;
  private int position;

  private UpdateJson(String text) {
    this.text = text;
  }

  /**
   * Decodes UTF-8 bytes strictly: a byte order mark, an incomplete sequence or an overlong
   * encoding is a malformed feed rather than something to guess about.
   */
  public static String decode(byte[] utf8) throws Malformed {
    if (utf8 == null) throw new Malformed("missing feed bytes");
    if (utf8.length == 0) throw new Malformed("empty feed");
    if (utf8.length > MAX_BYTES) throw new Malformed("feed larger than " + MAX_BYTES + " bytes");
    if (utf8.length >= 3 && (utf8[0] & 0xff) == 0xef && (utf8[1] & 0xff) == 0xbb
        && (utf8[2] & 0xff) == 0xbf) {
      throw new Malformed("feed starts with a byte order mark");
    }
    CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return decoder.decode(ByteBuffer.wrap(utf8)).toString();
    } catch (CharacterCodingException invalid) {
      throw new Malformed("feed is not valid UTF-8");
    }
  }

  /** Parses one JSON value. Numbers arrive as {@link BigDecimal}, objects as {@link Map}. */
  public static Object parse(String document) throws Malformed {
    if (document == null) throw new Malformed("missing feed text");
    if (document.length() > MAX_BYTES) throw new Malformed("feed larger than " + MAX_BYTES + " characters");
    UpdateJson reader = new UpdateJson(document);
    reader.skipWhitespace();
    Object value = reader.readValue(0);
    reader.skipWhitespace();
    if (reader.position != document.length()) throw reader.error("trailing content after document");
    return value;
  }

  /** Parses a document whose top level must be an object. */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> parseObject(String document) throws Malformed {
    Object value = parse(document);
    if (!(value instanceof Map)) throw new Malformed("feed must be a JSON object");
    return (Map<String, Object>) value;
  }

  private Object readValue(int depth) throws Malformed {
    if (depth > MAX_DEPTH) throw error("nesting deeper than " + MAX_DEPTH);
    if (position >= text.length()) throw error("unexpected end of feed");
    char c = text.charAt(position);
    switch (c) {
      case '{':
        return readObject(depth);
      case '[':
        return readArray(depth);
      case '"':
        return readString();
      case 't':
        expect("true");
        return Boolean.TRUE;
      case 'f':
        expect("false");
        return Boolean.FALSE;
      case 'n':
        expect("null");
        return null;
      default:
        if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
        throw error("unexpected character '" + c + "'");
    }
  }

  private Map<String, Object> readObject(int depth) throws Malformed {
    position++; // {
    Map<String, Object> members = new LinkedHashMap<>();
    skipWhitespace();
    if (at('}')) {
      position++;
      return members;
    }
    while (true) {
      if (members.size() >= MAX_MEMBERS) throw error("object with more than " + MAX_MEMBERS + " members");
      skipWhitespace();
      if (!at('"')) throw error("object key must be a string");
      String key = readString();
      skipWhitespace();
      if (!at(':')) throw error("expected ':' after key \"" + key + "\"");
      position++;
      skipWhitespace();
      Object value = readValue(depth + 1);
      if (members.put(key, value) != null) throw error("duplicate key \"" + key + "\"");
      skipWhitespace();
      if (at(',')) {
        position++;
        continue;
      }
      if (at('}')) {
        position++;
        return members;
      }
      throw error("expected ',' or '}' in object");
    }
  }

  private List<Object> readArray(int depth) throws Malformed {
    position++; // [
    List<Object> items = new ArrayList<>();
    skipWhitespace();
    if (at(']')) {
      position++;
      return items;
    }
    while (true) {
      if (items.size() >= MAX_MEMBERS) throw error("array with more than " + MAX_MEMBERS + " items");
      skipWhitespace();
      items.add(readValue(depth + 1));
      skipWhitespace();
      if (at(',')) {
        position++;
        continue;
      }
      if (at(']')) {
        position++;
        return items;
      }
      throw error("expected ',' or ']' in array");
    }
  }

  /** Reads a string, rejecting raw control characters and lone surrogate code units. */
  private String readString() throws Malformed {
    position++; // opening quote
    StringBuilder out = new StringBuilder();
    while (true) {
      if (position >= text.length()) throw error("unterminated string");
      char c = text.charAt(position++);
      if (c == '"') {
        requirePairedSurrogates(out);
        return out.toString();
      }
      if (c == '\\') {
        if (position >= text.length()) throw error("unterminated escape");
        char escape = text.charAt(position++);
        switch (escape) {
          case '"':
          case '\\':
          case '/':
            out.append(escape);
            break;
          case 'b':
            out.append('\b');
            break;
          case 'f':
            out.append('\f');
            break;
          case 'n':
            out.append('\n');
            break;
          case 'r':
            out.append('\r');
            break;
          case 't':
            out.append('\t');
            break;
          case 'u':
            out.append(readUnicodeEscape());
            break;
          default:
            throw error("invalid escape '\\" + escape + "'");
        }
        continue;
      }
      if (c < 0x20) throw error("raw control character 0x" + Integer.toHexString(c) + " in string");
      out.append(c);
    }
  }

  private char readUnicodeEscape() throws Malformed {
    if (position + 4 > text.length()) throw error("truncated \\u escape");
    String hex = text.substring(position, position + 4);
    position += 4;
    for (int i = 0; i < hex.length(); i++) {
      if (Character.digit(hex.charAt(i), 16) < 0) throw error("invalid hex digit in \\u" + hex);
    }
    return (char) Integer.parseInt(hex, 16);
  }

  /**
   * Rejects a string carrying a lone surrogate. A {@code \uD83D} with no low partner, or a
   * reordered pair, is well-formed JSON but cannot be rendered or re-encoded safely.
   */
  private void requirePairedSurrogates(CharSequence value) throws Malformed {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
          throw error("unpaired surrogate in string");
        }
        i++;
      } else if (Character.isLowSurrogate(c)) {
        throw error("unpaired surrogate in string");
      }
    }
  }

  /** Validates the RFC 8259 number grammar exactly; {@code -0}, {@code 01} and {@code 1.} are rejected. */
  private BigDecimal readNumber() throws Malformed {
    int start = position;
    if (at('-')) position++;
    if (position >= text.length()) throw error("truncated number");
    if (at('0')) {
      position++;
      if (position < text.length() && digit()) {
        throw error("number must not have a leading zero");
      }
    } else if (digit()) {
      while (position < text.length() && digit()) position++;
    } else {
      throw error("number must not start with a non-zero digit other than 1-9");
    }
    if (at('.')) {
      position++;
      int digits = 0;
      while (position < text.length() && digit()) {
        position++;
        digits++;
      }
      if (digits == 0) throw error("fraction with no digits");
    }
    if (at('e') || at('E')) {
      position++;
      if (at('+') || at('-')) position++;
      int digits = 0;
      while (position < text.length() && digit()) {
        position++;
        digits++;
      }
      if (digits == 0) throw error("exponent with no digits");
    }
    String literal = text.substring(start, position);
    if (literal.length() > 40) throw error("number is too long to interpret safely");
    try {
      return new BigDecimal(literal);
    } catch (NumberFormatException invalid) {
      throw error("number out of range: " + literal);
    }
  }

  private void expect(String literal) throws Malformed {
    if (!text.startsWith(literal, position)) throw error("expected " + literal);
    position += literal.length();
  }

  private boolean at(char c) {
    return position < text.length() && text.charAt(position) == c;
  }

  private boolean digit() {
    char c = text.charAt(position);
    return c >= '0' && c <= '9';
  }

  private void skipWhitespace() {
    while (position < text.length()) {
      char c = text.charAt(position);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        position++;
      } else {
        break;
      }
    }
  }

  private Malformed error(String message) {
    return new Malformed(message + " at offset " + position);
  }

  /** True when a value is a JSON object. */
  public static boolean isObject(Object value) {
    return value instanceof Map;
  }

  /** Reads a member of an object, or {@code null} when absent. */
  @SuppressWarnings("unchecked")
  public static Object member(Object object, String key) {
    if (!(object instanceof Map)) return null;
    return ((Map<String, Object>) object).get(key);
  }
}
