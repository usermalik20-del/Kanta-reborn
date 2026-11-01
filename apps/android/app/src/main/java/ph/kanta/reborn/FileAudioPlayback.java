package ph.kanta.reborn;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import ph.kanta.reborn.core.RenderedPlaybackClock;
import java.io.File;

/** All calls and MediaPlayer callbacks belong to the main Looper. */
public final class FileAudioPlayback implements PlaybackBackend {
  private final MediaPlayer player = new MediaPlayer();
  private boolean released;
  private final Callback callback;
  private final RenderedPlaybackClock clock = new RenderedPlaybackClock(44100);
  private long lastPosition = 0;
  private long framesRendered = 0;

  FileAudioPlayback(File file, Callback callback) throws Exception {
    this.callback = callback;
    player.setAudioAttributes(
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build());
    try {
      player.setDataSource(file.getAbsolutePath());
      player.setOnPreparedListener(
          p -> {
            if (!released) callback.ready(this);
          });
      player.setOnCompletionListener(
          p -> {
            if (!released) callback.completed(this);
          });
      player.setOnErrorListener(
          (p, what, extra) -> {
            if (!released) callback.failed(this);
            return true;
          });
      player.prepareAsync();
    } catch (Exception e) {
      release();
      throw e;
    }
  }

  public void start() {
    player.start();
    callback.started(this);
  }

  public void pause() {
    if (!released) player.pause();
  }

  public void release() {
    if (!released) {
      released = true;
      player.release();
    }
  }

  public long position() {
    try { return released ? 0 : player.getCurrentPosition(); }
    catch (IllegalStateException e) { return 0; }
  }
  public long duration() {
    try { return released ? 0 : Math.max(0, player.getDuration()); }
    catch (IllegalStateException e) { return 0; }
  }

  /** Returns the audible clock for lyric synchronization. */
  public RenderedPlaybackClock getClock() {
    return clock;
  }

  /** Updates the clock with current playback position. Call periodically. */
  public void updateClock() {
    if (released) return;
    long pos = position();
    if (pos > lastPosition) {
      // Approximate frames: 44100 samples/sec * position(ms) / 1000
      long frames = pos * 44100L / 1000L;
      clock.append(framesRendered, pos, 1.0); // tempo=1 for MediaPlayer
      framesRendered = frames;
      lastPosition = pos;
    }
  }

  public void controls(float volume, float tempo, int pitch) {
    if (released) return;
    player.setVolume(volume, volume);
    boolean wasPlaying = player.isPlaying();
    player.setPlaybackParams(
        new PlaybackParams()
            .allowDefaults()
            .setSpeed(tempo)
            .setPitch((float) Math.pow(2, pitch / 12.0)));
    // MediaPlayer may start when nonzero playback parameters are assigned.
    if (!wasPlaying && player.isPlaying()) player.pause();
  }
}
