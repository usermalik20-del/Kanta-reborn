package ph.kanta.reborn.font;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import ph.kanta.reborn.settings.SettingsStore;

/** Device-side decoder checks. Synthetic failures plus a system font, no bundled third-party asset. */
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
    } finally {
      settings.setLyricFont(previous);
      malformed.delete(); oversized.delete();
    }
    return checks;
  }
}
