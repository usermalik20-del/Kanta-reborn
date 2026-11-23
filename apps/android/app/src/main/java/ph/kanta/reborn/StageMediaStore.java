package ph.kanta.reborn;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** App-private visual copies, with selection and inventory committed in the same atomic file. */
final class StageMediaStore {
  static final int MAX_IMPORTS = 512;
  private static final int MAX_METADATA_BYTES = 1024 * 1024;

  static final class Entry {
    final File file;
    final String displayName;
    final boolean video;

    Entry(File file, String displayName) {
      this.file = file;
      this.displayName = displayName;
      this.video = isImported(file, true);
    }
  }

  static final class Selection {
    final File photo, video, importedVideo;
    final String kind;
    final List<Entry> imports;

    Selection(File photo, File video, File importedVideo, String kind, List<Entry> imports) {
      this.photo = photo;
      this.video = video;
      this.importedVideo = importedVideo;
      this.kind = kind;
      this.imports = Collections.unmodifiableList(imports);
    }
  }

  static final class ImportResult {
    final int added, duplicates, skipped;
    final boolean cancelled;

    ImportResult(int added, int duplicates, int skipped, boolean cancelled) {
      this.added = added;
      this.duplicates = duplicates;
      this.skipped = skipped;
      this.cancelled = cancelled;
    }
  }

  private final Context context;
  private final File root;
  private final AtomicFile options;
  private final Map<String, Entry> imports = new LinkedHashMap<>();
  private boolean loaded;
  private String photo = "", video = "", retainedVideo = "", kind = "scenic";

  StageMediaStore(Context context) throws IOException {
    this.context = context;
    root = new File(context.getFilesDir(), "stage-media");
    if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot open stage storage");
    options = new AtomicFile(new File(root, "selection.json"));
  }

  /** Returns the root directory for stage media. */
  public File getRoot() {
    return root;
  }

  /** Distinguish first launch from an explicit saved scenic background. */
  boolean hasSavedSelection() {
    return options.getBaseFile().isFile();
  }

  synchronized Selection load() throws Exception {
    loaded = false;
    File base = options.getBaseFile();
    File backup = new File(base + ".bak");
    if (!base.exists() && !backup.exists()) {
      loaded = true;
      return selection();
    }
    if (Math.max(base.length(), backup.length()) > MAX_METADATA_BYTES)
      throw new IOException("Stage settings too large");
    JSONObject value = new JSONObject(new String(options.readFully(), StandardCharsets.UTF_8));
    int version = value.getInt("version");
    if (version != 1 && version != 2) throw new IOException("Unsupported stage settings");
    String savedPhoto = safe(value.optString("photo"), ".photo");
    String savedVideo = safe(value.optString("video"), ".video");
    String savedRetained = safe(value.optString("importedVideo"), ".video");
    if (!isImported(new File(root, savedRetained), true)) savedRetained = "";
    Map<String, Entry> restored = new LinkedHashMap<>();
    if (version == 2) {
      JSONArray entries = value.getJSONArray("imports");
      if (entries.length() > MAX_IMPORTS) throw new IOException("Stage library too large");
      for (int i = 0; i < entries.length(); i++) {
        JSONObject item = entries.getJSONObject(i);
        String name = item.getString("file");
        File file = new File(root, name);
        if ((importName(name, true) || importName(name, false)) && file.isFile()) {
          restored.putIfAbsent(name, new Entry(file, cleanDisplayName(item.optString("name"))));
        }
      }
    } else {
      // v1 never saved display names. Preserve only its explicit references, not orphan files.
      addLegacyImport(restored, savedPhoto, false);
      addLegacyImport(restored, savedVideo, true);
      addLegacyImport(restored, savedRetained, true);
    }
    imports.clear();
    imports.putAll(restored);
    photo = imports.containsKey(savedPhoto) ? savedPhoto : "";
    video = isImported(new File(root, savedVideo), true) && !imports.containsKey(savedVideo)
        ? "" : savedVideo;
    retainedVideo = imports.containsKey(savedRetained) ? savedRetained : "";
    String selected = value.optString("kind", "scenic");
    kind = selected.equals("photo") && !photo.isEmpty() ? "photo"
        : selected.equals("video") && !video.isEmpty() ? "video" : "scenic";
    loaded = true;
    return selection();
  }

  private void addLegacyImport(Map<String, Entry> items, String name, boolean video) {
    File file = new File(root, name);
    if (isImported(file, video)) items.putIfAbsent(name, new Entry(file, ""));
  }

