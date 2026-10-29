package ph.kanta.reborn.core.connect;

/**
 * QR Code encoder (byte mode, error-correction level M, versions 1..10).
 *
 * <p>Framework-free: no Android type is referenced, so the host-JVM {@code coreRegression}
 * task can exercise it. The matrices this produces were verified by rendering them to PNG
 * and decoding the result with an independent scanner; {@code QrEncoderRegressionTests}
 * pins the exact matrices so the encoder cannot drift.
 *
 * <p>Why in-tree rather than a dependency: the project keeps its dependency surface small,
 * the encoding is fully specified by ISO/IEC 18004, and the whole encoder is small enough
 * to test exhaustively. The maximum payload is 216 bytes (version 10, level M), which is
 * far more than a Kanta-Connect link needs.
 */
public final class QrEncoder {

  /** Lowest supported symbol version. */
  public static final int MIN_VERSION = 1;

  /** Highest supported symbol version. */
  public static final int MAX_VERSION = 10;

  /** Error-correction level byte for level M in the format information. */
  private static final int ECC_M_BITS = 0b00;

  /** Byte-mode indicator. */
  private static final int MODE_BYTE = 0b0100;

  /** Pad codewords, alternated to fill the capacity. */
  private static final int[] PAD_CODEWORDS = {0xEC, 0x11};

  /** Reed-Solomon generator polynomial for GF(256), primitive polynomial 0x11D. */
  private static final int[] EXP = new int[512];

  private static final int[] LOG = new int[256];

  static {
    int x = 1;
    for (int i = 0; i < 255; i++) {
      EXP[i] = x;
      LOG[x] = i;
      x <<= 1;
      if ((x & 0x100) != 0) x ^= 0x11D;
    }
    for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
  }

  /** version index 1..10 to {error correction codewords per block, blockCount, dataCodewords, blockCount2, dataCodewords2}. */
  private static final int[][] ECC_M = {
    null,
    {10, 1, 16, 0, 0},
    {16, 1, 28, 0, 0},
    {26, 1, 44, 0, 0},
    {18, 2, 32, 0, 0},
    {24, 2, 43, 0, 0},
    {16, 4, 27, 0, 0},
    {18, 4, 31, 0, 0},
    {22, 2, 38, 2, 39},
    {22, 3, 36, 2, 37},
    {26, 4, 43, 1, 44},
  };

  /** Alignment-pattern centre coordinates per version. */
  private static final int[][] ALIGNMENT = {
    null,
    {},
    {6, 18},
    {6, 22},
    {6, 26},
    {6, 30},
    {6, 34},
    {6, 22, 38},
    {6, 24, 42},
    {6, 26, 46},
    {6, 28, 50},
  };

  private QrEncoder() {}

  /** One encoded symbol. */
  public static final class Symbol {
    public final String text;
    public final int version;
    public final int size;
    public final int mask;
    private final boolean[][] modules;

    Symbol(String text, int version, int size, int mask, boolean[][] modules) {
      this.text = text;
      this.version = version;
      this.size = size;
      this.mask = mask;
      this.modules = modules;
    }

    /** True when the module at {@code row, col} is dark. */
    public boolean dark(int row, int col) {
      return modules[row][col];
    }

    /** The matrix as rows of {@code '0'}/{@code '1'}, top row first. */
    public String[] rows() {
      String[] out = new String[size];
      for (int row = 0; row < size; row++) {
        StringBuilder line = new StringBuilder(size);
        for (int col = 0; col < size; col++) line.append(modules[row][col] ? '1' : '0');
        out[row] = line.toString();
      }
      return out;
    }
  }

