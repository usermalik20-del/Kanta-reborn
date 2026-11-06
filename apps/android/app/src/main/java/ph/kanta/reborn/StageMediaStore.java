package ph.kanta.reborn;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Serial-IO owner of byte-preserved visual copies and their atomic selection. */
final class StageMediaStore {
  static final class Selection {
    final File photo, video;
    final String kind;

    Selection(File photo, File video, String kind) {
      this.photo = photo;
      this.video = video;
      this.kind = kind;
    }
  }

  private final Context context;
  private final File root;
  private final AtomicFile options;
  private String photo = "", video = "", kind = "scenic";

  StageMediaStore(Context context) throws IOException {
    this.context = context;
    root = new File(context.getFilesDir(), "stage-media");
    if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot open stage storage");
    options = new AtomicFile(new File(root, "selection.json"));
  }

  Selection load() throws Exception {
    if (options.getBaseFile().exists() || new File(options.getBaseFile() + ".bak").exists()) {
      if (Math.max(
              options.getBaseFile().length(), new File(options.getBaseFile() + ".bak").length())
          > 4096) throw new IOException("Stage settings too large");
      JSONObject value =
          new JSONObject(new String(options.readFully(), java.nio.charset.StandardCharsets.UTF_8));
      if (value.getInt("version") != 1) throw new IOException("Unsupported stage settings");
      photo = safe(value.optString("photo"), ".photo");
      video = safe(value.optString("video"), ".video");
      String selected = value.optString("kind", "scenic");
      kind =
          selected.equals("photo") && !photo.isEmpty()
              ? "photo"
              : selected.equals("video") && !video.isEmpty() ? "video" : "scenic";
    }
    return selection();
  }

  private String safe(String name, String suffix) {
    return name.matches("[a-f0-9]{64}\\" + suffix) && new File(root, name).isFile() ? name : "";
  }

  Selection selection() {
    return new Selection(
        photo.isEmpty() ? null : new File(root, photo),
        video.isEmpty() ? null : new File(root, video),
        kind);
  }

  Selection select(String value) throws Exception {
    if (!value.equals("scenic")
        && !(value.equals("photo") && !photo.isEmpty())
        && !(value.equals("video") && !video.isEmpty()))
      throw new IOException("No saved stage media");
    String previous = kind;
    kind = value;
    try {
      persist();
    } catch (Exception failure) {
      kind = previous;
      throw failure;
    }
    return selection();
  }

