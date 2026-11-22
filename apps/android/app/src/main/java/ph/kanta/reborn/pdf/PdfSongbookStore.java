package ph.kanta.reborn.pdf;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;

/** Owns app-private copies, never a persistent grant to the provider's original. Worker only. */
final class PdfSongbookStore {
  private static final long MAX_BYTES = 100L * 1024 * 1024;
  private static final int PAGE_PIXELS = 1800;
  private final File directory;

  PdfSongbookStore(File directory) {
    this.directory = directory;
  }

  static final class TooLarge extends IOException {
    private static final long serialVersionUID = 1;
  }

  static final class Unreadable extends IOException {
    private static final long serialVersionUID = 1;

    Unreadable(Throwable cause) {
      super(cause);
    }
  }

  File[] list() throws IOException {
    if (!directory.exists()) return new File[0];
    File[] books = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".pdf"));
    if (books == null) throw new IOException("Cannot list songbooks");
    Arrays.sort(books, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
    return books;
  }

  File importDocument(ContentResolver resolver, Uri uri) throws IOException {
    if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme()))
      throw new IOException("Not a document URI");
    String title = null;
    try (Cursor cursor = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME},
        null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) title = cursor.getString(0);
    } catch (RuntimeException unavailable) {
      // Some providers allow reading the document but not its metadata.
    }
    try (InputStream input = resolver.openInputStream(uri)) {
      if (input == null) throw new IOException("Provider returned no PDF stream");
      return save(title, input);
    }
  }

  synchronized File save(String displayName, InputStream input) throws IOException {
    if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory())
      throw new IOException("Cannot create songbook folder");
    String name = safeTitle(displayName);
    File target = new File(directory, name + ".pdf");
    for (int i = 2; target.exists(); i++) {
      target = new File(directory, name + " (" + i + ").pdf");
    }
    File partial = File.createTempFile("songbook-", ".part", directory);
    try {
      try (FileOutputStream output = new FileOutputStream(partial)) {
        byte[] buffer = new byte[32 * 1024];
        long bytes = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
          checkInterrupted();
          bytes += read;
          if (bytes > MAX_BYTES) throw new TooLarge();
          output.write(buffer, 0, read);
        }
      }
      try (PdfRenderer renderer = open(partial)) {
        if (renderer.getPageCount() == 0) throw new Unreadable(null);
        try (PdfRenderer.Page page = renderer.openPage(0)) {
          if (page.getWidth() <= 0 || page.getHeight() <= 0) throw new Unreadable(null);
        }
      } catch (IOException | RuntimeException invalid) {
        throw new Unreadable(invalid);
      }
      checkInterrupted();
      Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
      return target;
    } finally {
      Files.deleteIfExists(partial.toPath());
    }
  }

  private static void checkInterrupted() throws IOException {
    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
  }

  static String safeTitle(String name) {
    if (name == null) return "Songbook";
    String title = name.trim();
    if (title.toLowerCase(Locale.ROOT).endsWith(".pdf"))
      title = title.substring(0, title.length() - 4);
    // Strip path separators, controls and direction overrides; keep ordinary Unicode titles.
    title = title.replaceAll("[/\\\\\\p{Cc}\\p{Cf}]", "_").trim();
    int count = title.codePointCount(0, title.length());
    if (count > 48) title = title.substring(0, title.offsetByCodePoints(0, 48)).trim();
    if (title.isEmpty() || ".".equals(title) || "..".equals(title)) return "Songbook";
    return title;
  }

  File selected(String name) {
    // Bundle state holds only names returned by list()/save(), never provider paths.
    if (name == null || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
        || !name.endsWith(".pdf")) return null;
    return new File(directory, name);
  }

  void delete(String name) throws IOException {
    File book = selected(name);
    if (book == null || !Files.isRegularFile(book.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
      throw new IOException("No saved songbook to remove");
    Files.delete(book.toPath());
  }

  static final class RenderedPage {
    final Bitmap image;
    final int index;
    final int total;

    RenderedPage(Bitmap image, int index, int total) {
      this.image = image;
      this.index = index;
      this.total = total;
    }
  }

  static RenderedPage render(File file, int requested) throws IOException {
    try (PdfRenderer renderer = open(file)) {
      int total = renderer.getPageCount();
      if (total <= 0) throw new Unreadable(null);
      int index = Math.max(0, Math.min(total - 1, requested));
      try (PdfRenderer.Page page = renderer.openPage(index)) {
        int width = page.getWidth();
        int height = page.getHeight();
        if (width <= 0 || height <= 0) throw new Unreadable(null);
        float scale = PAGE_PIXELS / (float) Math.max(width, height);
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
            Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
        try {
          bitmap.setDensity(Bitmap.DENSITY_NONE);
          bitmap.eraseColor(Color.WHITE);
          Matrix transform = new Matrix();
          transform.setScale(scale, scale);
          page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
          return new RenderedPage(bitmap, index, total);
        } catch (RuntimeException | Error failed) {
          bitmap.recycle();
          throw failed;
        }
      }
    }
  }

  private static PdfRenderer open(File file) throws IOException {
    ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    try {
      // PdfRenderer owns the descriptor only after its constructor succeeds.
      return new PdfRenderer(descriptor);
    } catch (IOException | RuntimeException | Error failed) {
      try {
        descriptor.close();
      } catch (IOException closeFailed) {
        failed.addSuppressed(closeFailed);
      }
      throw failed;
    }
  }
}
