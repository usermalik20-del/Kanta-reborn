package ph.kanta.reborn.core.songbook;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Research-only structural probe for the legacy indexed-container format ("shubidx").
 *
 * <p>This mirrors the iOS LegacyCompatibility probe so both platforms describe the same
 * container. The probe never mutates its input and keeps unknown fields opaque until
 * their semantics are confirmed against additional fixtures. It is a compatibility
 * reader only: it never decodes or executes a payload.
 */
public final class ShubIndexProbe {

  /** One metadata record (kind 2..7): an opaque prefix plus its decoded text. */
  public static final class MetadataRecord {
    public final int offset;
    public final int kind;
    public final int declaredLength;
    public final String metadataHex;
    public final String text;

    MetadataRecord(
        int offset, int kind, int declaredLength, String metadataHex, String text) {
      this.offset = offset;
      this.kind = kind;
      this.declaredLength = declaredLength;
      this.metadataHex = metadataHex;
      this.text = text;
    }
  }

  /** One title index record (kind 1). The opaque words stay opaque. */
  public static final class TitleRecord {
    public final int offset;
    public final int declaredLength;
    public final long opaqueA;
    public final long opaqueB;
    public final String title;

    TitleRecord(int offset, int declaredLength, long opaqueA, long opaqueB, String title) {
      this.offset = offset;
      this.declaredLength = declaredLength;
      this.opaqueA = opaqueA;
      this.opaqueB = opaqueB;
      this.title = title;
    }
  }

  /** The probe result. Unknown values stay null or empty instead of being guessed. */
  public static final class Result {
    public final int fileSize;
    public final long[] headerWordsLE;
    public final Integer metadataTableOffset;
    public final int metadataRecordCount;
    public final Map<Integer, Integer> metadataRecordCountsByKind;
    public final List<MetadataRecord> metadataSamples;
    public final Integer firstUnparsedOffset;
    public final Integer firstUnparsedKind;
    public final Integer titleIndexOffset;
    public final int titleIndexRecordCount;
    public final List<TitleRecord> titleSamples;
    public final Integer trailingOffset;
    public final int trailingByteCount;
    public final Integer trailingBytesPerTitleRecord;
    public final ShubBlobOffsetCorrelator.Correlation blobOffsetCorrelation;

    Result(
        int fileSize,
        long[] headerWordsLE,
        Integer metadataTableOffset,
        int metadataRecordCount,
        Map<Integer, Integer> metadataRecordCountsByKind,
        List<MetadataRecord> metadataSamples,
        Integer firstUnparsedOffset,
        Integer firstUnparsedKind,
        Integer titleIndexOffset,
        int titleIndexRecordCount,
        List<TitleRecord> titleSamples,
        Integer trailingOffset,
        int trailingByteCount,
        Integer trailingBytesPerTitleRecord,
        ShubBlobOffsetCorrelator.Correlation blobOffsetCorrelation) {
      this.fileSize = fileSize;
      this.headerWordsLE = headerWordsLE;
      this.metadataTableOffset = metadataTableOffset;
      this.metadataRecordCount = metadataRecordCount;
      this.metadataRecordCountsByKind = metadataRecordCountsByKind;
      this.metadataSamples = metadataSamples;
      this.firstUnparsedOffset = firstUnparsedOffset;
      this.firstUnparsedKind = firstUnparsedKind;
      this.titleIndexOffset = titleIndexOffset;
      this.titleIndexRecordCount = titleIndexRecordCount;
      this.titleSamples = titleSamples;
      this.trailingOffset = trailingOffset;
      this.trailingByteCount = trailingByteCount;
      this.trailingBytesPerTitleRecord = trailingBytesPerTitleRecord;
      this.blobOffsetCorrelation = blobOffsetCorrelation;
    }
  }

  private static final int HEADER_WORD_COUNT = 7;
  private static final int CANDIDATE_WORD_INDEX = 5;
  private static final String BINARY_TEXT = "<binary>";

  private ShubIndexProbe() {}

  /** Probes the container with the default sample limit. */
  public static Result inspect(byte[] data) {
    return inspect(data, null, 128);
  }

