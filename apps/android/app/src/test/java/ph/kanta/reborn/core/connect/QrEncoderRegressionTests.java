package ph.kanta.reborn.core.connect;

/**
 * Domain regression checks for the in-tree QR encoder.
 *
 * <p>Runs on a host JVM inside {@code coreRegression}. The golden matrices in
 * {@link QrEncoderGoldenVectors} were produced by a reference encoder whose rendered PNGs
 * decoded correctly under an independent scanner, so a match here means this encoder emits
 * genuinely scannable symbols rather than merely self-consistent ones.
 */
public final class QrEncoderRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static void goldenMatricesMatchExactly() {
    check(QrEncoderGoldenVectors.ALL.length >= 4, "Golden set covers several versions");
    int versionsCovered = 0;
    for (QrEncoderGoldenVectors.Vector vector : QrEncoderGoldenVectors.ALL) {
      QrEncoder.Symbol symbol = QrEncoder.encode(vector.text);
      check(
          symbol.version == vector.version,
          "Version for payload of " + vector.text.length() + " chars");
      check(symbol.mask == vector.mask, "Selected mask for version " + vector.version);
      check(symbol.size == vector.rows.length, "Symbol size for version " + vector.version);
      check(symbol.size == 17 + 4 * vector.version, "Size matches 17 + 4 * version");

      String[] emitted = symbol.rows();
      boolean identical = true;
      int firstMismatch = -1;
      for (int row = 0; row < emitted.length; row++) {
        if (!emitted[row].equals(vector.rows[row])) {
          identical = false;
          if (firstMismatch < 0) firstMismatch = row;
          break;
        }
      }
      check(
          identical,
          "Matrix matches the scannable reference for version "
              + vector.version
              + (firstMismatch < 0 ? "" : " (first differing row " + firstMismatch + ")"));
      versionsCovered++;
    }
    check(versionsCovered == QrEncoderGoldenVectors.ALL.length, "Every golden vector was checked");
  }

  private static void encodingIsDeterministic() {
    QrEncoder.Symbol first = QrEncoder.encode("kanta-connect://10.0.0.2:45731?code=deadbeef&v=1");
    QrEncoder.Symbol second = QrEncoder.encode("kanta-connect://10.0.0.2:45731?code=deadbeef&v=1");
    check(first.version == second.version && first.mask == second.mask, "Stable version and mask");
    String[] a = first.rows();
    String[] b = second.rows();
    boolean same = true;
    for (int i = 0; i < a.length; i++) {
      if (!a[i].equals(b[i])) {
        same = false;
        break;
      }
    }
    check(same, "Repeated encoding yields an identical matrix");
  }

  /** The format information must decode back to error-correction level M and the chosen mask. */
  private static void formatInformationRoundTrips() {
    for (QrEncoderGoldenVectors.Vector vector : QrEncoderGoldenVectors.ALL) {
      QrEncoder.Symbol symbol = QrEncoder.encode(vector.text);
      int size = symbol.size;
      int[] copyA = new int[15];
      int[] copyB = new int[15];

      // Copy 1: left column of row 8 and the top of column 8.
      for (int i = 0; i < 6; i++) copyA[i] = symbol.dark(8, i) ? 1 : 0;
      copyA[6] = symbol.dark(8, 7) ? 1 : 0;
      copyA[7] = symbol.dark(8, 8) ? 1 : 0;
      copyA[8] = symbol.dark(7, 8) ? 1 : 0;
      for (int i = 0; i < 6; i++) copyA[9 + i] = symbol.dark(5 - i, 8) ? 1 : 0;

      // Copy 2: bottom of column 8 and the right end of row 8.
      for (int i = 0; i < 7; i++) copyB[i] = symbol.dark(size - 1 - i, 8) ? 1 : 0;
      for (int i = 0; i < 8; i++) copyB[7 + i] = symbol.dark(8, size - 8 + i) ? 1 : 0;

      check(sameBits(copyA, copyB), "Both format-information copies agree on main");
      int value = 0;
      for (int bit : copyA) value = (value << 1) | bit;
      int unmasked = value ^ 0x5412;
      int levelAndMask = (unmasked >>> 10) & 0b11111;
      check(((levelAndMask >>> 3) & 0b11) == 0b00, "Format information declares level M");
      check((levelAndMask & 0b111) == symbol.mask, "Format information matches the chosen mask");
      check(bchRemainderIsZero(unmasked), "Format information has a valid BCH remainder");
    }
  }

  /** Versions 7 and above carry a version-information block that must read back correctly. */
  private static void versionInformationRoundTripsForLargeSymbols() {
    int largeSymbols = 0;
    for (QrEncoderGoldenVectors.Vector vector : QrEncoderGoldenVectors.ALL) {
      if (vector.version < 7) continue;
      largeSymbols++;
      QrEncoder.Symbol symbol = QrEncoder.encode(vector.text);
      int size = symbol.size;
      int bits = 0;
      // Read the bottom-left copy: bit i lives at (size - 11 + i % 3, i / 3), LSB first.
      for (int i = 17; i >= 0; i--) {
        boolean bit = symbol.dark(size - 11 + (i % 3), i / 3);
        bits = (bits << 1) | (bit ? 1 : 0);
      }
      check(
          (bits >>> 12) == vector.version,
          "Version information reads back version " + vector.version);
      check(versionBchRemainderIsZero(bits), "Version information has a valid BCH remainder");
    }
    check(largeSymbols > 0, "A version 7 or larger vector is present in the golden set");
  }

  private static void structuralPatternsAreCorrect() {
    QrEncoder.Symbol symbol = QrEncoder.encode("KANTA");
    int size = symbol.size;
    // Finder patterns: 7x7 ring plus 3x3 core at each corner.
    for (int[] origin : new int[][] {{0, 0}, {0, size - 7}, {size - 7, 0}}) {
      int row = origin[0];
      int col = origin[1];
      check(symbol.dark(row, col), "Finder outer ring is dark");
      check(!symbol.dark(row + 1, col + 1), "Finder inner gap is light");
      check(symbol.dark(row + 3, col + 3), "Finder core is dark");
    }
    check(!symbol.dark(7, 7), "Separator next to the top-left finder is light");
    // Timing patterns alternate, starting dark at the first position after the separator.
    check(symbol.dark(6, 8), "Horizontal timing starts dark");
    check(!symbol.dark(6, 9), "Horizontal timing alternates");
    check(symbol.dark(8, 6), "Vertical timing starts dark");
    check(!symbol.dark(9, 6), "Vertical timing alternates");
    // Dark module.
    check(symbol.dark(size - 8, 8), "Dark module is set");
  }

  private static void versionSelectionUsesUtf8ByteLength() {
    // Two Baybayin characters are six UTF-8 bytes, not two.
    String baybayin = "\u1B03\u1B08";
    check(baybayin.length() == 2, "Two code units");
    QrEncoder.Symbol symbol = QrEncoder.encode(baybayin);
    check(
        symbol.version == QrEncoder.chooseVersion(6),
        "Version selection uses UTF-8 byte length, not code-unit count");
    check(symbol.version == 1, "A short multi-byte payload still fits version 1");
  }

  private static void capacityBoundariesAreRespected() {
    int previous = 0;
    for (int version = QrEncoder.MIN_VERSION; version <= QrEncoder.MAX_VERSION; version++) {
      int capacity = QrEncoder.capacity(version);
      check(capacity > 0, "Version " + version + " has capacity");
      check(capacity > previous, "Capacity grows with version " + version);
      previous = capacity;

      // The nominal capacity is not itself usable: the mode indicator and the character
      // count are subtracted from it. Use the true largest fitting payload.
      int largestFitting = capacity;
      while (largestFitting > 0 && payloadExceedsCapacity(largestFitting, version)) {
        largestFitting--;
      }
      check(largestFitting > 0, "Version " + version + " has a fitting payload");
      check(
          QrEncoder.chooseVersion(largestFitting) == version,
          "The largest payload fitting version "
              + version
              + " selects version "
              + QrEncoder.chooseVersion(largestFitting));

      if (version < QrEncoder.MAX_VERSION) {
        boolean movedOn;
        try {
          movedOn = QrEncoder.chooseVersion(largestFitting + 1) > version;
        } catch (IllegalArgumentException tooLong) {
          movedOn = true;
        }
        check(movedOn, "One byte past version " + version + " capacity moves on or is rejected");
      }
    }
  }

  /** True when a payload of this length cannot fit the version at level M. */
  private static boolean payloadExceedsCapacity(int payloadLength, int version) {
    int countBits = version <= 9 ? 8 : 16;
    return (4 + countBits + 8 * payloadLength + 7) / 8 > QrEncoder.capacity(version);
  }

  private static void oversizedAndInvalidInputIsRejected() {
    StringBuilder tooLong = new StringBuilder();
    for (int i = 0; i < 300; i++) tooLong.append('K');
    boolean rejected = false;
    try {
      QrEncoder.encode(tooLong.toString());
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    check(rejected, "A payload beyond version 10 is rejected");

    rejected = false;
    try {
      QrEncoder.encode("\uD83D");
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    check(rejected, "An unpaired high surrogate is rejected");

    rejected = false;
    try {
      QrEncoder.encode(null);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    check(rejected, "A null payload is rejected");

    rejected = false;
    try {
      QrEncoder.capacity(0);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    check(rejected, "Version 0 is rejected");
  }

  private static void realLinkPayloadsFitComfortably() {
    // A full IPv4 Kanta-Connect link with the longest plausible pairing code.
    String link =
        "kanta-connect://192.168.100.200:45731?code=0123456789abcdef0123456789abcdef&v=1";
    QrEncoder.Symbol symbol = QrEncoder.encode(link);
    check(symbol.version <= 5, "A full link fits version 5 or smaller");
    check(symbol.size == 17 + 4 * symbol.version, "Link symbol size is consistent");
    // A host name plus code should also fit.
    QrEncoder.Symbol named =
        QrEncoder.encode(
            "kanta-connect://kanta-host.local:45731?code=0123456789abcdef0123456789abcdef&v=1");
    check(named.version <= QrEncoder.MAX_VERSION, "A named host link fits the supported range");
  }

  private static boolean sameBits(int[] a, int[] b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
    return true;
  }

  /**
   * The 18-bit version codeword (6 data + 12 check bits) divides evenly by 0x1F25.
   *
   * <p>Distinct from the format check below: using the 15-bit format polynomial here
   * silently rejects every valid version block.
   */
  private static boolean versionBchRemainderIsZero(int codeword) {
    int remainder = codeword;
    for (int i = 17; i >= 12; i--) {
      if ((remainder & (1 << i)) != 0) remainder ^= 0x1F25 << (i - 12);
    }
    return remainder == 0;
  }

  /** The 15-bit format codeword and its 10-bit BCH remainder must divide evenly. */
  private static boolean bchRemainderIsZero(int codeword) {
    int remainder = codeword;
    for (int i = 14; i >= 10; i--) {
      if ((remainder & (1 << i)) != 0) remainder ^= 0x537 << (i - 10);
    }
    return remainder == 0;
  }

  /** Runs every QR encoder regression check. */
  public static int run() {
    checks = 0;
    goldenMatricesMatchExactly();
    encodingIsDeterministic();
    formatInformationRoundTrips();
    versionInformationRoundTripsForLargeSymbols();
    structuralPatternsAreCorrect();
    versionSelectionUsesUtf8ByteLength();
    capacityBoundariesAreRespected();
    oversizedAndInvalidInputIsRejected();
    realLinkPayloadsFitComfortably();
    return checks;
  }
}
