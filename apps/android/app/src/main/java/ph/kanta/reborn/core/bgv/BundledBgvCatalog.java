package ph.kanta.reborn.core.bgv;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The built-in bundled background videos.
 *
 * <p>These videos are packaged in the APK under assets/bgv/ and are available immediately
 * on first launch without any download. They serve as the default background video
 * selection and for the auto-shuffle feature.
 */
public final class BundledBgvCatalog {

  /** A bundled background video clip. */
  public static final class Clip {
    public final String id;
    public final String title;
    public final String assetPath; // relative to assets/bgv/
    public final long sizeBytes;

    public Clip(String id, String title, String assetPath, long sizeBytes) {
      this.id = id;
      this.title = title;
      this.assetPath = assetPath;
      this.sizeBytes = sizeBytes;
    }

    /** Returns the full asset path for opening via AssetManager. */
    public String fullAssetPath() {
      return "bgv/" + assetPath;
    }
  }

  /** All bundled clips. */
  public static final List<Clip> BUILT_IN = Collections.unmodifiableList(
      new ArrayList<>(List.of(
          new Clip(
              "bgv_default",
              "Default Ambience",
              "video_31F9FE1A.mp4",
              2952060L),
          new Clip(
              "bgv_ocean",
              "Ocean Waves",
              "video_0431EA43.mp4",
              8538265L),
          new Clip(
              "bgv_sunset",
              "Golden Sunset",
              "video_BD431C68.mp4",
              8571532L),
          new Clip(
              "bgv_forest",
              "Forest Light",
              "video_BF279006.mp4",
              8595883L),
          new Clip(
              "bgv_city",
              "City Nights",
              "video_F630A5E5.mp4",
              2900656L)
      )));

  /** Returns a clip by id, or null. */
  public static Clip byId(String id) {
    if (id == null) return null;
    for (Clip clip : BUILT_IN) {
      if (clip.id.equals(id)) return clip;
    }
    return null;
  }

  /** Returns a random clip for shuffle. */
  public static Clip random() {
    return BUILT_IN.get(new Random().nextInt(BUILT_IN.size()));
  }

  /** Returns the first clip as default. */
  public static Clip defaultClip() {
    return BUILT_IN.get(0);
  }

  /** Copies a bundled clip to a destination file. Returns true on success. */
  public static boolean copyTo(Context context, Clip clip, java.io.File dest) {
    AssetManager assets = context.getAssets();
    try (InputStream in = assets.open(clip.fullAssetPath());
         java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      out.getFD().sync();
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /** Human-readable size label. */
  public static String sizeLabel(long bytes) {
    if (bytes < 1024L * 1024L) return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
    return Math.round(bytes / (1024.0 * 1024.0)) + " MB";
  }

  private BundledBgvCatalog() {}
}
