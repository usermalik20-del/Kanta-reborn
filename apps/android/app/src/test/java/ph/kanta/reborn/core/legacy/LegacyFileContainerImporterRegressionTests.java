package ph.kanta.reborn.core.legacy;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;

/** Synthetic address tests; no user media or key material is included. */
public final class LegacyFileContainerImporterRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static void u32(RandomAccessFile out, long value) throws IOException {
    for (int i = 0; i < 4; i++) out.write((int) (value >>> (8 * i)) & 255);
  }

  private static void header(RandomAccessFile out, long offset) throws IOException {
    out.seek(offset);
    out.write(new byte[] {'M', 'K', 'M', 'D', 1});
    u32(out, 1);
    u32(out, 16);
  }

  private static void rejected(File blob, long[] blocks, long[] offsets, String reason)
      throws Exception {
    boolean refused = false;
    try {
      LegacyFileContainerImporter.locateRecordStarts(blob, blocks, offsets);
    } catch (IOException expected) {
      refused = true;
    }
    check(refused, reason);
  }

  private static void namedImport() throws Exception {
    java.nio.file.Path dir = Files.createTempDirectory("legacy-named-import-");
    try {
      File index = dir.resolve("input.idx").toFile();
      File blob = dir.resolve("input.blob").toFile();
      byte[] first = new byte[] {'M', 'T', 'h', 'd', 11};
      byte[] second = new byte[] {'M', 'T', 'h', 'd', 22};
      try (RandomAccessFile out = new RandomAccessFile(blob, "rw")) {
        out.setLength(8192);
        encryptedRecord(out, 0, first);
        encryptedRecord(out, 2048, second);
        encryptedRecord(out, 4096, first); // Two unreferenced records.
        encryptedRecord(out, 6144, second);
      }
      try (RandomAccessFile out = new RandomAccessFile(index, "rw")) {
        out.setLength(28);
        out.seek(20); u32(out, 28);
        out.seek(28);
        for (int i = 0; i < 2; i++) {
          byte[] title = (i == 0 ? "Synthetic B" : "Synthetic A")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          out.write(1); out.write(12 + title.length); out.writeShort(0);
          u32(out, 0); u32(out, i == 0 ? 1 : 0); out.write(title);
        }
        u32(out, 17); u32(out, 513); // Correct count, not record byte addresses.
      }
      LegacyFileContainerImporter.ImportResult result = LegacyFileContainerImporter.importPair(
          index, blob, dir.resolve("output").toFile());
      check(result.decodedCount == 2 && result.skippedCount == 0 && result.errors.isEmpty(),
          "Paired import counts only index-referenced songs");
      check(result.songs.get(0).title.equals("Synthetic B")
          && Arrays.equals(Files.readAllBytes(result.songs.get(0).file.toPath()), second),
          "First title materializes its addressed payload, not first blob payload");
      check(result.songs.get(1).title.equals("Synthetic A")
          && Arrays.equals(Files.readAllBytes(result.songs.get(1).file.toPath()), first),
          "Second title materializes its addressed payload");
    } finally {
      try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(dir)) {
        for (java.nio.file.Path path : paths.sorted(java.util.Comparator.reverseOrder())
            .toArray(java.nio.file.Path[]::new)) Files.delete(path);
      }
    }
  }

  private static void encryptedRecord(RandomAccessFile out, long offset, byte[] payload)
      throws Exception {
    java.util.zip.Deflater compressor = new java.util.zip.Deflater(6, true);
    byte[] packed = new byte[128];
    int length;
    try {
      compressor.setInput(payload); compressor.finish();
      length = compressor.deflate(packed);
      check(compressor.finished(), "Synthetic fixture compression complete");
    } finally { compressor.end(); }
    // Exercise the existing compatibility decoder without duplicating its key in tests.
    java.lang.reflect.Field key = LegacyFileContainerImporter.class.getDeclaredField("KEY");
    java.lang.reflect.Field iv = LegacyFileContainerImporter.class.getDeclaredField("IV");
    key.setAccessible(true); iv.setAccessible(true);
    javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
        new javax.crypto.spec.SecretKeySpec((byte[]) key.get(null), "AES"),
        new javax.crypto.spec.IvParameterSpec((byte[]) iv.get(null)));
    byte[] encrypted = cipher.doFinal(Arrays.copyOf(packed, ((length + 15) / 16) * 16));
    out.seek(offset); out.write(new byte[] {'M', 'K', 'M', 'D', 1});
    u32(out, payload.length); u32(out, length); out.write(encrypted);
  }

  public static int run() throws Exception {
    checks = 0;
    File blob = Files.createTempFile("legacy-record-locator-", ".blob").toFile();
    try {
      try (RandomAccessFile out = new RandomAccessFile(blob, "rw")) {
        out.setLength(8192);
        header(out, 0);
        header(out, 2048);
        header(out, 4096);
        header(out, 6144); // Unreferenced records must never shift title association.
        header(out, 101);  // Byte-offset layouts need not be block-aligned.
      }
      check(LegacyFileContainerImporter.locateRecordStarts(
          blob, new long[] {2, 0, 1}, new long[] {17, 513, 99999})
          .equals(Arrays.asList(4096L, 0L, 2048L)),
          "Title-record addresses must preserve names despite invalid trailer and extra records");
      check(LegacyFileContainerImporter.locateRecordStarts(
          blob, new long[] {99, 98}, new long[] {4096, 101})
          .equals(Arrays.asList(4096L, 101L)), "Valid byte-offset layout preserves title order");
      check(LegacyFileContainerImporter.locateRecordStarts(
          blob, new long[] {2, 0}, new long[] {4096, 0})
          .equals(Arrays.asList(4096L, 0L)), "Matching layouts are unambiguous");
      rejected(blob, new long[] {1, 0}, new long[] {0, 4096}, "Conflicting layouts rejected");
      rejected(blob, new long[] {99}, new long[] {17}, "No physical-order guessing");
      rejected(blob, new long[] {0, 0}, new long[] {0, 0}, "Duplicate references rejected");
      rejected(blob, new long[] {99, 98}, new long[] {0}, "Wrong cardinality rejected");
      rejected(blob, new long[] {0xffffffffL}, new long[] {-1}, "Address bounds enforced");
      try (RandomAccessFile out = new RandomAccessFile(blob, "rw")) {
        header(out, 8180); // The declared cipher does not fit in this file.
      }
      rejected(blob, new long[] {99}, new long[] {8180}, "Truncated ciphertext rejected");
      try (RandomAccessFile out = new RandomAccessFile(blob, "rw")) {
        out.seek(4);
        out.write(2);
      }
      rejected(blob, new long[] {0}, new long[] {0}, "Unsupported version rejected");
    } finally {
      Files.deleteIfExists(blob.toPath());
    }
    namedImport();
    return checks;
  }
}
