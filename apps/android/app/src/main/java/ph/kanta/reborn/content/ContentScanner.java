package ph.kanta.reborn.content;

import android.provider.DocumentsContract;
import java.util.Locale;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import java.io.File;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Scans the KANTA content root via SAF (Storage Access Framework).
 *
 * <p>Discovers media files and indexes them by category:
 * - songs: MIDI (.mid, .midi), KAR (.kar), MP3, WAV
 * - sound: SoundFont (.sf2)
 * - bgv: background video (MP4, etc.)
 * - bgphoto: background photos (JPG, PNG)
 * - idx: legacy index files
 * - font: custom .ttf fonts
 * - assets: optional visual assets
 *
 * <p>Respects the scan limits from the existing DocumentTreeScanner:
 * - 10,000 file entries max
 * - 1,024 directories max
 * - depth 16 max
 */
public final class ContentScanner {

  private static final String TAG = "ContentScanner";
  private static final int MAX_ENTRIES = 10_000;
  private static final int MAX_DIRECTORIES = 1_024;
  private static final int MAX_DEPTH = 16;

  private final ContentResolver resolver;
  private final ContentRoot contentRoot;

  public ContentScanner(ContentResolver resolver, ContentRoot contentRoot) {
    this.resolver = resolver;
    this.contentRoot = contentRoot;
  }

  /** Result of a content scan. */
  public static final class ScanResult {
    public final List<Uri> songFiles;
    public final List<Uri> soundFontFiles;
    public final List<Uri> bgvFiles;
    public final List<Uri> bgphotoFiles;
    public final List<Uri> idxFiles;
    public final List<Uri> fontFiles;
    public final List<Uri> assetFiles;
    public final boolean isLegacyFallback;
    public final boolean cancelled;

    private ScanResult(Builder builder) {
      this.songFiles = Collections.unmodifiableList(new ArrayList<>(builder.songFiles));
      this.soundFontFiles = Collections.unmodifiableList(new ArrayList<>(builder.soundFontFiles));
      this.bgvFiles = Collections.unmodifiableList(new ArrayList<>(builder.bgvFiles));
      this.bgphotoFiles = Collections.unmodifiableList(new ArrayList<>(builder.bgphotoFiles));
      this.idxFiles = Collections.unmodifiableList(new ArrayList<>(builder.idxFiles));
      this.fontFiles = Collections.unmodifiableList(new ArrayList<>(builder.fontFiles));
      this.assetFiles = Collections.unmodifiableList(new ArrayList<>(builder.assetFiles));
      this.isLegacyFallback = builder.isLegacyFallback;
      this.cancelled = builder.cancelled;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {
      List<Uri> songFiles = new ArrayList<>();
      List<Uri> soundFontFiles = new ArrayList<>();
      List<Uri> bgvFiles = new ArrayList<>();
      List<Uri> bgphotoFiles = new ArrayList<>();
      List<Uri> idxFiles = new ArrayList<>();
      List<Uri> fontFiles = new ArrayList<>();
      List<Uri> assetFiles = new ArrayList<>();
      boolean isLegacyFallback = false;
      boolean cancelled = false;
    public Builder cancelled(boolean v) { this.cancelled = v; return this; }
    }
  }

  /**
   * Scans the content root for media files.
   *
   * @param rootUri the SAF tree URI selected by the user
   * @param cancelled cancellation token
   * @return ScanResult with categorized files
   */
  public ScanResult scan(Uri rootUri, BooleanSupplier cancelled) {
    ScanResult.Builder builder = ScanResult.builder();

    // Find the effective root folder (KANTA/ or legacy SongHub/)
    Uri effectiveRoot = findEffectiveRoot(rootUri, cancelled);
    if (effectiveRoot == null) {
      return builder.cancelled(cancelled != null && cancelled.getAsBoolean()).build();
    }

    builder.isLegacyFallback = isLegacyRoot(effectiveRoot);

    // Scan each subdirectory
    scanDirectory(effectiveRoot, ContentRoot.SONGS_DIR, cancelled, builder.songFiles, this::isSongFile);
    scanDirectory(effectiveRoot, ContentRoot.SOUND_DIR, cancelled, builder.soundFontFiles, this::isSoundFontFile);
    scanDirectory(effectiveRoot, ContentRoot.BGV_DIR, cancelled, builder.bgvFiles, this::isBgvFile);
    scanDirectory(effectiveRoot, ContentRoot.BGPHOTO_DIR, cancelled, builder.bgphotoFiles, this::isBgphotoFile);
    scanDirectory(effectiveRoot, ContentRoot.IDX_DIR, cancelled, builder.idxFiles, this::isIdxFile);
    scanDirectory(effectiveRoot, ContentRoot.FONT_DIR, cancelled, builder.fontFiles, this::isFontFile);
    scanDirectory(effectiveRoot, ContentRoot.ASSETS_DIR, cancelled, builder.assetFiles, this::isAssetFile);

    return builder.build();
  }

