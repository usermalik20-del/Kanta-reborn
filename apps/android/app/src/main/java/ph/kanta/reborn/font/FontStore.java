package ph.kanta.reborn.font;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import ph.kanta.reborn.content.ContentRoot;
import ph.kanta.reborn.content.ContentScanner;
import ph.kanta.reborn.settings.SettingsStore;

/** App-lifetime font adapter. UI reads a cached face only; all provider/font I/O is serial. */
public final class FontStore {
  // getInstance converts every caller to application context before construction.
  // No Activity context is retained; this cache intentionally has app lifetime.
  @android.annotation.SuppressLint("StaticFieldLeak")
  private static FontStore instance;
  private static final long MAX_FONT_BYTES = 8L * 1024 * 1024;
  private static final int MAX_FONTS = 64;
  // Bundled faces are small OFL assets; caching keeps chips, picker rows and
  // stage renderers on one shared instance instead of re-decoding per rebuild.
  private static final Map<String, Typeface> BUNDLED_CACHE = new HashMap<>();
  private final Context context;
  private final SettingsStore settings;
  private final Handler main = new Handler(Looper.getMainLooper());
  // One worker keeps provider I/O serial. The queue is unbounded on purpose:
  // with a 1-slot queue, a select() submitted while chip previews were pending
  // was rejected, the saved font reset silently, and the stage preview never
  // changed. A queued tap must always win once earlier loads finish.
  private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<Runnable>(), new ThreadPoolExecutor.AbortPolicy());
  private volatile Typeface typeface;
  private long selectionGeneration;

  public static synchronized FontStore getInstance(Context context) {
    if (instance == null) instance = new FontStore(context.getApplicationContext());
    return instance;
  }

  private FontStore(Context context) {
    this.context = context;
    settings = new SettingsStore(context);
    String saved = settings.getLyricFont();
    if (!saved.isEmpty()) select(Uri.parse(saved), ignored -> {});
  }

  /** Null means use each renderer's original built-in face. No disk/provider access. */
  public Typeface typeface() { return typeface; }

  public static final class FontEntry {
    public final Uri uri;
    public final String name;
    // Package-private so device regression checks can build synthetic entries.
    FontEntry(Uri uri, String name) { this.uri = uri; this.name = name; }
  }

  /** Called on main. Latest selection wins; malformed/inaccessible fonts reset to built-in. */
  public void select(Uri uri, Consumer<Boolean> completed) {
    long generation = ++selectionGeneration;
    typeface = null;
    settings.setLyricFont(uri == null ? "" : uri.toString());
    if (uri == null) { completed.accept(true); return; }
    Typeface cached = cachedBundled(uri);
    if (cached != null) {
      typeface = cached; // No I/O on the main thread; refresh is immediate.
      completed.accept(true);
      return;
    }
    submit(() -> {
      Typeface loaded = load(context, uri);
      main.post(() -> {
        if (generation != selectionGeneration) return;
        typeface = loaded;
        if (loaded == null) settings.setLyricFont("");
        completed.accept(loaded != null);
      });
    }, () -> { settings.setLyricFont(""); completed.accept(false); });
  }

  /** Called on main; discovery never blocks rendering or opens a new audio owner. */
  public void discover(Uri root, Consumer<List<FontEntry>> completed) {
    submit(() -> {
      List<FontEntry> entries = new ArrayList<>();
      try {
        ContentScanner scanner = new ContentScanner(context.getContentResolver(), new ContentRoot(context));
        for (Uri uri : scanner.scanFonts(root, () -> false)) {
          if (entries.size() >= MAX_FONTS) break;
          String name = "Custom font";
          try (Cursor cursor = context.getContentResolver().query(uri,
              new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null)
              name = cursor.getString(0);
          }
          entries.add(new FontEntry(uri, name));
        }
        entries.sort((left, right) -> left.name.compareToIgnoreCase(right.name));
      } catch (RuntimeException unavailable) {
        entries.clear(); // Permission/provider failure: no private paths or URIs in logs.
      }
      List<FontEntry> result = Collections.unmodifiableList(entries);
      main.post(() -> completed.accept(result));
    }, () -> completed.accept(Collections.emptyList()));
  }

  private void submit(Runnable work, Runnable rejected) {
    try { worker.execute(work); }
    catch (java.util.concurrent.RejectedExecutionException busy) { rejected.run(); }
  }

  /** Package-private for device regression tests. Temporary bytes never become durable state. */
  public java.util.List<FontEntry> bundledFonts() {
    List<FontEntry> entries = new ArrayList<>();
    for (BundledLyricFonts.Option option : BundledLyricFonts.OPTIONS) {
      entries.add(new FontEntry(option.uri, option.label));
    }
    return Collections.unmodifiableList(entries);
  }

  public void preview(Uri uri, Consumer<Typeface> completed) {
    if (uri == null) { completed.accept(null); return; }
    Typeface cached = cachedBundled(uri);
    if (cached != null) {
      main.post(() -> completed.accept(cached));
      return;
    }
    submit(() -> {
      Typeface face = load(context, uri);
      main.post(() -> completed.accept(face));
    }, () -> completed.accept(null));
  }

  /** Cached bundled face, or null when it has not loaded yet. Never does I/O. */
  Typeface cachedBundled(Uri uri) {
    if (uri == null || !BundledLyricFonts.SCHEME.equals(uri.getScheme())) return null;
    synchronized (BUNDLED_CACHE) {
      return BUNDLED_CACHE.get(uri.toString());
    }
  }

  static Typeface load(Context context, Uri uri) {
    File temporary = null;
    try {
      if (BundledLyricFonts.SCHEME.equals(uri.getScheme())) {
        String key = uri.toString();
        synchronized (BUNDLED_CACHE) {
          Typeface cached = BUNDLED_CACHE.get(key);
          if (cached != null) return cached;
        }
        Typeface bundled = BundledLyricFonts.load(context, uri);
        if (bundled != null) {
          synchronized (BUNDLED_CACHE) {
            BUNDLED_CACHE.put(key, bundled);
          }
        }
        return bundled;
      }
      temporary = File.createTempFile("lyric-font-", ".tmp", context.getCacheDir());
      long copied = 0;
      try (InputStream input = context.getContentResolver().openInputStream(uri);
           FileOutputStream output = new FileOutputStream(temporary)) {
        if (input == null) return null;
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
          copied += count;
          if (copied > MAX_FONT_BYTES) return null;
          output.write(buffer, 0, count);
        }
      }
      if (copied == 0) return null;
      // Unlike createFromFile, Builder reports invalid font data as null.
      return new Typeface.Builder(temporary).build();
    } catch (java.io.IOException | RuntimeException malformedOrUnavailable) {
      return null;
    } finally {
      if (temporary != null) temporary.delete();
    }
  }
}
