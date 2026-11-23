package ph.kanta.reborn.core.legacy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The app's bundled legacy unlock table.
 *
 * <p>Format v1 is plain text so future container versions can be added by editing one file:
 * blank-line-separated blocks of exactly {@code label=}, {@code key=}, {@code iv=} (a blank line or end of file commits each block); {@code #}
 * comments and blank lines are ignored. {@code key=PLACEHOLDER} marks a reserved slot whose
 * material has not been provided yet; placeholder entries are reported but never importable.
 * Limits keep the asset small and predictable: 64 KiB and 32 entries.
 *
 * <p>The table ships in main assets because the owner distributes the container files it
 * unlocks; see the ownership contract in {@code docs/ARCHITECTURE.md}.
 */
public final class LegacyKeyTable {
  public static final String PLACEHOLDER = "PLACEHOLDER";
  private static final int MAX_TABLE_BYTES = 64 * 1024;
  private static final int MAX_ENTRIES = 32;

  public static final class Entry {
    public final String label;
    /** 64 lowercase-free hex digits, or {@link #PLACEHOLDER} for a reserved slot. */
    public final String keyHex;
    /** 32 hex digits; ignored for placeholder entries. */
    public final String ivHex;
    public final boolean placeholder;

    Entry(String label, String keyHex, String ivHex, boolean placeholder) {
      this.label = label;
      this.keyHex = keyHex;
      this.ivHex = ivHex;
      this.placeholder = placeholder;
    }
  }

  private LegacyKeyTable() {}

  public static List<Entry> fromStream(InputStream input) throws IOException {
    ByteArrayOutputStream data = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = input.read(buffer)) != -1) {
      data.write(buffer, 0, read);
      if (data.size() > MAX_TABLE_BYTES) throw new IOException("Legacy key table exceeds the 64 KiB limit.");
    }
    return parse(data.toByteArray());
  }

  /** Strict parse; every structural violation is an IOException that names no material. */
  public static List<Entry> parse(byte[] data) throws IOException {
    if (data == null) throw new IOException("Missing legacy key table.");
    if (data.length == 0) throw new IOException("Legacy key table is empty.");
    if (data.length > MAX_TABLE_BYTES) throw new IOException("Legacy key table exceeds the 64 KiB limit.");
    String[] lines = new String(data, StandardCharsets.UTF_8).split("\n", -1);
    List<Entry> entries = new ArrayList<>();
    String label = null, key = null, iv = null;
    for (String raw : lines) {
      String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        // A blank line commits the finished block; extra blanks are ignored.
        if (label != null && key != null && iv != null) {
          entries.add(commit(entries, label, key, iv));
          label = null; key = null; iv = null;
        }
        continue;
      }
      if (trimmed.startsWith("#")) continue;
      int separator = trimmed.indexOf('=');
      if (separator <= 0) throw new IOException("Malformed legacy key table line.");
      String field = trimmed.substring(0, separator);
      String value = trimmed.substring(separator + 1).trim();
      if ("label".equals(field)) {
        if (label != null) throw new IOException("Duplicate label in a legacy key entry.");
        label = value;
      } else if ("key".equals(field)) {
        if (key != null) throw new IOException("Duplicate key in a legacy key entry.");
        key = value;
      } else if ("iv".equals(field)) {
        if (iv != null) throw new IOException("Duplicate IV in a legacy key entry.");
        iv = value;
      } else {
        throw new IOException("Unknown field in the legacy key table.");
      }
    }
    // The final block may end the file without a trailing blank line.
    if (label != null && key != null && iv != null) {
      entries.add(commit(entries, label, key, iv));
    } else if (label != null || key != null || iv != null) {
      throw new IOException("A legacy key entry is missing a field.");
    }
    if (entries.isEmpty()) throw new IOException("Legacy key table has no entries.");
    return entries;
  }

  private static Entry commit(List<Entry> entries, String label, String key, String iv)
      throws IOException {
    if (entries.size() >= MAX_ENTRIES) {
      throw new IOException("Legacy key table exceeds the 32-entry limit.");
    }
    if (label.isEmpty() || label.length() > 64) {
      throw new IOException("Every legacy key entry needs a short label.");
    }
    boolean placeholder = PLACEHOLDER.equals(key);
    if (!placeholder && !isHex(key, 32)) {
      throw new IOException("Every real legacy key needs 64 hexadecimal digits.");
    }
    if (!isHex(iv, 16)) {
      throw new IOException("Every legacy IV needs 32 hexadecimal digits.");
    }
    return new Entry(label, key, iv, placeholder);
  }

  private static boolean isHex(String value, int digits) {
    if (value.length() != digits * 2) return false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      if (!ok) return false;
    }
    return true;
  }
}
