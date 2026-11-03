package ph.kanta.reborn.update;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import ph.kanta.reborn.core.update.UpdateDecision;
import ph.kanta.reborn.core.update.UpdateDigest;
import ph.kanta.reborn.core.update.UpdateManifest;
import ph.kanta.reborn.core.update.UpdatePolicy;

/**
 * Owns the whole OTA flow for this app: feed check, decision, resumable
 * download into app-private storage, digest verification and hand-off to the
 * installer.
 *
 * <p>All network and hashing work runs on a single worker executor; listener
 * callbacks are posted onto the thread supplied by the owning UI. The manager
 * is not lifecycle-aware: the owner cancels in-flight work via
 * {@link #cancel()} when the app leaves the foreground, and closes the worker
 * via {@link #close()} when the owning activity is destroyed.
 */
public final class AppUpdateManager {

  private static final int CONNECT_TIMEOUT_MS = 15_000;
  private static final int READ_TIMEOUT_MS = 30_000;
  private static final int STREAM_BUFFER_BYTES = 64 * 1024;
  private static final String USER_AGENT = "KANTA-Reborn-Android/update-manager";

  /** Observable stages of the update flow. */
  public enum State {
    IDLE, CHECKING, DOWNLOADING, VERIFYING, READY, CANCELLED, FAILED
  }

  /** Progress and stage notifications; each method may be called from any thread. */
  public interface Listener {
    /** Stage transitions; {@code detail} is a short human-readable failure text on FAILED. */
    void onState(State state, String detail);

    /** Download progress in bytes; {@code total} is -1 when the server omitted it. */
    void onProgress(long bytes, long total);

    /** A staged, SHA-256-verified APK is ready for install. */
    void onReady(UpdateManifest.AndroidBuild build, File apk);
  }

  private final File stagingDir;
  private final long installedVersionCode;
  private final int platformApiLevel;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private volatile boolean active;

  public AppUpdateManager(File stagingDir, long installedVersionCode, int platformApiLevel) {
    this.stagingDir = stagingDir;
    this.installedVersionCode = installedVersionCode;
    this.platformApiLevel = platformApiLevel;
    if (!stagingDir.isDirectory() && !stagingDir.mkdirs()) {
      throw new IllegalArgumentException("cannot create update staging directory");
    }
  }

  /** Starts a background check; the eligible result is delivered via {@code onReady}. */
  public void checkForUpdate(Listener listener) {
    if (active) {
      return;
    }
    active = true;
    cancelled.set(false);
    worker.execute(() -> runCheck(listener));
  }

  /** Cancels an in-flight fetch; any retained partial file stays for resume. */
  public void cancel() {
    cancelled.set(true);
  }

  /** Shuts the worker down; no further callbacks are delivered. */
  public void close() {
    worker.shutdownNow();
  }

  /** Runnable after any check; delivers the final outcome to the listener. */
  private void runCheck(Listener listener) {
    try {
      UpdateManifest manifest = fetchFeed();
      UpdateManifest.AndroidBuild build = manifest.android();
      String stagedSha = stagedDigest(build.apkSha256());
      UpdateDecision.Reason reason =
          UpdateDecision.decide(build, installedVersionCode, platformApiLevel, stagedSha);
      if (reason == UpdateDecision.Reason.APPLY) {
        deliver(build, stagedSha, listener);
      } else if (reason == UpdateDecision.Reason.DIGEST_MISMATCH) {
        deleteStaged(build.apkSha256());
        listener.onState(State.FAILED, "staged update failed its SHA-256 check");
      } else if (reason == UpdateDecision.Reason.NOTHING_PUBLISHED
          || reason == UpdateDecision.Reason.OLDER_OR_SAME_VERSION_PUBLISHED) {
        listener.onState(State.IDLE, reason.name());
      } else {
        listener.onState(State.FAILED, reason.name());
      }
    } catch (Exception failure) {
      if (cancelled.get() || worker.isShutdown()) {
        listener.onState(State.CANCELLED, null);
      } else {
        listener.onState(
            State.FAILED,
            failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage());
      }
    } finally {
      active = false;
    }
  }

