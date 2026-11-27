package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.TextureView;
import android.view.View;
import java.io.File;
import ph.kanta.reborn.SessionController;

/**
 * Muted looping video behind the interlude message. Owns one TextureView and
 * MediaPlayer pair and follows the session: visible only while the interlude
 * owns the stage and a clip is installed, transparent otherwise. The stage
 * text surfaces keep drawing on top of it.
 */
public final class InterludeVideoHost implements TextureView.SurfaceTextureListener {
  private final SessionController session;
  private final TextureView texture;
  private MediaPlayer player;
  private File attempted;

  public InterludeVideoHost(Context context, SessionController session) {
    this.session = session;
    texture = new TextureView(context);
    texture.setOpaque(false);
    texture.setSurfaceTextureListener(this);
    texture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    texture.setVisibility(View.GONE);
  }

  public View view() { return texture; }

  /** Re-evaluate visibility. The surface lifecycle callbacks start/stop the
   *  player; this only toggles the TextureView on session publishes. */
  public void sync() {
    boolean shown = session.interludeShownNow() && session.foregroundActive()
        && session.interludeVideoFile() != null;
    int visibility = shown ? View.VISIBLE : View.GONE;
    if (texture.getVisibility() != visibility) texture.setVisibility(visibility);
    if (!shown) { stopPlayer(); return; }
    File video = session.interludeVideoFile();
    if (player != null && attempted != null && attempted.equals(video)) return;
    if (texture.isAvailable()) startPlayer(video);
  }

  private void startPlayer(File video) {
    stopPlayer();
    try {
      player = new MediaPlayer();
      player.setDataSource(video.getAbsolutePath());
      player.setLooping(true);
      player.setVolume(0f, 0f);
      if (texture.isAvailable() && texture.getSurfaceTexture() != null)
        player.setSurface(new android.view.Surface(texture.getSurfaceTexture()));
      player.prepare();
      player.start();
      attempted = video;
    } catch (Exception failure) {
      stopPlayer();
    }
  }

  private void stopPlayer() {
    if (player != null) {
      try {
        player.stop();
      } catch (Exception ignored) {
      }
      player.release();
      player = null;
    }
    attempted = null;
  }

  public void release() {
    stopPlayer();
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
    File video = session.interludeVideoFile();
    if (session.interludeShownNow() && session.foregroundActive() && video != null)
      startPlayer(video);
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
    stopPlayer();
    return true;
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture st) {
  }
}
