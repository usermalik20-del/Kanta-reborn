package ph.kanta.reborn.core;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Submits optional background work without leaking a busy latch when its owner is closing. */
public final class SafeExecutorSubmission {
  private SafeExecutorSubmission() {}

  public static boolean execute(Executor executor, Runnable task, AtomicBoolean busy) {
    try {
      executor.execute(task);
      return true;
    } catch (RejectedExecutionException rejected) {
      busy.set(false);
      return false;
    }
  }
}
