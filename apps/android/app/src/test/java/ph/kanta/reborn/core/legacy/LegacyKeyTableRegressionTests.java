package ph.kanta.reborn.core.legacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Synthetic table-format tests; the committed asset is the only real material and it is never
 * parsed here, so no key hex appears in this source.
 */
public final class LegacyKeyTableRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static String table(String... blocks) {
    StringBuilder text = new StringBuilder();
    for (String block : blocks) text.append(block).append('\n');
    return text.toString();
  }

  private static void entryValues() throws IOException {
    List<LegacyKeyTable.Entry> entries = LegacyKeyTable.parse(table(
        "# comment lines are ignored",
        "",
        "label=First library",
        "key=" + "ab".repeat(32),
        "iv=" + "CD".repeat(16)
    ).getBytes(StandardCharsets.UTF_8));
    check(entries.size() == 1, "One entry parsed");
    LegacyKeyTable.Entry entry = entries.get(0);
    check(entry.label.equals("First library"), "Label preserved");
    check(entry.keyHex.equals("ab".repeat(32)) && entry.ivHex.equals("CD".repeat(16)),
        "Hex is stored verbatim, either case");
    check(!entry.placeholder, "A real entry is not a placeholder");
    try (LegacyImportKey owned = LegacyImportKey.fromHex(entry.keyHex, entry.ivHex)) {
      check(owned.key().length == 32 && owned.iv().length == 16,
          "Table material feeds the runtime key unchanged");
    }
  }

  private static void multiEntryAndPlaceholder() throws IOException {
    List<LegacyKeyTable.Entry> entries = LegacyKeyTable.parse(table(
        "label=A\nkey=" + "11".repeat(32) + "\niv=" + "22".repeat(16),
        "",
        "label=B\niv=" + "33".repeat(16) + "\nkey=" + "44".repeat(32),
        "",
        "label=Future version\nkey=PLACEHOLDER\niv=" + "00".repeat(16)
    ).getBytes(StandardCharsets.UTF_8));
    check(entries.size() == 3, "Three entries parsed in file order");
    check(entries.get(0).label.equals("A") && entries.get(1).label.equals("B"),
        "Field order inside a block does not matter");
    check(entries.get(2).placeholder && entries.get(2).label.equals("Future version"),
        "PLACEHOLDER marks a reserved slot");
    int real = 0;
    for (LegacyKeyTable.Entry entry : entries) if (!entry.placeholder) real++;
    check(real == 2, "Only non-placeholder entries are import candidates");
  }

  private static void rejectsMalformedTables() throws IOException {
    String validBlock = "label=A\nkey=" + "11".repeat(32) + "\niv=" + "22".repeat(16) + "\n";
    String[] malformed = {
        "",
        "# only comments\n",
        "label=A\nkey=" + "11".repeat(32) + "\n", // Missing IV at end of file.
        "label=A\nkey=" + "11".repeat(32) + "\niv=" + "22".repeat(16)
            + "\nlabel=B\nkey=" + "11".repeat(32) + "\niv=" + "22".repeat(16), // No separator.
        "label=A\nlabel=B\nkey=" + "11".repeat(32) + "\niv=" + "22".repeat(16) + "\n",
        "label=A\nkey=" + "11".repeat(32) + "\niv=" + "22".repeat(16) + "\niv=" + "22".repeat(16) + "\n",
        "label=A\nunknown=1\nkey=" + "11".repeat(32) + "\niv=" + "22".repeat(16) + "\n",
        "label=\nkey=" + "11".repeat(32) + "\niv=" + "22".repeat(16) + "\n",
        "label=" + "x".repeat(65) + "\nkey=" + "11".repeat(32) + "\niv=" + "22".repeat(16) + "\n",
        "label=A\nkey=" + "11".repeat(31) + "1\niv=" + "22".repeat(16) + "\n", // 63 digits.
        "label=A\nkey=" + "11".repeat(32) + "1\niv=" + "22".repeat(16) + "\n", // 65 digits.
        "label=A\nkey=" + "11".repeat(31) + "g1\niv=" + "22".repeat(16) + "\n", // Non-hex.
        "label=A\nkey=" + "11".repeat(32) + "\niv=" + "22".repeat(15) + "2\n" // 31 digits.
    };
    for (int i = 0; i < malformed.length; i++) {
      boolean refused = false;
      try {
        LegacyKeyTable.parse(malformed[i].getBytes(StandardCharsets.UTF_8));
      } catch (IOException expected) {
        refused = expected.getMessage() != null && !expected.getMessage().isEmpty();
      }
      check(refused, "Malformed table " + i + " rejected with a message naming no material");
    }
    boolean refused = false;
    try {
      LegacyKeyTable.parse(null);
    } catch (IOException expected) {
      refused = true;
    }
    check(refused, "Missing table rejected");
    refused = false;
    try {
      LegacyKeyTable.parse(new byte[64 * 1024 + 1]);
    } catch (IOException expected) {
      refused = true;
    }
    check(refused, "Oversized table rejected before parsing");
  }

  private static void entryLimit() throws IOException {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 32; i++) {
      if (i > 0) text.append('\n');
      text.append("label=Entry ").append(i)
          .append("\nkey=").append(String.format("%02x", i).repeat(32))
          .append("\niv=").append("22".repeat(16)).append('\n');
    }
    check(LegacyKeyTable.parse(text.toString().getBytes(StandardCharsets.UTF_8)).size() == 32,
        "The 32-entry limit is inclusive");
    text.append("\n").append("label=Over\nkey=").append("11".repeat(32))
        .append("\niv=").append("22".repeat(16)).append('\n');
    boolean refused = false;
    try {
      LegacyKeyTable.parse(text.toString().getBytes(StandardCharsets.UTF_8));
    } catch (IOException expected) {
      refused = true;
    }
    check(refused, "The 32-entry limit is enforced");
  }

  public static int run() throws Exception {
    checks = 0;
    entryValues();
    multiEntryAndPlaceholder();
    rejectsMalformedTables();
    entryLimit();
    return checks;
  }
}
