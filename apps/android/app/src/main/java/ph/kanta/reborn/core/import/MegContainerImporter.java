package ph.kanta.reborn.core.import;

import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ph.kanta.reborn.core.songbook.IndexedContainerCompatibility;
import ph.kanta.reborn.core.songbook.ShubBlobPayloadProbe;
import ph.kanta.reborn.core.songbook.ShubBlobSpanIndex;

public final class MegContainerImporter {

  private static final String TAG = "MegContainerImporter";
  private static final int MAX_COMPRESSED_BYTES = 64 * 1024 * 1024;
  private static final int MAX_DECODED_BYTES = 64 * 1024 * 1024;

  private MegContainerImporter() {}

  public static final class ImportResult {
    public final int totalSpans;
    public final int decodedCount;
    public final int skippedCount;
    public final List<DecodedSong> songs;
    public final List<ImportError> errors;
    public ImportResult(int totalSpans, int decodedCount, int skippedCount,
        List<DecodedSong> songs, List<ImportError> errors) {
      this.totalSpans = totalSpans;
      this.decodedCount = decodedCount;
      this.skippedCount = skippedCount;
      this.songs = songs;
      this.errors = errors;
    }
  }

  public static final class DecodedSong {
    public final String id;
    public final String title;
    public final String fileName;
    public final Uri fileUri;
    public final MkmdDecoder.Signature signature;
    public final long decodedBytes;
    public DecodedSong(String id, String title, String fileName, Uri fileUri,
        MkmdDecoder.Signature signature, long decodedBytes) {
      this.id = id;
      this.title = title;
      this.fileName = fileName;
      this.fileUri = fileUri;
      this.signature = signature;
      this.decodedBytes = decodedBytes;
    }
  }

  public static final class ImportError {
    public final int recordIndex;
    public final String message;
    public ImportError(int recordIndex, String message) {
      this.recordIndex = recordIndex;
      this.message = message;
    }
  }

  public interface KeyProvider {
    byte[] keyForTitle(String title);
    byte[] ivForTitle(String title);
  }

  public static ImportResult importContainer(byte[] indexBytes, byte[] blobBytes,
      KeyProvider keyProvider, File outputDir) throws IOException {
    if (indexBytes == null || blobBytes == null) {
      throw new IOException("Index and blob bytes are required");
    }
    if (blobBytes.length > MAX_COMPRESSED_BYTES) {
      throw new IOException("Meg blob exceeds 64 MB cap: " + blobBytes.length);
    }
    if (outputDir != null && !outputDir.exists() && !outputDir.mkdirs()) {
      throw new IOException("Cannot create output directory: " + outputDir);
    }

    Log.i(TAG, "Importing Meg container: " + blobBytes.length + " blob bytes");

    List<ShubBlobSpanIndex.PayloadSpan> spans;
    try {
      spans = IndexedContainerCompatibility.spans(indexBytes, blobBytes.length);
    } catch (ShubBlobSpanIndex.SpanIndexException e) {
      throw new IOException("Cannot build payload spans: " + e.failure
          + " (record " + e.recordIndex + ")", e);
    }

    List<ShubBlobSpanIndex.PayloadSpan> mkmdSpans = new ArrayList<>();
    List<ShubBlobSpanIndex.PayloadSpan> otherSpans = new ArrayList<>();
    for (ShubBlobSpanIndex.PayloadSpan span : spans) {
      int prefixLen = (int) Math.min(16, span.byteCount());
      byte[] prefix = new byte[prefixLen];
      int read = readRange(blobBytes, span.startOffset, prefixLen, prefix);
      if (read < 4) {
        otherSpans.add(span);
        continue;
      }
      ShubBlobPayloadProbe.Signature sig = ShubBlobPayloadProbe.signature(prefix);
      if (sig == ShubBlobPayloadProbe.Signature.MKMD_V1) {
        mkmdSpans.add(span);
      } else {
        otherSpans.add(span);
      }
    }

    List<DecodedSong> songs = new ArrayList<>();
    List<ImportError> errors = new ArrayList<>();
    int decodedCount = 0;
    int skippedCount = otherSpans.size();

    for (ShubBlobSpanIndex.PayloadSpan span : mkmdSpans) {
      long spanByteCount = span.byteCount();
      if (spanByteCount > MAX_COMPRESSED_BYTES) {
        errors.add(new ImportError(span.recordIndex,
            "MKMD span exceeds 64 MB cap: " + spanByteCount));
        skippedCount++;
        continue;
      }

      byte[] ciphertext = new byte[(int) spanByteCount];
      int read = readRange(blobBytes, span.startOffset, ciphertext.length, ciphertext);
      if (read != ciphertext.length) {
        errors.add(new ImportError(span.recordIndex,
            "Short read on MKMD span: wanted " + ciphertext.length + " got " + read));
        skippedCount++;
        continue;
      }

      String title = null;
      byte[] key = null;
      byte[] iv = null;
      if (keyProvider != null) {
        title = titleForRecord(indexBytes, span.recordIndex);
        key = title != null ? keyProvider.keyForTitle(title) : null;
        iv = title != null ? keyProvider.ivForTitle(title) : null;
      }

      if (key == null || iv == null || key.length != 32 || iv.length != 16) {
        errors.add(new ImportError(span.recordIndex,
            "No runtime key material available for MKMD span"));
        skippedCount++;
        continue;
      }

      MkmdDecoder.DecodedPayload decoded;
      try {
        decoded = MkmdDecoder.decode(ciphertext, key, iv);
      } catch (MkmdDecoder.DecodeException e) {
        errors.add(new ImportError(span.recordIndex,
            "MKMD decode failed: " + e.getMessage()));
        skippedCount++;
        continue;
      }

      String fileName = (title != null ? safeFileName(title) : "song-" + span.recordIndex)
          + extensionForSignature(decoded.signature);
      File outputFile;
      try {
        outputFile = writeTempFile(outputDir, fileName, decoded.payload);
      } catch (IOException e) {
        errors.add(new ImportError(span.recordIndex,
            "Cannot write decoded file: " + e.getMessage()));
        skippedCount++;
        continue;
      }

      DecodedSong song = new DecodedSong(
          UUID.randomUUID().toString(),
          title != null ? title : "Untitled (" + span.recordIndex + ")",
          fileName, Uri.fromFile(outputFile), decoded.signature, decoded.payload.length);
      songs.add(song);
      decodedCount++;

      Log.i(TAG, "Decoded MKMD record " + span.recordIndex + " -> " + fileName
          + " (" + decoded.payload.length + " bytes, " + decoded.signature + ")");
    }

    Log.i(TAG, "Meg container import complete: " + decodedCount + " decoded, "
        + skippedCount + " skipped out of " + spans.size() + " spans");

    return new ImportResult(spans.size(), decodedCount, skippedCount, songs, errors);
  }