  /**
   * Encodes {@code text} as UTF-8 in byte mode.
   *
   * @throws IllegalArgumentException when the payload does not fit version 10 at level M,
   *     or when the bytes cannot be represented (e.g. lone surrogates)
   */
  public static Symbol encode(String text) {
    if (text == null) throw new IllegalArgumentException("text is required");
    byte[] payload = toUtf8(text);
    int version = chooseVersion(payload.length);
    int[] data = dataCodewords(payload, version);
    int[] interleaved = interleave(data, version);

    Symbol best = null;
    int bestPenalty = Integer.MAX_VALUE;
    for (int mask = 0; mask < 8; mask++) {
      boolean[][] matrix = buildMatrix(version, interleaved, mask);
      applyFormat(matrix, version, mask);
      applyVersion(matrix, version);
      int penalty = penalty(matrix, version);
      if (penalty < bestPenalty) {
        bestPenalty = penalty;
        best = new Symbol(text, version, matrix.length, mask, matrix);
      }
    }
    return best;
  }

  /** Data codeword capacity of a version at level M. */
  public static int capacity(int version) {
    requireVersion(version);
    int[] spec = ECC_M[version];
    return spec[1] * spec[2] + spec[3] * spec[4];
  }

  /** The smallest version that fits {@code byteLength} payload bytes, including headers. */
  public static int chooseVersion(int byteLength) {
    for (int version = MIN_VERSION; version <= MAX_VERSION; version++) {
      int countBits = version <= 9 ? 8 : 16;
      int needed = (4 + countBits + 8 * byteLength + 7) / 8;
      if (needed <= capacity(version)) return version;
    }
    throw new IllegalArgumentException(
        "payload of " + byteLength + " bytes exceeds version 10 at error-correction level M");
  }

  // ---- data encoding -----------------------------------------------------------------