  /**
   * Finds the effective root folder (KANTA/ or legacy SongHub/) under the SAF tree.
   */
  private Uri findEffectiveRoot(Uri rootUri, BooleanSupplier cancelled) {
    try (Cursor cursor = resolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, DocumentsContract.getTreeDocumentId(rootUri)),
        new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        },
        null, null, null)) {

      if (cursor == null) return null;

      while (cursor.moveToNext()) {
        if (cancelled != null && cancelled.getAsBoolean()) return null;
        String id = cursor.getString(0);
        String name = cursor.getString(1);
        String mime = cursor.getString(2);

        if (id == null || name == null) continue;

        if (ContentRoot.ROOT_FOLDER.equalsIgnoreCase(name) || ContentRoot.LEGACY_ROOT_FOLDER.equalsIgnoreCase(name)) {
          if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
            return DocumentsContract.buildDocumentUriUsingTree(rootUri, id);
          }
        }
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to find effective root", e);
    }
    return null;
  }

  private boolean isLegacyRoot(Uri rootUri) {
    try {
      String docId = DocumentsContract.getDocumentId(rootUri);
      return ContentRoot.LEGACY_ROOT_FOLDER.equalsIgnoreCase(docId);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Scans a subdirectory for files matching the predicate.
   */
  private void scanDirectory(Uri parentUri, String dirName, BooleanSupplier cancelled,
      List<Uri> results, FilePredicate predicate) {
    try {
      // Find the subdirectory
      Uri dirUri = findChildDirectory(parentUri, dirName);
      if (dirUri == null) return;

      // Recursively scan with limits
      scanDirectoryRecursive(dirUri, 0, cancelled, results, predicate, new HashSet<>());
    } catch (Exception e) {
      Log.w(TAG, "Failed to scan directory: " + dirName, e);
    }
  }

  private Uri findChildDirectory(Uri parentUri, String dirName) {
    try (Cursor cursor = resolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, DocumentsContract.getTreeDocumentId(parentUri)),
        new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        },
        null, null, null)) {

      if (cursor == null) return null;

      while (cursor.moveToNext()) {
        String id = cursor.getString(0);
        String name = cursor.getString(1);
        String mime = cursor.getString(2);

        if (id == null || name == null) continue;

        if (dirName.equalsIgnoreCase(name) && DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
          return DocumentsContract.buildDocumentUriUsingTree(parentUri, id);
        }
      }
    } catch (Exception e) {
      // Ignore
    }
    return null;
  }

  private void scanDirectoryRecursive(Uri dirUri, int depth, BooleanSupplier cancelled,
      List<Uri> results, FilePredicate predicate, Set<String> visited) {
    if (cancelled != null && cancelled.getAsBoolean()) return;
    if (results.size() >= MAX_ENTRIES) return;
    if (visited.size() >= MAX_DIRECTORIES) return;
    if (depth > MAX_DEPTH) return;

    String dirId;
    try {
      dirId = DocumentsContract.getTreeDocumentId(dirUri);
    } catch (Exception e) {
      return;
    }

    if (!visited.add(dirId)) return;

    try (Cursor cursor = resolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, dirId),
        new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        },
        null, null, null)) {

      if (cursor == null) return;

      while (cursor.moveToNext()) {
        if (cancelled != null && cancelled.getAsBoolean()) return;
        if (results.size() >= MAX_ENTRIES) return;

        String id = cursor.getString(0);
        String name = cursor.getString(1);
        String mime = cursor.getString(2);

        if (id == null || name == null) continue;

        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
          Uri childDir = DocumentsContract.buildDocumentUriUsingTree(dirUri, id);
          scanDirectoryRecursive(childDir, depth + 1, cancelled, results, predicate, visited);
        } else if (predicate.test(name)) {
          results.add(DocumentsContract.buildDocumentUriUsingTree(dirUri, id));
        }
      }
    } catch (Exception e) {
      Log.w(TAG, "Error scanning directory", e);
    }
  }

  @FunctionalInterface
  private interface FilePredicate {
    boolean test(String name);
  }

  private boolean isSongFile(String name) {
    String lower = name.toLowerCase();
    return lower.endsWith(".mid") || lower.endsWith(".midi") || lower.endsWith(".kar")
        || lower.endsWith(".mp3") || lower.endsWith(".wav");
  }

  private boolean isSoundFontFile(String name) {
    return name.toLowerCase().endsWith(".sf2");
  }

  private boolean isBgvFile(String name) {
    String lower = name.toLowerCase();
    return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".m4v")
        || lower.endsWith(".webm") || lower.endsWith(".mkv");
  }

  private boolean isBgphotoFile(String name) {
    String lower = name.toLowerCase();
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
        || lower.endsWith(".webp") || lower.endsWith(".bmp");
  }

  private boolean isIdxFile(String name) {
    String lower = name.toLowerCase();
    return lower.endsWith(".idx") || lower.endsWith(".blob")
        || name.equals(ContentRoot.INDEX_IDX) || name.equals(ContentRoot.INDEX_BLOB)
        || name.equals(ContentRoot.LEGACY_INDEX_IDX) || name.equals(ContentRoot.LEGACY_INDEX_BLOB);
  }

  private boolean isFontFile(String name) {
    return name.toLowerCase().endsWith(".ttf") || name.toLowerCase().endsWith(".otf");
  }

  private boolean isAssetFile(String name) {
    // Accept common asset types
    String lower = name.toLowerCase();
    return lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".txt")
        || isBgphotoFile(name) || lower.endsWith(".svg");
  }
}