  private static int readRange(byte[] data, long offset, int length, byte[] out) {
    int toRead = Math.min(length, (int) (data.length - offset));
    if (toRead <= 0) return 0;
    System.arraycopy(data, (int) offset, out, 0, toRead);
    return toRead;
  }

  private static String titleForRecord(byte[] indexBytes, int recordIndex) {
    try {
      java.util.List<ph.kanta.reborn.core.songbook.ShubIndexProbe.TitleRecord> titles =
          IndexedContainerCompatibility.titles(indexBytes);
      if (recordIndex >= 0 && recordIndex < titles.size()) {
        return titles.get(recordIndex).title;
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static String safeFileName(String title) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < title.length(); i++) {
      char c = title.charAt(i);
      if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    String name = sb.toString().trim();
    return name.isEmpty() ? "untitled" : name;
  }

  private static String extensionForSignature(MkmdDecoder.Signature sig) {
    switch (sig) {
      case STANDARD_MIDI: return ".mid";
      case WAVE: return ".wav";
      case MP3_ID3: return ".mp3";
      default: return ".bin";
    }
  }

  private static File writeTempFile(File dir, String prefix, byte[] data) throws IOException {
    if (dir == null) throw new IOException("Output directory is null");
    File temp = null;
    FileOutputStream out = null;
    FileChannel channel = null;
    try {
      temp = new File(dir, prefix + "-" + System.nanoTime() + ".tmp");
      if (temp.exists()) throw new IOException("Temp file already exists: " + temp);
      if (!temp.createNewFile()) throw new IOException("Cannot create temp file: " + temp);
      temp.setReadable(false, false);
      temp.setWritable(true, false);
      temp.setReadable(true, true);
      temp.setWritable(true, true);
      out = new FileOutputStream(temp);
      channel = out.getChannel();
      channel.write(ByteBuffer.wrap(data));
      out.flush();
      channel.force(false);
      out.close(); out = null;
      channel.close(); channel = null;
      File finalFile = new File(dir, prefix);
      if (finalFile.exists()) {
        finalFile = new File(dir, prefix + "." + System.currentTimeMillis());
      }
      if (!temp.renameTo(finalFile)) {
        copyFile(temp, finalFile);
        temp.delete();
      }
      return finalFile;
    } finally {
      if (channel != null) { try { channel.close(); } catch (IOException ignored) {} }
      if (out != null) { try { out.close(); } catch (IOException ignored) {} }
      if (temp != null && temp.exists()) { temp.delete(); }
    }
  }

  private static void copyFile(File src, File dst) throws IOException {
    try (FileInputStream in = new FileInputStream(src);
         FileOutputStream out = new FileOutputStream(dst)) {
      FileChannel inChannel = in.getChannel();
      FileChannel outChannel = out.getChannel();
      inChannel.transferTo(0, inChannel.size(), outChannel);
    }
  }
}
