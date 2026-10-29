package ph.kanta.reborn.core;

/**
 * Read-only view of the current pitch estimate.
 *
 * <p>The microphone has exactly one owner ({@code MicrophoneInput}, which runs
 * {@link DryPitchDetector} on its own analysis frame). Pitch-based features must read the
 * estimate through this port rather than opening a second capture: two {@code AudioRecord}s
 * would contend for the device, fight over the audio session, and double the battery cost.
 *
 * <p>Framework-free on purpose, so the pitch path can be exercised by the host-JVM
 * {@code coreRegression} task with a fake source.
 */
public interface PitchSource {

  /** The current pitch in Hz, or 0 when there is no usable estimate right now. */
  float currentPitchHz();

  /** When that estimate was measured, in milliseconds; 0 when unknown. */
  long measuredAtMs();
}
