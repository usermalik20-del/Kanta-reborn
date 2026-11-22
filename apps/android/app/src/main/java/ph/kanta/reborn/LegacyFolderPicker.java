package ph.kanta.reborn;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.util.Locale;

/** Discovers files only in the folder the user explicitly selected. */
final class LegacyFolderPicker {
  private LegacyFolderPicker() {}

  static Uri[] find(ContentResolver resolver, Uri tree) throws IOException {
    Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
        DocumentsContract.getTreeDocumentId(tree));
    Uri[] pair = new Uri[2];
    try (Cursor rows = resolver.query(children, new String[] {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
      if (rows == null) throw new IOException("Cannot read folder");
      int count = 0;
      while (rows.moveToNext()) {
        if (++count > 10000) throw new IOException("Too many files");
        if (rows.isNull(0) || DocumentsContract.Document.MIME_TYPE_DIR.equals(rows.getString(2)))
          continue;
        int role = role(rows.getString(1));
        if (role < 0) continue;
        if (pair[role] != null) return null;
        pair[role] = DocumentsContract.buildDocumentUriUsingTree(tree, rows.getString(0));
      }
    }
    return pair[0] != null && pair[1] != null && !pair[0].equals(pair[1]) ? pair : null;
  }

  private static int role(String displayName) {
    if (displayName == null) return -1;
    String name = displayName.toLowerCase(Locale.ROOT);
    if (name.equals("shubidx") || name.equals("kantidx") || name.equals("kantaidx")
        || name.endsWith(".shubidx") || name.endsWith(".kantidx") || name.endsWith(".idx")) return 0;
    if (name.equals("shubblob") || name.equals("kantablob") || name.endsWith(".shubblob")
        || name.endsWith(".kantablob") || name.endsWith(".blob")) return 1;
    return -1;
  }
}
