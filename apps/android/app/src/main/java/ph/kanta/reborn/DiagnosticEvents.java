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
  private final AtomicFile lastCheckpoint;

  DiagnosticEvents(Context context) {
    File directory = context.getNoBackupFilesDir();
    lastCrash = new AtomicFile(new File(directory, "last-app-crash.txt"));
    lastCheckpoint = new AtomicFile(new File(directory, "last-diagnostic-checkpoint.txt"));
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
    String item = event(operation, failure);
    if (recent.size() == LIMIT) recent.removeFirst();
    recent.addLast(item);
    saveCheckpoint(item);
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
    try {
      String checkpoint = new String(lastCheckpoint.readFully(), StandardCharsets.UTF_8);
      out.append("\nLast persisted checkpoint\n").append(checkpoint);
    } catch (IOException unavailable) {
      out.append("\nNo persisted checkpoint.\n");
    }
    return out.toString();
  }

  private synchronized void saveCheckpoint(String item) {
    FileOutputStream stream = null;
    try {
      stream = lastCheckpoint.startWrite();
      String memory = "memoryPssKb=" + (android.os.Debug.getPss())
          + " nativeHeapKb=" + (android.os.Debug.getNativeHeapAllocatedSize() / 1024);
      stream.write((item + "\n" + memory + "\n").getBytes(StandardCharsets.UTF_8));
      lastCheckpoint.finishWrite(stream);
    } catch (IOException | RuntimeException unavailable) {
      lastCheckpoint.failWrite(stream);
    }
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
