package ph.kanta.reborn.core.connect;

import java.util.Arrays;
import java.util.List;

/**
 * Domain regression checks for the Kanta-Connect link model.
 *
 * <p>Runs on a host JVM inside {@code coreRegression}. The link string is what the QR code
 * carries, so these checks pin the wire format as well as the parsing and address
 * selection the pairing UI depends on.
 */
public final class KantaConnectInfoRegressionTests {
  private static int checks;

  private static final String CODE = "0123456789abcdef0123456789abcdef";

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static void linkFormatIsStable() {
    KantaConnectInfo info = KantaConnectInfo.of("192.168.1.42", CODE);
    check(
        info.link().equals("kanta-connect://192.168.1.42:45731?code=" + CODE + "&v=1"),
        "Link format is stable: " + info.link());
    check(info.endpoint().equals("192.168.1.42:45731"), "Endpoint reads host and port");
    check(info.port == KantaConnectInfo.DEFAULT_PORT, "Default port is used");
    check(info.version == 1, "Protocol version is 1");
  }

  private static void linkFitsAComfortableQrSymbol() {
    KantaConnectInfo info = KantaConnectInfo.of("192.168.100.200", CODE);
    int bytes = info.link().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    check(bytes <= 80, "Link is " + bytes + " bytes, small enough for a low-density QR");
    QrEncoder.Symbol symbol = QrEncoder.encode(info.link());
    check(symbol.version <= 5, "Encoded link uses version " + symbol.version + " or smaller");
  }

  private static void linkRoundTripsThroughParsing() {
    KantaConnectInfo original = new KantaConnectInfo("10.0.0.7", 45731, CODE, 1);
    KantaConnectInfo parsed = KantaConnectInfo.parse(original.link());
    check(parsed.host.equals(original.host), "Host round-trips");
    check(parsed.port == original.port, "Port round-trips");
    check(parsed.code.equals(original.code), "Code round-trips");
    check(parsed.version == original.version, "Version round-trips");
    check(parsed.link().equals(original.link()), "Re-encoding is byte-identical");
  }

