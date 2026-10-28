package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
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

    // Cross-fade transition
    isTransitioning = true;
    transitionProgress = 0f;

    // Swap child views
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
        }
        addView(videoView, 0);
        break;
      case CAMERA:
        if (cameraView == null) {
          cameraView = new PreviewView(getContext());
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
    if (photoView != null && file != null && file.exists()) {
      photoView.setImageURI(android.net.Uri.fromFile(file));
    }
    setSource(Source.PHOTO);
  }

  /** Sets the BGV video surface. */
  public TextureView getVideoSurface() {
    return videoView;
  }

  /** Sets the camera preview. */
  public PreviewView getCameraPreview() {
    return cameraView;
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
