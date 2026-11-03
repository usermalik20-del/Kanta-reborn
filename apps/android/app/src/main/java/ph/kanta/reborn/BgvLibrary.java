package ph.kanta.reborn;

import android.content.Context;
import android.util.AtomicFile;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;
import ph.kanta.reborn.core.bgv.ScenicCatalog;
import ph.kanta.reborn.core.bgv.ScenicClip;

/**
 * The downloaded background-video library.
 *
 * <p>Byte copies land in the app's own storage, exactly like every other piece of user media
 * KANTA imports: the original source is never modified and nothing is written into the user's
 * folders behind their back. The index is written atomically and dropped entries whose file has
 * disappeared are pruned on load, so the list can never point at a missing file.
 *
 * <p>Downloads run on their own single worker, never on the main thread and never on the shared
 * library IO executor, and a download that exceeds {@link ScenicCatalog#MAX_BYTES} or reports a
 * mismatched length fails instead of leaving a truncated video in the library.
 */
final class BgvLibrary {

  interface Listener {
    void onResult(String clipId, boolean ok, String message);
  }

  /** One downloaded clip as stored on disk. */
  static final class Entry {
    final String id;
    final String title;
    final String channel;
    final String credit;
    final String licence;
    final String pageUrl;
    final long bytes;
    final String sha256;
    final long downloadedAt;
    final File file;

    Entry(
        String id,
        String title,
        String channel,
        String credit,
        String licence,
        String pageUrl,
        long bytes,
        String sha256,
        long downloadedAt,
        File file) {
      this.id = id;
      this.title = title;
      this.channel = channel;
      this.credit = credit;
      this.licence = licence;
      this.pageUrl = pageUrl;
      this.bytes = bytes;
      this.sha256 = sha256;
      this.downloadedAt = downloadedAt;
      this.file = file;
    }

    /** The credit line shown in the library list. */
    String attribution() {
      return credit + " · " + licence;
    }
  }

  private static final String USER_AGENT = "KANTA-Android/0.10 (offline karaoke; scenic downloader)";
  private static final int CONNECT_TIMEOUT_MS = 20000;
  private static final int READ_TIMEOUT_MS = 60000;

  private final Context context;
  private final File root;
  private final AtomicFile index;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean cancelled = new AtomicBoolean();
  private final List<Entry> entries = new ArrayList<>();
  private volatile String status = "";
  private volatile long copied, total;
  private volatile String activeId = "";

  /** True when a download is currently running. */
  private synchronized boolean busy() {
    return !activeId.isEmpty();
  }

  /** Current status line for UI (idle, progress, or completion message). */
  String progressLine() {
    return status;
  }

  BgvLibrary(Context context) throws IOException {
    this.context = context.getApplicationContext();
    root = new File(context.getFilesDir(), "bgv");
    if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot open video storage");
    index = new AtomicFile(new File(context.getFilesDir(), "bgv-v1.json"));
    load();
  }

  /** Downloaded clips, newest first. */
  synchronized List<Entry> entries() {
    List<Entry> copy = new ArrayList<>(entries);
    Collections.sort(
        copy,
        new Comparator<Entry>() {
          @Override
          public int compare(Entry left, Entry right) {
            return Long.compare(right.downloadedAt, left.downloadedAt);
          }
        });
    return Collections.unmodifiableList(copy);
  }

  synchronized boolean isDownloaded(String id) {
    for (Entry entry : entries) {
      if (entry.id.equals(id)) return true;
    }
    return false;
  }

  synchronized Entry entry(String id) {
    for (Entry entry : entries) {
      if (entry.id.equals(id)) return entry;
    }
    return null;
  }

  /** Starts a download. A second request while one is running is refused, not queued. */
  synchronized boolean download(final ScenicClip clip, final Listener listener) {
    if (clip == null || !ScenicCatalog.isAllowed(clip) || busy()) return false;
    activeId = clip.id;
    status = "Starting download…";
    copied = 0;
    total = clip.expectedBytes;
    cancelled.set(false);
    worker.execute(
        () -> {
          boolean ok = false;
          String message;
          try {
            ok = fetch(clip);
            message =
                ok
                    ? clip.title + " downloaded. It is in your background video library."
                    : "Download failed. Check your connection and try again.";
          } catch (Exception failure) {
            message = "Download failed. Check your connection and try again.";
          }
          activeId = "";
          status = message;
          cancelled.set(false);
          if (listener != null) listener.onResult(clip.id, ok, message);
        });
    return true;
  }

  void cancel() {
    cancelled.set(true);
  }

