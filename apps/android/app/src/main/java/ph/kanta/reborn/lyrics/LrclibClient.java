package ph.kanta.reborn.lyrics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Context;
import android.content.pm.PackageManager;
import ph.kanta.reborn.lyrics.LyricCache;

/** Explicit-use LRCLIB adapter. No uploads, background scans, credentials or automatic retries. */
public final class LrclibClient {
  // Public self-asserted client identifier supplied by the owner; not an API secret.
  public static final String APPLICATION_ID = "fXIQgJ6_D0XJj4mKNCsBRQ5yBvnM5L-z85P8AOt009g";
  private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
  private final String userAgent;
  private final LyricCache lyricsCache;

  public LrclibClient(Context context) {
    String version = "unknown";
    try { version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
    catch (PackageManager.NameNotFoundException unavailable) { /* Own package normally exists. */ }
    userAgent = "KANTA-Reborn/" + version + " (https://github.com/usermalik20-del/Kanta-reborn)";
    this.lyricsCache = new LyricCache(context);
  }

  public static final class Result {
    public final long id;
    public final String title, artist, album, plainLyrics, syncedLyrics;
    public final double duration;
    public final boolean instrumental;
    public final List<ph.kanta.reborn.core.MidiLyrics.Cue> timedCues;

    private Result(JSONObject value) {
      id = value.optLong("id", 0);
      title = value.optString("trackName", "");
      artist = value.optString("artistName", "");
      album = value.optString("albumName", "");
      plainLyrics = value.isNull("plainLyrics") ? "" : value.optString("plainLyrics", "");
      syncedLyrics = value.isNull("syncedLyrics") ? "" : value.optString("syncedLyrics", "");
      double seconds = value.optDouble("duration", 0);
      duration = Double.isFinite(seconds) && seconds >= 0 && seconds <= 86400 ? seconds : 0;
      instrumental = value.optBoolean("instrumental", false);
      timedCues = ph.kanta.reborn.core.LrcLyrics.parse(syncedLyrics);
    }
  }

  public List<Result> search(String title, String artist) throws IOException {
    title = title == null ? "" : title.trim();
    artist = artist == null ? "" : artist.trim();
    if (title.isEmpty() || title.length() > 512 || artist.length() > 512)
      throw new IOException("Enter a song title and an optional artist (up to 512 characters each).");
    String query = "track_name=" + URLEncoder.encode(title, StandardCharsets.UTF_8.name());
    if (!artist.isEmpty()) query += "&artist_name=" + URLEncoder.encode(artist, StandardCharsets.UTF_8.name());
    HttpURLConnection connection = (HttpURLConnection)
        new URL("https://lrclib.net/api/search?" + query).openConnection();
    try {
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(12000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Accept-Encoding", "identity");
      connection.setRequestProperty("Lrclib-App-Id", APPLICATION_ID);
      connection.setRequestProperty("User-Agent", userAgent);
      int status = connection.getResponseCode();
      if (status == 429) throw new IOException("LRCLIB is busy. Please wait before trying again.");
      if (status == 401) throw new IOException("LRCLIB did not accept this application's identifier.");
      if (status != 200) throw new IOException("LRCLIB could not complete the search (HTTP " + status + ").");
      if (connection.getContentLengthLong() > MAX_RESPONSE_BYTES)
        throw new IOException("The lyric response is too large.");
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      long deadline = System.nanoTime() + 20_000_000_000L;
      try (InputStream input = connection.getInputStream()) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
          if (bytes.size() + count > MAX_RESPONSE_BYTES || System.nanoTime() > deadline)
            throw new IOException("The lyric response exceeded its size or time limit.");
          bytes.write(buffer, 0, count);
        }
      }
      List<Result> results = parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
      return results;
    } finally {
      connection.disconnect();
    }
  }

  public void cacheIfConfident(Result result, long durationMs) {
    if (lyricsCache == null || result == null || result.instrumental || result.timedCues.isEmpty()) return;
    lyricsCache.put(result.title, result.artist, durationMs, result.timedCues);
  }

  public java.util.List<ph.kanta.reborn.core.MidiLyrics.Cue> getCached(
      String title, String artist, long durationMs) {
    if (lyricsCache == null) return null;
    return lyricsCache.get(title, artist, durationMs);
  }

  /** Separate from networking so tests never need LRCLIB or a private music library. */
  static List<Result> parse(String json) throws IOException {
    try {
      JSONArray values = new JSONArray(json);
      List<Result> results = new ArrayList<>();
      for (int i = 0; i < Math.min(values.length(), 20); i++) {
        JSONObject object = values.optJSONObject(i);
        if (object == null) continue;
        Result result = new Result(object);
        if (result.id > 0 && !result.title.isEmpty() && result.title.length() <= 512
            && result.artist.length() <= 512 && result.album.length() <= 512
            && result.plainLyrics.length() <= 512 * 1024 && result.syncedLyrics.length() <= 512 * 1024)
          results.add(result);
      }
      return Collections.unmodifiableList(results);
    } catch (JSONException malformed) {
      throw new IOException("LRCLIB returned an unreadable response.");
    }
  }
}