  private void deliver(UpdateManifest.AndroidBuild build, String stagedSha, Listener listener)
      throws IOException {
    if (stagedSha != null) {
      File verified = stagedApk(build.apkSha256());
      if (verified.isFile()) {
        listener.onState(State.READY, null);
        listener.onReady(build, verified);
        return;
      }
    }
    listener.onState(State.DOWNLOADING, null);
    File partial = download(build, listener);
    if (partial == null) {
      return; // cancelled; cancel() path already reported CANCELLED.
    }
    listener.onState(State.VERIFYING, null);
    File verified = verify(partial, build);
    if (verified == null) {
      deleteStaged(build.apkSha256());
      listener.onState(State.FAILED, "downloaded APK failed its SHA-256 check");
      return;
    }
    listener.onReady(build, verified);
  }private UpdateManifest fetchFeed() throws IOException, UpdateManifest.Malformed {
    HttpURLConnection connection =
        (HttpURLConnection) new URL(UpdatePolicy.FEED_URL).openConnection();
    try {
      connection.setInstanceFollowRedirects(true);
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      connection.setRequestMethod("GET");
      connection.setRequestProperty("User-Agent", USER_AGENT);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Accept-Encoding", "identity");
      int code = connection.getResponseCode();
      if (code != HttpURLConnection.HTTP_OK) {
        throw new IOException("update feed returned HTTP " + code);
      }
      if (connection.getContentLengthLong() > UpdatePolicy.MAX_FEED_BYTES) {
        throw new IOException("update feed is oversized");
      }
      java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
      try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) > 0) {
          if (cancelled.get()) {
            throw new IOException("cancelled");
          }
          body.write(buffer, 0, read);
          if (body.size() > UpdatePolicy.MAX_FEED_BYTES) {
            throw new IOException("update feed is oversized");
          }
        }
      }
      String text = decodeStrictUtf8(body.toByteArray());
      return UpdateManifest.parseText(text);
    } finally {
      connection.disconnect();
    }
  }

  /**
   * Resumable download of the published APK into a {@code .part} staging file.
   *
   * <p>Returns the partial file, or null when cancelled. The download restarts
   * from scratch when the server ignores the Range request (HTTP 200) or
   * refuses it (416); a stale larger-than-target partial is discarded first.
   */
  private File download(UpdateManifest.AndroidBuild build, Listener listener) throws IOException {
    for (int attempt = 0; attempt < 2; attempt++) {
      File partial = stagedFile(build.apkSha256());
      long existing = partial.isFile() ? partial.length() : 0;
      if (existing > build.apkBytes()) {
        partial.delete();
        existing = 0;
      }
      HttpURLConnection connection =
          (HttpURLConnection) new URL(build.apkUrl()).openConnection();
      try {
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (existing > 0) {
          connection.setRequestProperty("Range", "bytes=" + existing + "-");
        }
        int code = connection.getResponseCode();
        boolean resume = code == HttpURLConnection.HTTP_PARTIAL;
        if (code == HttpURLConnection.HTTP_RANGE_NOT_SATISFIABLE && existing > 0) {
          partial.delete();
          continue; // retry without a Range header
        }
        if (code != HttpURLConnection.HTTP_OK && !resume) {
          throw new IOException("APK download returned HTTP " + code);
        }
        long declared = connection.getContentLengthLong();
        long total = resume ? existing + declared : declared;
        if (total > build.apkBytes()) {
          throw new IOException("APK download is oversized");
        }
        long written = existing;
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = new FileOutputStream(partial, resume)) {
          byte[] buffer = new byte[STREAM_BUFFER_BYTES];
          int read;
          while ((read = input.read(buffer)) > 0) {
            if (cancelled.get()) {
              throw new IOException("cancelled");
            }
            written += read;
            if (written > build.apkBytes()) {
              throw new IOException("APK download is oversized");
            }
            output.write(buffer, 0, read);
            listener.onProgress(written, total);
          }
        }
        if (declared > 0 && written != total) {
          throw new IOException("APK download was truncated");
        }
        return partial;
      } finally {
        connection.disconnect();
      }
    }
    throw new IOException("APK download could not be resumed");
  }

  /** Re-hashes the partial and renames it into place; null on any digest mismatch. */
  private File verify(File partial, UpdateManifest.AndroidBuild build) throws IOException {
    if (!UpdateDigest.sha256Hex(partial).equalsIgnoreCase(build.apkSha256())) {
      return null;
    }
    File verified = stagedApk(build.apkSha256());
    if (verified.equals(partial)) {
      return verified;
    }
    verified.delete();
    return partial.renameTo(verified) ? verified : null;
  }

  /** SHA-256 of the currently staged verified APK for this entry, or null. */
  private String stagedDigest(String apkSha256) {
    File verified = stagedApk(apkSha256);
    if (!verified.isFile()) {
      return null;
    }
    try {
      return UpdateDigest.sha256Hex(verified);
    } catch (IOException ignored) {
      return null;
    }
  }

  private File stagedApk(String apkSha256) {
    return new File(stagingDir, "kanta-update-" + apkSha256.substring(0, 12) + ".apk");
  }

  private File stagedFile(String apkSha256) {
    return new File(stagingDir, UpdateDigest.stagingName(apkSha256));
  }

  private void deleteStaged(String apkSha256) {
    stagedFile(apkSha256).delete();
    stagedApk(apkSha256).delete();
  }

  private static String decodeStrictUtf8(byte[] bytes) throws IOException {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException malformed) {
      throw new IOException("feed is not valid UTF-8");
    }
  }
}