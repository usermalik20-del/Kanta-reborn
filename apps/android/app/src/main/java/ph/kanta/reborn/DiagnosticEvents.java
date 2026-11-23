package ph.kanta.reborn;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** App-only breadcrumbs. Callers supply fixed operation names and non-personal counters. */
final class DiagnosticEvents {
  private static final int LIMIT = 32;
  private final ArrayDeque<String> recent = new ArrayDeque<>();
  private final AtomicFile lastCrash;

  DiagnosticEvents(Context context) {
    lastCrash = new AtomicFile(new File(context.getNoBackupFilesDir(), "last-app-crash.txt"));
  }

  void installCrashCapture(Runnable markAutomaticCrash) {
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    if (previous == null) return;
    Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
      try {
        markAutomaticCrash.run();
      } finally {
        try {
          saveCrash(failure);
        } finally {
          previous.uncaughtException(thread, failure);
        }
      }
    });
  }

  synchronized void record(String operation, Throwable failure) {
    if (recent.size() == LIMIT) recent.removeFirst();
    recent.addLast(event(operation, failure));
  }

  synchronized String snapshot() {
    StringBuilder out = new StringBuilder("\nRecent app events (this launch)\n");
    if (recent.isEmpty()) out.append("No events recorded.\n");
    for (String item : recent) out.append(item).append('\n');
    try {
      String crash = new String(lastCrash.readFully(), StandardCharsets.UTF_8);
      out.append("\nLast app crash (may be from an earlier launch)\n").append(crash);
    } catch (IOException unavailable) {
      out.append("\nNo saved app crash.\n");
    }
    return out.toString();
  }

  private synchronized void saveCrash(Throwable failure) {
    FileOutputStream stream = null;
    try {
      stream = lastCrash.startWrite();
      stream.write((event("Uncaught app error", failure) + '\n').getBytes(StandardCharsets.UTF_8));
      lastCrash.finishWrite(stream);
    } catch (IOException | RuntimeException unavailable) {
      lastCrash.failWrite(stream);
    }
  }

  private static String event(String operation, Throwable failure) {
    StringBuilder line = new StringBuilder(java.time.Instant.now().toString())
        .append(" · ").append(operation);
    // Do not export exception messages, URIs, file paths, search text, or raw logcat.
    if (failure != null) {
      line.append(" · ").append(failure.getClass().getName());
      int frames = 0;
      for (StackTraceElement frame : failure.getStackTrace()) {
        if (frame.getClassName().startsWith("ph.kanta.reborn.")) {
          line.append("\n  ").append(frame.getClassName()).append('.').append(frame.getMethodName())
              .append(':').append(frame.getLineNumber());
          if (++frames == 5) break;
        }
      }
    }
    return line.toString();
  }
}
