package ph.kanta.reborn.core;

import java.util.concurrent.atomic.AtomicLong;

/** Control requests cross threads; rendered/heard updates belong to the one audio worker. */
public final class AudibleControlFence {
  private final AtomicLong requested = new AtomicLong();
  private volatile long audible;
  private long pending, firstFrame;

  public long request() {
    return requested.incrementAndGet();
  }

  public void rendered(long request, long firstFrame) {
    if (request <= 0 || request > requested.get() || firstFrame < 0)
      throw new IllegalArgumentException();
    if (request < pending) return;
    pending = request;
    this.firstFrame = firstFrame;
  }

  /** A flush discards buffered old settings; the first new frame uses the latest settings. */
  public void rebased() {
    firstFrame = 0;
  }

  public void heard(long playbackHead) {
    if (playbackHead >= firstFrame) audible = pending;
  }

  public boolean settled() {
    return requested.get() == audible;
  }
}
