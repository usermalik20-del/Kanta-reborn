package ph.kanta.reborn.core.legacy;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.Inflater;

/** Synthetic address tests; no user media or key material is included. */
public final class LegacyFileContainerImporterRegressionTests {
  private static int checks;
  // Deliberately synthetic material, unrelated to any real container.
  private static final byte[] TEST_KEY = new byte[32];
  private static final byte[] TEST_IV = new byte[16];

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static void privateAssetKey() throws IOException {
    byte[] asset = new byte[48];
    for (int i = 0; i < asset.length; i++) asset[i] = (byte) (i + 1);
    try (LegacyImportKey key = LegacyImportKey.fromPrivateAsset(new java.io.ByteArrayInputStream(asset))) {
      check(Arrays.equals(key.key(), Arrays.copyOfRange(asset, 0, 32)), "Private asset key order");
      check(Arrays.equals(key.iv(), Arrays.copyOfRange(asset, 32, 48)), "Private asset IV order");
    }
    for (int size : new int[] {0, 31, 32, 47, 49}) {
      try (LegacyImportKey ignored = LegacyImportKey.fromPrivateAsset(
          new java.io.ByteArrayInputStream(new byte[size]))) {
        check(false, "Wrong-sized private asset accepted: " + size);
      } catch (IOException expected) {
        check(true, "Wrong-sized private asset rejected");
      }
    }
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

  private static final class DictionaryRequiredInflater extends Inflater {
    volatile boolean ended;
    DictionaryRequiredInflater() { super(true); }
    @Override public int inflate(byte[] output) { return 0; }
    @Override public boolean needsDictionary() { return true; }
    @Override public boolean needsInput() { return false; }
    @Override public boolean finished() { return false; }
    @Override public void end() { ended = true; super.end(); }
  }

  private static final class CancellationInflater extends Inflater {
    volatile boolean ended;
    private int calls;
    CancellationInflater() { super(true); }
    @Override public int inflate(byte[] output) {
      if (calls++ == 0) { output[0] = 1; return 1; }
      return 0;
    }
    @Override public boolean needsDictionary() { return false; }
    @Override public boolean needsInput() { return false; }
    @Override public boolean finished() { return false; }
    @Override public void end() { ended = true; super.end(); }
  }

  private static void cancellationStopsInflaterAndEndsNativeState() throws Exception {
    CancellationInflater inflater = new CancellationInflater();
    java.util.concurrent.atomic.AtomicInteger polls = new java.util.concurrent.atomic.AtomicInteger();
    boolean cancelled = false;
    try {
      LegacyFileContainerImporter.inflateExact(
          new byte[] {1}, 1, 2, inflater, () -> polls.incrementAndGet() > 1);
    } catch (java.io.InterruptedIOException expected) {
      cancelled = true;
    }
    check(cancelled, "Legacy MKMD decompression observes cancellation between inflate calls");
    check(inflater.ended, "Cancelled legacy MKMD inflater native state was released");
  }
  private static void rejectsStalledLegacyInflater() throws Exception {
    DictionaryRequiredInflater inflater = new DictionaryRequiredInflater();
    FutureTask<byte[]> task = new FutureTask<>(
        () -> LegacyFileContainerImporter.inflateExact(new byte[] {1}, 1, 1, inflater));
    Thread worker = new Thread(task, "legacy-mkmd-stalled-inflater-regression");
    worker.setDaemon(true);
    worker.start();
    try {
      task.get(2, TimeUnit.SECONDS);
      check(false, "Dictionary-required legacy MKMD stream was accepted");
    } catch (ExecutionException failure) {
      check(failure.getCause() instanceof IOException,
          "Dictionary-required legacy MKMD stream did not fail as an import error");
      check(failure.getCause().getMessage().contains("dictionary"),
          "Legacy MKMD no-progress failure was not classified");
    } catch (TimeoutException timeout) {
      check(false, "Legacy MKMD inflater stalled instead of rejecting no progress");
    }
    check(inflater.ended, "Legacy MKMD inflater native state was released on failure");
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
      LegacyImportKey credentials = new LegacyImportKey(TEST_KEY, TEST_IV);
      byte[] ownedKey = credentials.key(), ownedIv = credentials.iv();
      LegacyFileContainerImporter.ImportResult result = LegacyFileContainerImporter.importPair(
          index, blob, dir.resolve("output").toFile(), credentials);
      checkCleared(credentials, ownedKey, ownedIv);
      byte[] wrong = TEST_KEY.clone();
      wrong[0] = 1;
      LegacyImportKey wrongCredentials = new LegacyImportKey(wrong, TEST_IV);
      byte[] wrongOwnedKey = wrongCredentials.key(), wrongOwnedIv = wrongCredentials.iv();
      LegacyFileContainerImporter.ImportResult refused = LegacyFileContainerImporter.importPair(
          index, blob, dir.resolve("wrong-key").toFile(), wrongCredentials);
      check(refused.decodedCount == 0 && refused.skippedCount == 2,
          "Wrong runtime key never silently falls back to bundled material");
      checkCleared(wrongCredentials, wrongOwnedKey, wrongOwnedIv);
      check(result.decodedCount == 2 && result.skippedCount == 0 && result.errors.isEmpty(),
          "Paired import counts only index-referenced songs");
      check(result.songs.get(0).title.equals("Synthetic B")
          && Arrays.equals(Files.readAllBytes(result.songs.get(0).file.toPath()), second),
          "First title materializes its addressed payload, not first blob payload");
      check(result.songs.get(1).title.equals("Synthetic A")
          && Arrays.equals(Files.readAllBytes(result.songs.get(1).file.toPath()), first),
          "Second title materializes its addressed payload");
      check(result.songs.get(0).sourceCode.isEmpty(), "Untagged trailer supplied a source number");
      try (RandomAccessFile out = new RandomAccessFile(index, "rw")) {
        out.seek(out.length() - 8);
        out.write(new byte[] {9, 1, 3, 2, 9, 0, 42, 0});
      }
      LegacyFileContainerImporter.ImportResult numbered = LegacyFileContainerImporter.importPair(
          index, blob, dir.resolve("numbered").toFile(), new LegacyImportKey(TEST_KEY, TEST_IV));
      check(numbered.decodedCount == 2 && numbered.errors.isEmpty(), "Numbered pair did not import");
      LegacyFileContainerImporter.DecodedSong row = numbered.songs.get(0);
      check(row.sourceCode.equals("066051") && row.titleOrdinal == 1 && Arrays.equals(Files.readAllBytes(row.file.toPath()), second),
          "The packed keypad number and payload must both follow their title record");
      check(numbered.songs.get(1).sourceCode.equals("000042"), "Second title must keep its printed number");
      ph.kanta.reborn.core.Song imported = new ph.kanta.reborn.core.Song(
          "legacy:" + row.id, row.title, "", row.sourceCode, "legacy-" + row.id + ".mid", false, true);
      java.util.List<ph.kanta.reborn.core.Song> catalog = java.util.Collections.singletonList(imported);
      for (String digits : new String[] {"66051", "066051"}) {
        check(imported.numericPreviewRank(digits) == 0, "Legacy number did not rank as an exact match");
        check(ph.kanta.reborn.core.Song.uniqueCodeMatch(catalog, digits) == imported,
            "Legacy number did not resolve the same song for PLAY and RES");
      }
      check(imported.numericPreviewRank("066") >= 0
              && ph.kanta.reborn.core.Song.uniqueCodeMatch(catalog, "066") == null,
          "A partial code should preview but not submit");
      check(imported.numericPreviewRank("0066051") == -1
              && ph.kanta.reborn.core.Song.uniqueCodeMatch(catalog, "0066051") == null,
          "An overlong alias must not match or submit");
      check(imported.numericPreviewRank("1") == -1, "The title ordinal became a searchable code");
      ph.kanta.reborn.core.Song literal = new ph.kanta.reborn.core.Song(
          "literal", "Collision", "", "66051", "literal.mid", false);
      check(ph.kanta.reborn.core.Song.uniqueCodeMatch(Arrays.asList(imported, literal), "66051") == null,
          "Ambiguous alias submitted a different song");
      try (RandomAccessFile out = new RandomAccessFile(blob, "rw")) {
        encryptedRecord(out, 2048, new byte[] {1, 2, 3, 4, 5});
      }
      LegacyFileContainerImporter.ImportResult withSkip = LegacyFileContainerImporter.importPair(
          index, blob, dir.resolve("with-skip").toFile(), new LegacyImportKey(TEST_KEY, TEST_IV));
      check(withSkip.skippedCount == 1 && withSkip.songs.get(0).sourceCode.equals("000042"),
          "Skipped payload shifted the remaining printed number");
      check(ph.kanta.reborn.core.songbook.ShubIndexProbe.keypadNumbers(
          new long[] {1, 0}, new long[] {0x002a0009L, 0x02030109L}).length == 0,
          "Tagged but uncorrelated trailer supplied source numbers");
    } finally {
      try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(dir)) {
        for (java.nio.file.Path path : paths.sorted(java.util.Comparator.reverseOrder())
            .toArray(java.nio.file.Path[]::new)) Files.delete(path);
      }
    }
  }

  private static void cancelledImportRemovesPartialOutputs() throws Exception {
    java.nio.file.Path dir = Files.createTempDirectory("legacy-cancelled-import-");
    File output = dir.resolve("output").toFile();
    try {
      File index = dir.resolve("input.idx").toFile();
      File blob = dir.resolve("input.blob").toFile();
      byte[] first = new byte[] {'M', 'T', 'h', 'd', 11};
      byte[] second = new byte[] {'M', 'T', 'h', 'd', 22};
      try (RandomAccessFile out = new RandomAccessFile(blob, "rw")) {
        out.setLength(4096);
        encryptedRecord(out, 0, first);
        encryptedRecord(out, 2048, second);
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
        u32(out, 17); u32(out, 513);
      }
      LegacyImportKey credentials = new LegacyImportKey(TEST_KEY, TEST_IV);
      byte[] ownedKey = credentials.key(), ownedIv = credentials.iv();
      boolean cancelled = false;
      try {
        LegacyFileContainerImporter.importPair(index, blob, output, credentials,
            () -> output.isDirectory() && output.listFiles() != null && output.listFiles().length > 0);
      } catch (java.io.InterruptedIOException expected) {
        cancelled = true;
      }
      check(cancelled, "Legacy paired import stops after cancellation during record processing");
      check(output.isDirectory() && output.listFiles().length == 0,
          "Cancelled legacy import deletes already-decoded staged files");
      checkCleared(credentials, ownedKey, ownedIv);
    } finally {
      try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(dir)) {
        for (java.nio.file.Path path : paths.sorted(java.util.Comparator.reverseOrder())
            .toArray(java.nio.file.Path[]::new)) Files.delete(path);
      }
    }
  }
  private static void probeKeySelection() throws Exception {
    java.nio.file.Path dir = Files.createTempDirectory("legacy-probe-");
    try {
      File index = dir.resolve("input.idx").toFile();
      File blob = dir.resolve("input.blob").toFile();
      byte[] first = new byte[] {'M', 'T', 'h', 'd', 11};
      byte[] second = new byte[] {'M', 'T', 'h', 'd', 22};
      try (RandomAccessFile out = new RandomAccessFile(blob, "rw")) {
        out.setLength(4096);
        encryptedRecord(out, 0, first);
        encryptedRecord(out, 2048, second);
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
        u32(out, 17); u32(out, 513);
      }
      byte[] wrong = TEST_KEY.clone();
      wrong[0] = 1;
      LegacyImportKey wrongCredentials = new LegacyImportKey(wrong, TEST_IV);
      byte[] wrongKeyBytes = wrongCredentials.key();
      byte[] wrongIvBytes = wrongCredentials.iv();
      check(!LegacyFileContainerImporter.probeKey(index, blob, wrongCredentials),
          "A key that cannot decode the first record is refused by the probe");
      check(!LegacyFileContainerImporter.probeKey(index, dir.resolve("missing.blob").toFile(), wrongCredentials),
          "A missing blob is refused by the probe");
      check(!LegacyFileContainerImporter.probeKey(index, blob, null),
          "Missing credentials are refused by the probe");
      check(!Arrays.equals(wrongKeyBytes, new byte[32]),
          "The probe refused to zero live key bytes");
      LegacyFileContainerImporter.ImportResult refused = LegacyFileContainerImporter.importPair(
          index, blob, dir.resolve("wrong-key").toFile(), wrongCredentials);
      check(refused.decodedCount == 0 && refused.skippedCount == 2,
          "The probe does not consume or mutate the probed credentials");
      checkCleared(wrongCredentials, wrongKeyBytes, wrongIvBytes);

      LegacyImportKey credentials = new LegacyImportKey(TEST_KEY, TEST_IV);
      byte[] keyBytes = credentials.key();
      byte[] ivBytes = credentials.iv();
      check(LegacyFileContainerImporter.probeKey(index, blob, credentials),
          "A key that decodes the first record is accepted by the probe");
      boolean stillUsable = true;
      try {
        credentials.key(); credentials.iv();
      } catch (IllegalStateException closedAfterProbe) {
        stillUsable = false;
      }
      check(stillUsable, "A passed probe leaves the credentials available for the import");
      LegacyFileContainerImporter.ImportResult result = LegacyFileContainerImporter.importPair(
          index, blob, dir.resolve("output").toFile(), credentials);
      check(result.decodedCount == 2 && result.errors.isEmpty(),
          "A probed key still imports the full pair");
      checkCleared(credentials, keyBytes, ivBytes);
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
    javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
        new javax.crypto.spec.SecretKeySpec(TEST_KEY, "AES"),
        new javax.crypto.spec.IvParameterSpec(TEST_IV));
    byte[] encrypted = cipher.doFinal(Arrays.copyOf(packed, ((length + 15) / 16) * 16));
    out.seek(offset); out.write(new byte[] {'M', 'K', 'M', 'D', 1});
    u32(out, payload.length); u32(out, length); out.write(encrypted);
  }

  private static void checkCleared(LegacyImportKey credentials, byte[] key, byte[] iv) {
    check(Arrays.equals(key, new byte[32]) && Arrays.equals(iv, new byte[16]),
        "Owned key and IV are zeroed");
    boolean refused = false;
    try { credentials.key(); } catch (IllegalStateException expected) { refused = true; }
    check(refused, "Closed material cannot be reused");
    credentials.close(); // Idempotent cleanup at controller and importer boundaries.
  }

  private static void runtimeKeyLifecycle() throws Exception {
    byte[] sourceKey = new byte[32], sourceIv = new byte[16];
    Arrays.fill(sourceKey, (byte) 17); Arrays.fill(sourceIv, (byte) 34);
    LegacyImportKey credentials = new LegacyImportKey(sourceKey, sourceIv);
    byte[] key = credentials.key(), iv = credentials.iv();
    sourceKey[0] = 99; sourceIv[0] = 99;
    check(key[0] == 17 && iv[0] == 34, "Runtime key owns defensive copies");
    credentials.close();
    checkCleared(credentials, key, iv);
    check(sourceKey[0] == 99 && sourceIv[0] == 99, "Cleanup does not mutate caller arrays");

    String keyHex = "11".repeat(32), ivHex = "aB".repeat(16);
    try (LegacyImportKey parsed = LegacyImportKey.fromHex(keyHex, ivHex)) {
      check(parsed.key()[0] == 17 && parsed.iv()[0] == (byte) 0xab,
          "Runtime hexadecimal parser accepts both cases");
    }
    for (String invalid : new String[] {"", "0".repeat(63), "0".repeat(65),
        "0".repeat(63) + "g", "0".repeat(63) + "\uff10"}) {
      boolean refused = false;
      try { LegacyImportKey.fromHex(invalid, ivHex); }
      catch (IllegalArgumentException expected) { refused = true; }
      check(refused, "Malformed key rejected without including input in errors");
    }
    boolean refused = false;
    try { LegacyImportKey.fromHex(keyHex, "0".repeat(31)); }
    catch (IllegalArgumentException expected) { refused = true; }
    check(refused, "Malformed IV rejected");
    for (int size : new int[] {0, 16, 24, 31, 33}) {
      refused = false;
      try { new LegacyImportKey(new byte[size], new byte[16]); }
      catch (IllegalArgumentException expected) { refused = true; }
      check(refused, "Unsupported key size rejected before import");
    }
    refused = false;
    try { new LegacyImportKey(new byte[32], null); }
    catch (IllegalArgumentException expected) { refused = true; }
    check(refused, "Missing material rejected");

    credentials = new LegacyImportKey(sourceKey, sourceIv);
    key = credentials.key(); iv = credentials.iv();
    refused = false;
    try {
      LegacyFileContainerImporter.importPair(new File("missing-index"),
          new File("missing-blob"), new File("unused-output"), credentials);
    } catch (IOException expected) { refused = true; }
    check(refused, "Invalid pair rejected");
    checkCleared(credentials, key, iv);
  }

  public static int run() throws Exception {
    checks = 0;
    privateAssetKey();
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
    runtimeKeyLifecycle();
    rejectsStalledLegacyInflater();
    cancellationStopsInflaterAndEndsNativeState();
    namedImport();
    cancelledImportRemovesPartialOutputs();
    probeKeySelection();
    return checks;
  }
}
