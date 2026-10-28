package ph.kanta.reborn.content;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;

/**
 * Resolves the KANTA content root directory with legacy SongHub/ read-only fallback.
 *
 * <p>The content root is the user-selected SAF tree (DocumentsProvider) that contains the
 * karaoke library structure:
 *   KANTA/
 *     songs/      — MIDI/KAR/MP3/WAV files
 *     sound/      — SoundFont (.sf2) files
 *     bgv/        — background video files
 *     bgphoto/    — background photo files
 *     idx/        — legacy index files (kantidx, kantablob)
 *     font/       — custom .ttf fonts
 *     assets/     — optional visual assets
 *
 * Legacy SongHub/ folder is supported as a read-only fallback with a user notice.
 */
public final class ContentRoot {

  private static final String PREFS_FILE = "kanta_settings";
  private static final String PREF_CONTENT_ROOT_URI = "contentRootUri";
  private static final String PREF_LEGACY_NOTICE_SHOWN = "legacyNoticeShown";

  /** Folder names as shipped in the rebrand (recon §10). */
  public static final String ROOT_FOLDER = "KANTA";
  public static final String LEGACY_ROOT_FOLDER = "SongHub";
  public static final String SONGS_DIR = "songs";
  public static final String SOUND_DIR = "sound";
  public static final String BGV_DIR = "bgv";
  public static final String BGPHOTO_DIR = "bgphoto";
  public static final String IDX_DIR = "idx";
  public static final String FONT_DIR = "font";
  public static final String ASSETS_DIR = "assets";

  /** Index file names (rebrand map). */
  public static final String INDEX_IDX = "kantidx";
  public static final String INDEX_BLOB = "kantablob";
  public static final String LEGACY_INDEX_IDX = "shubidx";
  public static final String LEGACY_INDEX_BLOB = "shubblob";

  private final Context context;
  private final SharedPreferences prefs;

  public ContentRoot(Context context) {
    this.context = context.getApplicationContext();
    this.prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
  }

  /** Returns the user-selected content root URI, or null if not set. */
  public Uri getContentRootUri() {
    String uriString = prefs.getString(PREF_CONTENT_ROOT_URI, null);
    return uriString != null ? Uri.parse(uriString) : null;
  }

  /** Persists the user-selected content root URI. */
  public void setContentRootUri(Uri uri) {
    prefs.edit().putString(PREF_CONTENT_ROOT_URI, uri.toString()).apply();
  }

  /** Clears the content root selection. */
  public void clearContentRootUri() {
    prefs.edit().remove(PREF_CONTENT_ROOT_URI).apply();
  }

  /** Checks if a content root has been selected. */
  public boolean hasContentRoot() {
    return getContentRootUri() != null;
  }

  /**
   * Resolves the effective content root directory.
   *
   * @return the KANTA/ directory if present, otherwise the legacy SongHub/ directory (read-only),
   *         or null if neither exists.
   */
  public File resolveRoot(File baseDir) {
    if (baseDir == null || !baseDir.isDirectory()) return null;

    File kantaDir = new File(baseDir, ROOT_FOLDER);
    if (kantaDir.isDirectory()) {
      return kantaDir;
    }

    File legacyDir = new File(baseDir, LEGACY_ROOT_FOLDER);
    if (legacyDir.isDirectory()) {
      return legacyDir;
    }

    return null;
  }

  /** Returns the effective root directory under the user's SAF tree. */
  public File resolveRootFromUri(Uri rootUri) {
    // The SAF tree root is a virtual URI; we can't directly list files from it.
    // The actual scanning is done via DocumentsContract in ContentScanner.
    return null;
  }

  /**
   * Checks if the effective root is the legacy SongHub folder (read-only fallback).
   */
  public boolean isLegacyFallback(File baseDir) {
    File kantaDir = new File(baseDir, ROOT_FOLDER);
    File legacyDir = new File(baseDir, LEGACY_ROOT_FOLDER);
    return !kantaDir.isDirectory() && legacyDir.isDirectory();
  }

  /**
   * Returns whether the legacy fallback notice has been shown.
   */
  public boolean hasShownLegacyNotice() {
    return prefs.getBoolean(PREF_LEGACY_NOTICE_SHOWN, false);
  }

  /** Marks the legacy fallback notice as shown. */
  public void markLegacyNoticeShown() {
    prefs.edit().putBoolean(PREF_LEGACY_NOTICE_SHOWN, true).apply();
  }

  /** Returns the songs directory under the effective root. */
  public File getSongsDir(File root) {
    return root != null ? new File(root, SONGS_DIR) : null;
  }

  /** Returns the sound directory under the effective root. */
  public File getSoundDir(File root) {
    return root != null ? new File(root, SOUND_DIR) : null;
  }

  /** Returns the BGV directory under the effective root. */
  public File getBgvDir(File root) {
    return root != null ? new File(root, BGV_DIR) : null;
  }

  /** Returns the bgphoto directory under the effective root. */
  public File getBgphotoDir(File root) {
    return root != null ? new File(root, BGPHOTO_DIR) : null;
  }

  /** Returns the idx directory under the effective root. */
  public File getIdxDir(File root) {
    return root != null ? new File(root, IDX_DIR) : null;
  }

  /** Returns the font directory under the effective root. */
  public File getFontDir(File root) {
    return root != null ? new File(root, FONT_DIR) : null;
  }

  /** Returns the assets directory under the effective root. */
  public File getAssetsDir(File root) {
    return root != null ? new File(root, ASSETS_DIR) : null;
  }

  /** Returns the index .idx file (kantidx or legacy shubidx). */
  public File getIndexFile(File root) {
    if (root == null) return null;
    File idxDir = getIdxDir(root);
    if (idxDir == null) return null;
    File kantaIdx = new File(idxDir, INDEX_IDX);
    if (kantaIdx.exists()) return kantaIdx;
    File legacyIdx = new File(idxDir, LEGACY_INDEX_IDX);
    if (legacyIdx.exists()) return legacyIdx;
    return null;
  }

  /** Returns the index .blob file (kantablob or legacy shubblob). */
  public File getBlobFile(File root) {
    if (root == null) return null;
    File idxDir = getIdxDir(root);
    if (idxDir == null) return null;
    File kantaBlob = new File(idxDir, INDEX_BLOB);
    if (kantaBlob.exists()) return kantaBlob;
    File legacyBlob = new File(idxDir, LEGACY_INDEX_BLOB);
    if (legacyBlob.exists()) return legacyBlob;
    return null;
  }

  /** Returns the default soundfont file under the sound directory. */
  public File getDefaultSoundFont(File root) {
    File soundDir = getSoundDir(root);
    if (soundDir == null || !soundDir.isDirectory()) return null;
    // Prefer kanta.sf2 (rebrand), then any .sf2
    File kantaSf2 = new File(soundDir, "kanta.sf2");
    if (kantaSf2.exists()) return kantaSf2;
    File[] sf2Files = soundDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".sf2"));
    return (sf2Files != null && sf2Files.length > 0) ? sf2Files[0] : null;
  }
}
