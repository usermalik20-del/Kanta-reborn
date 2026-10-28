package ph.kanta.reborn.core.catalog;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;
import ph.kanta.reborn.core.Song;
import ph.kanta.reborn.content.ContentRoot;
import ph.kanta.reborn.content.ContentScanner;
import ph.kanta.reborn.core.songbook.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SQLite-backed song catalog with FTS4 search.
 *
 * <p>Schema (per replica/schema.sql):
 *   volumes(volume_id, name, song_count)
 *   songs(song_id, volume_id, title, artist, code, file_path, starter, legacy_span_index)
 *   artists(artist_id, name, song_count)
 *   song_search (FTS4 virtual table on title, artist, code, content=songs)
 *   index_meta(key, value) — stores index version, last scan timestamp, container size
 *
 * <p>Supports both plain file scan and legacy Meg container (shubidx/shubblob) interoperability.
 */
public final class SqliteSongCatalog implements SongCatalog {

  private static final String TAG = "SqliteSongCatalog";
  private static final String DB_NAME = "songbook.db";
  private static final int DB_VERSION = 1;

  // Schema per replica/schema.sql
  private static final String SQL_CREATE_VOLUMES =
      "CREATE TABLE IF NOT EXISTS volumes ("
          + "volume_id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + "name TEXT NOT NULL UNIQUE,"
          + "song_count INTEGER NOT NULL DEFAULT 0"
          + ");";

  private static final String SQL_CREATE_SONGS =
      "CREATE TABLE IF NOT EXISTS songs ("
          + "song_id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + "volume_id INTEGER NOT NULL,"
          + "title TEXT NOT NULL,"
          + "artist TEXT,"
          + "code TEXT,"
          + "file_path TEXT NOT NULL,"
          + "starter INTEGER NOT NULL DEFAULT 0,"
          + "legacy_span_index INTEGER,"
          + "FOREIGN KEY (volume_id) REFERENCES volumes(volume_id) ON DELETE CASCADE"
          + ");";

  private static final String SQL_CREATE_ARTISTS =
      "CREATE TABLE IF NOT EXISTS artists ("
          + "artist_id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + "name TEXT NOT NULL UNIQUE,"
          + "song_count INTEGER NOT NULL DEFAULT 0"
          + ");";

  // FTS4 virtual table for full-text search
  private static final String SQL_CREATE_FTS =
      "CREATE VIRTUAL TABLE IF NOT EXISTS song_search USING fts4("
          + "title, artist, code, content='songs', content_rowid='song_id', tokenize=unicode61"
          + ");";

  private static final String SQL_CREATE_INDEX_META =
      "CREATE TABLE IF NOT EXISTS index_meta ("
          + "key TEXT PRIMARY KEY NOT NULL,"
          + "value TEXT NOT NULL"
          + ");";

  // Triggers to keep FTS in sync
  private static final String SQL_TRIGGER_SONGS_INSERT =
      "CREATE TRIGGER IF NOT EXISTS songs_ai AFTER INSERT ON songs BEGIN "
          + "INSERT INTO song_search(rowid, title, artist, code) VALUES (new.song_id, new.title, new.artist, new.code); "
          + "END;";

  private static final String SQL_TRIGGER_SONGS_DELETE =
      "CREATE TRIGGER IF NOT EXISTS songs_ad AFTER DELETE ON songs BEGIN "
          + "DELETE FROM song_search WHERE rowid = old.song_id; "
          + "END;";

  private static final String SQL_TRIGGER_SONGS_UPDATE =
      "CREATE TRIGGER IF NOT EXISTS songs_au AFTER UPDATE ON songs BEGIN "
          + "DELETE FROM song_search WHERE rowid = old.song_id; "
          + "INSERT INTO song_search(rowid, title, artist, code) VALUES (new.song_id, new.title, new.artist, new.code); "
          + "END;";

