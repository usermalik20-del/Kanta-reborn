package ph.kanta.reborn.core;

import java.util.ArrayDeque;

/** Maps audible output frames to source time across buffered tempo changes. */
public final class RenderedPlaybackClock {
  private static final class Span {
    final long frame;
    final double milliseconds, rate;

    Span(long frame, double milliseconds, double rate) {
      this.frame = frame;
      this.milliseconds = milliseconds;
      this.rate = rate;
    }
  }

  private final ArrayDeque<Span> spans = new ArrayDeque<>();
  private final int sampleRate;

  public RenderedPlaybackClock(int sampleRate) {
    if (sampleRate <= 0) throw new IllegalArgumentException("Invalid sample rate");
    this.sampleRate = sampleRate;
  }

  public void append(long frame, double milliseconds, double tempo) {
    if (frame < 0
        || !Double.isFinite(milliseconds)
        || milliseconds < 0
        || !Double.isFinite(tempo)
        || tempo < 0.5
        || tempo > 2) throw new IllegalArgumentException();
    if (!spans.isEmpty() && frame <= spans.getLast().frame) throw new IllegalArgumentException();
    spans.addLast(new Span(frame, milliseconds, tempo));
  }

  public long position(long playedFrames) {
  /** Drops every span; used after a seek restarts the audible timeline. */
  public void reset() {
    spans.clear();
  }

    while (spans.size() > 1) {
      Span first = spans.removeFirst();
      if (spans.getFirst().frame > playedFrames) {
        spans.addFirst(first);
        break;
      }
    }
    if (spans.isEmpty()) return 0;
    Span span = spans.getFirst();
    double time =
        span.milliseconds
            + Math.max(0, playedFrames - span.frame) * 1000.0 * span.rate / sampleRate;
    return (long) Math.min(Long.MAX_VALUE, time);
  }
}
