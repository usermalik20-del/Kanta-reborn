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

  public static boolean hasReserve(long availableBytes) {
    return availableBytes >= MINIMUM_FREE_BYTES;
  }
}
