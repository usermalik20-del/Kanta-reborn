package ph.kanta.reborn.core.legacy;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

/** Framework-free checks for legacy record-address selection. */
public final class LegacyFileContainerImporterRegressionTests {
  private static int checks;

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  private static void writeUInt32LE(RandomAccessFile out, long value) throws Exception {
    out.write((int) value & 0xff);
    out.write((int) (value >>> 8) & 0xff);
    out.write((int) (value >>> 16) & 0xff);
    out.write((int) (value >>> 24) & 0xff);
  }

  private static void writeRecordHeader(RandomAccessFile out) throws Exception {
    out.write(new byte[] {'M', 'K', 'M', 'D', 1});
    writeUInt32LE(out, 1);
    writeUInt32LE(out, 16);
  }

  public static int run() throws Exception {
    checks = 0;
    File blob = Files.createTempFile("legacy-record-locator-", ".blob").toFile();
    try {
      try (RandomAccessFile out = new RandomAccessFile(blob, "rw")) {
        out.setLength(3072);
        out.seek(0);
        writeRecordHeader(out);
        out.seek(1024);
        writeRecordHeader(out);
        out.seek(2048);
        writeRecordHeader(out);
      }

      ArrayList<Long> records = LegacyFileContainerImporter.locateRecordStarts(
          blob, 2, new long[] {17, 513});
      check(records.equals(Arrays.asList(0L, 1024L, 2048L)),
          "Count-matched non-record index values must fall back and retain extra scanned records");

      records = LegacyFileContainerImporter.locateRecordStarts(blob, 2, new long[] {1024});
      check(records.equals(Arrays.asList(0L, 1024L, 2048L)),
          "Mismatched index cardinality must use scanned MKMD records");

      records = LegacyFileContainerImporter.locateRecordStarts(
          blob, 2, new long[] {1024, 0});
      check(records.equals(Arrays.asList(1024L, 0L)),
          "Valid explicit record addresses must preserve index order");
    } finally {
      Files.deleteIfExists(blob.toPath());
    }
    return checks;
  }
}
