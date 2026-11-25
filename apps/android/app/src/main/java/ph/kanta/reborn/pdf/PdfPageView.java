package ph.kanta.reborn.pdf;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.function.Consumer;

/** Draws one fixed-resolution bitmap; zooming never asks the renderer for another bitmap. */
@SuppressLint("ViewConstructor") // Constructed only by the reader with a rendered page.
final class PdfPageView extends View {
  private final Bitmap image;
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final RectF destination = new RectF();
  private final PdfPageBounds bounds = new PdfPageBounds();
  private final ScaleGestureDetector scaleDetector;
  private final GestureDetector gestureDetector;
  private final Consumer<PdfPageView> onZoom;
  private final Consumer<Integer> onSwipePage;
  private float zoom;
  private boolean multiTouch;
  private float lastX, lastY, focusX, focusY;
  private float downX, downY;
  private boolean pageSwipeDispatched;

  PdfPageView(Context context, Bitmap image, float zoom, Consumer<PdfPageView> onZoom, Consumer<Integer> onSwipePage) {
    super(context);
    this.image = image;
    this.zoom = zoom;
    this.onZoom = onZoom;
    this.onSwipePage = onSwipePage;
    scaleDetector = new ScaleGestureDetector(context,
        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
          @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
            focusX = detector.getFocusX();
            focusY = detector.getFocusY();
            return true;
          }

          @Override public boolean onScale(ScaleGestureDetector detector) {
            float x = detector.getFocusX();
            float y = detector.getFocusY();
            zoomAt(bounds.resolvedZoom() * detector.getScaleFactor(), focusX, focusY, x, y);
            focusX = x;
            focusY = y;
            return true;
          }
        });
    scaleDetector.setQuickScaleEnabled(false);
    gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
      @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1 == null || multiTouch || bounds.resolvedZoom() > PdfPageBounds.FIT_WIDTH) return false;
        float dx = e2.getX() - e1.getX();
        float density = getResources().getDisplayMetrics().density;
        if (Math.abs(dx) >= 32 * density && Math.abs(dx) > Math.abs(e2.getY() - e1.getY()) * 2) {
          pageSwipeDispatched = true;
          onSwipePage.accept(dx > 0 ? -1 : 1);
          return true;
        }
        return false;
      }
    });
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    bounds.layout(width, height, image.getWidth(), image.getHeight(), zoom);
    onZoom.accept(this);
  }

  float zoom() {
    return zoom;
  }

  boolean canZoomOut() {
    return bounds.canZoomOut();
  }

  boolean canZoomIn() {
    return bounds.resolvedZoom() < PdfPageBounds.MAX_ZOOM;
  }

  void setZoom(float value) {
    zoomAt(value, getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, getHeight() / 2f);
  }

  void scaleBy(float factor) {
    zoomAt(bounds.resolvedZoom() * factor, getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, getHeight() / 2f);
  }

  private void zoomAt(float value, float fromX, float fromY, float toX, float toY) {
    bounds.zoomAt(value, fromX, fromY, toX, toY);
    zoom = bounds.zoom();
    onZoom.accept(this);
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    destination.set(bounds.left, bounds.top, bounds.left + bounds.width, bounds.top + bounds.height);
    canvas.drawBitmap(image, null, destination, paint);
  }

  @SuppressLint("ClickableViewAccessibility") // Drag and pinch are not clicks; zoom also has buttons.
  @Override public boolean onTouchEvent(MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) multiTouch = false;
    else if (event.getPointerCount() > 1) multiTouch = true;
    scaleDetector.onTouchEvent(event);
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        // The surrounding utility ScrollView must not grab a vertical page drag or pinch.
        getParent().requestDisallowInterceptTouchEvent(true);
        downX = lastX = event.getX();
        downY = lastY = event.getY();
        pageSwipeDispatched = false;
        break;
      case MotionEvent.ACTION_MOVE:
        if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
          float x = event.getX();
          float y = event.getY();
          bounds.pan(x - lastX, y - lastY);
          lastX = x;
          lastY = y;
          invalidate();
        }
        break;
      case MotionEvent.ACTION_POINTER_UP:
        if (event.getPointerCount() == 2) {
          int remaining = event.getActionIndex() == 0 ? 1 : 0;
          lastX = event.getX(remaining);
          lastY = event.getY(remaining);
        }
        break;
      case MotionEvent.ACTION_UP:
        if (!multiTouch && !pageSwipeDispatched
            && bounds.resolvedZoom() <= PdfPageBounds.FIT_WIDTH) {
          float dx = event.getX() - downX;
          float dy = event.getY() - downY;
          float density = getResources().getDisplayMetrics().density;
          if (Math.abs(dx) >= 32 * density && Math.abs(dx) > Math.abs(dy) * 2) {
            pageSwipeDispatched = true;
            onSwipePage.accept(dx > 0 ? -1 : 1);
          }
        }
        getParent().requestDisallowInterceptTouchEvent(false);
        break;
      case MotionEvent.ACTION_CANCEL:
        getParent().requestDisallowInterceptTouchEvent(false);
        break;
      default:
        break;
    }
    gestureDetector.onTouchEvent(event);
    return true;
  }
}