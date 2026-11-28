package ph.kanta.reborn.font;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import ph.kanta.reborn.settings.SettingsStore;
import ph.kanta.reborn.ui.components.KantaFontPicker;

/**
 * Device-side font flow checks: decoder failures fall back, bundled selections
 * resolve, cache and settle, and picker rows render their actual typefaces.
 */
public final class FontStoreChecks {
  private static int checks;
  private static void check(boolean valid, String message) {
    checks++; if (!valid) throw new AssertionError(message);
  }
  public static int run(Context context) throws Exception {
    checks = 0;
    File malformed = File.createTempFile("font-check-", ".ttf", context.getCacheDir());
    File oversized = File.createTempFile("font-check-", ".ttf", context.getCacheDir());
    SettingsStore settings = new SettingsStore(context);
    String previous = settings.getLyricFont();
    try {
      try (FileOutputStream output = new FileOutputStream(malformed)) { output.write(new byte[] {1,2,3,4}); }
      check(FontStore.load(context, Uri.fromFile(malformed)) == null, "Malformed font must fall back");
      try (RandomAccessFile output = new RandomAccessFile(oversized, "rw")) { output.setLength(8L * 1024 * 1024 + 1); }
      check(FontStore.load(context, Uri.fromFile(oversized)) == null, "Oversized font must fall back");
      check(FontStore.load(context, Uri.fromFile(new File(context.getCacheDir(), "missing-font-check"))) == null,
          "Missing font must fall back");
      File[] systemFonts = new File("/system/fonts").listFiles((dir, name) -> name.endsWith(".ttf"));
      check(systemFonts != null && systemFonts.length > 0, "Test device must provide a valid system font");
      check(FontStore.load(context, Uri.fromFile(systemFonts[0])) != null, "Valid font must decode");
      settings.setLyricFont("content://fixture/font.ttf");
      check(new SettingsStore(context).getLyricFont().equals("content://fixture/font.ttf"), "Font choice persists across store instances");
      settings.setLyricFont("");
      check(new SettingsStore(context).getLyricFont().isEmpty(), "Built-in font selection persists");

      // Diagnostic (stdout is captured in the CI job log): pinpoint which bundled-font
      // sub-step fails on this emulator so the fix is evidence-based, not a guess.
      diagnoseBundledFont(context);

      // Bundled selection resolves, caches, and rapid re-selection settles on the last pick.
      FontStore store = FontStore.getInstance(context);
      Uri anton = Uri.parse("kanta-font://anton-regular");
      Uri sora = Uri.parse("kanta-font://sora-regular");
      AtomicBoolean first = new AtomicBoolean();
      store.select(anton, ok -> first.set(ok));
      awaitFont(first::get, "Built-in font selection must complete");
      check(store.typeface() != null, "Built-in font must resolve to a face");
      Typeface antonFace = store.typeface();
      store.select(anton, ok -> { });
      awaitFont(() -> store.typeface() == antonFace, "Re-selecting a loaded font must be instant");
      check(store.typeface() == antonFace, "Loaded built-in face must be cached");
      store.select(sora, ok -> { });
      awaitFont(() -> store.typeface() != null && store.typeface() != antonFace,
          "Second built-in font must resolve");
      store.select(anton, ok -> { });
      store.select(sora, ok -> { });
      store.select(anton, ok -> { });
      awaitFont(() -> store.typeface() == antonFace, "Rapid selections must settle on the last pick");
      check(new SettingsStore(context).getLyricFont().equals(anton.toString()),
          "Selection persists while resolving");
      check(store.bundledFonts().size() == BundledLyricFonts.OPTIONS.size(),
          "Picker must expose every bundled font option");

      // Picker rows render their own typeface; custom rows list by name.
      final Object[] pickerResult = new Object[2];
      final CountDownLatch built = new CountDownLatch(1);
      new Handler(Looper.getMainLooper()).post(() -> {
        try {
          pickerResult[0] = new KantaFontPicker(context, store, store.bundledFonts(), true, "",
              (pickerView, uri, entry) -> { });
        } catch (Throwable failed) {
          pickerResult[1] = failed;
        }
        built.countDown();
      });
      check(built.await(15, TimeUnit.SECONDS), "Picker must build on the main thread");
      check(pickerResult[1] == null, "Picker build failed: " + pickerResult[1]);
      KantaFontPicker picker = (KantaFontPicker) pickerResult[0];
      ViewGroup rows = (ViewGroup) picker.getChildAt(0);
      check(rows.getChildCount() == store.bundledFonts().size() + 1,
          "Picker must list every built-in font plus the default");
      View vtRow = findRow(rows, "kanta.font.picker.row.vt323");
      check(vtRow != null, "Picker must include the previously available VT323 option");
      awaitFont(() -> vtRow != null && onMain(() -> {
        TextView sample = (TextView) ((ViewGroup) vtRow).getChildAt(0);
        return !sample.getTypeface().equals(Typeface.DEFAULT_BOLD);
      }), "Built-in row must render its own typeface");
      final Object[] customResult = new Object[2];
      final CountDownLatch customBuilt = new CountDownLatch(1);
      new Handler(Looper.getMainLooper()).post(() -> {
        try {
          List<FontStore.FontEntry> custom = java.util.Collections.singletonList(
              new FontStore.FontEntry(Uri.parse("content://media/tree/font.ttf"), "My Font.ttf"));
          customResult[0] = new KantaFontPicker(context, store, custom, false,
              "content://media/tree/font.ttf", (pickerView, uri, entry) -> { });
        } catch (Throwable failed) {
          customResult[1] = failed;
        }
        customBuilt.countDown();
      });
      check(customBuilt.await(15, TimeUnit.SECONDS), "Custom font picker must build");
      check(customResult[1] == null, "Custom picker build failed: " + customResult[1]);
      KantaFontPicker customPicker = (KantaFontPicker) customResult[0];
      check(((ViewGroup) customPicker.getChildAt(0)).getChildCount() == 2,
          "Custom picker must list the default plus each imported font");

      store.select(null, ok -> { });
      awaitFont(() -> store.typeface() == null, "Default selection must clear the face");
    } finally {
      settings.setLyricFont(previous);
      malformed.delete(); oversized.delete();
    }
    return checks;
  }

