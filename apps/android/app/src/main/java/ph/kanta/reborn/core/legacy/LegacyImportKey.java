package ph.kanta.reborn.core.legacy;

import java.util.Arrays;

/** One import's caller-supplied AES-256 material. Never persist or log this object. */
public final class LegacyImportKey implements AutoCloseable {
  private final byte[] key;
  private final byte[] iv;
  private boolean closed;

  public LegacyImportKey(byte[] key, byte[] iv) {
    if (key == null || key.length != 32 || iv == null || iv.length != 16) {
      throw new IllegalArgumentException("Use a 32-byte key and a 16-byte IV.");
    }
    this.key = key.clone();
    this.iv = iv.clone();
  }

  /** Parses directly from an editable field, without making an immutable secret String. */
  public static LegacyImportKey fromHex(CharSequence keyText, CharSequence ivText) {
    byte[] key = null;
    byte[] iv = null;
    try {
      key = parseHex(keyText, 32);
      iv = parseHex(ivText, 16);
      return new LegacyImportKey(key, iv);
    } finally {
      if (key != null) Arrays.fill(key, (byte) 0);
      if (iv != null) Arrays.fill(iv, (byte) 0);
    }
  }

  private static byte[] parseHex(CharSequence text, int bytes) {
    if (text == null || text.length() != bytes * 2) {
      throw new IllegalArgumentException("Enter a 64-digit key and a 32-digit IV in hexadecimal.");
    }
    // Validate before allocating a secret buffer, including the last character.
    for (int i = 0; i < text.length(); i++) {
      if (digit(text.charAt(i)) < 0) {
        throw new IllegalArgumentException("Use hexadecimal digits only (0-9 and A-F).");
      }
    }
    byte[] result = new byte[bytes];
    for (int i = 0; i < bytes; i++) {
      result[i] = (byte) ((digit(text.charAt(i * 2)) << 4) | digit(text.charAt(i * 2 + 1)));
    }
    return result;
  }

  private static int digit(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
  }

  // Package-private access is confined to the synchronous importer. Ownership is exclusive.
  byte[] key() {
    if (closed) throw new IllegalStateException("Import key has been cleared.");
    return key;
  }

  byte[] iv() {
    if (closed) throw new IllegalStateException("Import key has been cleared.");
    return iv;
  }

  @Override
  public void close() {
    Arrays.fill(key, (byte) 0);
    Arrays.fill(iv, (byte) 0);
    closed = true;
  }
}