  /**
   * Probes the container.
   *
   * @param data the index bytes; never modified
   * @param containerByteCount size of the paired payload container, or null when unknown
   * @param sampleLimit maximum number of retained samples per table
   */
  public static Result inspect(byte[] data, Long containerByteCount, int sampleLimit) {
    long[] header = readHeaderWords(data, HEADER_WORD_COUNT);
    long candidate = header.length > CANDIDATE_WORD_INDEX ? header[CANDIDATE_WORD_INDEX] : -1L;
    if (candidate < 0 || candidate >= data.length) {
      return emptyResult(data, header, containerByteCount, sampleLimit);
    }

    int limit = Math.max(0, sampleLimit);
    List<MetadataRecord> metadataSamples = new ArrayList<>();
    Map<Integer, Integer> metadataCounts = new LinkedHashMap<>();
    int offset = (int) candidate;
    int metadataRecordCount = 0;

    while (offset + 2 <= data.length) {
      int kind = data[offset] & 0xFF;
      if (kind == 1) break;
      int prefixLength = metadataPrefixLength(kind);
      if (prefixLength < 0) break;

      int totalLength = data[offset + 1] & 0xFF;
      if (totalLength < prefixLength || totalLength <= 0 || offset + totalLength > data.length) {
        break;
      }

      metadataRecordCount++;
      metadataCounts.put(kind, metadataCounts.getOrDefault(kind, 0) + 1);
      if (metadataSamples.size() < limit) {
        metadataSamples.add(
            new MetadataRecord(
                offset,
                kind,
                totalLength,
                hex(data, offset + 2, offset + prefixLength),
                decodeText(data, offset + prefixLength, offset + totalLength)));
      }
      offset += totalLength;
    }

    Integer titleIndexOffset = offset < data.length && (data[offset] & 0xFF) == 1 ? offset : null;
    List<TitleRecord> titleSamples = new ArrayList<>();
    int titleCount = 0;

    while (offset + 12 <= data.length && (data[offset] & 0xFF) == 1) {
      int totalLength = data[offset + 1] & 0xFF;
      if (totalLength < 12 || offset + totalLength > data.length) break;

      titleCount++;
      if (titleSamples.size() < limit) {
        titleSamples.add(
            new TitleRecord(
                offset,
                totalLength,
                readUInt32LE(data, offset + 4),
                readUInt32LE(data, offset + 8),
                decodeText(data, offset + 12, offset + totalLength)));
      }
      offset += totalLength;
    }

    int trailingByteCount = Math.max(0, data.length - offset);
    Integer bytesPerTitle =
        titleCount > 0 && trailingByteCount % titleCount == 0
            ? trailingByteCount / titleCount
            : null;
    long[] trailingValues = readTrailingValues(data, offset, titleCount, bytesPerTitle);

    return new Result(
        data.length,
        header,
        (int) candidate,
        metadataRecordCount,
        Collections.unmodifiableMap(metadataCounts),
        Collections.unmodifiableList(metadataSamples),
        titleIndexOffset,
        titleIndexOffset == null ? null : data[titleIndexOffset] & 0xFF,
        titleIndexOffset,
        titleCount,
        Collections.unmodifiableList(titleSamples),
        offset < data.length ? offset : null,
        trailingByteCount,
        bytesPerTitle,
        ShubBlobOffsetCorrelator.correlate(
            trailingValues, titleCount, containerByteCount, sampleLimit));
  }

  /** Returns every title record in the index. */
  public static List<TitleRecord> decodeTitleRecords(byte[] data) {
    Result report = inspect(data, null, 0);
    List<TitleRecord> records = new ArrayList<>();
    Integer start = report.titleIndexOffset;
    if (start == null) return records;

    int offset = start;
    while (offset + 12 <= data.length && (data[offset] & 0xFF) == 1) {
      int totalLength = data[offset + 1] & 0xFF;
      if (totalLength < 12 || offset + totalLength > data.length) break;
      records.add(
          new TitleRecord(
              offset,
              totalLength,
              readUInt32LE(data, offset + 4),
              readUInt32LE(data, offset + 8),
              decodeText(data, offset + 12, offset + totalLength)));
      offset += totalLength;
    }
    return Collections.unmodifiableList(records);
  }

  /** Returns the trailing offset table of the index. */
  public static long[] trailingUInt32Values(byte[] data) {
    Result report = inspect(data, null, 0);
    if (report.trailingOffset == null) return new long[0];
    return readTrailingValues(
        data, report.trailingOffset, report.titleIndexRecordCount, report.trailingBytesPerTitleRecord);
  }

  private static Result emptyResult(
      byte[] data, long[] header, Long containerByteCount, int sampleLimit) {
    return new Result(
        data.length,
        header,
        null,
        0,
        Collections.emptyMap(),
        Collections.emptyList(),
        null,
        null,
        null,
        0,
        Collections.emptyList(),
        null,
        0,
        null,
        ShubBlobOffsetCorrelator.correlate(new long[0], 0, containerByteCount, sampleLimit));
  }

  private static int metadataPrefixLength(int kind) {
    switch (kind) {
      case 2:
      case 3:
        return 6;
      case 4:
      case 5:
        return 5;
      case 6:
      case 7:
        return 4;
      default:
        return -1;
    }
  }

  private static long[] readTrailingValues(
      byte[] data, int offset, int titleCount, Integer bytesPerTitle) {
    if (titleCount <= 0 || bytesPerTitle == null || bytesPerTitle != 4) return new long[0];
    long[] values = new long[titleCount];
    for (int index = 0; index < titleCount; index++) {
      values[index] = readUInt32LE(data, offset + index * 4);
    }
    return values;
  }

  private static long[] readHeaderWords(byte[] data, int count) {
    int words = Math.min(count, data.length / 4);
    long[] values = new long[words];
    for (int index = 0; index < words; index++) {
      values[index] = readUInt32LE(data, index * 4);
    }
    return values;
  }

  private static long readUInt32LE(byte[] data, int offset) {
    if (offset + 4 > data.length) return 0L;
    return (data[offset] & 0xFFL)
        | ((data[offset + 1] & 0xFFL) << 8)
        | ((data[offset + 2] & 0xFFL) << 16)
        | ((data[offset + 3] & 0xFFL) << 24);
  }

  private static String hex(byte[] data, int from, int to) {
    StringBuilder builder = new StringBuilder();
    for (int index = from; index < to && index < data.length; index++) {
      builder.append(String.format("%02x", data[index] & 0xFF));
    }
    return builder.toString();
  }

  private static String decodeText(byte[] data, int from, int to) {
    if (from < 0 || to > data.length || from >= to) return "";
    byte[] slice = new byte[to - from];
    System.arraycopy(data, from, slice, 0, slice.length);
    String utf8 = new String(slice, StandardCharsets.UTF_8);
    if (!utf8.contains("�")) return utf8;
    return new String(slice, StandardCharsets.ISO_8859_1);
  }
}
