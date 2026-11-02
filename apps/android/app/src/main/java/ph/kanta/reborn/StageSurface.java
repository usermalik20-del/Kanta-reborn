package ph.kanta.reborn;

import android.content.Context;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.view.*;
import android.widget.FrameLayout;
import java.io.File;

/** Lifecycle-bound visual output. Karaoke state and its clock stay in SessionController. */
final class StageSurface extends FrameLayout implements TextureView.SurfaceTextureListener {
  private final SessionController session;
  private final TextureView video;
  private final StageView lyrics;
  private MediaPlayer player;
  private Surface surface;
  private File attempted;
  private long generation = -1;
  private boolean active = true, prepared, mediaLive, cameraLive;

  StageSurface(Context context, SessionController session) {
    super(context);
    this.session = session;
    video = new TextureView(context);
    video.setOpaque(false);
    video.setSurfaceTextureListener(this);
    video.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(video, new FrameLayout.LayoutParams(-1, -1));
    lyrics = new StageView(context, session);
    addView(lyrics, new FrameLayout.LayoutParams(-1, -1));
    setMinimumHeight((int) (220 * getResources().getDisplayMetrics().density));
  }

  void setFullscreenMode(boolean value) {
    lyrics.setFullscreenMode(value);
  }

  void setVisualActive(boolean value) {
    active = value;
    if (!value) {
      closeVideo();
      attempted = null;
    } else refresh();
  }

  void refresh() {
    File wanted =
        active && session.foreground && !session.cameraPreviewEnabled ? session.stageVideo : null;
    if (generation != session.visualGeneration
        || (attempted != null && !attempted.equals(wanted))
        || wanted == null) {
      closeVideo();
      attempted = null;
      generation = session.visualGeneration;
    }
    if (wanted != null && attempted == null && video.isAvailable() && isAttachedToWindow())
      prepare(wanted);
    lyrics.mediaVisible(mediaLive || cameraLive);
    lyrics.refresh();
  }

  void mountCamera(android.view.View preview) {
    closeVideo();
    attempted = null;
    addView(preview, getChildCount() - 1, new FrameLayout.LayoutParams(-1, -1));
  }

  void cameraFrame(boolean visible) {
    cameraLive = visible;
    lyrics.mediaVisible(cameraLive || mediaLive);
  }

  void unmountCamera(android.view.View preview) {
    cameraFrame(false);
    removeView(preview);
  }

  boolean isCameraVisible() {
    return cameraLive;
  }

  private void prepare(File file) {
    attempted = file;
    long token = generation;
    MediaPlayer candidate = new MediaPlayer();
    player = candidate;
    try {
      surface = new Surface(video.getSurfaceTexture());
      candidate.setSurface(surface);
      candidate.setDataSource(file.getAbsolutePath());
      candidate.setVolume(0, 0);
      candidate.setLooping(true);
      candidate.setOnVideoSizeChangedListener(
          (p, width, height) -> {
            if (player == p) crop(width, height);
          });
      candidate.setOnPreparedListener(
          p -> {
            if (player != p || token != session.visualGeneration || !active || !session.foreground)
              return;
            try {
              int duration = p.getDuration();
              if (duration > 0)
                p.seekTo((int) ((SystemClock.elapsedRealtime() - session.visualEpoch) % duration));
              p.start();
              prepared = true;
              crop(p.getVideoWidth(), p.getVideoHeight());

            } catch (RuntimeException failure) {
              fail(p);
            }
          });
      candidate.setOnInfoListener(
          (p, what, extra) -> {
            if (player == p
                && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START
                && active
                && session.foreground) {
              mediaLive = true;
              lyrics.mediaVisible(true);
            }
            return false;
          });
      candidate.setOnErrorListener(
          (p, what, extra) -> {
            fail(p);
            return true;
          });
      candidate.prepareAsync();
    } catch (Exception failure) {
      fail(candidate);
    }
  }

  private void fail(MediaPlayer failed) {
    if (player != failed) return;
    closeVideo();
    session.message =
        "This background video cannot be displayed here. Choose another or use Scenic.";
    session.publish();
  }

  private void crop(int width, int height) {
    if (width <= 0 || height <= 0 || video.getWidth() <= 0 || video.getHeight() <= 0) return;
    float scale = Math.max(video.getWidth() / (float) width, video.getHeight() / (float) height);
    Matrix transform = new Matrix();
    transform.setScale(
        width * scale / video.getWidth(),
        height * scale / video.getHeight(),
        video.getWidth() / 2f,
        video.getHeight() / 2f);
    video.setTransform(transform);
  }

  boolean isVideoVisible() {
    return mediaLive;
  }

  private void closeVideo() {
    prepared = false;
    mediaLive = false;
    lyrics.mediaVisible(cameraLive);
    if (player != null) {
      player.release();
      player = null;
    }
    if (surface != null) {
      surface.release();
      surface = null;
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    refresh();
  }

  @Override
  protected void onDetachedFromWindow() {
    closeVideo();
    attempted = null;
    super.onDetachedFromWindow();
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
    attempted = null;
    refresh();
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
    if (player != null && prepared) crop(player.getVideoWidth(), player.getVideoHeight());
  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
    closeVideo();
    attempted = null;
    return true;
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