  private static void malformedLinksAreRejected() {
    String[] malformed = {
      "http://192.168.1.42:45731?code=" + CODE + "&v=1", // wrong scheme
      "kanta-connect://192.168.1.42?code=" + CODE,        // no port
      "kanta-connect://192.168.1.42:45731",               // no query
      "kanta-connect://192.168.1.42:45731?v=1",           // no code
      "kanta-connect://192.168.1.42:abc?code=" + CODE,    // port not a number
      "kanta-connect://192.168.1.42:45731?code=" + CODE + "&v=x", // version not a number
      "kanta-connect://:45731?code=" + CODE,              // empty host
      "kanta-connect://192.168.1.42:45731?code=short",    // code too short
    };
    for (String bad : malformed) {
      boolean rejected = false;
      try {
        KantaConnectInfo.parse(bad);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
      check(rejected, "Rejected malformed link: " + bad);
    }
    boolean rejected = false;
    try {
      KantaConnectInfo.parse(null);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    check(rejected, "Rejected a null link");
  }

  private static void pairingCodesAreNormalisedForTyping() {
    String spaced = "0123 4567 89ab cdef 0123 4567 89ab cdef";
    check(
        KantaConnectInfo.normalizeCode(spaced).equals(CODE),
        "Spaces are stripped from a typed code");
    check(
        KantaConnectInfo.normalizeCode("0123-4567-89ab-cdef-0123-4567-89ab-cdef").equals(CODE),
        "Dashes are stripped from a typed code");
    check(
        KantaConnectInfo.normalizeCode("0123456789ABCDEF0123456789ABCDEF").equals(CODE),
        "Upper case is accepted and lower-cased");
    check(
        KantaConnectInfo.normalizeCode("0123.4567.89ab.cdef.0123.4567.89ab.cdef").equals(CODE),
        "Dots are stripped from a typed code");
    check(KantaConnectInfo.normalizeCode("0123 4567") == null, "A short code is rejected");
    check(KantaConnectInfo.normalizeCode("z".repeat(32)) == null, "A non-hex code is rejected");
    check(KantaConnectInfo.normalizeCode(null) == null, "A null code is rejected");

    check(
        KantaConnectInfo.groupedCode(CODE).equals("01234567 89abcdef 01234567 89abcdef"),
        "Code is grouped in fours of eight: " + KantaConnectInfo.groupedCode(CODE));
    KantaConnectInfo info = KantaConnectInfo.of("192.168.1.42", CODE);
    check(info.groupedCode().equals(KantaConnectInfo.groupedCode(CODE)), "Instance grouping matches");
  }

  private static void constructorRejectsUnusableInput() {
    check(rejects(null, 45731, CODE, 1), "Empty host is rejected");
    check(rejects("  ", 45731, CODE, 1), "Blank host is rejected");
    check(rejects("192.168.1.42", 0, CODE, 1), "Port 0 is rejected");
    check(rejects("192.168.1.42", 65536, CODE, 1), "Port above 65535 is rejected");
    check(rejects("192.168.1.42", -1, CODE, 1), "Negative port is rejected");
    check(rejects("192.168.1.42", 45731, "nope", 1), "Invalid code is rejected");
  }

  private static boolean rejects(String host, int port, String code, int version) {
    try {
      new KantaConnectInfo(host, port, code, version);
      return false;
    } catch (IllegalArgumentException expected) {
      return true;
    }
  }

  private static void privateAddressDetectionIsStrict() {
    for (String priv : new String[] {"10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.1",
        "192.168.0.1", "192.168.255.1"}) {
      check(KantaConnectInfo.isPrivateIpv4(priv), priv + " is private");
    }
    for (String pub : new String[] {"8.8.8.8", "1.1.1.1", "172.15.0.1", "172.32.0.1",
        "192.169.0.1", "11.0.0.1", "127.0.0.1"}) {
      check(!KantaConnectInfo.isPrivateIpv4(pub), pub + " is not a private LAN address");
    }
    for (String bad : new String[] {"", "192.168.1", "192.168.1.1.1", "192.168.1.256",
        "192.168.1.-1", "010.0.0.1", "192.168.1.a", "kanta.local"}) {
      check(!KantaConnectInfo.isPrivateIpv4(bad), "Rejected malformed host: " + bad);
    }
  }

  private static void hostSelectionPrefersTheReachableLanAddress() {
    List<String> mixed =
        Arrays.asList("10.8.0.2", "192.168.1.42", "8.8.8.8", "172.20.0.5");
    check(
        "192.168.1.42".equals(KantaConnectInfo.chooseHost(mixed)),
        "A 192.168/16 address wins over VPN and public addresses");
    check(
        "10.8.0.2".equals(KantaConnectInfo.chooseHost(Arrays.asList("10.8.0.2", "172.20.0.5"))),
        "10/8 wins over 172.16/12");
    check(
        "172.20.0.5".equals(KantaConnectInfo.chooseHost(Arrays.asList("172.20.0.5", "8.8.8.8"))),
        "172.16/12 is used when it is the only private option");
    check(KantaConnectInfo.chooseHost(Arrays.asList("8.8.8.8", "1.1.1.1")) == null,
        "No private address yields null");
    check(KantaConnectInfo.chooseHost(Arrays.asList()) == null, "Empty candidate list yields null");
    check(KantaConnectInfo.chooseHost(null) == null, "Null candidate list yields null");
    check(
        "192.168.1.42".equals(
            KantaConnectInfo.chooseHost(Arrays.asList("192.168.1.42", "192.168.1.42"))),
        "Duplicate addresses are harmless");
  }

  private static void instructionsCoverBothRoutes() {
    KantaConnectInfo info = KantaConnectInfo.of("192.168.1.42", CODE);
    List<String> steps = info.instructions();
    check(steps.size() >= 4, "Instructions have enough steps to be actionable");
    String joined = String.join(" ", steps);
    check(joined.contains(info.endpoint()), "Instructions show the address to type");
    check(joined.contains(info.groupedCode()), "Instructions show the grouped pairing code");
    boolean mentionsScan = false;
    for (String step : steps) {
      if (step.toLowerCase(java.util.Locale.ROOT).contains("scan")) mentionsScan = true;
    }
    check(mentionsScan, "Instructions offer scanning as the primary route");
    boolean mentionsRevoke = false;
    for (String step : steps) {
      if (step.toLowerCase(java.util.Locale.ROOT).contains("revoke")) mentionsRevoke = true;
    }
    check(mentionsRevoke, "Instructions say how to revoke the code");
  }

  private static void shareableTextMasksTheLastOctet() {
    check(
        "192.168.1.x".equals(KantaConnectInfo.maskedForSharing("192.168.1.42")),
        "Last octet is masked for sharing");
    check(
        "kanta.x".equals(KantaConnectInfo.maskedForSharing("kanta.local")),
        "Host names are masked after the first label");
    check("".equals(KantaConnectInfo.maskedForSharing(null)), "Null masks to empty");
  }

  private static void toStringCarriesNoCode() {
    KantaConnectInfo info = KantaConnectInfo.of("192.168.1.42", CODE);
    check(!info.toString().contains(CODE), "toString must not leak the pairing code");
    check(info.toString().contains("192.168.1.42:45731"), "toString names the endpoint");
  }

  /** Runs every Kanta-Connect link regression check. */
  public static int run() {
    checks = 0;
    linkFormatIsStable();
    linkFitsAComfortableQrSymbol();
    linkRoundTripsThroughParsing();
    malformedLinksAreRejected();
    pairingCodesAreNormalisedForTyping();
    constructorRejectsUnusableInput();
    privateAddressDetectionIsStrict();
    hostSelectionPrefersTheReachableLanAddress();
    instructionsCoverBothRoutes();
    shareableTextMasksTheLastOctet();
    toStringCarriesNoCode();
    return checks;
  }
}
