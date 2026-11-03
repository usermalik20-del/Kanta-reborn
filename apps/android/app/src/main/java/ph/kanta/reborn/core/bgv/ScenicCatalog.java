package ph.kanta.reborn.core.bgv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The built-in scenic channel.
 *
 * <p>Every entry was requested with a HEAD before it was committed: the host answered 200, the
 * content type matched the container and the length matched {@link ScenicClip#expectedBytes}.
 * Sources are Wikimedia Commons (CC0 or public domain, VP9 WebM) and the NASA Image and Video
 * Library (public domain, H.264 MP4 for the widest device compatibility).
 *
 * <p>Nothing here depends on YouTube, on an API key or on a third-party downloader library, so
 * the offline-first promises on the public site stay true and the app takes on no new dependency
 * and no new permission: <code>INTERNET</code> is already declared for the companion feature.
 */
public final class ScenicCatalog {

  /** Largest download the app will start, in bytes. A guard against a wrong or hostile URL. */
  public static final long MAX_BYTES = 64L * 1024 * 1024;

  /** Hosts the app will download from. Anything else is refused before a socket is opened. */
  private static final List<String> ALLOWED_HOSTS =
      Collections.unmodifiableList(Arrays.asList("upload.wikimedia.org", "images-assets.nasa.gov"));

  public static final List<ScenicClip> BUILT_IN =
      Collections.unmodifiableList(
          Arrays.asList(
              new ScenicClip(
                  "waves-06",
                  "Calm Sea",
                  "Sea",
                  "Wikimedia Commons",
                  "CC0",
                  "https://commons.wikimedia.org/wiki/File:Ocean_surface_waves_06.ogv",
                  "https://upload.wikimedia.org/wikipedia/commons/transcoded/0/08/Ocean_surface_waves_06.ogv/Ocean_surface_waves_06.ogv.480p.vp9.webm",
                  577017L),
              new ScenicClip(
                  "waves-08",
                  "Rolling Waves",
                  "Sea",
                  "Wikimedia Commons",
                  "CC0",
                  "https://commons.wikimedia.org/wiki/File:Ocean_surface_waves_08.ogv",
                  "https://upload.wikimedia.org/wikipedia/commons/transcoded/a/a2/Ocean_surface_waves_08.ogv/Ocean_surface_waves_08.ogv.480p.vp9.webm",
                  1679832L),
              new ScenicClip(
                  "waves-09",
                  "Shore Break",
                  "Sea",
                  "Wikimedia Commons",
                  "CC0",
                  "https://commons.wikimedia.org/wiki/File:Ocean_surface_waves_09.ogv",
                  "https://upload.wikimedia.org/wikipedia/commons/transcoded/e/ee/Ocean_surface_waves_09.ogv/Ocean_surface_waves_09.ogv.480p.vp9.webm",
                  934791L),
              new ScenicClip(
                  "waves-10",
                  "Evening Tide",
                  "Sea",
                  "Wikimedia Commons",
                  "CC0",
                  "https://commons.wikimedia.org/wiki/File:Ocean_surface_waves_10.ogv",
                  "https://upload.wikimedia.org/wikipedia/commons/transcoded/d/dc/Ocean_surface_waves_10.ogv/Ocean_surface_waves_10.ogv.480p.vp9.webm",
                  1899366L),
              new ScenicClip(
                  "clouds-moving",
                  "Moving Clouds",
                  "Sky",
                  "Wikimedia Commons",
                  "Public domain",
                  "https://commons.wikimedia.org/wiki/File:Moving_clouds_timelapse_1152x694x10fps.ogv",
                  "https://upload.wikimedia.org/wikipedia/commons/transcoded/0/0d/Moving_clouds_timelapse_1152x694x10fps.ogv/Moving_clouds_timelapse_1152x694x10fps.ogv.480p.vp9.webm",
                  498947L),
              new ScenicClip(
                  "clouds-timelapse",
                  "Cloud Timelapse",
                  "Sky",
                  "Wikimedia Commons",
                  "Public domain",
                  "https://commons.wikimedia.org/wiki/File:Timelapse_Clouds_Compilation.ogv",
                  "https://upload.wikimedia.org/wikipedia/commons/transcoded/d/db/Timelapse_Clouds_Compilation.ogv/Timelapse_Clouds_Compilation.ogv.480p.vp9.webm",
                  14675505L),
              new ScenicClip(
                  "sunset-triple",
                  "Triple Sunset",
                  "Space",
                  "Wikimedia Commons",
                  "Public domain",
                  "https://commons.wikimedia.org/wiki/File:Triple_Sunset_on_HD_188753_Ab.ogv",
                  "https://upload.wikimedia.org/wikipedia/commons/transcoded/b/bf/Triple_Sunset_on_HD_188753_Ab.ogv/Triple_Sunset_on_HD_188753_Ab.ogv.480p.vp9.webm",
                  5304728L),
              new ScenicClip(
                  "waterfall-peru",
                  "Waterfall, Peru",
                  "Land",
                  "Wikimedia Commons",
                  "CC0",
                  "https://commons.wikimedia.org/wiki/File:Waterfall_in_Huascar%C3%A1n_National_Park_-_Peru.webm",
                  "https://upload.wikimedia.org/wikipedia/commons/transcoded/f/f7/Waterfall_in_Huascar%C3%A1n_National_Park_-_Peru.webm/Waterfall_in_Huascar%C3%A1n_National_Park_-_Peru.webm.480p.vp9.webm",
                  4260512L),
              new ScenicClip(
                  "aurora-nasa",
                  "Northern Lights",
                  "Space",
                  "NASA",
                  "Public domain",
                  "https://images.nasa.gov/details/How%20Can%20I%20See%20the%20Northern%20Lights",
                  "https://images-assets.nasa.gov/video/How%20Can%20I%20See%20the%20Northern%20Lights/How%20Can%20I%20See%20the%20Northern%20Lights~small.mp4",
                  19327083L),
              new ScenicClip(
                  "volcano-nasa",
                  "Volcanic Ash",
                  "Land",
                  "NASA",
                  "Public domain",
                  "https://images.nasa.gov/details/GSFC_20160512_Volcanic_m12221_Ash",
                  "https://images-assets.nasa.gov/video/GSFC_20160512_Volcanic_m12221_Ash/GSFC_20160512_Volcanic_m12221_Ash~small.mp4",
                  19784607L)));

  /** Channel names in catalogue order. */
  public static List<String> channels() {
    Map<String, Boolean> seen = new LinkedHashMap<>();
    for (ScenicClip clip : BUILT_IN) seen.put(clip.channel, Boolean.TRUE);
    return Collections.unmodifiableList(new ArrayList<>(seen.keySet()));
  }

  /** The clips in one channel, in catalogue order. */
  public static List<ScenicClip> inChannel(String channel) {
    List<ScenicClip> found = new ArrayList<>();
    for (ScenicClip clip : BUILT_IN) {
      if (clip.channel.equals(channel)) found.add(clip);
    }
    return Collections.unmodifiableList(found);
  }

  /** A clip by id, or null. */
  public static ScenicClip byId(String id) {
    if (id == null) return null;
    for (ScenicClip clip : BUILT_IN) {
      if (clip.id.equals(id)) return clip;
    }
    return null;
  }

  /**
   * Whether this clip may be downloaded: https, an allow-listed host, a known container and a
   * size within {@link #MAX_BYTES}.
   */
  public static boolean isAllowed(ScenicClip clip) {
    if (clip == null) return false;
    if (!clip.downloadUrl.startsWith("https://")) return false;
    if (clip.expectedBytes <= 0 || clip.expectedBytes > MAX_BYTES) return false;
    if (".bin".equals(clip.extension())) return false;
    String host = hostOf(clip.downloadUrl);
    return host != null && ALLOWED_HOSTS.contains(host);
  }

  /** The lower-case host of an https URL, or null when it cannot be read. */
  public static String hostOf(String url) {
    if (url == null) return null;
    int start = url.indexOf("://");
    if (start < 0) return null;
    String rest = url.substring(start + 3);
    int end = rest.length();
    for (int index = 0; index < rest.length(); index++) {
      char c = rest.charAt(index);
      if (c == '/' || c == '?' || c == '#') {
        end = index;
        break;
      }
    }
    String authority = rest.substring(0, end);
    if (authority.isEmpty() || authority.indexOf('@') >= 0) return null;
    int colon = authority.indexOf(':');
    if (colon >= 0) authority = authority.substring(0, colon);
    return authority.toLowerCase(Locale.ROOT);
  }

  /** Human-readable size for the picker, e.g. "14 MB". */
  public static String sizeLabel(long bytes) {
    if (bytes < 1024L * 1024L) return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
    return Math.round(bytes / (1024.0 * 1024.0)) + " MB";
  }

  private ScenicCatalog() {}
}
