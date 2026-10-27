package ph.kanta.reborn.core.songbook;

import java.util.List;

/**
 * Neutral interoperability facade for the legacy indexed container.
 *
 * <p>Format-specific implementation details stay confined to the compatibility classes in
 * this package. Callers depend on this facade so a future format change cannot leak
 * vendor-specific concepts into the playback, library or UI owners.
 */
public final class IndexedContainerCompatibility {

  private IndexedContainerCompatibility() {}

  /** Probes an index container. */
  public static ShubIndexProbe.Result probe(
      byte[] indexData, Long containerByteCount, int sampleLimit) {
    return ShubIndexProbe.inspect(indexData, containerByteCount, sampleLimit);
  }

  /** Returns the title records of an index container. */
  public static List<ShubIndexProbe.TitleRecord> titles(byte[] indexData) {
    return ShubIndexProbe.decodeTitleRecords(indexData);
  }

  /** Correlates an offset table with a known container size. */
  public static ShubBlobOffsetCorrelator.Correlation correlate(
      long[] trailingValues, int expectedTitleCount, Long containerByteCount, int sampleLimit) {
    return ShubBlobOffsetCorrelator.correlate(
        trailingValues, expectedTitleCount, containerByteCount, sampleLimit);
  }

  /** Produces the payload spans described by an index container. */
  public static List<ShubBlobSpanIndex.PayloadSpan> spans(byte[] indexData, long containerByteCount)
      throws ShubBlobSpanIndex.SpanIndexException {
    return ShubBlobSpanIndex.spans(indexData, containerByteCount);
  }

  /** Classifies the payloads described by an index container. */
  public static ShubBlobPayloadProbe.Result classify(
      List<ShubBlobSpanIndex.PayloadSpan> spans,
      ShubBlobPayloadProbe.Window window,
      int prefixByteCount) {
    return ShubBlobPayloadProbe.inspect(spans, window, prefixByteCount);
  }
}
