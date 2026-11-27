package ph.kanta.reborn.core;

/** One capture activation. A preference is not evidence; route loss permanently retires it. */
public final class InputRouteGuard {
  public enum State { WAITING, VERIFIED, REJECTED }
  private final int preferredId;
  private final long deadline;
  private int verifiedId;
  private State state = State.WAITING;

  public InputRouteGuard(int preferredId, long startedAt) {
    this.preferredId = preferredId;
    deadline = startedAt + 1500;
  }

  public State observe(int actualId, long now) {
    if (state == State.REJECTED) return state;
    if (state == State.VERIFIED) {
      if (actualId != verifiedId) state = State.REJECTED;
    } else if (now > deadline) {
      state = State.REJECTED;
    } else if (actualId > 0 && (preferredId == 0 || preferredId == actualId)) {
      verifiedId = actualId;
      state = State.VERIFIED;
    }
    return state;
  }
}
