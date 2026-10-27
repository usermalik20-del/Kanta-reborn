package ph.kanta.reborn.core.songbook;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Domain regression checks for the legacy indexed-container compatibility layer.
 *
 * <p>No framework dependency: the checks run on a server JVM before cloud CI spends
 * emulator minutes. The fixtures are synthetic, so no song catalogue ever enters the
 * repository.
 */
public final class ShubCompatibilityRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static void append(ByteArrayOutputStream out, int... values) {
    for (int value : values) out.write(value);
  }

  private static void appendText(ByteArrayOutputStream out, String text) {
    for (byte value : text.getBytes(java.nio.charset.StandardCharsets.UTF_8)) out.write(value);
  }

  /** A 28 byte header whose sixth word points at the first record. */
  private static ByteArrayOutputStream header(int candidateOffset) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    append(out, 0, 0, 0, 0);
    append(out, 0, 0, 0, 0);
    append(out, 0, 0, 0, 0);
    append(out, 0, 0, 0, 0);
    append(out, 0, 0, 0, 0);
    append(
        out,
        candidateOffset & 0xFF,
        (candidateOffset >> 8) & 0xFF,
        (candidateOffset >> 16) & 0xFF,
        (candidateOffset >> 24) & 0xFF);
    append(out, 0, 0, 0, 0);
    return out;
  }

  private static void probeFollowsObservedMetadataRecordLayouts() {
    ByteArrayOutputStream out = header(28);
    append(out, 0x06, 0x08, 0x00, 0x00);
    appendText(out, "Male");
    append(out, 0x04, 0x09, 0x02, 0x00, 0x00);
    appendText(out, "Rock");
    append(out, 0x01, 0x04, 0x00, 0x00);

    ShubIndexProbe.Result report = ShubIndexProbe.inspect(out.toByteArray());
    check(report.metadataTableOffset != null && report.metadataTableOffset == 28, "Metadata offset");
    check(report.metadataRecordCount == 2, "Metadata record count");
    check(
        report.metadataRecordCountsByKind.getOrDefault(6, 0) == 1,
        "Metadata kind 6 count");
    check(
        report.metadataRecordCountsByKind.getOrDefault(4, 0) == 1,
        "Metadata kind 4 count");
    check(
        report.metadataSamples.size() == 2
            && report.metadataSamples.get(0).text.equals("Male")
            && report.metadataSamples.get(1).text.equals("Rock"),
        "Metadata text decoding");
    check(
        report.firstUnparsedKind != null && report.firstUnparsedKind == 1,
        "First unparsed kind");
  }

  private static void probeParsesTitleIndexAndTrailingRelationship() {
    ByteArrayOutputStream out = header(28);
    append(out, 0x06, 0x08, 0x00, 0x00);
    appendText(out, "Male");
    append(out, 0x01, 0x11, 0x01, 0x00, 1, 0, 0, 0, 2, 0, 0, 0);
    appendText(out, "Dream");
    append(out, 0x09, 0x00, 0x01, 0x02);

    ShubIndexProbe.Result report = ShubIndexProbe.inspect(out.toByteArray());
    check(report.metadataRecordCount == 1, "Title fixture metadata count");
    check(report.titleIndexRecordCount == 1, "Title index record count");
    check(
        !report.titleSamples.isEmpty() && report.titleSamples.get(0).title.equals("Dream"),
        "Title text decoding");
    check(
        !report.titleSamples.isEmpty() && report.titleSamples.get(0).opaqueA == 1L,
        "Title opaque word A");
    check(
        !report.titleSamples.isEmpty() && report.titleSamples.get(0).opaqueB == 2L,
        "Title opaque word B");
    check(report.trailingByteCount == 4, "Trailing byte count");
    check(
        report.trailingBytesPerTitleRecord != null && report.trailingBytesPerTitleRecord == 4,
        "Trailing bytes per title record");
  }

  private static void correlatorAcceptsStrictlyIncreasingBoundedOffsets() {
    ShubBlobOffsetCorrelator.Correlation result =
        ShubBlobOffsetCorrelator.correlate(new long[] {16, 24, 40}, 3, 64L, 2);
    check(
        result.verdict == ShubBlobOffsetCorrelator.Verdict.STRUCTURALLY_PLAUSIBLE,
        "Plausible verdict");
    check(result.trailingValueCount == 3, "Trailing value count");
    check(
        Arrays.equals(result.trailingValueSamples, new long[] {16, 24}),
        "Trailing value samples");
    check(
        result.minimumTrailingValue != null && result.minimumTrailingValue == 16L,
        "Minimum trailing value");
    check(
        result.maximumTrailingValue != null && result.maximumTrailingValue == 40L,
        "Maximum trailing value");
    check(result.nonIncreasingTransitionCount == 0, "Non increasing transitions");
    check(
        result.outOfBoundsValueCount != null && result.outOfBoundsValueCount == 0,
        "Out of bounds values");
    check(
        result.candidatePayloadByteCount != null && result.candidatePayloadByteCount == 48L,
        "Candidate payload byte count");
  }

  private static void correlatorRejectsNonIncreasingOrOutOfBoundsOffsets() {
    ShubBlobOffsetCorrelator.Correlation result =
        ShubBlobOffsetCorrelator.correlate(new long[] {16, 16, 80}, 3, 64L, 128);
    check(result.verdict == ShubBlobOffsetCorrelator.Verdict.REJECTED, "Rejected verdict");
    check(result.nonIncreasingTransitionCount == 1, "Non increasing transition count");
    check(
        result.outOfBoundsValueCount != null && result.outOfBoundsValueCount == 1,
        "Out of bounds value count");
    check(result.candidatePayloadByteCount == null, "Rejected candidate payload size");
  }

  private static void correlatorKeepsVerdictUnavailableWithoutContainer() {
    ShubBlobOffsetCorrelator.Correlation result =
        ShubBlobOffsetCorrelator.correlate(new long[] {16, 24}, 2, null, 128);
    check(result.verdict == ShubBlobOffsetCorrelator.Verdict.UNAVAILABLE, "Unavailable verdict");
    check(result.outOfBoundsValueCount == null, "Unavailable bounds count");
    check(result.candidatePayloadByteCount == null, "Unavailable candidate payload size");
  }

  private static void probeCorrelatesOneTrailingUInt32PerTitleAgainstContainerSize() {
    ByteArrayOutputStream out = header(28);
    append(out, 0x01, 0x0d, 0x01, 0x00, 1, 0, 0, 0, 2, 0, 0, 0);
    appendText(out, "A");
    append(out, 0x01, 0x0d, 0x01, 0x00, 3, 0, 0, 0, 4, 0, 0, 0);
    appendText(out, "B");
    append(out, 16, 0, 0, 0);
    append(out, 32, 0, 0, 0);

    ShubIndexProbe.Result report = ShubIndexProbe.inspect(out.toByteArray(), 64L, 128);
    check(report.titleIndexRecordCount == 2, "Correlated title count");
    check(
        report.trailingBytesPerTitleRecord != null && report.trailingBytesPerTitleRecord == 4,
        "Correlated trailing stride");
    check(
        report.blobOffsetCorrelation.trailingValueCount == 2,
        "Correlated trailing value count");
    check(
        report.blobOffsetCorrelation.verdict
            == ShubBlobOffsetCorrelator.Verdict.STRUCTURALLY_PLAUSIBLE,
        "Correlated verdict");
    check(
        Arrays.equals(report.blobOffsetCorrelation.trailingValueSamples, new long[] {16, 32}),
        "Correlated trailing samples");
  }

  private static void spanIndexBuildsBoundedPayloadSpans() throws Exception {
    ByteArrayOutputStream out = header(28);
    append(out, 0x01, 0x0d, 0x01, 0x00, 1, 0, 0, 0, 2, 0, 0, 0);
    appendText(out, "A");
    append(out, 0x01, 0x0d, 0x01, 0x00, 3, 0, 0, 0, 4, 0, 0, 0);
    appendText(out, "B");
    append(out, 16, 0, 0, 0);
    append(out, 32, 0, 0, 0);

    List<ShubBlobSpanIndex.PayloadSpan> spans =
        IndexedContainerCompatibility.spans(out.toByteArray(), 64L);
    check(spans.size() == 2, "Span count");
    check(spans.get(0).startOffset == 16L && spans.get(0).endOffset == 32L, "First span range");
    check(spans.get(1).startOffset == 32L && spans.get(1).endOffset == 64L, "Second span range");
    check(spans.get(1).byteCount() == 32L, "Second span size");
  }

  private static void spanIndexRejectsUnusableOffsetTables() {
    // Keep one trailing offset per title so the correlator reaches the ordering
    // check instead of rejecting a malformed stride before this regression runs.
    ByteArrayOutputStream out = header(28);
    append(out, 0x01, 0x0d, 0x01, 0x00, 1, 0, 0, 0, 2, 0, 0, 0);
    appendText(out, "A");
    append(out, 0x01, 0x0d, 0x01, 0x00, 3, 0, 0, 0, 4, 0, 0, 0);
    appendText(out, "B");
    append(out, 16, 0, 0, 0);
    append(out, 16, 0, 0, 0);
    boolean rejected = false;
    try {
      IndexedContainerCompatibility.spans(out.toByteArray(), 64L);
    } catch (ShubBlobSpanIndex.SpanIndexException expected) {
      rejected =
          expected.failure
              == ShubBlobSpanIndex.Failure.OFFSET_CORRELATION_REJECTED;
    }
    check(rejected, "Non increasing offset table was not rejected by correlation");
  }

  private static void payloadProbeClassifiesKnownSignatures() {
    check(
        ShubBlobPayloadProbe.signature(new byte[] {'M', 'T', 'h', 'd'}) ==
            ShubBlobPayloadProbe.Signature.STANDARD_MIDI,
        "MIDI signature");
    check(
        ShubBlobPayloadProbe.signature(
                new byte[] {
                  'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'A', 'V', 'E'
                })
            == ShubBlobPayloadProbe.Signature.WAVE,
        "Wave signature");
    check(
        ShubBlobPayloadProbe.signature(new byte[] {'I', 'D', '3', 4}) ==
            ShubBlobPayloadProbe.Signature.MP3_ID3,
        "MP3 signature");
    check(
        ShubBlobPayloadProbe.signature(new byte[] {'M', 'K', 'M', 'D', 1}) ==
            ShubBlobPayloadProbe.Signature.MKMD_V1,
        "Container signature");
    check(
        ShubBlobPayloadProbe.signature(new byte[] {0x50, 0x4b, 0x03, 0x04}) ==
            ShubBlobPayloadProbe.Signature.ZIP,
        "Zip signature");
    check(
        ShubBlobPayloadProbe.signature(new byte[] {0x1f, (byte) 0x8b, 8}) ==
            ShubBlobPayloadProbe.Signature.GZIP,
        "Gzip signature");
    check(
        ShubBlobPayloadProbe.signature(new byte[] {9, 9, 9, 9}) ==
            ShubBlobPayloadProbe.Signature.UNKNOWN,
        "Unknown signature");
  }

  private static void facadeKeepsFormatDetailsBehindOneSurface() {
    byte[] empty = new byte[0];
    ShubIndexProbe.Result report = IndexedContainerCompatibility.probe(empty, null, 0);
    check(report.titleIndexRecordCount == 0, "Facade probe of an empty container");
    check(IndexedContainerCompatibility.titles(empty).isEmpty(), "Facade titles of an empty index");
    check(
        IndexedContainerCompatibility.correlate(new long[0], 0, null, 0).verdict
            == ShubBlobOffsetCorrelator.Verdict.UNAVAILABLE,
        "Facade correlation without a container");
  }

  /** Runs every compatibility regression check. */
  public static int run() {
    checks = 0;
    probeFollowsObservedMetadataRecordLayouts();
    probeParsesTitleIndexAndTrailingRelationship();
    correlatorAcceptsStrictlyIncreasingBoundedOffsets();
    correlatorRejectsNonIncreasingOrOutOfBoundsOffsets();
    correlatorKeepsVerdictUnavailableWithoutContainer();
    probeCorrelatesOneTrailingUInt32PerTitleAgainstContainerSize();
    try {
      spanIndexBuildsBoundedPayloadSpans();
    } catch (Exception e) {
      throw new AssertionError("Span index failed: " + e);
    }
    spanIndexRejectsUnusableOffsetTables();
    payloadProbeClassifiesKnownSignatures();
    facadeKeepsFormatDetailsBehindOneSurface();
    return checks;
  }
}
