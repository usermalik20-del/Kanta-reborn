package ph.kanta.reborn;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Read-only, signature-protected synthetic fixtures, packaged only in the test APK. */
public final class FixtureProvider extends ContentProvider {
  static final String AUTHORITY = "ph.kanta.reborn.test.fixtures";

  static Uri file(String id) {
    return Uri.parse("content://" + AUTHORITY + "/document/" + id);
  }

  private String id(Uri uri) {
    return DocumentsContract.getDocumentId(uri);
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Cursor query(
      Uri uri, String[] projection, String selection, String[] arguments, String order) {
    String[] columns =
        projection != null
            ? projection
            : new String[] {"document_id", "_display_name", "mime_type", "_size"};
    MatrixCursor rows = new MatrixCursor(columns);
    String document = id(uri);
    List<String> ids =
        uri.getLastPathSegment().equals("children")
            ? document.equals("root")
                ? Arrays.asList("song", "bad", "sub")
                : document.equals("sub")
                    ? Arrays.asList("bank", "audio", "root")
                    : Collections.emptyList()
            : Collections.singletonList(document);
    for (String entry : ids) {
      Object[] values = new Object[columns.length];
      for (int i = 0; i < columns.length; i++) {
        switch (columns[i]) {
          case "document_id":
            values[i] = entry;
            break;
          case "_display_name":
            values[i] = name(entry);
            break;
          case "mime_type":
            values[i] =
                directory(entry)
                    ? DocumentsContract.Document.MIME_TYPE_DIR
                    : "application/octet-stream";
            break;
          case "_size":
            values[i] = 0L;
            break;
        }
      }
      rows.addRow(values);
    }
    return rows;
  }

  private boolean directory(String id) {
    return id.equals("root") || id.equals("sub");
  }

  private String name(String id) {
    switch (id) {
      case "root":
        return "Fixture root";
      case "sub":
        return "Nested";
      case "song":
        return "Original KAR.kar";
      case "bad":
        return "Damaged.kar";
      case "bank":
        return "Any SoundFont.SF2";
      case "audio":
        return "Original tone.wav";
      case "photo":
        return "Original photo.png";
      case "video":
        return "Original video.mp4";
      default:
        throw new IllegalArgumentException("Unknown fixture");
    }
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!mode.equals("r")) throw new FileNotFoundException("Read-only fixtures");
    String document = id(uri);
    name(document);
    File output = new File(getContext().getCacheDir(), document + ".fixture");
    try (FileOutputStream stream = new FileOutputStream(output)) {
      if (document.equals("video")) {
        try (InputStream input = getContext().getAssets().open("stage-color.mp4")) {
          byte[] block = new byte[4096];
          int count;
          while ((count = input.read(block)) != -1) stream.write(block, 0, count);
        }
      } else if (document.equals("song") || document.equals("bank")) {
        Context app = getContext().createPackageContext("ph.kanta.reborn", 0);
        try (InputStream input =
            document.equals("song")
                ? getContext().getAssets().open("song-fixtures/morning-light.kar")
                : app.getAssets().open("starter/kanta-tone.sf2")) {
          byte[] bytes = new byte[4096];
          int count;
          while ((count = input.read(bytes)) != -1) stream.write(bytes, 0, count);
        }
      } else if (document.equals("photo")) {
        android.graphics.Bitmap image =
            android.graphics.Bitmap.createBitmap(80, 60, android.graphics.Bitmap.Config.ARGB_8888);
        image.eraseColor(android.graphics.Color.CYAN);
        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        image.recycle();
      } else if (document.equals("audio")) stream.write(tone());
      else if (document.equals("bad")) stream.write(new byte[24]);
      else throw new FileNotFoundException("Not a fixture file");
    } catch (Exception error) {
      throw new FileNotFoundException("Fixture unavailable: " + document);
    }
    return ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY);
  }

  private byte[] tone() {
    int frames = 44100;
    ByteBuffer wave = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN);
    wave.put(new byte[] {'R', 'I', 'F', 'F'}).putInt(36 + frames * 2);
    wave.put(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '})
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1);
    wave.putInt(22050).putInt(44100).putShort((short) 2).putShort((short) 16);
    wave.put(new byte[] {'d', 'a', 't', 'a'}).putInt(frames * 2);
    for (int i = 0; i < frames; i++)
      wave.putShort((short) (Math.sin(i * 2 * Math.PI * 440 / 22050) * 3000));
    return wave.array();
  }

  @Override
  public String getType(Uri uri) {
    return "application/octet-stream";
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] args) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String selection, String[] args) {
    throw new UnsupportedOperationException();
  }
}
