package ph.kanta.reborn;

import ph.kanta.reborn.core.RenderedPlaybackClock;

interface PlaybackBackend {
  long START_ACK_TIMEOUT_MS = 5_000L;

  interface Callback {
    void ready(PlaybackBackend player);

    /** The backend observed playback progress, not merely a successful start request. */
    void started(PlaybackBackend player);

    void completed(PlaybackBackend player);

    void failed(PlaybackBackend player);
  }

  void start();

  void pause();

  void release();

  long position();

  default long duration() { return 0L; }

  default boolean controlsSettled() {
    return true;
  }

  /** Returns the audible clock for lyric synchronization. */
  default RenderedPlaybackClock getClock() {
    return null;
  }

  void controls(float volume, float tempo, int pitch);
  /** Requests a jump to an absolute source time on the backend worker. */
  default void seekTo(long milliseconds) {}

  /**
   * The media audio session this backend renders into, for attaching session effects
   * (equalizer). Returns -1 when the backend has no dedicated session.
   */
  default int getAudioSessionId() {
    return -1;
  }

}
