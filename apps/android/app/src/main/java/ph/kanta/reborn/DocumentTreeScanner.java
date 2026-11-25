package ph.kanta.reborn;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Traverses only provider children of the explicitly selected SAF tree. */
final class DocumentTreeScanner {
  private static final class Directory {
    final String id;
    final int depth;

    Directory(String id, int depth) {
      this.id = id;
      this.depth = depth;
    }
  }

  static List<Uri> scan(ContentResolver resolver, Uri tree, BooleanSupplier cancelled)
      throws Exception {
    ArrayDeque<Directory> directories = new ArrayDeque<>();
    directories.add(new Directory(DocumentsContract.getTreeDocumentId(tree), 0));
    Set<String> visited = new HashSet<>();
    ArrayList<Uri> files = new ArrayList<>();
    int count = 0;
    while (!directories.isEmpty()) {
      if (cancelled.getAsBoolean()) throw new IOException("Cancelled");
      Directory directory = directories.removeFirst();
      if (!visited.add(directory.id)) continue;
      if (visited.size() > 1024 || directory.depth > 16)
        throw new IOException("Folder tree too large");
      Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory.id);
      try (Cursor rows =
          resolver.query(
              children,
              new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
              },
              null,
              null,
              null)) {
        if (rows == null) throw new IOException("Cannot read folder");
        while (rows.moveToNext()) {
          if (++count > 10000 || cancelled.getAsBoolean()) throw new IOException("Scan stopped");
          String id = rows.getString(0), name = rows.getString(1), mime = rows.getString(2);
          if (id == null || name == null) continue;
          if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))
            directories.addLast(new Directory(id, directory.depth + 1));
          else if (name.toLowerCase(Locale.ROOT).matches(".*\\.(mid|midi|kar|mp3|wav|sf2)$"))
            files.add(DocumentsContract.buildDocumentUriUsingTree(tree, id));
        }
      }
    }
    return Collections.unmodifiableList(files);
  }

  /** Recursively classifies every file in the selected tree by destination. */
  static Classification scanClassified(ContentResolver resolver, Uri tree,
      BooleanSupplier cancelled) throws Exception {
    ArrayDeque<Directory> directories = new ArrayDeque<>();
    directories.add(new Directory(DocumentsContract.getTreeDocumentId(tree), 0));
    Set<String> visited = new HashSet<>();
    ArrayList<Uri> songs = new ArrayList<>(), videos = new ArrayList<>(), photos = new ArrayList<>();
    Uri legacyIndex = null, legacyBlob = null;
    int count = 0;
    while (!directories.isEmpty()) {
      if (cancelled.getAsBoolean()) throw new IOException("Cancelled");
      Directory directory = directories.removeFirst();
      if (!visited.add(directory.id)) continue;
      if (visited.size() > 1024 || directory.depth > 16) throw new IOException("Folder tree too large");
      Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory.id);
      try (Cursor rows = resolver.query(children, new String[] {
          DocumentsContract.Document.COLUMN_DOCUMENT_ID,
          DocumentsContract.Document.COLUMN_DISPLAY_NAME,
          DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
        if (rows == null) throw new IOException("Cannot read folder");
        while (rows.moveToNext()) {
          if (++count > 10000 || cancelled.getAsBoolean()) throw new IOException("Scan stopped");
          String id = rows.getString(0), name = rows.getString(1), mime = rows.getString(2);
          if (id == null || name == null) continue;
          if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
            directories.addLast(new Directory(id, directory.depth + 1));
            continue;
          }
          Uri file = DocumentsContract.buildDocumentUriUsingTree(tree, id);
          String lower = name.toLowerCase(Locale.ROOT);
          int legacy = legacyRole(lower);
          if (legacy == 0 && legacyIndex == null) legacyIndex = file;
          else if (legacy == 1 && legacyBlob == null) legacyBlob = file;
          else if (lower.matches(".*\\.(mid|midi|kar|mp3|wav|sf2)$")) songs.add(file);
          else if (lower.matches(".*\\.(mp4|webm|mov|m4v)$")) videos.add(file);
          else if (lower.matches(".*\\.(jpg|jpeg|png|webp|gif)$")) photos.add(file);
        }
      }
    }
    return new Classification(legacyIndex, legacyBlob,
        Collections.unmodifiableList(songs), Collections.unmodifiableList(videos),
        Collections.unmodifiableList(photos));
  }

  /** 0 = legacy index, 1 = legacy blob, -1 = neither (mirrors LegacyFolderPicker). */
  private static int legacyRole(String lowerName) {
    if (lowerName.equals("shubidx") || lowerName.equals("kantidx") || lowerName.equals("kantaidx")
        || lowerName.endsWith(".shubidx") || lowerName.endsWith(".kantidx") || lowerName.endsWith(".idx"))
      return 0;
    if (lowerName.equals("shubblob") || lowerName.equals("kantablob")
        || lowerName.endsWith(".shubblob") || lowerName.endsWith(".kantablob")
        || lowerName.endsWith(".blob"))
      return 1;
    return -1;
  }

  /** One scan result split into the destinations KANTA already owns. */
  static final class Classification {
    final Uri legacyIndex, legacyBlob;
    final List<Uri> songs, videos, photos;

    Classification(Uri legacyIndex, Uri legacyBlob, List<Uri> songs,
        List<Uri> videos, List<Uri> photos) {
      this.legacyIndex = legacyIndex;
      this.legacyBlob = legacyBlob;
      this.songs = songs;
      this.videos = videos;
      this.photos = photos;
    }
  }
}
