package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.camera.view.PreviewView;

import java.io.File;

/**
 * Renders background media: solid gradient, photo, camera preview, or BGV video.
 *
 * <p>Per {@link replica/design/components.md} BackgroundStage spec.
 * Draws behind StageSurface and LyricStage.
 */
public final class BackgroundStage extends FrameLayout {

  public enum Source {
    SOLID,
    PHOTO,
    CAMERA,
    BGV
  }

  private final Paint solidPaintTop;
  private final Paint solidPaintBottom;
  private final Paint photoScrimPaint;
  private final Paint videoOverlayPaint;

  private Source currentSource = Source.SOLID;
  private ImageView photoView;
  private TextureView videoView;
  private PreviewView cameraView;
  private boolean isTransitioning = false;
  private float transitionProgress = 0f;
  private MediaPlayer videoPlayer;
  private Surface videoSurface;
  private File videoFile;
  private File failedVideoFile;
  private int videoGeneration;
  private boolean visualActive;
  private android.graphics.Bitmap photoBitmap;

  public BackgroundStage(Context context) {
    this(context, null);
  }

  public BackgroundStage(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);

    // Solid gradient: canvasTop → canvasBottom
    solidPaintTop = new Paint();
    solidPaintTop.setColor(0xFF140A26); // canvasTop

    solidPaintBottom = new Paint();
    solidPaintBottom.setColor(0xFF0C0618); // canvasBottom

    // Photo scrim: bottom 40% gradient to transparent
    photoScrimPaint = new Paint();
    photoScrimPaint.setColor(0x660A0614); // canvas at 0.4 alpha

