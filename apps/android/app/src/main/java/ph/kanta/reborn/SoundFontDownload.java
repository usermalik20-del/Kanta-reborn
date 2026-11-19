package ph.kanta.reborn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Stages downloads in private media storage before publishing a playable bank. */
final class SoundFontDownload {
  private static final long MIB = 1024L * 1024L;
  private static final long MAX_BYTES = 768 * MIB;
  private static final String OSUOSL = "https://ftp.osuosl.org/pub/musescore/soundfont/";

  enum Bank {
    FLUID("FluidR3 GM", "FluidR3_GM.sf2", "fluidr3gm-license.txt",
        OSUOSL + "fluid-soundfont.zip", "FluidR3 GM2-2.SF2"),
    MUSESCORE("MuseScore General", "MuseScore_General.sf2", "musescore-license.txt",
        OSUOSL + "MuseScore_General/MuseScore_General.sf2", null),
    SGM("SGM-v2.01", "SGM-v2.01-YamahaGrand-Guit-Bass-v2.7.sf2", "sgm-license.txt",
        "https://huggingface.co/spaces/projectlosangeles/Orpheus-Mono-Melodies-Mixer/resolve/main/"
            + "SGM-v2.01-YamahaGrand-Guit-Bass-v2.7.sf2", null);

    final String label;
    final String fileName;
    final String licenseName;
    final String source;
    final String archiveMember;

    Bank(String label, String fileName, String licenseName, String source, String archiveMember) {
      this.label = label;
      this.fileName = fileName;
      this.licenseName = licenseName;
      this.source = source;
      this.archiveMember = archiveMember;
    }
  }

  interface Progress {
    void report(String phase, long copied, long expected);
  }

  interface Validator {
    void validate(File bank) throws IOException;
  }

  private SoundFontDownload() {}

  /** Returns false for an existing destination. All callers serialize media writes. */
  static boolean install(File media, Bank bank, InputStream license, Progress progress,
      Validator validator) throws IOException {
    return install(media, bank, new URL(bank.source), license, progress, validator);
  }

  static boolean install(File media, Bank bank, URL source, InputStream license, Progress progress,
      Validator validator) throws IOException {
    File destination = new File(media, bank.fileName);
    if (destination.exists()) return false;
    File licenseDestination = new File(media, bank.licenseName);
    File payload = null;
    File extracted = null;
    File stagedLicense = null;
    boolean publishedLicense = false;
    boolean installed = false;
    try {
      payload = File.createTempFile("soundfont-", ".part", media);
      download(source, payload, progress);
      File candidate = payload;
      if (bank.archiveMember != null) {
        progress.report("Extracting", 0, -1);
        extracted = File.createTempFile("soundfont-", ".part", media);
        extract(payload, bank.archiveMember, extracted);
        candidate = extracted;
      }
      progress.report("Validating", 0, -1);
      checkInterrupted();
      validator.validate(candidate);
      stagedLicense = File.createTempFile("soundfont-license-", ".part", media);
      copy(license, stagedLicense, -1, null, null);
      if (!licenseDestination.exists()) {
        Files.move(stagedLicense.toPath(), licenseDestination.toPath());
        publishedLicense = true;
      }
      // Staged in the same directory and no replacement of an existing bank.
      Files.move(candidate.toPath(), destination.toPath());
      installed = true;
      return true;
    } finally {
      if (!installed && publishedLicense) remove(licenseDestination);
      remove(stagedLicense);
      remove(extracted);
      remove(payload);
    }
  }

  private static void download(URL source, File target, Progress progress) throws IOException {
    URL current = source;
    for (int hop = 0; hop <= 5; hop++) {
      checkInterrupted();
      URLConnection opened = current.openConnection();
      if (!(opened instanceof HttpURLConnection)) throw new IOException("Invalid download URL");
      HttpURLConnection connection = (HttpURLConnection) opened;
      try {
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("Accept-Encoding", "identity");
        int status = connection.getResponseCode();
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
          String location = connection.getHeaderField("Location");
          if (location == null) throw new IOException("Download redirect has no destination");
          URL next = new URL(current, location);
          if (!current.getProtocol().equals(next.getProtocol())) {
            throw new IOException("Download redirect changes protocol");
          }
          current = next;
          continue;
        }
        if (status != HttpURLConnection.HTTP_OK) throw new IOException("Server returned HTTP " + status);
        long expected = connection.getContentLengthLong();
        progress.report("Downloading", 0, expected);
        try (InputStream in = connection.getInputStream()) {
          copy(in, target, expected, progress, null);
        }
        return;
      } finally {
        connection.disconnect();
      }
    }
    throw new IOException("Too many download redirects");
  }

  private static void extract(File archive, String member, File target) throws IOException {
    try (ZipFile zip = new ZipFile(archive)) {
      ZipEntry entry = zip.getEntry(member);
      if (entry == null || entry.isDirectory()) throw new IOException("SoundFont is missing from ZIP");
      CRC32 crc = new CRC32();
      try (InputStream in = zip.getInputStream(entry)) {
        copy(in, target, entry.getSize(), null, crc);
      }
      if (crc.getValue() != entry.getCrc()) throw new IOException("SoundFont ZIP checksum mismatch");
    }
  }

  private static void copy(InputStream in, File target, long expected, Progress progress, CRC32 crc)
      throws IOException {
    if (expected > MAX_BYTES) throw new IOException("SoundFont download exceeds size limit");
    long size = 0;
    try (FileOutputStream out = new FileOutputStream(target)) {
      byte[] buffer = new byte[65536];
      while (true) {
        checkInterrupted();
        int count = in.read(buffer);
        if (count == -1) break;
        long previous = size;
        size += count;
        if (size > MAX_BYTES || (expected >= 0 && size > expected)) {
          throw new IOException("SoundFont download exceeds expected size");
        }
        out.write(buffer, 0, count);
        if (crc != null) crc.update(buffer, 0, count);
        if (progress != null && size / MIB > previous / MIB && size != expected) {
          progress.report("Downloading", size, expected);
        }
      }
      if (size == 0 || (expected >= 0 && size != expected)) {
        throw new IOException("Incomplete SoundFont download");
      }
      out.getFD().sync();
    }
    if (progress != null) progress.report("Downloading", size, expected);
  }

  private static void checkInterrupted() throws InterruptedIOException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Download interrupted");
  }

  private static void remove(File file) {
    if (file != null) {
      try {
        Files.deleteIfExists(file.toPath());
      } catch (IOException ignored) {
        // Temp files do not have a SoundFont extension and never enter the library.
      }
    }
  }
}
