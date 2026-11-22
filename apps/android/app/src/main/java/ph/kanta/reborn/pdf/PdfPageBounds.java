package ph.kanta.reborn.pdf;

/** Positions a page in a clipped viewport, independently of its bitmap resolution. */
final class PdfPageBounds {
  static final float FIT_PAGE = 0f;
  static final float FIT_WIDTH = 1f;
  static final float MAX_ZOOM = 4f;
  private int viewWidth, viewHeight, pageWidth, pageHeight;
  private float fitZoom;
  private float zoom = FIT_WIDTH;
  float left, top, width, height;

  static float savedZoom(float value) {
    return Float.isFinite(value) ? Math.max(FIT_PAGE, Math.min(MAX_ZOOM, value)) : FIT_WIDTH;
  }

  float zoom() {
    return zoom;
  }

  float resolvedZoom() {
    return Math.max(fitZoom, zoom);
  }

  boolean canZoomOut() {
    return resolvedZoom() > fitZoom;
  }

  private void applyZoom(float value) {
    zoom = value <= fitZoom ? FIT_PAGE : savedZoom(value);
  }

  void layout(int viewWidth, int viewHeight, int pageWidth, int pageHeight, float zoom) {
    this.viewWidth = viewWidth;
    this.viewHeight = viewHeight;
    this.pageWidth = pageWidth;
    this.pageHeight = pageHeight;
    this.zoom = savedZoom(zoom);
    if (viewWidth <= 0 || viewHeight <= 0) {
      width = height = 0;
      return;
    }
    fitZoom = Math.min(FIT_WIDTH, (viewHeight / (float) pageHeight) / (viewWidth / (float) pageWidth));
    sizePage();
    left = (viewWidth - width) / 2f;
    top = 0;
    constrain();
  }

  void zoomAt(float requested, float fromX, float fromY, float toX, float toY) {
    if (width <= 0 || height <= 0) return;
    float fractionX = (fromX - left) / width;
    float fractionY = (fromY - top) / height;
    applyZoom(savedZoom(requested));
    sizePage();
    left = toX - fractionX * width;
    top = toY - fractionY * height;
    constrain();
  }

  void pan(float dx, float dy) {
    left += dx;
    top += dy;
    constrain();
  }

  private void sizePage() {
    float scale = viewWidth / (float) pageWidth * resolvedZoom();
    width = pageWidth * scale;
    height = pageHeight * scale;
  }

  private void constrain() {
    left = constrainAxis(left, viewWidth, width);
    top = constrainAxis(top, viewHeight, height);
  }

  private static float constrainAxis(float start, int viewportSize, float pageSize) {
    return pageSize <= viewportSize ? (viewportSize - pageSize) / 2f
        : Math.max(viewportSize - pageSize, Math.min(0f, start));
  }
}
