package ph.kanta.reborn.core.songbook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Classifies legacy container payloads by their leading bytes.
 *
 * <p>Mirrors the iOS LegacyCompatibility payload probe. Classification is read-only and
 * bounded to a small prefix window; an unrecognised payload stays {@code UNKNOWN} rather
 * than being guessed.
 */
public final class ShubBlobPayloadProbe {

  /** Known payload signatures. */
  public enum Signature {
    STANDARD_MIDI,
    WAVE,
    MP3_ID3,
    MKMD_V1,
    ZIP,
    GZIP,
    UNKNOWN
  }

  /** One classified payload span. */
  public static final class PayloadSample {
    public final int recordIndex;
    public final long startOffset;
    public final long endOffset;
    public final String prefixHex;
    public final Signature signature;

    PayloadSample(
        int recordIndex, long startOffset, long endOffset, String prefixHex, Signature signature) {
      this.recordIndex = recordIndex;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.prefixHex = prefixHex;
      this.signature = signature;
    }
  }

  /** Probe result: one sample per span plus the signature histogram. */
  public static final class Result {
    public final int prefixByteCount;
    public final java.util.List<PayloadSample> samples;
    public final Map<Signature, Integer> signatureCounts;

    Result(
        int prefixByteCount,
        java.util.List<PayloadSample> samples,
        Map<Signature, Integer> signatureCounts) {
      this.prefixByteCount = prefixByteCount;
      this.samples = samples;
      this.signatureCounts = signatureCounts;
    }
  }

  /** Reads a bounded window from the paired container. */
  public interface Window {
    /** Returns up to {@code length} bytes starting at {@code offset}, or an empty array. */
    byte[] read(long offset, int length);
  }

  private static final int DEFAULT_PREFIX_BYTE_COUNT = 16;

  private ShubBlobPayloadProbe() {}

  /** Classifies every span of the container. */
  public static Result inspect(
      java.util.List<ShubBlobSpanIndex.PayloadSpan> spans, Window window, int prefixByteCount) {
    int windowSize = Math.max(1, prefixByteCount);
    Map<Signature, Integer> counts = new LinkedHashMap<>();
    java.util.List<PayloadSample> samples = new java.util.ArrayList<>();
    for (ShubBlobSpanIndex.PayloadSpan span : spans) {
      byte[] prefix = window.read(span.startOffset, windowSize);
      Signature signature = signature(prefix);
      counts.put(signature, counts.getOrDefault(signature, 0) + 1);
      samples.add(
          new PayloadSample(
              span.recordIndex, span.startOffset, span.endOffset, hex(prefix), signature));
    }
    return new Result(
        windowSize, Collections.unmodifiableList(samples), Collections.unmodifiableMap(counts));
  }

  /** Classifies a single prefix window. */
  public static Signature signature(byte[] prefix) {
    if (startsWith(prefix, new byte[] {'M', 'T', 'h', 'd'})) return Signature.STANDARD_MIDI;
    if (prefix.length >= 12
        && startsWith(prefix, new byte[] {'R', 'I', 'F', 'F'})
        && matchesAt(prefix, 8, new byte[] {'W', 'A', 'V', 'E'})) {
      return Signature.WAVE;
    }
    if (startsWith(prefix, new byte[] {'I', 'D', '3'})) return Signature.MP3_ID3;
    if (startsWith(prefix, new byte[] {'M', 'K', 'M', 'D'})) return Signature.MKMD_V1;
    if (startsWith(prefix, new byte[] {0x50, 0x4b, 0x03, 0x04})) return Signature.ZIP;
    if (startsWith(prefix, new byte[] {0x1f, (byte) 0x8b})) return Signature.GZIP;
    return Signature.UNKNOWN;
  }

  private static boolean startsWith(byte[] data, byte[] prefix) {
    return matchesAt(data, 0, prefix);
  }

  private static boolean matchesAt(byte[] data, int offset, byte[] prefix) {
    if (data.length < offset + prefix.length) return false;
    for (int index = 0; index < prefix.length; index++) {
      if (data[offset + index] != prefix[index]) return false;
    }
    return true;
  }

  private static String hex(byte[] data) {
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < data.length; index++) {
      builder.append(String.format("%02x", data[index] & 0xFF));
    }
    return builder.toString();
  }
}
