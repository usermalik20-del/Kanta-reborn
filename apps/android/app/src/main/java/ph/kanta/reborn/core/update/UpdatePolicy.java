package ph.kanta.reborn.core.update;

import java.util.Locale;

/**
 * Security and sizing boundaries shared by every consumer of the update feed.
 *
 * <p>The feed URL is pinned at compile time, transport is HTTPS-only and the
 * accepted hosts are the project's own durable release-asset locations. GitHub
 * Actions artifact URLs are refused: they require sign-in and expire, so a
 * feed pointing at one would produce a download that fails for most devices.
 */
public final class UpdatePolicy {

  /** Pinned feed location on the project's public site. */
  public static final String FEED_URL = "https://kanta-public.vercel.app/updates.json";

  /** Upper sanity bound for a KANTA APK (the published APK is far below this). */
  public static final long MAX_APK_BYTES = 256L * 1024 * 1024;

  /** Upper bound for the whole feed document. */
  public static final int MAX_FEED_BYTES = 256 * 1024;

  /** Repo whose release assets the feed may name. */
  public static final String REPOSITORY = "usermalik20-del/Kanta-reborn";

  private static final String RELEASE_PREFIX = "/" + REPOSITORY + "/releases/download/";

  private UpdatePolicy() {}

  /**
   * Accepts only durable, public, HTTPS artifact URLs.
   *
   * <p>Accepted shapes:
   * <ul>
   *   <li>{@code https://github.com/<repo>/releases/download/<tag>/<file>} — the
   *       canonical release-asset URL: durable, public, no sign-in; it redirects
   *       to a signed CDN URL served over HTTPS.</li>
   *   <li>{@code https://release-assets.githubusercontent.com/...} and
   *       {@code https://objects.githubusercontent.com/...} — the redirect
   *       targets GitHub uses for those assets.</li>
   * </ul>
   *
   * <p>Everything else (Actions artifact URLs, expiring presigned links from
   * other hosts, http:// schemes, userinfo tricks) is refused.
   */
  public static boolean isAcceptedApkUrl(String url) {
    if (url == null) {
      return false;
    }
    String lower = url.toLowerCase(Locale.ROOT);
    if (!lower.startsWith("https://")) {
      return false;
    }
    // Hosts are case-insensitive, but the GitHub path is case-sensitive: keep
    // the path in its original case so only the exact repository spelling is
    // accepted under https://github.com.
    String rest = url.substring("https://".length());
    String lowerRest = lower.substring("https://".length());
    int slash = rest.indexOf('/');
    if (slash < 0) {
      return false;
    }
    String host = lowerRest.substring(0, slash);
    String path = rest.substring(slash);
    if (host.equals("github.com")) {
      // Canonical release asset: clean path, exact repo, at least tag + file.
      if (!path.startsWith(RELEASE_PREFIX)) {
        return false;
      }
      String suffix = path.substring(RELEASE_PREFIX.length());
      if (suffix.isEmpty() || suffix.equals("/") || suffix.contains("..")) {
        return false;
      }
      String[] segments = suffix.split("/");
      if (segments.length != 2 || segments[0].isEmpty() || segments[1].isEmpty()) {
        return false;
      }
      // No userinfo, query or fragment on the canonical URL.
      return !url.contains("@")
          && lower.indexOf('?') < 0
          && lower.indexOf('#') < 0;
    }
    if (host.equals("release-assets.githubusercontent.com")
        || host.equals("objects.githubusercontent.com")) {
      // Redirect targets carry signed query parameters; only shape-check host/HTTPS.
      return !url.contains("@") && path.startsWith("/");
    }
    return false;
  }
}