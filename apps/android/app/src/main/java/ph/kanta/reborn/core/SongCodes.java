package ph.kanta.reborn.core;

import java.io.IOException;
import java.util.BitSet;

/** Decimal song numbers, independent of song titles and file identity. */
public final class SongCodes {
  public static final int MAX_DIGITS = 6;
  public static final int MAX_NUMBER = 999999;
  public static final int FIRST_GENERATED = 100000;

  private SongCodes() {}

  public static boolean isDigits(CharSequence value) {
    if (value == null || value.length() == 0) return false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') return false;
    }
    return true;
  }

  public static int number(CharSequence value) {
    if (value == null || value.length() > MAX_DIGITS || !isDigits(value)) return -1;
    int number = 0;
    for (int i = 0; i < value.length(); i++) number = number * 10 + value.charAt(i) - '0';
    return number;
  }

  public static boolean isOverlongNumericQuery(String query) {
    String trimmed = query.trim();
    return trimmed.length() > MAX_DIGITS && isDigits(trimmed);
  }

  public static String format(int number) {
    if (number < 0 || number > MAX_NUMBER) throw new IllegalArgumentException("Song code out of range");
    String digits = Integer.toString(number);
    return "000000".substring(digits.length()) + digits;
  }

  public static void markUsed(BitSet used, String code) {
    int number = number(code);
    if (number >= 0) used.set(number);
  }

  /** Claims an available generated reference without stepping outside six digits. */
  public static int claimGenerated(BitSet used, int start) throws IOException {
    int number = used.nextClearBit(start);
    if (number > MAX_NUMBER) throw new IOException("No unused song numbers remain");
    used.set(number);
    return number;
  }
}
