package ph.kanta.reborn.core.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * Owns the user's interlude image: one file in app-private storage, atomically replaced
 * when a new image is imported. The source document is never modified.
 */
public final class InterludeStore {
  public static final String FILE_NAME = "interlude-image";
  static final long MAX_BYTES = 16L * 1024 * 1024;

  private InterludeStore() {}

  static File file(Context context) {
    return new File(context.getFilesDir(), FILE_NAME);
  }

  /**
   * Validates and copies {@code source} into the interlude slot. Returns false (and leaves the
   * previous image untouched) when the type, size, or decode check fails.
   */
  public static boolean install(Context context, Uri source) {
    String type = context.getContentResolver().getType(source);
    String suffix = suffixOf(source);
    boolean typeOk =
        "image/png".equals(type)
            || "image/jpeg".equals(type)
            || "image/webp".equals(type)
            || "image/gif".equals(type)
            || (type == null
                && (".png".equals(suffix)
                    || ".jpg".equals(suffix)
                    || ".jpeg".equals(suffix)
                    || ".webp".equals(suffix)
                    || ".gif".equals(suffix)));
    if (!typeOk) return false;
    try {
      try (InputStream input = context.getContentResolver().openInputStream(source)) {
        if (input == null) return false;
        File destination = file(context);
        File temporary = new File(context.getFilesDir(), FILE_NAME + ".importing");
        long written = 0;
        byte[] buffer = new byte[64 * 1024];
        int read;
        try (OutputStream out = new FileOutputStream(temporary)) {
          while ((read = input.read(buffer)) >= 0) {
            written += read;
            if (written > MAX_BYTES) {
              out.write(buffer, 0, Math.min(read, (int) (written - MAX_BYTES)));
              return false;
            }
            out.write(buffer, 0, read);
          }
        }
        if (!decodeTest(temporary)) {
          temporary.delete();
          return false;
        }
        if (destination.exists() && !destination.delete()) return false;
        if (!temporary.renameTo(destination)) {
          temporary.delete();
          return false;
        }
        return true;
      }
    } catch (Exception failure) {
      return false;
    }
  }

  private static boolean decodeTest(File candidate) {
    try (RandomAccessFile probe = new RandomAccessFile(candidate, "r")) {
      try (InputStream stream = new java.io.FileInputStream(candidate)) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(stream, null, options);
        return options.outWidth > 0 && options.outHeight > 0;
      }
    } catch (Exception failure) {
      return false;
    }
  }

  /**
   * Loads the stored image as a Drawable. Animated images use the platform animator on API 28+
   * and their first frame elsewhere. Returns null when nothing is stored or unreadable.
   */
  public static Drawable load(Context context) {
    File candidate = file(context);
    if (!candidate.isFile()) return null;
    if (isAnimated(candidate)) {
      if (android.os.Build.VERSION.SDK_INT >= 28) {
        // The platform factory is not present in every compile stub, so resolve it by name.
        try {
          java.lang.reflect.Method factory =
              AnimatedImageDrawable.class.getMethod(
                  "create", android.content.Context.class, android.net.Uri.class);
          return (AnimatedImageDrawable) factory
              .invoke(null, context, android.net.Uri.fromFile(candidate));
        } catch (Exception failure) {
          return null;
        }
      }
      Bitmap frame = firstFrame(candidate);
      return frame == null ? null : new BitmapDrawable(context.getResources(), frame);
    }
    Bitmap decoded = firstFrame(candidate);
    return decoded == null ? null : new BitmapDrawable(context.getResources(), decoded);
  }

  public static void delete(Context context) {
    file(context).delete();
  }

  private static Bitmap firstFrame(File candidate) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    try (InputStream stream = new java.io.FileInputStream(candidate)) {
      BitmapFactory.decodeStream(stream, null, options);
      if (options.outWidth <= 0 || options.outHeight <= 0) return null;
      int sample = 1;
      while (options.outWidth / (sample * 2) >= 1024
          && options.outHeight / (sample * 2) >= 1024) sample *= 2;
      options.inJustDecodeBounds = false;
      options.inSampleSize = sample;
      try (InputStream decoded = new java.io.FileInputStream(candidate)) {
        return BitmapFactory.decodeStream(decoded, null, options);
      }
    } catch (Exception failure) {
      return null;
    }
  }

  private static boolean isAnimated(File candidate) {
    if (candidate.length() < 12) return false;
    try (RandomAccessFile gif = new RandomAccessFile(candidate, "r")) {
      byte[] head = new byte[6];
      gif.readFully(head);
      return head[0] == 'G' && head[1] == 'I' && head[2] == 'F';
    } catch (Exception failure) {
      return false;
    }
  }

  private static String suffixOf(Uri source) {
    String name = source.getLastPathSegment();
    if (name == null) return "";
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot).toLowerCase(java.util.Locale.ROOT);
  }
}
