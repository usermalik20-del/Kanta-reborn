package ph.kanta.reborn.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-app QR payload, not a URL: neither secret nor pin ever enters browser history. */
public final class BrowserPairingQr {
  private static final Pattern FORMAT = Pattern.compile(
      "KANTA-REMOTE-1:([0-9.]{7,15}):([a-f0-9]{64}):([a-f0-9]{32})");

  public final String address, fingerprint, code;

  private BrowserPairingQr(String address, String fingerprint, String code) {
    this.address = address;
    this.fingerprint = fingerprint;
    this.code = code;
  }

  public static BrowserPairingQr parse(String text) {
    if (text == null || text.length() > 128) return null;
    Matcher match = FORMAT.matcher(text);
    if (!match.matches()) return null;
    try {
      String address = match.group(1);
      byte[] ip = CompanionProtocol.privateAddress(address);
      String canonical = (ip[0] & 255) + "." + (ip[1] & 255) + "." + (ip[2] & 255) + "." + (ip[3] & 255);
      if (!canonical.equals(address)) return null;
      return new BrowserPairingQr(address, match.group(2), match.group(3));
    } catch (java.io.IOException e) {
      return null;
    }
  }

  public static String encode(String address, String fingerprint, String code) {
    String text = "KANTA-REMOTE-1:" + address + ":" + fingerprint + ":" + code;
    if (parse(text) == null) throw new IllegalArgumentException("Invalid remote pairing details");
    return text;
  }
}