  private String safe(String name, String suffix) {
    boolean isVideo = ".video".equals(suffix);
    if (importName(name, isVideo) && new File(root, name).isFile()) return name;
    if (isVideo) {
      for (BundledBgvCatalog.Clip clip : BundledBgvCatalog.BUILT_IN) {
        if ((clip.id + ".video").equals(name) && new File(root, name).isFile()) return name;
      }
    }
    return "";
  }

  synchronized Selection selection() {
    File currentVideo = fileOrNull(video);
    return new Selection(fileOrNull(photo), currentVideo,
        isImported(currentVideo, true) ? currentVideo : fileOrNull(retainedVideo), kind,
        new ArrayList<>(imports.values()));
  }

  private File fileOrNull(String name) {
    return name.isEmpty() ? null : new File(root, name);
  }

  synchronized Selection select(String value) throws Exception {
    if ("video".equals(value)) {
      File imported = selection().importedVideo;
      if (imported == null) throw new IOException("No saved video");
      return selectImport(imported.getName(), true);
    }
    if ("photo".equals(value)) return selectImport(photo, false);
    if (!"scenic".equals(value)) throw new IOException("Unknown stage background");
    return changeSelection(photo, video, retainedVideo, value);
  }

  synchronized Selection selectImport(String name, boolean isVideo) throws Exception {
    Entry entry = imports.get(name);
    if (entry == null || entry.video != isVideo || !entry.file.isFile())
      throw new IOException("No saved stage media");
    return isVideo ? changeSelection(photo, name, name, "video")
        : changeSelection(name, video, retainedVideo, "photo");
  }

  private Selection changeSelection(String nextPhoto, String nextVideo, String nextRetained,
      String nextKind) throws Exception {
    String oldPhoto = photo, oldVideo = video, oldRetained = retainedVideo, oldKind = kind;
    photo = nextPhoto;
    video = nextVideo;
    retainedVideo = nextRetained;
    kind = nextKind;
    try {
      persist();
    } catch (Exception failure) {
      photo = oldPhoto;
      video = oldVideo;
      retainedVideo = oldRetained;
      kind = oldKind;
      throw failure;
    }
    return selection();
  }

  private static boolean importName(String name, boolean video) {
    return name.matches(video ? "[a-f0-9]{64}\\.video" : "[a-f0-9]{64}\\.photo");
  }

  static boolean isImported(File file, boolean video) {
    return file != null && importName(file.getName(), video);
  }

  /** Remove only the exact saved copy the user confirmed, and never the active background. */
  synchronized boolean removeImport(String expected, boolean isVideo) throws Exception {
    Entry saved = imports.get(expected);
    if (saved == null || saved.video != isVideo
        || (isVideo ? "video".equals(kind) && video.equals(expected)
            : "photo".equals(kind) && photo.equals(expected)))
      throw new IOException("The saved background changed or is still in use.");
    Map<String, Entry> previous = new LinkedHashMap<>(imports);
    imports.remove(expected);
    try {
      changeSelection(photo.equals(expected) ? "" : photo, video.equals(expected) ? "" : video,
          retainedVideo.equals(expected) ? "" : retainedVideo, kind);
    } catch (Exception failure) {
      imports.clear();
      imports.putAll(previous);
      throw failure;
    }
    // Never delete bytes before their row and inactive selection references are committed.
    try {
      java.nio.file.Files.deleteIfExists(saved.file.toPath());
      return true;
    } catch (IOException | SecurityException failed) {
      return false;
    }
  }

  /** Existing single-file callers can still copy and immediately use a visual. */
  synchronized Selection importMedia(Uri uri, boolean isVideo, BooleanSupplier cancelled)
      throws Exception {
    saveImport(uri, isVideo, cancelled, true);
    return selection();
  }

  ImportResult importBatch(List<Uri> sources, boolean isVideo,
      BooleanSupplier cancelled) throws IOException {
    synchronized (this) { requireLoaded(); }
    int added = 0, duplicates = 0, skipped = 0;
    for (Uri source : sources) {
      if (cancelled.getAsBoolean()) break;
      try {
        if (saveImport(source, isVideo, cancelled, false)) added++;
        else duplicates++;
      } catch (InterruptedIOException stopped) {
        return new ImportResult(added, duplicates, skipped, true);
      } catch (Exception unsupported) {
        skipped++;
      }
    }
    return new ImportResult(added, duplicates, skipped, cancelled.getAsBoolean());
  }

