package ph.kanta.reborn;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import ph.kanta.reborn.core.RenderedPlaybackClock;
import java.io.File;

/** All calls and MediaPlayer callbacks belong to the main Looper. */
public final class FileAudioPlayback implements PlaybackBackend {
  private static final int START_POLL_MS = 15;
  private final MediaPlayer player = new MediaPlayer();
  private final Handler main = new Handler(Looper.getMainLooper());
  private boolean released, started, controlsDirty, awaitingStart;
  private long startPosition, startDeadline;
  private final Runnable pollStart = this::pollPlaybackStart;
  private float requestedTempo = 1;
  private int requestedPitch;
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
            if (released) return;
            // A very short file can complete between startup polls.
            if (awaitingStart) acknowledgeStart();
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
    if (released || started) return;
    startPosition = player.getCurrentPosition();
    // Non-default PlaybackParams also starts the player. Apply them once, at the
    // actual start/resume boundary, instead of starting and pausing in controls().
    if (controlsDirty) applyControls();
    else player.start();
    started = true;
    awaitingStart = true;
    startDeadline = SystemClock.uptimeMillis() + START_ACK_TIMEOUT_MS;
    main.post(pollStart);
  }

  private void pollPlaybackStart() {
    if (released || !awaitingStart) return;
    try {
      // MediaPlayer has no audio-render-start event. Observe its first position advance.
      if (player.isPlaying() && player.getCurrentPosition() > startPosition) {
        acknowledgeStart();
        return;
      }
    } catch (IllegalStateException e) {
      callback.failed(this);
      return;
    }
    if (SystemClock.uptimeMillis() >= startDeadline) callback.failed(this);
    else main.postDelayed(pollStart, START_POLL_MS);
  }

  private void acknowledgeStart() {
    awaitingStart = false;
    main.removeCallbacks(pollStart);
    callback.started(this);
  }

  public void pause() {
    if (!released) {
      awaitingStart = false;
      main.removeCallbacks(pollStart);
      player.pause();
      started = false;
    }
  }

  @Override
  public void seekTo(long milliseconds) {
    if (!released) {
      try {
        player.seekTo((int) milliseconds);
      } catch (IllegalStateException ignored) {
      }
    }
  }


  @Override
  public int getAudioSessionId() {
    return released ? -1 : player.getAudioSessionId();
  }

  public void release() {
    if (!released) {
      released = true;
      main.removeCallbacks(pollStart);
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
    if (requestedTempo == tempo && requestedPitch == pitch) return;
    requestedTempo = tempo;
    requestedPitch = pitch;
    controlsDirty = true;
    if (started) applyControls();
  }

  private void applyControls() {
    player.setPlaybackParams(
        new PlaybackParams()
            .allowDefaults()
            .setSpeed(requestedTempo)
            .setPitch((float) Math.pow(2, requestedPitch / 12.0)));
    controlsDirty = false;
  }
}
