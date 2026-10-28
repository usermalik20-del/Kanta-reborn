package ph.kanta.reborn;

import ph.kanta.reborn.core.RenderedPlaybackClock;

interface PlaybackBackend {
  interface Callback {
    void ready(PlaybackBackend player);

    void started(PlaybackBackend player);

    void completed(PlaybackBackend player);

    void failed(PlaybackBackend player);
  }

  void start();

  void pause();

  void release();

  long position();

  default boolean controlsSettled() {
    return true;
  }

  /** Returns the audible clock for lyric synchronization. */
  default RenderedPlaybackClock getClock() {
    return null;
  }

  void controls(float volume, float tempo, int pitch);
}
