package ph.kanta.reborn;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import java.io.File;

/** All calls and MediaPlayer callbacks belong to the main Looper. */
final class FileAudioPlayback implements PlaybackBackend {
  private final MediaPlayer player = new MediaPlayer();
  private boolean released;
  private final Callback callback;

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
    try {
      return released ? 0 : player.getCurrentPosition();
    } catch (IllegalStateException e) {
      return 0;
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
