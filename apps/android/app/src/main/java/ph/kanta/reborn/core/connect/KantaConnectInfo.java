package ph.kanta.reborn.core.connect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything a Kanta-Connect client needs to reach this device.
 *
 * <p>Framework-free so the host-JVM {@code coreRegression} task can exercise the link
 * format, code normalisation and host selection. The Android layer only gathers the
 * candidate addresses and draws the QR image.
 *
 * <p>The link is the single source of truth: the QR code encodes {@link #link()} and the
 * on-screen instructions show the same host, port and code, so a user can either scan or
 * type. Pairing codes are never persisted and never logged.
 */
public final class KantaConnectInfo {

  /** URI scheme the phone app recognises. */
  public static final String SCHEME = "kanta-connect";

  /** Default Kanta-Connect port. */
  public static final int DEFAULT_PORT = 45731;

  /** Link format version, so a future client can refuse a format it does not understand. */
  public static final int PROTOCOL_VERSION = 1;

  /** Length of a pairing code in hex characters. */
  public static final int CODE_LENGTH = 32;

  public final String host;
  public final int port;
  public final String code;
  public final int version;

  public KantaConnectInfo(String host, int port, String code, int version) {
    if (host == null || host.trim().isEmpty()) {
      throw new IllegalArgumentException("host is required");
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("port out of range: " + port);
    }
    String normalised = normalizeCode(code);
    if (normalised == null) {
      throw new IllegalArgumentException("pairing code must be " + CODE_LENGTH + " hex characters");
    }
    this.host = host.trim();
    this.port = port;
    this.code = normalised;
    this.version = version;
  }

  /** Convenience constructor with the current protocol version. */
  public static KantaConnectInfo of(String host, String code) {
    return new KantaConnectInfo(host, DEFAULT_PORT, code, PROTOCOL_VERSION);
  }

  /** The exact string the QR code encodes. */
  public String link() {
    return SCHEME + "://" + host + ":" + port + "?code=" + code + "&v=" + version;
  }

  /** The pairing code in four groups of eight, for reading aloud or typing. */
  public String groupedCode() {
    return groupedCode(code);
  }

  /** The host and port as a user would type them. */
  public String endpoint() {
    return host + ":" + port;
  }

  /**
   * Human-readable connection steps. Deliberately plain: the host, the port, the code and
   * what to do, in the order a person needs them.
   */
  public List<String> instructions() {
    List<String> steps = new ArrayList<>(5);
    steps.add("Open Kanta-Connect on the phone.");
    steps.add("Scan the QR code, or enter this address: " + endpoint());
    steps.add("Enter the pairing code: " + groupedCode());
    steps.add("Keep both devices on the same Wi-Fi network.");
    steps.add("The code is single-session. Stop hosting to revoke it.");
    return steps;
  }

  /** Parses a link produced by {@link #link()}. */
  public static KantaConnectInfo parse(String link) {
    if (link == null) throw new IllegalArgumentException("link is required");
    String prefix = SCHEME + "://";
    if (!link.startsWith(prefix)) {
      throw new IllegalArgumentException("unsupported scheme");
    }
    String rest = link.substring(prefix.length());
    int queryAt = rest.indexOf('?');
    if (queryAt < 0) throw new IllegalArgumentException("missing query");
    String authority = rest.substring(0, queryAt);
    String query = rest.substring(queryAt + 1);

    int colon = authority.lastIndexOf(':');
    if (colon <= 0 || colon == authority.length() - 1) {
      throw new IllegalArgumentException("missing port");
    }
    String host = authority.substring(0, colon);
    int port;
    try {
      port = Integer.parseInt(authority.substring(colon + 1));
    } catch (NumberFormatException notANumber) {
      throw new IllegalArgumentException("port is not a number");
    }

    String code = null;
    int version = PROTOCOL_VERSION;
    for (String pair : query.split("&")) {
      if (pair.isEmpty()) continue;
      int equals = pair.indexOf('=');
      if (equals <= 0) continue;
      String key = pair.substring(0, equals);
      String value = pair.substring(equals + 1);
      if ("code".equals(key)) {
        code = value;
      } else if ("v".equals(key)) {
        try {
          version = Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
          throw new IllegalArgumentException("version is not a number");
        }
      }
    }
    if (code == null) throw new IllegalArgumentException("missing code");
    return new KantaConnectInfo(host, port, code, version);
  }

  /** Lower-cases and strips separators from a typed code; null when it is not valid. */
  public static String normalizeCode(String raw) {
    if (raw == null) return null;
    StringBuilder cleaned = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == ' ' || c == '-' || c == ':' || c == '.') continue;
      char lower = Character.toLowerCase(c);
      if ((lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f')) {
        cleaned.append(lower);
      } else {
        return null;
      }
    }
    return cleaned.length() == CODE_LENGTH ? cleaned.toString() : null;
  }

  /** Groups a hex code into blocks of eight for display. */
  public static String groupedCode(String code) {
    String normalised = normalizeCode(code);
    if (normalised == null) return code == null ? "" : code;
    StringBuilder out = new StringBuilder(CODE_LENGTH + 3);
    for (int i = 0; i < normalised.length(); i += 8) {
      if (i > 0) out.append(' ');
      out.append(normalised, i, Math.min(i + 8, normalised.length()));
    }
    return out.toString();
  }

  /** True when {@code host} is an IPv4 literal inside a private range. */
  public static boolean isPrivateIpv4(String host) {
    int[] octets = parseIpv4(host);
    if (octets == null) return false;
    int a = octets[0];
    int b = octets[1];
    if (a == 10) return true;
    if (a == 172 && b >= 16 && b <= 31) return true;
    return a == 192 && b == 168;
  }

  /**
   * Picks the address to advertise from the device's candidates.
   *
   * <p>Home and small-office networks use {@code 192.168/16}, so those are preferred: a
   * device can hold several private addresses (VPN, hotspot, container) and the one a
   * companion can actually reach is nearly always the Wi-Fi LAN address. Ties fall back to
   * the first candidate, and {@code null} is returned when nothing usable is present.
   */
  public static String chooseHost(List<String> candidates) {
    if (candidates == null || candidates.isEmpty()) return null;
    String best = null;
    int bestRank = Integer.MAX_VALUE;
    for (String candidate : candidates) {
      if (!isPrivateIpv4(candidate)) continue;
      int[] octets = parseIpv4(candidate);
      int rank;
      if (octets[0] == 192 && octets[1] == 168) rank = 0;
      else if (octets[0] == 10) rank = 1;
      else rank = 2;
      if (rank < bestRank) {
        bestRank = rank;
        best = candidate;
      }
    }
    return best;
  }

  /** Returns the four octets of an IPv4 literal, or null when it is not one. */
  public static int[] parseIpv4(String host) {
    if (host == null) return null;
    String trimmed = host.trim();
    String[] parts = trimmed.split("\\.", -1);
    if (parts.length != 4) return null;
    int[] octets = new int[4];
    for (int i = 0; i < 4; i++) {
      String part = parts[i];
      if (part.isEmpty() || part.length() > 3) return null;
      if (part.length() > 1 && part.charAt(0) == '0') return null; // reject 010 style
      for (int c = 0; c < part.length(); c++) {
        if (part.charAt(c) < '0' || part.charAt(c) > '9') return null;
      }
      int value = Integer.parseInt(part);
      if (value > 255) return null;
      octets[i] = value;
    }
    return octets;
  }

  /**
   * Renders an IPv4 with its last octet masked, for screenshots and support reports.
   * The full address is still shown on screen; this is only for anything shareable.
   */
  public static String maskedForSharing(String ipv4) {
    int[] octets = parseIpv4(ipv4);
    if (octets == null) return hostMask(ipv4);
    return octets[0] + "." + octets[1] + "." + octets[2] + ".x";
  }

  private static String hostMask(String host) {
    if (host == null) return "";
    int dot = host.indexOf('.');
    return dot < 0 ? host : host.substring(0, dot) + ".x";
  }

  @Override
  public String toString() {
    return String.format(Locale.ROOT, "KantaConnectInfo[%s, v%d]", endpoint(), version);
  }
}