  /** Polls until the condition holds; the main looper keeps dispatching meanwhile.
   *  30 s: the CI emulator is a 2-core / 2 GB swiftshader x86_64 image where first
   *  font loads (asset copy + Skia parse) are far slower than on a real device. */
  private static void awaitFont(BooleanSupplier condition, String message)
      throws InterruptedException {
    long deadline = SystemClock.uptimeMillis() + 30000;
    while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < deadline) {
      SystemClock.sleep(50);
    }
    check(condition.getAsBoolean(), message);
  }

  /**
   * Reports, on stdout (captured by the CI job log, not just the logcat artifact), which
   * bundled-font sub-step fails on a given runtime: the APK asset stream read, the
   * {@code Typeface.Builder(File)} decode of the copied bytes, and the
   * {@code Typeface.Builder(AssetManager)} path (guarded against hanging).
   */
  private static void diagnoseBundledFont(Context context) {
    String asset = "lyric-fonts/anton.ttf";
    long t0 = SystemClock.uptimeMillis();
    byte[] bytes = null;
    String openError = null;
    try (InputStream in = context.getAssets().open(asset)) {
      bytes = readAllBounded(in, 4L * 1024 * 1024);
    } catch (Throwable t) {
      openError = t.getClass().getName() + ": " + t.getMessage();
    }
    StringBuilder diag = new StringBuilder("KANTA-FONT-DIAG anton.ttf; ")
        .append("testThread=").append(Thread.currentThread().getName())
        .append(Thread.currentThread() == android.os.Looper.getMainLooper().getThread()
            ? "(MAIN-DEADLOCK)" : "")
        .append("; ");
    diag.append("assetOpen=").append(bytes == null ? "NULL" : (bytes.length + "B"))
        .append(openError != null ? (" err=" + openError) : "")
        .append(" took=").append(SystemClock.uptimeMillis() - t0).append("ms; ");

    if (bytes != null) {
      File tmp = new File(context.getCacheDir(), "kanta-font-diag.ttf");
      try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(bytes); } catch (IOException ignored) { }
      long t1 = SystemClock.uptimeMillis();
      try {
        Typeface face = new Typeface.Builder(tmp).build();
        diag.append("builderFile=").append(face == null ? "NULL" : "ok")
            .append(" took=").append(SystemClock.uptimeMillis() - t1).append("ms; ");
      } catch (Throwable t) {
        diag.append("builderFile=THREW(").append(t).append("); ");
      }
      tmp.delete();
    } else {
      diag.append("builderFile=SKIPPED(no bytes); ");
    }

    final Object[] result = new Object[2];
    Thread probe = new Thread(() -> {
      try {
        result[0] = new Typeface.Builder(context.getAssets(), asset).build();
      } catch (Throwable t) {
        result[0] = t;
      }
      result[1] = Boolean.TRUE;
    });
    probe.setDaemon(true);
    probe.start();
    long t2 = SystemClock.uptimeMillis();
    try { probe.join(10000); } catch (InterruptedException ignored) { }
    if (!Boolean.TRUE.equals(result[1])) {
      diag.append("builderAssetManager=HANG(>10000ms);");
    } else if (result[0] instanceof Throwable) {
      diag.append("builderAssetManager=THREW(").append(result[0]).append(");");
    } else {
      diag.append("builderAssetManager=").append(result[0] == null ? "NULL" : "ok")
          .append(" took=").append(SystemClock.uptimeMillis() - t2).append("ms;");
    }
    // The font decodes fine, so the failure must be in async delivery: select() posts to
    // the FontStore worker, which then does main.post(callback). Probe whether a plain
    // post to the app main looper is actually dispatched from this (instrumentation)
    // thread — if not, the callback can never run and select() can never complete.
    final Object[] mainProbe = new Object[1];
    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> mainProbe[0] = Boolean.TRUE);
    long tm = SystemClock.uptimeMillis();
    while (mainProbe[0] == null && SystemClock.uptimeMillis() - tm < 5000) SystemClock.sleep(25);
    diag.append("mainLooperDispatch=")
        .append(mainProbe[0] != null
            ? ("ok took=" + (SystemClock.uptimeMillis() - tm) + "ms")
            : "NOT_DISPATCHED_in_5000ms")
        .append(";");

    // Surface findings in the CI job log: System.out maps to logcat on Android, but an
    // AssertionError message is printed by the instrumentation runner.
    throw new AssertionError(diag.toString());
  }

  private static byte[] readAllBounded(InputStream in, long max) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    long total = 0;
    int read;
    while ((read = in.read(chunk)) != -1) {
      buffer.write(chunk, 0, read);
      total += read;
      if (total > max) break;
    }
    return buffer.toByteArray();
  }

  private static boolean onMain(BooleanSupplier probe) {
    AtomicReference<Boolean> result = new AtomicReference<>(false);
    final CountDownLatch latch = new CountDownLatch(1);
    new Handler(Looper.getMainLooper()).post(() -> {
      result.set(probe.getAsBoolean());
      latch.countDown();
    });
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException ignored) {
    }
    return result.get();
  }

  private static View findRow(ViewGroup parent, String tag) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      View child = parent.getChildAt(i);
      if (tag.equals(child.getTag())) return child;
      if (child instanceof ViewGroup && findRow((ViewGroup) child, tag) != null)
        return findRow((ViewGroup) child, tag);
    }
    return null;
  }
}