  private static byte[] toUtf8(String text) {
    // Reject lone surrogates rather than silently substituting: the pair scan below would
    // otherwise produce a different payload than the caller sees.
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
          throw new IllegalArgumentException("Unpaired high surrogate at " + i);
        }
        i++;
      } else if (Character.isLowSurrogate(c)) {
        throw new IllegalArgumentException("Unpaired low surrogate at " + i);
      }
    }
    return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private static int[] dataCodewords(byte[] payload, int version) {
    int capacity = capacity(version);
    int countBits = version <= 9 ? 8 : 16;
    java.util.List<Boolean> bits = new java.util.ArrayList<>(capacity * 8);
    put(bits, MODE_BYTE, 4);
    put(bits, payload.length, countBits);
    for (byte value : payload) put(bits, value & 0xFF, 8);

    int capacityBits = capacity * 8;
    for (int i = 0; i < Math.min(4, capacityBits - bits.size()); i++) bits.add(false);
    while (bits.size() % 8 != 0) bits.add(false);

    int[] words = new int[bits.size() / 8];
    for (int i = 0; i < words.length; i++) {
      int value = 0;
      for (int b = 0; b < 8; b++) value = (value << 1) | (bits.get(i * 8 + b) ? 1 : 0);
      words[i] = value;
    }
    int[] filled = new int[capacity];
    System.arraycopy(words, 0, filled, 0, words.length);
    for (int i = words.length, k = 0; i < capacity; i++, k++) filled[i] = PAD_CODEWORDS[k % 2];
    return filled;
  }

  private static void put(java.util.List<Boolean> bits, int value, int length) {
    for (int i = length - 1; i >= 0; i--) bits.add(((value >>> i) & 1) != 0);
  }

  // ---- Reed-Solomon and interleaving --------------------------------------------------

  private static int gfMul(int a, int b) {
    if (a == 0 || b == 0) return 0;
    return EXP[LOG[a] + LOG[b]];
  }

  private static int[] rsGenerator(int degree) {
    int[] poly = {1};
    for (int i = 0; i < degree; i++) {
      int[] next = new int[poly.length + 1];
      for (int j = 0; j < poly.length; j++) {
        next[j] ^= poly[j];
        next[j + 1] ^= gfMul(poly[j], EXP[i]);
      }
      poly = next;
    }
    return poly;
  }

  private static int[] rsEncode(int[] data, int ecLength) {
    int[] generator = rsGenerator(ecLength);
    int[] remainder = new int[data.length + ecLength];
    System.arraycopy(data, 0, remainder, 0, data.length);
    for (int i = 0; i < data.length; i++) {
      int coefficient = remainder[i];
      if (coefficient != 0) {
        for (int j = 0; j < generator.length; j++) {
          remainder[i + j] ^= gfMul(generator[j], coefficient);
        }
      }
    }
    int[] ec = new int[ecLength];
    System.arraycopy(remainder, data.length, ec, 0, ecLength);
    return ec;
  }

  private static int[] interleave(int[] data, int version) {
    int[] spec = ECC_M[version];
    int ecPerBlock = spec[0];
    java.util.List<int[]> blocks = new java.util.ArrayList<>();
    int offset = 0;
    for (int group = 0; group < 2; group++) {
      int blockCount = spec[1 + group * 2];
      int blockLength = spec[2 + group * 2];
      for (int b = 0; b < blockCount; b++) {
        int[] block = new int[blockLength];
        System.arraycopy(data, offset, block, 0, blockLength);
        offset += blockLength;
        blocks.add(block);
      }
    }
    if (offset != data.length) {
      throw new IllegalStateException("block split consumed " + offset + " of " + data.length);
    }

    int maxLength = 0;
    for (int[] block : blocks) maxLength = Math.max(maxLength, block.length);
    int[] out = new int[data.length + blocks.size() * ecPerBlock];
    int at = 0;
    for (int i = 0; i < maxLength; i++) {
      for (int[] block : blocks) {
        if (i < block.length) out[at++] = block[i];
      }
    }
    java.util.List<int[]> ecBlocks = new java.util.ArrayList<>();
    for (int[] block : blocks) ecBlocks.add(rsEncode(block, ecPerBlock));
    for (int i = 0; i < ecPerBlock; i++) {
      for (int[] ec : ecBlocks) out[at++] = ec[i];
    }
    if (at != out.length) throw new IllegalStateException("interleave mismatch");
    return out;
  }

  // ---- matrix construction ------------------------------------------------------------

  private static final class Builder {
    final int size;
    final boolean[][] modules;
    final boolean[][] function;

    Builder(int size) {
      this.size = size;
      this.modules = new boolean[size][size];
      this.function = new boolean[size][size];
    }

    void set(int row, int col, boolean dark) {
      modules[row][col] = dark;
      function[row][col] = true;
    }
  }

  private static boolean[][] buildMatrix(int version, int[] codewords, int mask) {
    int size = 17 + 4 * version;
    Builder builder = new Builder(size);
    drawFinder(builder, 0, 0);
    drawFinder(builder, 0, size - 7);
    drawFinder(builder, size - 7, 0);
    drawTiming(builder);
    drawAlignmentPatterns(builder, version);
    builder.set(size - 8, 8, true); // dark module
    reserveFormatAreas(builder);
    reserveVersionAreas(builder, version);
    placeData(builder, codewords, mask);
    return builder.modules;
  }

  private static void drawFinder(Builder builder, int rowCentre, int colCentre) {
    for (int dr = -1; dr <= 7; dr++) {
      for (int dc = -1; dc <= 7; dc++) {
        int row = rowCentre + dr;
        int col = colCentre + dc;
        if (row < 0 || col < 0 || row >= builder.size || col >= builder.size) continue;
        boolean inside = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6;
        boolean ring = dr == 0 || dr == 6 || dc == 0 || dc == 6;
        boolean core = dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4;
        builder.set(row, col, inside && (ring || core));
      }
    }
  }

  private static void drawTiming(Builder builder) {
    for (int i = 8; i < builder.size - 8; i++) {
      boolean dark = i % 2 == 0;
      if (!builder.function[6][i]) builder.set(6, i, dark);
      if (!builder.function[i][6]) builder.set(i, 6, dark);
    }
  }

  private static void drawAlignmentPatterns(Builder builder, int version) {
    int[] centres = ALIGNMENT[version];
    int count = centres.length;
    for (int i = 0; i < count; i++) {
      for (int j = 0; j < count; j++) {
        // Skip only the three finder corners, by index. Testing the centre module instead
        // would wrongly skip the patterns whose centre lies on the timing row or column.
        if ((i == 0 && j == 0) || (i == 0 && j == count - 1) || (i == count - 1 && j == 0)) {
          continue;
        }
        for (int dr = -2; dr <= 2; dr++) {
          for (int dc = -2; dc <= 2; dc++) {
            builder.set(
                centres[i] + dr, centres[j] + dc, Math.max(Math.abs(dr), Math.abs(dc)) != 1);
          }
        }
      }
    }
  }

  private static void reserveFormatAreas(Builder builder) {
    int size = builder.size;
    for (int i = 0; i < 9; i++) {
      if (!builder.function[8][i]) builder.set(8, i, false);
      if (!builder.function[i][8]) builder.set(i, 8, false);
    }
    for (int i = 0; i < 8; i++) {
      if (!builder.function[8][size - 1 - i]) builder.set(8, size - 1 - i, false);
      if (!builder.function[size - 1 - i][8]) builder.set(size - 1 - i, 8, false);
    }
  }

  private static void reserveVersionAreas(Builder builder, int version) {
    if (version < 7) return;
    int size = builder.size;
    for (int i = 0; i < 18; i++) {
      int row = i / 3;
      int col = i % 3;
      if (!builder.function[row][size - 11 + col]) builder.set(row, size - 11 + col, false);
      if (!builder.function[size - 11 + col][row]) builder.set(size - 11 + col, row, false);
    }
  }

  private static void placeData(Builder builder, int[] codewords, int mask) {
    int size = builder.size;
    int totalDataModules = 0;
    for (int row = 0; row < size; row++) {
      for (int col = 0; col < size; col++) if (!builder.function[row][col]) totalDataModules++;
    }
    int totalCodewordBits = codewords.length * 8;
    if (totalCodewordBits > totalDataModules) {
      throw new IllegalStateException("codewords exceed the symbol capacity");
    }

    int index = 0;
    int col = size - 1;
    boolean upward = true;
    while (col > 0) {
      if (col == 6) col = 5;
      for (int step = 0; step < size; step++) {
        int row = upward ? size - 1 - step : step;
        for (int c = col; c >= col - 1; c--) {
          if (builder.function[row][c]) continue;
          boolean bit = false;
          if (index < totalCodewordBits) {
            bit = ((codewords[index / 8] >>> (7 - (index % 8))) & 1) != 0;
          }
          index++;
          if (maskBit(mask, row, c)) bit = !bit;
          builder.modules[row][c] = bit;
        }
      }
      upward = !upward;
      col -= 2;
    }
    if (index != totalDataModules) {
      throw new IllegalStateException("placed " + index + " of " + totalDataModules + " modules");
    }
  }

  private static boolean maskBit(int mask, int row, int col) {
    switch (mask) {
      case 0:
        return (row + col) % 2 == 0;
      case 1:
        return row % 2 == 0;
      case 2:
        return col % 3 == 0;
      case 3:
        return (row + col) % 3 == 0;
      case 4:
        return (row / 2 + col / 3) % 2 == 0;
      case 5:
        return (row * col) % 2 + (row * col) % 3 == 0;
      case 6:
        return ((row * col) % 2 + (row * col) % 3) % 2 == 0;
      default:
        return ((row + col) % 2 + (row * col) % 3) % 2 == 0;
    }
  }

  // ---- format and version information -------------------------------------------------

  private static int bch(int value, int polynomial, int degreeBits) {
    int v = value << degreeBits;
    int highest = 31 - Integer.numberOfLeadingZeros(Math.max(v, 1));
    for (int i = highest; i >= degreeBits; i--) {
      if ((v & (1 << i)) != 0) v ^= polynomial << (i - degreeBits);
    }
    return v;
  }

  private static int formatBits(int mask) {
    int data = (ECC_M_BITS << 3) | mask;
    return ((data << 10) | bch(data, 0x537, 10)) ^ 0x5412;
  }

  private static int versionBits(int version) {
    return (version << 12) | bch(version, 0x1F25, 12);
  }

  private static void applyFormat(boolean[][] matrix, int version, int mask) {
    int size = matrix.length;
    int format = formatBits(mask);
    boolean[] bits = new boolean[15];
    for (int i = 0; i < 15; i++) bits[14 - i] = ((format >>> i) & 1) != 0;

    for (int i = 0; i < 6; i++) matrix[8][i] = bits[i];
    matrix[8][7] = bits[6];
    matrix[8][8] = bits[7];
    matrix[7][8] = bits[8];
    for (int i = 0; i < 6; i++) matrix[5 - i][8] = bits[9 + i];

    for (int i = 0; i < 7; i++) matrix[size - 1 - i][8] = bits[i];
    for (int i = 0; i < 8; i++) matrix[8][size - 8 + i] = bits[7 + i];
    matrix[size - 8][8] = true;
  }

  private static void applyVersion(boolean[][] matrix, int version) {
    if (version < 7) return;
    int size = matrix.length;
    int bits = versionBits(version);
    for (int i = 0; i < 18; i++) {
      boolean bit = ((bits >>> i) & 1) != 0;
      int a = size - 11 + (i % 3);
      int b = i / 3;
      matrix[b][a] = bit;
      matrix[a][b] = bit;
    }
  }

  // ---- mask penalty -------------------------------------------------------------------

  private static int penalty(boolean[][] matrix, int version) {
    int size = matrix.length;
    int score = 0;

    // Rule 1: runs of five or more same-coloured modules.
    for (int line = 0; line < size; line++) {
      score += runPenalty(rowRun(matrix, line));
      score += runPenalty(columnRun(matrix, line));
    }

    // Rule 2: 2x2 blocks of one colour.
    for (int row = 0; row < size - 1; row++) {
      for (int col = 0; col < size - 1; col++) {
        boolean v = matrix[row][col];
        if (matrix[row][col + 1] == v && matrix[row + 1][col] == v && matrix[row + 1][col + 1] == v) {
          score += 3;
        }
      }
    }

    // Rule 3: finder-like patterns.
    int[] pattern = {1, 0, 1, 1, 1, 0, 1};
    for (int line = 0; line < size; line++) {
      score += patternPenalty(rowRun(matrix, line), pattern, size);
      score += patternPenalty(columnRun(matrix, line), pattern, size);
    }

    // Rule 4: dark/light balance.
    int dark = 0;
    for (boolean[] row : matrix) {
      for (boolean module : row) if (module) dark++;
    }
    int percent = dark * 100 / (size * size);
    score += 10 * (Math.abs(percent - 50) / 5);
    return score;
  }

  private static boolean[] rowRun(boolean[][] matrix, int row) {
    return matrix[row].clone();
  }

  private static boolean[] columnRun(boolean[][] matrix, int column) {
    boolean[] out = new boolean[matrix.length];
    for (int row = 0; row < matrix.length; row++) out[row] = matrix[row][column];
    return out;
  }

  private static int runPenalty(boolean[] line) {
    int score = 0;
    int run = 1;
    for (int i = 1; i < line.length; i++) {
      if (line[i] == line[i - 1]) {
        run++;
      } else {
        if (run >= 5) score += 3 + (run - 5);
        run = 1;
      }
    }
    if (run >= 5) score += 3 + (run - 5);
    return score;
  }

  private static int patternPenalty(boolean[] line, int[] pattern, int size) {
    int score = 0;
    for (int i = 0; i + 7 <= size; i++) {
      boolean match = true;
      for (int k = 0; k < 7; k++) {
        if ((line[i + k] ? 1 : 0) != pattern[k]) {
          match = false;
          break;
        }
      }
      if (!match) continue;
      if (allLight(line, i - 4, i)) score += 40;
      if (allLight(line, i + 7, i + 11)) score += 40;
    }
    return score;
  }

  private static boolean allLight(boolean[] line, int from, int to) {
    if (from < 0 || to > line.length) return false;
    if (to - from != 4) return false;
    for (int i = from; i < to; i++) if (line[i]) return false;
    return true;
  }

  private static void requireVersion(int version) {
    if (version < MIN_VERSION || version > MAX_VERSION) {
      throw new IllegalArgumentException("unsupported QR version: " + version);
    }
  }
}
