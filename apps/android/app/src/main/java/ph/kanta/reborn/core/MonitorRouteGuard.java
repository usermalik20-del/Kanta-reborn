package ph.kanta.reborn.core;

/** A route loss retires this activation; a reconnect requires a new user action. */
public final class MonitorRouteGuard {
  public enum Decision {
    WAIT,
    ALLOW,
    STOP
  }

  /** Readiness bound for wired and USB routes. */
  public static final long READY_MS = 1500;

  private final int deviceId;
  private final long startedAt;
  private final long readyDeadlineMs;
  private boolean verified, closed;

  public MonitorRouteGuard(int deviceId, long startedAt) {
    this(deviceId, startedAt, READY_MS);
  }

  /**
   * @param readyDeadlineMs how long an activation may stay unverified before it is refused.
   *     Bluetooth headsets need a longer bound because link negotiation takes one to three
   *     seconds.
   */
  public MonitorRouteGuard(int deviceId, long startedAt, long readyDeadlineMs) {
    if (deviceId <= 0 || startedAt < 0 || readyDeadlineMs <= 0)
      throw new IllegalArgumentException();
    this.deviceId = deviceId;
    this.startedAt = startedAt;
    this.readyDeadlineMs = readyDeadlineMs;
  }

  public synchronized Decision verify(int[] routedIds, long now) {
    if (closed) return Decision.STOP;
    long elapsed = now - startedAt;
    if (elapsed < 0 || (!verified && elapsed >= readyDeadlineMs)) return close();
    if (routedIds == null || routedIds.length == 0) {
      if (verified || elapsed >= readyDeadlineMs) return close();
      return Decision.WAIT;
    }
    if (routedIds.length != 1 || routedIds[0] != deviceId) return close();
    verified = true;
    return Decision.ALLOW;
  }

  public synchronized Decision close() {
    closed = true;
    return Decision.STOP;
  }
}
