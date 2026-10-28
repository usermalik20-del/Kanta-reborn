package ph.kanta.reborn.core.songbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds payload spans from the legacy index trailing offset table.
 *
 * <p>Mirrors the iOS LegacyCompatibility span index. The spans describe where a payload
 * is expected inside the paired container; they are never read or decoded here, and a
 * rejected offset table produces an exception instead of a guessed range.
 */
public final class ShubBlobSpanIndex {

  /** Why a span list could not be produced. */
  public enum Failure {
    MALFORMED_TITLE_INDEX,
    OFFSET_CORRELATION_REJECTED,
    INVALID_SPAN
  }

  /** Raised when the index cannot describe the container. */
  public static final class SpanIndexException extends Exception {
    public final Failure failure;
    public final int recordIndex;

    SpanIndexException(Failure failure, int recordIndex) {
      super(failure.name());
      this.failure = failure;
      this.recordIndex = recordIndex;
    }
  }

  /** One expected payload range inside the container. */
  public static final class PayloadSpan {
    public final int recordIndex;
    public final long startOffset;
    public final long endOffset;

    PayloadSpan(int recordIndex, long startOffset, long endOffset) {
      this.recordIndex = recordIndex;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }

    /** Byte count covered by the span. */
    public long byteCount() {
      return endOffset - startOffset;
    }
  }

  private ShubBlobSpanIndex() {}

  /**
   * Produces the payload spans for a container of the given size.
   *
   * @param indexData the legacy index bytes; never modified
   * @param containerByteCount the size of the paired payload container
   */
  public static List<PayloadSpan> spans(byte[] indexData, long containerByteCount)
      throws SpanIndexException {
    ShubIndexProbe.Result report =
        ShubIndexProbe.inspect(indexData, containerByteCount, 0);
    if (report.titleIndexRecordCount <= 0
        || report.trailingBytesPerTitleRecord == null
        || report.trailingBytesPerTitleRecord != 4) {
      throw new SpanIndexException(Failure.MALFORMED_TITLE_INDEX, -1);
    }
    if (report.blobOffsetCorrelation.verdict
        != ShubBlobOffsetCorrelator.Verdict.STRUCTURALLY_PLAUSIBLE) {
      throw new SpanIndexException(Failure.OFFSET_CORRELATION_REJECTED, -1);
    }

    long[] offsets = ShubIndexProbe.trailingUInt32Values(indexData);
    if (offsets.length != report.titleIndexRecordCount) {
      throw new SpanIndexException(Failure.MALFORMED_TITLE_INDEX, -1);
    }

    List<PayloadSpan> spans = new ArrayList<>();
    for (int index = 0; index < offsets.length; index++) {
      long start = offsets[index];
      long end = index + 1 < offsets.length ? offsets[index + 1] : containerByteCount;
      if (end <= start || end > containerByteCount) {
        throw new SpanIndexException(Failure.INVALID_SPAN, index);
      }
      spans.add(new PayloadSpan(index, start, end));
    }
    if (spans.isEmpty()) throw new SpanIndexException(Failure.MALFORMED_TITLE_INDEX, -1);
    return Collections.unmodifiableList(spans);
  }
}