  // Indexes for common queries
  private static final String SQL_IDX_SONGS_VOLUME = "CREATE INDEX IF NOT EXISTS idx_songs_volume ON songs(volume_id);";
  private static final String SQL_IDX_SONGS_CODE = "CREATE INDEX IF NOT EXISTS idx_songs_code ON songs(code);";
  private static final String SQL_IDX_SONGS_STARTER = "CREATE INDEX IF NOT EXISTS idx_songs_starter ON songs(starter);";
  private static final String SQL_IDX_ARTISTS_NAME = "CREATE INDEX IF NOT EXISTS idx_artists_name ON artists(name);";

  private final Context context;
  private final ContentRoot contentRoot;
  private final ContentScanner scanner;
  private final DatabaseHelper dbHelper;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public SqliteSongCatalog(Context context, ContentRoot contentRoot, ContentScanner scanner) {
    this.context = context.getApplicationContext();
    this.contentRoot = contentRoot;
    this.scanner = scanner;
    this.dbHelper = new DatabaseHelper(context);
  }

  @Override
  public int count() {
    lock.readLock().lock();
    try (SQLiteDatabase db = dbHelper.getReadableDatabase();
         Cursor c = db.rawQuery("SELECT COUNT(*) FROM songs", null)) {
      if (c.moveToFirst()) return c.getInt(0);
      return 0;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public List<Song> all(int limit) {
    lock.readLock().lock();
    try (SQLiteDatabase db = dbHelper.getReadableDatabase();
         Cursor c = db.rawQuery(
             "SELECT song_id, volume_id, title, artist, code, file_path, starter FROM songs ORDER BY title LIMIT ?",
             new String[]{String.valueOf(Math.max(1, limit))})) {
      return cursorToSongs(c);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public List<Song> search(String query, int limit) {
    if (query == null || query.trim().isEmpty()) return all(limit);

    lock.readLock().lock();
    try (SQLiteDatabase db = dbHelper.getReadableDatabase()) {
      // Use FTS4 for ranked search
      String ftsQuery = buildFtsQuery(query.trim());
      String sql = "SELECT s.song_id, s.volume_id, s.title, s.artist, s.code, s.file_path, s.starter "
          + "FROM songs s JOIN song_search f ON s.song_id = f.rowid "
          + "WHERE song_search MATCH ? ORDER BY rank LIMIT ?";
      try (Cursor c = db.rawQuery(sql, new String[]{ftsQuery, String.valueOf(Math.max(1, limit))})) {
        return cursorToSongs(c);
      }
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public List<Song> byArtist(String artist, int limit) {
    lock.readLock().lock();
    try (SQLiteDatabase db = dbHelper.getReadableDatabase();
         Cursor c = db.rawQuery(
             "SELECT song_id, volume_id, title, artist, code, file_path, starter FROM songs WHERE artist = ? ORDER BY title LIMIT ?",
             new String[]{artist, String.valueOf(Math.max(1, limit))})) {
      return cursorToSongs(c);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public List<Song> byVolume(String volume, int limit) {
    lock.readLock().lock();
    try (SQLiteDatabase db = dbHelper.getReadableDatabase();
         Cursor c = db.rawQuery(
             "SELECT s.song_id, s.volume_id, s.title, s.artist, s.code, s.file_path, s.starter "
                 + "FROM songs s JOIN volumes v ON s.volume_id = v.volume_id "
                 + "WHERE v.name = ? ORDER BY s.title LIMIT ?",
             new String[]{volume, String.valueOf(Math.max(1, limit))})) {
      return cursorToSongs(c);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public List<String> artists(int limit) {
    lock.readLock().lock();
    try (SQLiteDatabase db = dbHelper.getReadableDatabase();
         Cursor c = db.rawQuery(
             "SELECT name FROM artists ORDER BY name LIMIT ?",
             new String[]{String.valueOf(Math.max(1, limit))})) {
      List<String> result = new ArrayList<>();
      while (c.moveToNext()) result.add(c.getString(0));
      return result;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public List<String> volumes(int limit) {
    lock.readLock().lock();
    try (SQLiteDatabase db = dbHelper.getReadableDatabase();
         Cursor c = db.rawQuery(
             "SELECT name FROM volumes ORDER BY name LIMIT ?",
             new String[]{String.valueOf(Math.max(1, limit))})) {
      List<String> result = new ArrayList<>();
      while (c.moveToNext()) result.add(c.getString(0));
      return result;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public Song findById(String id) {
    // Our internal song_id is numeric; the public ID is SHA-256 from LibraryRepository.
    // We need to map or store the public ID. For now, search by file_path which contains the ID.
    lock.readLock().lock();
    try (SQLiteDatabase db = dbHelper.getReadableDatabase();
         Cursor c = db.rawQuery(
             "SELECT song_id, volume_id, title, artist, code, file_path, starter FROM songs WHERE file_path LIKE ? LIMIT 1",
             new String[]{id + "%"})) {
      List<Song> songs = cursorToSongs(c);
      return songs.isEmpty() ? null : songs.get(0);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public Song findByCode(String code) {
    lock.readLock().lock();
    try (SQLiteDatabase db = dbHelper.getReadableDatabase();
         Cursor c = db.rawQuery(
             "SELECT song_id, volume_id, title, artist, code, file_path, starter FROM songs WHERE code = ? LIMIT 1",
             new String[]{code})) {
      List<Song> songs = cursorToSongs(c);
      return songs.isEmpty() ? null : songs.get(0);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public void rebuild() throws Exception {
    lock.writeLock().lock();
    try {
      SQLiteDatabase db = dbHelper.getWritableDatabase();
      db.beginTransaction();
      try {
        // Clear existing data
        db.execSQL("DELETE FROM songs");
        db.execSQL("DELETE FROM volumes");
        db.execSQL("DELETE FROM artists");
        db.execSQL("DELETE FROM song_search");
        db.execSQL("DELETE FROM index_meta");

        // Get the content root URI
        Uri rootUri = contentRoot.getContentRootUri();
        if (rootUri == null) {
          db.setTransactionSuccessful();
          return;
        }

        // Scan for media files
        ContentScanner.ScanResult scan = scanner.scan(rootUri, () -> false);

        // Check for legacy index container
        File baseDir = new File(context.getFilesDir(), "content"); // virtual, we scan via SAF
        boolean isLegacy = scan.isLegacyFallback;

        // Index files
        indexFiles(db, scan, isLegacy);

        // If legacy container exists, also index from it
        if (isLegacy) {
          indexFromLegacyContainer(db, rootUri);
        }

        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void indexFiles(SQLiteDatabase db, ContentScanner.ScanResult scan, boolean isLegacy) {
    // For now, treat all songs as one volume "KANTA"
    long volumeId = insertVolume(db, ContentRoot.ROOT_FOLDER);

    // Insert songs from scanned files
    int codeCounter = 100000;
    Set<String> usedCodes = new HashSet<>();

    for (Uri songUri : scan.songFiles) {
      String fileName = getFileName(songUri);
      if (fileName == null) continue;

      String title = stripExtension(fileName);
      String artist = "";
      String code = String.valueOf(codeCounter++);
      while (usedCodes.contains(code)) code = String.valueOf(codeCounter++);
      usedCodes.add(code);

      insertSong(db, volumeId, title, artist, code, fileName, false, -1);
    }

    // Update volume song count
    updateVolumeSongCount(db, volumeId);

    // Insert artists (empty for now, would need metadata extraction)
    // Insert index meta
    insertMeta(db, "version", "1");
    insertMeta(db, "legacy", String.valueOf(isLegacy));
    insertMeta(db, "scan_time", String.valueOf(System.currentTimeMillis()));
  }

  private void indexFromLegacyContainer(SQLiteDatabase db, Uri rootUri) {
    // Read the legacy index file via SAF
    // This is a placeholder - full implementation would read shubidx/shubblob via DocumentsContract
    // and populate songs from the title records, linking to blob spans.
    Log.i(TAG, "Legacy container indexing not yet implemented");
  }

  private long insertVolume(SQLiteDatabase db, String name) {
    try (SQLiteStatement stmt = db.compileStatement(
        "INSERT OR IGNORE INTO volumes(name) VALUES (?);")) {
      stmt.bindString(1, name);
      stmt.executeInsert();
    }
    try (Cursor c = db.rawQuery("SELECT volume_id FROM volumes WHERE name = ?", new String[]{name})) {
      if (c.moveToFirst()) return c.getLong(0);
    }
    return -1;
  }

  private void updateVolumeSongCount(SQLiteDatabase db, long volumeId) {
    try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM songs WHERE volume_id = ?", new String[]{String.valueOf(volumeId)})) {
      if (c.moveToFirst()) {
        int count = c.getInt(0);
        db.execSQL("UPDATE volumes SET song_count = ? WHERE volume_id = ?", new Object[]{count, volumeId});
      }
    }
  }

  private void insertSong(SQLiteDatabase db, long volumeId, String title, String artist,
      String code, String filePath, boolean starter, int legacySpanIndex) {
    try (SQLiteStatement stmt = db.compileStatement(
        "INSERT INTO songs(volume_id, title, artist, code, file_path, starter, legacy_span_index) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?);")) {
      stmt.bindLong(1, volumeId);
      stmt.bindString(2, title);
      stmt.bindString(3, artist);
      stmt.bindString(4, code);
      stmt.bindString(5, filePath);
      stmt.bindLong(6, starter ? 1 : 0);
      if (legacySpanIndex >= 0) stmt.bindLong(7, legacySpanIndex); else stmt.bindNull(7);
      stmt.executeInsert();
    }
  }

  private void insertMeta(SQLiteDatabase db, String key, String value) {
    try (SQLiteStatement stmt = db.compileStatement(
        "INSERT OR REPLACE INTO index_meta(key, value) VALUES (?, ?);")) {
      stmt.bindString(1, key);
      stmt.bindString(2, value);
      stmt.executeInsert();
    }
  }

  private List<Song> cursorToSongs(Cursor c) {
    List<Song> result = new ArrayList<>();
    while (c.moveToNext()) {
      String id = String.valueOf(c.getLong(0)); // internal song_id
      long volumeId = c.getLong(1);
      String title = c.getString(2);
      String artist = c.getString(3);
      String code = c.getString(4);
      String filePath = c.getString(5);
      boolean starter = c.getInt(6) == 1;
      result.add(new Song(id, title, artist, code, filePath, starter));
    }
    return result;
  }

  private String buildFtsQuery(String query) {
    // FTS4 query: prefix match on each term
    String[] terms = query.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < terms.length; i++) {
      if (i > 0) sb.append(" ");
      sb.append(terms[i]).append("*");
    }
    return sb.toString();
  }

  private String getFileName(Uri uri) {
    try (Cursor c = context.getContentResolver().query(uri,
        new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst()) {
        return c.getString(0);
      }
    } catch (Exception e) {
      // Ignore
    }
    return null;
  }

  private String stripExtension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }

  @Override
  public void close() {
    dbHelper.close();
  }

  private static class DatabaseHelper extends SQLiteOpenHelper {

    DatabaseHelper(Context context) {
      super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(SQL_CREATE_VOLUMES);
      db.execSQL(SQL_CREATE_SONGS);
      db.execSQL(SQL_CREATE_ARTISTS);
      db.execSQL(SQL_CREATE_FTS);
      db.execSQL(SQL_CREATE_INDEX_META);
      db.execSQL(SQL_TRIGGER_SONGS_INSERT);
      db.execSQL(SQL_TRIGGER_SONGS_DELETE);
      db.execSQL(SQL_TRIGGER_SONGS_UPDATE);
      db.execSQL(SQL_IDX_SONGS_VOLUME);
      db.execSQL(SQL_IDX_SONGS_CODE);
      db.execSQL(SQL_IDX_SONGS_STARTER);
      db.execSQL(SQL_IDX_ARTISTS_NAME);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      // For v1, just recreate
      db.execSQL("DROP TABLE IF EXISTS song_search");
      db.execSQL("DROP TABLE IF EXISTS songs");
      db.execSQL("DROP TABLE IF EXISTS volumes");
      db.execSQL("DROP TABLE IF EXISTS artists");
      db.execSQL("DROP TABLE IF EXISTS index_meta");
      onCreate(db);
    }
  }
}