    // Video overlay for lyric legibility
    videoOverlayPaint = new Paint();
    videoOverlayPaint.setColor(0xCC000000); // backgroundVideoOverlay
  }

  /** Sets the background source. */
  public void setSource(Source source) {
    if (source == currentSource) return;

    Source oldSource = currentSource;
    currentSource = source;
    failedVideoFile = null;

    // Cross-fade transition
    isTransitioning = true;
    transitionProgress = 0f;

    // Swap child views
    stopVideo();
    removeAllViews();
    switch (source) {
      case PHOTO:
        if (photoView == null) {
          photoView = new ImageView(getContext());
          photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
          photoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        addView(photoView, 0);
        break;
      case BGV:
        if (videoView == null) {
          videoView = new TextureView(getContext());
          videoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
          videoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture texture, int width, int height) {
              startVideoIfReady();
            }
            @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture texture, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture texture) {
              stopVideo();
              return true;
            }
            @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture texture) {}
          });
        }
        addView(videoView, 0);
        break;
      case CAMERA:
        if (cameraView == null) {
          cameraView = new PreviewView(getContext());
          cameraView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
          cameraView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
          cameraView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
          cameraView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        addView(cameraView, 0);
        break;
      case SOLID:
      default:
        // No child view needed
        break;
    }

    // Animate transition
    animate()
        .setDuration(400)
        .setListener(new android.animation.AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(android.animation.Animator animation) {
            isTransitioning = false;
            transitionProgress = 1f;
            invalidate();
          }
        })
        .start();
  }

  /** Sets the photo background from a file. */
  public void setPhoto(File file) {
    setSource(Source.PHOTO);
    if (photoView != null && file != null && file.exists())
      photoView.setImageURI(android.net.Uri.fromFile(file));
  }

  /** Projects the controller's decoded scenic bitmap without owning session state. */
  public void setPhoto(android.graphics.Bitmap bitmap) {
    setSource(Source.PHOTO);
    if (photoView != null && bitmap != photoBitmap) {
      photoBitmap = bitmap;
      photoView.setImageBitmap(bitmap);
    }
  }

  /** Starts the selected BGV on the renderer's own TextureView. */
  public void setVideo(File file) {
    setSource(Source.BGV);
    if (file == null || !file.exists()) {
      stopVideo();
      videoFile = file;
      return;
    }
    if (file.equals(failedVideoFile)) return;
    if (file.equals(videoFile) && (videoPlayer != null || !visualActive)) return;
    stopVideo();
    videoFile = file;
    startVideoIfReady();
  }

  private void startVideoIfReady() {
    if (!visualActive || currentSource != Source.BGV || videoFile == null
        || videoView == null || !videoView.isAvailable() || videoPlayer != null) return;
    final int generation = ++videoGeneration;
    final File file = videoFile;
    MediaPlayer player = new MediaPlayer();
    videoPlayer = player;
    try {
      player.setDataSource(file.getAbsolutePath());
      videoSurface = new Surface(videoView.getSurfaceTexture());
      player.setSurface(videoSurface);
      player.setLooping(true);
      player.setVolume(0f, 0f);
      player.setOnPreparedListener(prepared -> {
        if (generation == videoGeneration && prepared == videoPlayer && visualActive
            && currentSource == Source.BGV) {
          failedVideoFile = null;
          try { prepared.start(); }
          catch (RuntimeException failure) {
            failedVideoFile = file;
            stopVideo();
          }
        }
      });
      player.setOnErrorListener((failed, what, extra) -> {
        if (generation == videoGeneration && failed == videoPlayer) {
          failedVideoFile = file;
          stopVideo();
        }
        return true;
      });
      player.prepareAsync();
    } catch (Exception failure) {
      if (generation == videoGeneration) {
        failedVideoFile = file;
        stopVideo();
      }
    }
  }

  private void stopVideo() {
    videoGeneration++;
    MediaPlayer player = videoPlayer;
    videoPlayer = null;
    if (player != null) {
      try { player.setOnPreparedListener(null); player.setOnErrorListener(null); } catch (RuntimeException ignored) {}
      try { player.stop(); } catch (RuntimeException ignored) {}
      try { player.release(); } catch (RuntimeException ignored) {}
    }
    if (videoSurface != null) {
      videoSurface.release();
      videoSurface = null;
    }
  }

  /** Sets the BGV video surface. */
  public TextureView getVideoSurface() {
    return videoView;
  }

  /** Returns the currently selected background source. */
  public Source getSource() {
    return currentSource;
  }

  /** Sets the camera preview. */
  public PreviewView getCameraPreview() {
    return cameraView;
  }

  /**
   * Detaches the CameraX view and restores the background that was active before camera opt-in.
   * The controller owns the CameraX binding; this view owns only stage presentation.
   */
  public void releaseCameraPreview(Source fallback) {
    Source restore = fallback == null || fallback == Source.CAMERA ? Source.SOLID : fallback;
    if (currentSource == Source.CAMERA) setSource(restore);
    if (cameraView != null) {
      if (cameraView.getParent() == this) removeView(cameraView);
      cameraView = null;
    }
  }

  /** Enables rendering; inactive stages release media and never prepare in the background. */
  public void setVisualActive(boolean active) {
    visualActive = active;
    if (!active) stopVideo();
    else if (currentSource == Source.BGV) startVideoIfReady();
  }

  /** True only while the active camera background owns a streaming, attached preview. */
  @Override protected void onDetachedFromWindow() {
    visualActive = false;
    stopVideo();
    super.onDetachedFromWindow();
  }

  public boolean isCameraVisible() {
    return currentSource == Source.CAMERA
        && cameraView != null
        && cameraView.getParent() == this
        && cameraView.getPreviewStreamState().getValue() == PreviewView.StreamState.STREAMING;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int w = getWidth();
    int h = getHeight();

    if (isTransitioning) {
      // Draw both old and new with cross-fade
      // For simplicity, just draw new source with progress
      transitionProgress += 0.016f; // ~60fps
      if (transitionProgress >= 1f) transitionProgress = 1f;
    }

    switch (currentSource) {
      case SOLID:
        drawSolidGradient(canvas, w, h);
        break;
      case PHOTO:
        // Photo is drawn by ImageView child
        // Draw scrim on bottom 40%
        int scrimTop = h - (int)(h * 0.4f);
        canvas.drawRect(0, scrimTop, w, h, photoScrimPaint);
        break;
      case BGV:
        // Video is drawn by TextureView child
        // Draw overlay for lyric legibility
        canvas.drawRect(0, 0, w, h, videoOverlayPaint);
        break;
      case CAMERA:
        // Camera drawn by PreviewView child
        break;
    }
  }

  private void drawSolidGradient(Canvas canvas, int w, int h) {
    // Simple two-color gradient
    for (int y = 0; y < h; y++) {
      float t = (float) y / h;
      int color = interpolateColor(0xFF140A26, 0xFF0C0618, t);
      Paint p = new Paint();
      p.setColor(color);
      canvas.drawRect(0, y, w, y + 1, p);
    }
  }

  private int interpolateColor(int start, int end, float t) {
    int sr = (start >> 16) & 0xFF;
    int sg = (start >> 8) & 0xFF;
    int sb = start & 0xFF;
    int er = (end >> 16) & 0xFF;
    int eg = (end >> 8) & 0xFF;
    int eb = end & 0xFF;
    int r = (int) (sr + (er - sr) * t);
    int g = (int) (sg + (eg - sg) * t);
    int b = (int) (sb + (eb - sb) * t);
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }
}