  /** Removes a clip from the library and deletes its bytes. */
  synchronized boolean delete(String id) {
    for (int index = 0; index < entries.size(); index++) {
      if (!entries.get(index).id.equals(id)) continue;
      Entry entry = entries.remove(index);
      if (entry.file.isFile() && !entry.file.delete()) {
        entries.add(index, entry);
        return false;
      }
      persist();
      return true;
    }
    return false;
  }

  private boolean fetch(ScenicClip clip) throws Exception {
    File target = new File(root, clip.fileName());
    File partial = new File(root, clip.fileName() + ".part");
    if (partial.isFile() && !partial.delete()) throw new IOException("Cannot clear partial file");
    HttpURLConnection connection = (HttpURLConnection) new URL(clip.downloadUrl).openConnection();
    try {
      connection.setInstanceFollowRedirects(true);
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      connection.setRequestMethod("GET");
      connection.setRequestProperty("User-Agent", USER_AGENT);
      connection.setRequestProperty("Accept-Encoding", "identity");
      int code = connection.getResponseCode();
      if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
      long declared = connection.getContentLengthLong();
      if (declared > ScenicCatalog.MAX_BYTES) throw new IOException("Oversized download");
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long written = 0;
      try (InputStream input = new BufferedInputStream(connection.getInputStream());
          BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(partial))) {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) > 0) {
          if (cancelled.get()) throw new IOException("Cancelled");
          written += read;
          if (written > ScenicCatalog.MAX_BYTES) throw new IOException("Oversized download");
          digest.update(buffer, 0, read);
          output.write(buffer, 0, read);
          copied = written;
        }
        output.flush();
      }
      if (declared > 0 && written != declared) throw new IOException("Truncated download");
      if (written <= 0) throw new IOException("Empty download");
      if (target.isFile() && !target.delete()) throw new IOException("Cannot replace file");
      if (!partial.renameTo(target)) throw new IOException("Cannot store file");
      String sha256 = hex(digest.digest());
      synchronized (this) {
        entries.add(
            new Entry(
                clip.id,
                clip.title,
                clip.channel,
                clip.credit,
                clip.licence,
                clip.pageUrl,
                written,
                sha256,
                System.currentTimeMillis(),
                target));
        persist();
      }
      return true;
    } finally {
      connection.disconnect();
      if (partial.isFile()) partial.delete();
    }
  }

  private static String hex(byte[] value) {
    StringBuilder text = new StringBuilder(value.length * 2);
    for (byte one : value) text.append(String.format(Locale.ROOT, "%02x", one));
    return text.toString();
  }

  private void load() {
    if (!index.getBaseFile().exists() && !new File(index.getBaseFile() + ".bak").exists()) return;
    try {
      byte[] raw = index.readFully();
      if (raw.length > 262144) throw new IOException("Index too large");
      JSONObject document = new JSONObject(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
      if (document.getInt("version") != 1) throw new IOException("Unsupported index");
      JSONArray stored = document.getJSONArray("entries");
      synchronized (this) {
        for (int position = 0; position < stored.length(); position++) {
          JSONObject item = stored.getJSONObject(position);
          String id = item.getString("id");
          File file = new File(root, item.getString("file"));
          if (!file.isFile()) continue;
          entries.add(
              new Entry(
                  id,
                  item.optString("title", id),
                  item.optString("channel", ""),
                  item.optString("credit", ""),
                  item.optString("licence", ""),
                  item.optString("pageUrl", ""),
                  file.length(),
                  item.optString("sha256", ""),
                  item.optLong("downloadedAt", 0L),
                  file));
        }
      }
    } catch (Exception failure) {
      synchronized (this) {
        entries.clear();
      }
      status = "The saved video list could not be read. Downloads can be made again.";
    }
  }

  private void persist() {
    try {
      JSONObject document = new JSONObject();
      document.put("version", 1);
      JSONArray stored = new JSONArray();
      for (Entry entry : entries) {
        JSONObject item = new JSONObject();
        item.put("id", entry.id);
        item.put("title", entry.title);
        item.put("channel", entry.channel);
        item.put("credit", entry.credit);
        item.put("licence", entry.licence);
        item.put("pageUrl", entry.pageUrl);
        item.put("file", entry.file.getName());
        item.put("sha256", entry.sha256);
        item.put("downloadedAt", entry.downloadedAt);
        stored.put(item);
      }
      document.put("entries", stored);
      FileOutputStream stream = index.startWrite();
      try {
        stream.write(document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        index.finishWrite(stream);
      } catch (Exception failure) {
        index.failWrite(stream);
      }
    } catch (Exception failure) {
      status = "The download could not be recorded in your library.";
    }
  }

  /** Releases the worker. Called when the process is finishing with the library. */
  void shutdown() {
    cancelled.set(true);
    worker.shutdownNow();
  }
}