  Selection importMedia(Uri uri, boolean isVideo, BooleanSupplier cancelled) throws Exception {
    File temp = File.createTempFile("stage-", ".tmp", root), destination = null;
    String previousPhoto = photo, previousVideo = video, previousKind = kind;
    boolean created = false;
    try {
      MessageDigest hash = MessageDigest.getInstance("SHA-256");
      long limit = (isVideo ? 512L : 32L) * 1024 * 1024, total = 0;
      try (InputStream input = context.getContentResolver().openInputStream(uri);
          FileOutputStream output = new FileOutputStream(temp)) {
        if (input == null) throw new IOException("Cannot read selected media");
        byte[] block = new byte[65536];
        int count;
        while ((count = input.read(block)) != -1) {
          if (cancelled.getAsBoolean()) throw new InterruptedIOException();
          total += count;
          if (total > limit) throw new IOException("Selected media exceeds its size limit");
          output.write(block, 0, count);
          hash.update(block, 0, count);
        }
        output.getFD().sync();
      }
      if (isVideo) validateVideo(temp);
      else {
        try {
          Bitmap checked = displayPhoto(temp);
          checked.recycle();
        } catch (OutOfMemoryError failure) {
          throw new IOException("Cannot decode selected photo within memory limits", failure);
        }
      }
      if (cancelled.getAsBoolean()) throw new InterruptedIOException();
      StringBuilder id = new StringBuilder();
      for (byte value : hash.digest()) id.append(String.format(Locale.ROOT, "%02x", value & 255));
      destination = new File(root, id + (isVideo ? ".video" : ".photo"));
      if (!destination.exists()) {
        if (!temp.renameTo(destination)) throw new IOException("Cannot save selected media");
        created = true;
      } else {
        if (destination.length() != temp.length())
          throw new IOException("Stage media size check failed");
        MessageDigest existing = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(destination)) {
          byte[] block = new byte[65536];
          int count;
          while ((count = input.read(block)) != -1) {
            if (cancelled.getAsBoolean()) throw new InterruptedIOException();
            existing.update(block, 0, count);
          }
        }
        StringBuilder verified = new StringBuilder();
        for (byte value : existing.digest())
          verified.append(String.format(Locale.ROOT, "%02x", value & 255));
        if (!id.toString().equals(verified.toString()))
          throw new IOException("Stage media integrity check failed");
      }
      if (isVideo) video = destination.getName();
      else photo = destination.getName();
      kind = isVideo ? "video" : "photo";
      if (cancelled.getAsBoolean()) throw new InterruptedIOException();
      persist();
      String obsolete = isVideo ? previousVideo : previousPhoto;
      if (!obsolete.isEmpty() && !obsolete.equals(destination.getName())) {
        try {
          new File(root, obsolete).delete();
        } catch (SecurityException ignored) {
        }
      }
      return selection();
    } catch (Exception failure) {
      photo = previousPhoto;
      video = previousVideo;
      kind = previousKind;
      if (created && destination != null) destination.delete();
      throw failure;
    } finally {
      temp.delete();
    }
  }

  private void persist() throws Exception {
    JSONObject value =
        new JSONObject()
            .put("version", 1)
            .put("kind", kind)
            .put("photo", photo)
            .put("video", video);
    FileOutputStream stream = options.startWrite();
    try {
      stream.write(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      options.finishWrite(stream);
    } catch (Exception failure) {
      options.failWrite(stream);
      throw failure;
    }
  }

  /** Sets the video file name and kind to video, then persists. */
  public void setVideo(String videoFileName) {
    this.video = videoFileName;
    this.kind = "video";
    try { persist(); } catch (Exception ignored) {}
  }

  /** Sets the kind and persists. */
  public void setKind(String kind) {
    this.kind = kind;
    try { persist(); } catch (Exception ignored) {}
  }

  /** Persists current selection. */
  public void persistSelection() {
    try { persist(); } catch (Exception ignored) {}
  }

  private static BitmapFactory.Options imageBounds(File file) throws IOException {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
    if (bounds.outWidth <= 0
        || bounds.outHeight <= 0
        || bounds.outWidth > 16384
        || bounds.outHeight > 16384
        || (long) bounds.outWidth * bounds.outHeight > 100000000)
      throw new IOException("Unsupported image dimensions");
    return bounds;
  }

  static Bitmap displayPhoto(File file) throws IOException {
    BitmapFactory.Options options = imageBounds(file);
    options.inJustDecodeBounds = false;
    options.inSampleSize = 1;
    while (Math.max(
            options.outWidth / options.inSampleSize, options.outHeight / options.inSampleSize)
        > 2048) options.inSampleSize *= 2;
    Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    if (image == null) throw new IOException("Unsupported image");
    try {
      android.media.ExifInterface metadata =
          new android.media.ExifInterface(file.getAbsolutePath());
      int orientation = metadata.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
      android.graphics.Matrix transform = new android.graphics.Matrix();
      switch (orientation) {
        case 2:
          transform.setScale(-1, 1);
          break;
        case 3:
          transform.setRotate(180);
          break;
        case 4:
          transform.setScale(1, -1);
          break;
        case 5:
          transform.setRotate(90);
          transform.postScale(-1, 1);
          break;
        case 6:
          transform.setRotate(90);
          break;
        case 7:
          transform.setRotate(270);
          transform.postScale(-1, 1);
          break;
        case 8:
          transform.setRotate(270);
          break;
      }
      if (!transform.isIdentity()) {
        Bitmap oriented =
            Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), transform, true);
        if (oriented != image) image.recycle();
        image = oriented;
      }
    } catch (IOException ignored) {
    }
    return image;
  }

  private static void validateVideo(File file) throws IOException {
    MediaMetadataRetriever metadata = new MediaMetadataRetriever();
    try {
      metadata.setDataSource(file.getAbsolutePath());
      int width =
          Integer.parseInt(
              metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
      int height =
          Integer.parseInt(
              metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
      long duration =
          Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
      if (width <= 0
          || height <= 0
          || width > 8192
          || height > 8192
          || duration <= 0
          || duration > 21600000) throw new IOException("Unsupported video bounds");
    } catch (RuntimeException failure) {
      throw new IOException("Unsupported video", failure);
    } finally {
      metadata.release();
    }
  }
}