  private synchronized boolean saveImport(Uri uri, boolean isVideo, BooleanSupplier cancelled,
      boolean selectNow) throws Exception {
    requireLoaded();
    if (cancelled.getAsBoolean()) throw new InterruptedIOException();
    File temp = File.createTempFile("stage-", ".tmp", root), destination = null;
    boolean created = false;
    Entry previous = null;
    boolean changed = false;
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
      String key = id + (isVideo ? ".video" : ".photo");
      previous = imports.get(key);
      if (previous == null && imports.size() >= MAX_IMPORTS)
        throw new IOException("Too many saved stage backgrounds");
      destination = new File(root, key);
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
      String displayName = previous == null || previous.displayName.isEmpty() ? displayName(uri) : "";
      if (cancelled.getAsBoolean()) throw new InterruptedIOException();
      if (previous == null || !displayName.isEmpty()) {
        imports.put(key, new Entry(destination, displayName));
        changed = true;
      }
      if (selectNow) selectImport(key, isVideo);
      else if (changed) persist();
      return previous == null;
    } catch (Exception failure) {
      if (changed) {
        String key = destination.getName();
        if (previous == null) imports.remove(key);
        else imports.put(key, previous);
      }
      if (created && previous == null && destination != null) destination.delete();
      throw failure;
    } finally {
      temp.delete();
    }
  }

  private String displayName(Uri uri) {
    if ("file".equals(uri.getScheme())) return cleanDisplayName(uri.getLastPathSegment());
    try (android.database.Cursor cursor = context.getContentResolver().query(uri,
        new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) return cleanDisplayName(cursor.getString(0));
    } catch (RuntimeException unavailable) {
      // Do not display a document ID or other provider internals as a fallback.
    }
    return "";
  }

  private static String cleanDisplayName(String value) {
    if (value == null) return "";
    String name = value.substring(0, Math.min(value.length(), 256))
        .replaceAll("[\\p{Cntrl}\\u202a-\\u202e\\u2066-\\u2069]", " ").trim();
    return !name.isEmpty() && Character.isHighSurrogate(name.charAt(name.length() - 1))
        ? name.substring(0, name.length() - 1) : name;
  }

  private void requireLoaded() throws IOException {
    if (!loaded) throw new IOException("Stage settings are unavailable");
  }

  private void persist() throws Exception {
    requireLoaded();
    JSONArray items = new JSONArray();
    for (Entry entry : imports.values()) {
      items.put(new JSONObject().put("file", entry.file.getName()).put("name", entry.displayName));
    }
    JSONObject value = new JSONObject().put("version", 2).put("kind", kind)
        .put("photo", photo).put("video", video).put("importedVideo", retainedVideo)
        .put("imports", items);
    byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_METADATA_BYTES) throw new IOException("Stage settings too large");
    FileOutputStream stream = options.startWrite();
    try {
      stream.write(bytes);
      options.finishWrite(stream);
    } catch (Exception failure) {
      options.failWrite(stream);
      throw failure;
    }
  }

  /** Called on serial IO, like the other writes; select a bundled video without losing imports. */
  synchronized Selection selectBundledVideo(String name) throws Exception {
    if (safe(name, ".video").isEmpty() || importName(name, true))
      throw new IOException("No bundled video");
    File imported = selection().importedVideo;
    return changeSelection(photo, name, imported == null ? "" : imported.getName(), "video");
  }

  static Bitmap thumbnail(Entry entry) throws IOException {
    if (!entry.video) return displayPhoto(entry.file, 160);
    // Retain the caller's video icon on API 26 rather than decoding an unbounded frame.
    if (android.os.Build.VERSION.SDK_INT < 27) return null;
    MediaMetadataRetriever metadata = new MediaMetadataRetriever();
    try {
      metadata.setDataSource(entry.file.getAbsolutePath());
      return metadata.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160);
    } finally {
      metadata.release();
    }
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
    return displayPhoto(file, 2048);
  }

  private static Bitmap displayPhoto(File file, int maxEdge) throws IOException {
    BitmapFactory.Options options = imageBounds(file);
    options.inJustDecodeBounds = false;
    options.inSampleSize = 1;
    while (Math.max(
            options.outWidth / options.inSampleSize, options.outHeight / options.inSampleSize)
        > maxEdge) options.inSampleSize *= 2;
    Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    if (image == null) throw new IOException("Unsupported image");
    try {
      androidx.exifinterface.media.ExifInterface metadata =
          new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath());
      int orientation = metadata.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1);
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
