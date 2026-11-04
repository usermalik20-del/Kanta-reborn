package ph.kanta.reborn.core.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 helpers for the update pipeline.
 *
 * <p>SHA-256 is computed over the exact staged bytes and compared against the
 * feed's {@code apkSha256}; the digest of a partially downloaded file is
 * intentionally never trusted. Staging file names are derived from the digest
 * so a resumed download always targets the same file and a feed change forces
 * a fresh download rather than a name collision.
 */
public final class UpdateDigest {

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private UpdateDigest() {}

  /** Lowercase hex SHA-256 of a byte array. */
  public static String sha256Hex(byte[] data) {
    return hex(sha256(data));
  }

  /** Lowercase hex SHA-256 of a file, streamed with a fixed buffer. */
  public static String sha256Hex(File file) throws IOException {
    MessageDigest digest = newDigest();
    byte[] buffer = new byte[64 * 1024];
    try (FileInputStream in = new FileInputStream(file)) {
      int read;
      while ((read = in.read(buffer)) >= 0) {
        if (read > 0) {
          digest.update(buffer, 0, read);
        }
      }
    }
    return hex(digest.digest());
  }

  /**
   * Staging file name for an expected digest, e.g. {@code .a1b2….part}.
   *
   * <p>The leading dot keeps the file out of media scans; the {@code .part}
   * suffix marks it incomplete so the package inspector never mistakes it for
   * an installable APK.
   */
  public static String stagingName(String apkSha256) {
    if (apkSha256 == null || !apkSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("staging name requires a 64-hex SHA-256");
    }
    return "." + apkSha256 + ".part";
  }

  private static byte[] sha256(byte[] data) {
    MessageDigest digest = newDigest();
    return digest.digest(data);
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("JVM without SHA-256", impossible);
    }
  }

  private static String hex(byte[] bytes) {
    char[] out = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int value = bytes[i] & 0xFF;
      out[i * 2] = HEX[value >>> 4];
      out[i * 2 + 1] = HEX[value & 0x0F];
    }
    return new String(out);
  }
}