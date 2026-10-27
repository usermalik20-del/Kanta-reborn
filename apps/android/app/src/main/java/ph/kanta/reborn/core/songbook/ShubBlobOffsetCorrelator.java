package ph.kanta.reborn.core.songbook;

/**
 * Read-only correlation of the legacy index trailing offset table against the
 * payload container size.
 *
 * <p>Mirrors the iOS LegacyCompatibility correlator. The verdict is deliberately
 * conservative: an offset table is only {@code STRUCTURALLY_PLAUSIBLE} when it has the
 * expected cardinality, is strictly ordered and every value stays inside the container.
 * Anything else is rejected, and a missing container size stays unavailable.
 */
public final class ShubBlobOffsetCorrelator {

  /** Why the offset table can or cannot be trusted. */
  public enum Verdict {
    UNAVAILABLE,
    REJECTED,
    STRUCTURALLY_PLAUSIBLE
  }

  /** Correlation summary. Unknown values stay null instead of being guessed. */
  public static final class Correlation {
    public final Long containerByteCount;
    public final int trailingValueCount;
    public final long[] trailingValueSamples;
    public final Long minimumTrailingValue;
    public final Long maximumTrailingValue;
    public final int nonIncreasingTransitionCount;
    public final Integer outOfBoundsValueCount;
    public final Long candidatePayloadByteCount;
    public final Verdict verdict;

    Correlation(
        Long containerByteCount,
        int trailingValueCount,
        long[] trailingValueSamples,
        Long minimumTrailingValue,
        Long maximumTrailingValue,
        int nonIncreasingTransitionCount,
        Integer outOfBoundsValueCount,
        Long candidatePayloadByteCount,
        Verdict verdict) {
      this.containerByteCount = containerByteCount;
      this.trailingValueCount = trailingValueCount;
      this.trailingValueSamples = trailingValueSamples;
      this.minimumTrailingValue = minimumTrailingValue;
      this.maximumTrailingValue = maximumTrailingValue;
      this.nonIncreasingTransitionCount = nonIncreasingTransitionCount;
      this.outOfBoundsValueCount = outOfBoundsValueCount;
      this.candidatePayloadByteCount = candidatePayloadByteCount;
      this.verdict = verdict;
    }
  }

  private ShubBlobOffsetCorrelator() {}

  /** Correlates the trailing offsets with the known container size. */
  public static Correlation correlate(
      long[] trailingValues, int expectedTitleCount, Long containerByteCount, int sampleLimit) {
    int limit = Math.max(0, sampleLimit);
    int sampleCount = Math.min(limit, trailingValues.length);
    long[] samples = new long[sampleCount];
    System.arraycopy(trailingValues, 0, samples, 0, sampleCount);

    Long minimum = null;
    Long maximum = null;
    int nonIncreasing = 0;
    for (int index = 0; index < trailingValues.length; index++) {
      long value = trailingValues[index];
      if (minimum == null || value < minimum) minimum = value;
      if (maximum == null || value > maximum) maximum = value;
      if (index > 0 && value <= trailingValues[index - 1]) nonIncreasing++;
    }

    if (containerByteCount == null) {
      return new Correlation(
          null,
          trailingValues.length,
          samples,
          minimum,
          maximum,
          nonIncreasing,
          null,
          null,
          Verdict.UNAVAILABLE);
    }

    int outOfBounds = 0;
    for (long value : trailingValues) {
      if (containerByteCount <= 0 || value >= containerByteCount) outOfBounds++;
    }

    boolean expectedCardinality =
        expectedTitleCount > 0 && trailingValues.length == expectedTitleCount;
    boolean strictOrdering = trailingValues.length == 1 || nonIncreasing == 0;
    boolean bounded = outOfBounds == 0;
    boolean plausible = expectedCardinality && strictOrdering && bounded;

    Long candidate = null;
    if (plausible && trailingValues.length > 0) {
      candidate = containerByteCount - trailingValues[0];
    }

    return new Correlation(
        containerByteCount,
        trailingValues.length,
        samples,
        minimum,
        maximum,
        nonIncreasing,
        outOfBounds,
        candidate,
        plausible ? Verdict.STRUCTURALLY_PLAUSIBLE : Verdict.REJECTED);
  }
}
