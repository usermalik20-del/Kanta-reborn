package ph.kanta.reborn;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

/** Separate from the manually reviewed support report; only fixed-shape markers can be uploaded. */
final class AutomaticDiagnostics {
  private static final String ENDPOINT = "https://kanta-public.vercel.app/api/diagnostics";
  private static final int MAX_ATTEMPTS = 4;
  private static final long CRASH_MAX_AGE = TimeUnit.DAYS.toMillis(7);
  private final SharedPreferences prefs;
  private final AtomicFile pendingCrash;
  private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1);
  private boolean visible;
  private int epoch;
  private int attempts;
  private long nextAttempt;
  private String attemptDay = "";
  private ScheduledFuture<?> scheduled;
  private HttpsURLConnection connection;

  private final boolean available;

  AutomaticDiagnostics(Context context) {
    this(context.getSharedPreferences("automatic-diagnostics", Context.MODE_PRIVATE),
        new File(context.getNoBackupFilesDir(), "pending-crash-marker"),
        BuildConfig.AUTOMATIC_DIAGNOSTICS);
  }

  AutomaticDiagnostics(SharedPreferences prefs, File crashFile, boolean available) {
    this.prefs = prefs;
    this.available = available;
    pendingCrash = new AtomicFile(crashFile);
    worker.setRemoveOnCancelPolicy(true);
    if (!enabled()) {
      clearPending();
      if (!prefs.getAll().isEmpty()) prefs.edit().clear().apply();
    }
  }

  boolean available() { return available; }

  synchronized boolean enabled() {
    return available() && prefs.contains("installation") && prefs.contains("consent-key");
  }

  // Consent transitions are rare; persist before UI returns so a restart cannot restore opt-in.
  @android.annotation.SuppressLint("ApplySharedPref")
  synchronized boolean setEnabled(boolean enabled) {
    if (!enabled || !available()) {
      invalidate();
      clearPending();
      prefs.edit().clear().commit();
      attempts = 0;
      nextAttempt = 0;
      return false;
    }
    if (enabled()) return true;
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    StringBuilder hex = new StringBuilder(64);
    for (byte value : secret) {
      hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
      hex.append(Character.forDigit(value & 0x0f, 16));
    }
    if (!prefs.edit().putString("installation", UUID.randomUUID().toString())
        .putString("consent-key", hex.toString()).commit()) {
      prefs.edit().clear().apply();
      return false;
    }
    schedule();
    return true;
  }

  synchronized void setVisible(boolean visible) {
    this.visible = visible;
    if (visible) schedule(); else invalidate();
  }

  // Only a bounded local write in the uncaught handler. Never receive the Throwable here.
  synchronized void markCrash() {
    if (!enabled()) return;
    FileOutputStream stream = null;
    try {
      stream = pendingCrash.startWrite();
      stream.write(UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII));
      pendingCrash.finishWrite(stream);
    } catch (IOException | RuntimeException failure) {
      if (stream != null) pendingCrash.failWrite(stream);
    }
  }

  private String crashId() {
    File file = pendingCrash.getBaseFile();
    if (!file.exists()) return "";
    long age = System.currentTimeMillis() - file.lastModified();
    if (age < 0 || age >= CRASH_MAX_AGE || file.length() != 36) {
      clearPending();
      return "";
    }
    try {
      String id = new String(pendingCrash.readFully(), StandardCharsets.US_ASCII);
      if (id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) return id;
    } catch (IOException unreadable) {
      // Unreadable markers have no support value.
    }
    clearPending();
    return "";
  }

  private void clearPending() { pendingCrash.delete(); }

  private void invalidate() {
    epoch++;
    if (scheduled != null) scheduled.cancel(false);
    scheduled = null;
    if (connection != null) connection.disconnect();
    connection = null;
  }

  private void schedule() {
    if (!visible || !enabled() || scheduled != null || nextAttempt == Long.MAX_VALUE) return;
    String day = LocalDate.now(ZoneOffset.UTC).toString();
    if (!day.equals(attemptDay)) {
      attemptDay = day;
      attempts = 0;
      nextAttempt = 0;
    }
    boolean sentToday = day.equals(prefs.getString("active-day", ""));
    long delay = Math.max(0L, nextAttempt - SystemClock.elapsedRealtime());
    if (attempts >= MAX_ATTEMPTS || (sentToday && !pendingCrash.getBaseFile().exists())) {
      delay = Math.max(delay, LocalDate.parse(day).plusDays(1).atStartOfDay(ZoneOffset.UTC)
          .toInstant().toEpochMilli() - System.currentTimeMillis());
    }
    int expected = epoch;
    scheduled = worker.schedule(() -> send(expected), delay, TimeUnit.MILLISECONDS);
  }

  private void send(int expected) {
    final String sentDay, crash, installation, secret;
    final boolean active;
    synchronized (this) {
      if (expected != epoch || !enabled() || !visible) return;
      sentDay = LocalDate.now(ZoneOffset.UTC).toString();
      if (!sentDay.equals(attemptDay)) {
        attemptDay = sentDay;
        attempts = 0;
        nextAttempt = 0;
      }
      if (attempts >= MAX_ATTEMPTS) { scheduled = null; schedule(); return; }
      crash = crashId();
      active = !sentDay.equals(prefs.getString("active-day", ""));
      if (!active && crash.isEmpty()) { scheduled = null; schedule(); return; }
      installation = prefs.getString("installation", "");
      secret = prefs.getString("consent-key", "");
      attempts++;
    }
    int status = 0;
    long retryAfter = 0;
    HttpsURLConnection request = null;
    try {
      request = (HttpsURLConnection) new URL(ENDPOINT).openConnection();
      request.setInstanceFollowRedirects(false);
      request.setRequestMethod("POST");
      request.setConnectTimeout(5000);
      request.setReadTimeout(5000);
      request.setRequestProperty("Authorization", "Bearer " + secret);
      request.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      String marker = crash.isEmpty() ? "" : "{\"type\":\"crash\",\"id\":\"" + crash + "\"}";
      byte[] body = ("{\"installationId\":\"" + installation + "\",\"active\":" + active
          + ",\"events\":[" + marker + "]}").getBytes(StandardCharsets.UTF_8);
      request.setDoOutput(true);
      request.setFixedLengthStreamingMode(body.length);
      synchronized (this) {
        if (expected != epoch || !enabled()) return;
        connection = request;
      }
      try (OutputStream out = request.getOutputStream()) { out.write(body); }
      status = request.getResponseCode();
      if (status == 429) {
        try {
          retryAfter = 1000 * Math.min(86400L,
              Math.max(0L, Long.parseLong(request.getHeaderField("Retry-After"))));
        } catch (NumberFormatException ignored) { /* Use local backoff. */ }
      }
    } catch (IOException ignored) {
      // A transient failure never becomes a breadcrumb or a raw diagnostic message.
    } finally {
      if (request != null) request.disconnect();
      synchronized (this) {
        if (expected == epoch) finish(sentDay, crash, status, retryAfter);
      }
    }
  }

  private void finish(String day, String crash, int status, long retryAfter) {
    connection = null;
    scheduled = null;
    if (!enabled()) return;
    if (status == 204) {
      prefs.edit().putString("active-day", day).apply();
      if (!crash.isEmpty() && crash.equals(crashId())) clearPending();
      nextAttempt = 0;
      schedule();
    } else if (status == 0 || status == 429 || status >= 500) {
      nextAttempt = SystemClock.elapsedRealtime() + Math.max(retryAfter, 30_000L << attempts);
      schedule();
    } else {
      // Do not retry permanent rejection on this process's next foreground event.
      nextAttempt = Long.MAX_VALUE;
    }
  }
}
