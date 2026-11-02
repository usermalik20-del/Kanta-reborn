package ph.kanta.reborn.core;

/** Storage budget for streaming large SoundFont imports without exhausting app storage. */
public final class SoundFontStoragePolicy {
  public static final long MINIMUM_FREE_BYTES = 64L * 1024 * 1024;
  public static final long RECHECK_INTERVAL_BYTES = 8L * 1024 * 1024;

  private SoundFontStoragePolicy() {}

  /** True when the next write can complete while preserving the minimum free-space reserve. */
  public static boolean canCopy(long availableBytes, long bytesToWrite) {
    return bytesToWrite >= 0
        && availableBytes >= MINIMUM_FREE_BYTES
        && bytesToWrite <= availableBytes - MINIMUM_FREE_BYTES;
  }

  /**
   * Keeps enough space for the reported remainder, or one full recheck interval when a
   * provider omits or under-reports its size. The current chunk is included in the budget.
   */
  public static boolean canContinueCopying(
      long availableBytes, long reportedRemainingBytes, long nextChunkBytes) {
    if (reportedRemainingBytes < -1 || nextChunkBytes < 0) return false;
    long lookahead = reportedRemainingBytes > 0
        ? Math.max(RECHECK_INTERVAL_BYTES, reportedRemainingBytes)
        : RECHECK_INTERVAL_BYTES;
    if (lookahead > Long.MAX_VALUE - nextChunkBytes) return false;
    return canCopy(availableBytes, lookahead + nextChunkBytes);
  }

  public static boolean hasReserve(long availableBytes) {
    return availableBytes >= MINIMUM_FREE_BYTES;
  }
}
