package ph.kanta.reborn;

import android.content.Context;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import ph.kanta.reborn.core.YouTubeVideoReference;

/** One visible official embed; release is terminal and never touches local playback. */
final class YouTubeEmbeddedPlayer extends WebView {
  private String loadedId;
  private boolean released;
  private final Runnable onFailure;

  YouTubeEmbeddedPlayer(Context context, Runnable onFailure) {
    super(context);
    this.onFailure = onFailure;
    getSettings().setJavaScriptEnabled(true);
    getSettings().setMediaPlaybackRequiresUserGesture(true);
    getSettings().setDomStorageEnabled(false);
    getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
    getSettings().setSupportMultipleWindows(false);
    getSettings().setAllowFileAccessFromFileURLs(false);
    getSettings().setAllowUniversalAccessFromFileURLs(false);
    getSettings().setAllowFileAccess(false);
    getSettings().setAllowContentAccess(false);
    setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return released || !isAllowed(url);
      }
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return released || !isAllowed(request.getUrl().toString());
      }
      @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (request.isForMainFrame()) fail(false);
      }
      @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
        if (request.isForMainFrame() && response.getStatusCode() >= 400) fail(false);
      }
      @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
        fail(true);
        return true;
      }
    });
    setBackgroundColor(android.graphics.Color.BLACK);
  }

  private static boolean isAllowed(String raw) {
    try {
      android.net.Uri url = android.net.Uri.parse(raw);
      String host = url.getHost();
      return "https".equalsIgnoreCase(url.getScheme()) && host != null &&
          (host.equalsIgnoreCase("www.youtube-nocookie.com") ||
           host.equalsIgnoreCase("youtube-nocookie.com") ||
           host.equalsIgnoreCase("www.youtube.com") ||
           host.equalsIgnoreCase("youtube.com"));
    } catch (Exception ignored) { return false; }
  }

  void show(YouTubeVideoReference reference) {
    if (released || reference.videoId.equals(loadedId)) return;
    loadedId = reference.videoId;
    loadUrl("https://www.youtube-nocookie.com/embed/" + reference.videoId + "?playsinline=1");
  }

  private void fail(boolean rendererGone) {
    if (released) return;
    release(rendererGone);
    onFailure.run();
  }

  boolean released() { return released; }

  void release() { release(false); }

  private void release(boolean rendererGone) {
    if (released) return;
    released = true;
    loadedId = null;
    // Detach and retire callbacks before destroying a WebView or its renderer.
    if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    if (!rendererGone) {
      setWebViewClient(new WebViewClient());
      stopLoading();
      loadUrl("about:blank");
      onPause();
      removeAllViews();
    }
    destroy();
  }
}
