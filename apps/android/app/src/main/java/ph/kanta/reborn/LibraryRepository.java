package ph.kanta.reborn;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import java.io.*;
import java.security.*;
import java.util.*;
import org.json.*;
import ph.kanta.reborn.core.*;

/** Called on one IO executor. Only app-private copies and metadata are mutable. */
final class LibraryRepository {

  /** Callback for import progress updates. */
  public interface ImportProgressListener {
    void onProgress(long bytesTotal, long bytesCopied, String status);
    void onComplete(boolean success, String message);
  }
  /** Result of importing one media item (song or SoundFont). */
  static final class ImportOutcome {
    final Song song;
    final boolean duplicate;
    final boolean bank;

    ImportOutcome(Song song, boolean duplicate, boolean bank) {
      this.song = song;
      this.duplicate = duplicate;
      this.bank = bank;
    }
  }

  /** Aggregate batch result with a bounded per-file failure reason list. */
  static final class ImportBatchResult {
    final int imported, duplicates, failed;
    final List<String> reasons;

    ImportBatchResult(int imported, int duplicates, int failed, List<String> reasons) {
      this.imported = imported;
      this.duplicates = duplicates;
      this.failed = failed;
      this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
    }
  }

  private static final String[][] BUNDLED_SOUNDFONTS = {
    {"kanta_reborn_karaokelive_gm_v3.sf2", "KANTA KaraokeLive GM v3"},
    {"kanta_reborn_livestage_gm_v2.sf2", "KANTA LiveStage GM v2"}
  };

  static boolean removableSoundFont(String fileName) {
    if (fileName == null) return false;
    if (fileName.matches("[a-f0-9]{64}\\.sf2")) return true;
    for (SoundFontDownload.Bank bank : SoundFontDownload.Bank.values())
      if (bank.fileName.equals(fileName)) return true;
    return false;
  }

  private static boolean knownSoundFont(String fileName) {
    if ("kanta-tone.sf2".equals(fileName) || removableSoundFont(fileName)) return true;
    for (String[] bundled : BUNDLED_SOUNDFONTS)
      if (bundled[0].equals(fileName)) return true;
    return false;
  }

  static final class SoundFontEntry {
    final String fileName, name;
    SoundFontEntry(String fileName, String name) { this.fileName = fileName; this.name = name; }
  }

  static final class SavedSet {
    final String id, name;
    final List<String> songs;

    SavedSet(String id, String name, List<String> songs) {
      this.id = id;
      this.name = name;
      this.songs = Collections.unmodifiableList(new ArrayList<>(songs));
    }
  }

  final File media;
  private final Context context;
  private final AtomicFile state;
  private final ArrayList<Song> songs = new ArrayList<>();
  private final ArrayList<SavedSet> sets = new ArrayList<>();
  private final ArrayList<SoundFontEntry> soundFonts = new ArrayList<>();
  private List<ReservationQueue.Entry> queue = new ArrayList<>();
  private String selectedBank = "kanta-tone.sf2", bankName = "KANTA original tone";
  private boolean batching;
  private java.util.function.BooleanSupplier cancelled = () -> false;
  private final ArrayList<File> batchFiles = new ArrayList<>();
  private volatile ImportProgressListener progressListener;

  LibraryRepository(Context context) throws IOException {
    this.context = context;
    media = new File(context.getFilesDir(), "media");
    if (!media.isDirectory() && !media.mkdirs()) throw new IOException("Cannot open music storage");
    state = new AtomicFile(new File(context.getFilesDir(), "library-v1.json"));
  }

  void load() throws Exception {
    if (state.getBaseFile().exists() || new File(state.getBaseFile().getPath() + ".bak").exists()) {
      if (Math.max(
              state.getBaseFile().length(),
              new File(state.getBaseFile().getPath() + ".bak").length())
          > 16 * 1024 * 1024) throw new IOException("Saved library is too large");
      JSONObject root =
          new JSONObject(new String(state.readFully(), java.nio.charset.StandardCharsets.UTF_8));
      if (root.getInt("version") != 1) throw new IOException("Unsupported library version");
      String bank = root.optString("soundfont", "kanta-tone.sf2");
      if (knownSoundFont(bank) && new File(media, bank).isFile()) {
        selectedBank = bank;
        bankName = root.optString("soundfontName", "Imported SoundFont");
      }
      JSONArray banks = root.optJSONArray("soundfonts");
      if (banks != null) {
        if (banks.length() > 256) throw new IOException("Too many saved SoundFonts");
        for (int i = 0; i < banks.length(); i++) {
          JSONObject item = banks.getJSONObject(i);
          String file = item.optString("file", "");
          String name = item.optString("name", "Imported SoundFont");
          if (knownSoundFont(file)
              && new File(media, file).isFile()
              && soundFonts.stream().noneMatch(value -> value.fileName.equals(file)))
            soundFonts.add(new SoundFontEntry(file, name));
        }
      }
      // Migrate the pre-inventory format without losing the previously active imported bank.
      if (new File(media, selectedBank).isFile()
          && soundFonts.stream().noneMatch(value -> value.fileName.equals(selectedBank)))
        soundFonts.add(new SoundFontEntry(selectedBank, bankName));
      JSONArray rows = root.getJSONArray("songs");
      if (rows.length() > 150000) throw new IOException("Too many library rows");
      Set<String> ids = new HashSet<>();
      for (int i = 0; i < rows.length(); i++) {
        JSONObject o = rows.getJSONObject(i);
        Song s =
            new Song(
                o.getString("id"),
                o.getString("title"),
                o.optString("artist"),
                o.optString("code"),
                o.getString("file"),
                o.optBoolean("starter"), o.optBoolean("legacySourceNumber"));
        if (!ids.add(s.id)) throw new IOException("Duplicate song ID");
        // Imported rows whose private media copy is missing are dropped on load so a later
        // identical import recreates the pair instead of returning a permanently broken row.
        if (s.starter || s.id.startsWith("starter:") || !new File(media, s.fileName).isFile()) continue;
        songs.add(s);
      }
      JSONArray collections = root.optJSONArray("sets");
      if (collections != null) {
        if (collections.length() > 1000) throw new IOException("Too many saved sets");
        for (int i = 0; i < collections.length(); i++) {
          JSONObject o = collections.getJSONObject(i);
          ArrayList<String> idsInSet = new ArrayList<>();
          JSONArray a = o.getJSONArray("songs");
          if (a.length() > 10000) throw new IOException("Saved set too large");
          for (int j = 0; j < a.length(); j++) {
            String id = a.getString(j);
            if (!id.startsWith("starter:")) idsInSet.add(id);
          }
          sets.add(new SavedSet(o.getString("id"), o.getString("name"), idsInSet));
        }
      }
      JSONArray reservations = root.optJSONArray("queue");
      if (reservations != null) {
        if (reservations.length() > 1000) throw new IOException("Queue too large");
        for (int i = 0; i < reservations.length(); i++) {
          JSONObject o = reservations.getJSONObject(i);
          // Old online rows may have a source marker but no local song or valid queue ID.
          // Only restore reservations for songs present in the local catalog.
          if (!"local".equals(o.optString("source", "local")) || !o.isNull("youtube")) continue;
          Song s = find(o.optString("song", ""));
          if (s != null)
            queue.add(
                new ReservationQueue.Entry(
                    UUID.fromString(o.getString("id")), s, o.optString("singer")));
        }
      }
    }
    File defaultBank = new File(media, "kanta-tone.sf2");
    if (!defaultBank.exists()) {
      File temp = File.createTempFile("tone-", ".tmp", media);
      try (InputStream in = context.getAssets().open("starter/kanta-tone.sf2");
          FileOutputStream out = new FileOutputStream(temp)) {
        byte[] bytes = new byte[8192];
        int count;
        while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
        out.getFD().sync();
      }
      if (!temp.renameTo(defaultBank)) {
        temp.delete();
        throw new IOException("Cannot install original tone");
      }
    }
    if (soundFonts.stream().noneMatch(value -> value.fileName.equals("kanta-tone.sf2")))
      soundFonts.add(0, new SoundFontEntry("kanta-tone.sf2", "KANTA original tone"));
    for (String[] bundled : BUNDLED_SOUNDFONTS) {
      installBundledSoundFont(bundled[0]);
      if (soundFonts.stream().noneMatch(value -> value.fileName.equals(bundled[0])))
        soundFonts.add(new SoundFontEntry(bundled[0], bundled[1]));
    }
    if (soundFonts.stream().noneMatch(value -> value.fileName.equals(selectedBank))) {
      selectedBank = "kanta-tone.sf2";
      bankName = "KANTA original tone";
    }
    persist();
  }

  private void installBundledSoundFont(String fileName) throws IOException {
    File destination = new File(media, fileName);
    if (destination.isFile()) return;
    File temp = File.createTempFile("bundled-bank-", ".tmp", media);
    try {
      try (InputStream in = context.getAssets().open("starter/" + fileName);
          FileOutputStream out = new FileOutputStream(temp)) {
        byte[] bytes = new byte[8192];
        int count;
        while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
        out.getFD().sync();
      }
      if (!temp.renameTo(destination)) throw new IOException("Cannot install bundled SoundFont");
    } finally {
      if (temp.exists()) temp.delete();
    }
  }

  private Song find(String id) {
    for (Song s : songs) if (s.id.equals(id)) return s;
    return null;
  }

  List<Song> songs() {
    return Collections.unmodifiableList(new ArrayList<>(songs));
  }

  int importLegacyResult(ph.kanta.reborn.core.legacy.LegacyFileContainerImporter.ImportResult result) throws IOException {
    if (result == null || result.songs.isEmpty()) return 0;
    var previous = new ArrayList<>(songs);
    var installed = new ArrayList<File>();
    var codes = new BitSet();
    var existing = new HashMap<String, List<Integer>>();
    int nextGenerated = SongCodes.FIRST_GENERATED, changed = 0;
    try {
      var incomingIdentities = new HashMap<File, String>();
      var incomingCounts = new HashMap<String, Integer>();
      for (var row : result.songs) {
        if (row.sourceCode.isEmpty()) continue;
        SongCodes.markUsed(codes, row.sourceCode);
        String identity = legacyIdentity(row.title, row.file);
        incomingIdentities.put(row.file, identity);
        incomingCounts.merge(identity, 1, Integer::sum);
      }
      for (int i = 0; i < songs.size(); i++) {
        Song song = songs.get(i);
        SongCodes.markUsed(codes, song.code);
        if (!incomingIdentities.isEmpty() && song.id.startsWith("legacy:")) {
          File file = new File(media, song.fileName);
          if (file.isFile()) existing.computeIfAbsent(legacyIdentity(song.title, file), key -> new ArrayList<>()).add(i);
        }
      }
      for (var decoded : result.songs) {
        File source = decoded.file;
        String identity = incomingIdentities.get(source);
        List<Integer> matches = identity == null ? null : existing.get(identity);
        if (matches != null) {
          if (matches.stream().anyMatch(index -> songs.get(index).legacySourceNumber
              && songs.get(index).code.equals(decoded.sourceCode))) continue;
          if (matches.size() == 1 && incomingCounts.get(identity) == 1) {
            int index = matches.get(0);
            Song old = songs.get(index);
            // v28 wrote the one-based title ordinal; only repair a matching older identity.
            if (!old.legacySourceNumber || (decoded.titleOrdinal <= SongCodes.MAX_NUMBER
                && old.code.equals(SongCodes.format(decoded.titleOrdinal)))) {
              songs.set(index, new Song(old.id, old.title, old.artist, decoded.sourceCode,
                  old.fileName, old.starter, true));
              changed++;
              continue;
            }
          }
          // Identical older copies cannot be assigned different source numbers safely by order.
          if (matches.stream().anyMatch(index -> !songs.get(index).legacySourceNumber)) continue;
        }
        if (songs.size() >= 99999) throw new IOException("Library capacity reached");
        boolean numbered = !decoded.sourceCode.isEmpty();
        String number = decoded.sourceCode;
        if (!numbered) {
          int generated = SongCodes.claimGenerated(codes, nextGenerated);
          number = SongCodes.format(generated);
          nextGenerated = generated + 1;
        }
        String suffix = decoded.fileName.toLowerCase(Locale.ROOT).endsWith(".wav") ? ".wav"
            : decoded.fileName.toLowerCase(Locale.ROOT).endsWith(".mp3") ? ".mp3" : ".mid";
        File destination = new File(media, "legacy-" + decoded.id + suffix);
        if (!source.isFile() || !source.renameTo(destination)) throw new IOException("Cannot install decoded legacy song");
        installed.add(destination);
        if (identity != null) existing.computeIfAbsent(identity, key -> new ArrayList<>()).add(songs.size());
        songs.add(new Song("legacy:" + decoded.id, decoded.title, "", number, destination.getName(), false, numbered));
        changed++;
      }
      if (changed > 0) persist();
      return changed;
    } catch (Exception failure) {
      songs.clear();
      songs.addAll(previous);
      for (File file : installed) file.delete();
      throw new IOException("Cannot save legacy library", failure);
    } finally {
      for (var row : result.songs) row.file.delete();
    }
  }

  private static String legacyIdentity(String title, File file) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[32768];
      int n;
      while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
      return java.util.Base64.getEncoder().encodeToString(digest.digest()) + ':' + title;
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  List<SavedSet> sets() {
    return Collections.unmodifiableList(new ArrayList<>(sets));
  }

  List<ReservationQueue.Entry> queue() {
    return new ArrayList<>(queue);
  }

  public void setImportProgressListener(ImportProgressListener listener) {
    this.progressListener = listener;
  }
  void saveQueue(List<ReservationQueue.Entry> next) throws Exception {
    List<ReservationQueue.Entry> old = queue;
    Set<String> available = new HashSet<>();
    for (Song song : songs) available.add(song.id);
    queue = new ArrayList<>(next);
    queue.removeIf(entry -> !available.contains(entry.song.id));
    try {
      persist();
    } catch (Exception e) {
      notifyComplete(false, e.getMessage());
      queue = old;
      throw e;
    }
  }

  File soundFont() {
    return new File(media, selectedBank);
  }

  String soundFontName() {
    return bankName;
  }

  List<SoundFontEntry> soundFonts() {
    return Collections.unmodifiableList(new ArrayList<>(soundFonts));
  }

  void registerDownloadedSoundFont(SoundFontDownload.Bank bank) throws Exception {
    if (soundFonts.stream().anyMatch(entry -> entry.fileName.equals(bank.fileName))) return;
    if (!new File(media, bank.fileName).isFile()) throw new IOException("Downloaded SoundFont is missing");
    SoundFontEntry entry = new SoundFontEntry(bank.fileName, bank.label);
    soundFonts.add(entry);
    try {
      persist();
    } catch (Exception failure) {
      soundFonts.remove(entry);
      throw failure;
    }
  }

  void selectSoundFont(String fileName) throws Exception {
    SoundFontEntry selected = null;
    for (SoundFontEntry entry : soundFonts)
      if (entry.fileName.equals(fileName)) { selected = entry; break; }
    if (selected == null || !new File(media, selected.fileName).isFile())
      throw new IOException("That SoundFont is no longer available. Import it again.");
    String oldFile = selectedBank, oldName = bankName;
    selectedBank = selected.fileName;
    bankName = selected.name;
    try { persist(); }
    catch (Exception failure) { selectedBank = oldFile; bankName = oldName; throw failure; }
  }

  private ImportOutcome importSong(Uri uri) throws Exception {
    long fileSize = getFileSize(uri);
    notifyProgress(fileSize, 0, "Opening song...");

    String name = displayName(uri);
    if (songs.size() >= 99999) throw new IOException("Library capacity reached");
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")
        || lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".tgz")
        || lower.endsWith(".bz2") || lower.endsWith(".xz") || lower.endsWith(".cab"))
      throw new IOException("\"" + name + "\" is an archive. Import the MIDI/KAR file inside it directly.");
    String ext =
        lower.endsWith(".kar") || lower.endsWith(".mid") || lower.endsWith(".midi")
            ? "mid"
            : lower.endsWith(".mp3") ? "mp3" : lower.endsWith(".wav") ? "wav" : null;
    if (lower.endsWith(".shubblob") || lower.endsWith(".kantablob")
        || lower.endsWith(".shubidx") || lower.endsWith(".kantidx"))
      throw new IOException("Open Menu → Presets and choose “Import legacy idx + blob” to select the paired files.");
    if (ext == null) throw new IOException("Choose MIDI, KAR, MP3 or WAV music, or a valid SF2 SoundFont.");
    File temp = File.createTempFile("import-", ".tmp", media);
    boolean installed = false;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long total = 0;
      byte[] buffer = new byte[65536];
      try (InputStream input = context.getContentResolver().openInputStream(uri);
          FileOutputStream out = new FileOutputStream(temp)) {
        if (input == null) throw new IOException("Cannot open selected file");
        int count;
        while ((count = input.read(buffer)) != -1) {
          if ((Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()))
            throw new InterruptedIOException();
          total += count;
          notifyProgress(fileSize, total, "Copying: " + formatBytes(total) + " / " + formatBytes(fileSize));
          if (ext.equals("mp3") || ext.equals("wav")) {
            if (total > 512L * 1024 * 1024) throw new IOException("Music file exceeds 512 MB");
          }
          digest.update(buffer, 0, count);
          out.write(buffer, 0, count);
        }
        out.getFD().sync();
      }
      if (total == 0) throw new IOException("Selected file is empty");
      notifyProgress(fileSize, total, "Validating...");
      byte[] signature = new byte[12];
      try (FileInputStream in = new FileInputStream(temp)) {
        if (readPrefix(in, signature) < 4) throw new IOException("Music file is incomplete");
      }
      String title = name.replaceFirst("\\.[^.]+$", "");
      String artist = "";
      if (ext.equals("mid")) {
        // File-mapped decode validates and returns KAR metadata and lyrics in one pass.
        MidiLyrics.Document midi = MidiLyrics.decode(temp);
        if (!midi.title.isEmpty()) title = midi.title;
        artist = midi.artist;
      } else if (ext.equals("wav")
          && !(new String(signature, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)
                  .equals("RIFF")
              && new String(signature, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                  .equals("WAVE")))
        throw new IOException("Not a WAVE file");
      else if (ext.equals("mp3")
          && !(signature[0] == 'I' && signature[1] == 'D' && signature[2] == '3')
          && !((signature[0] & 255) == 255 && (signature[1] & 224) == 224))
        throw new IOException("Not a supported MP3 file");
      StringBuilder hex = new StringBuilder();
      for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
      String id = hex.toString();
      Song existing = find(id);
      if (existing != null && new File(media, existing.fileName).isFile())
        return new ImportOutcome(existing, true, false);
      var codes = new BitSet();
      for (Song existingSong : songs) if (existingSong != existing) SongCodes.markUsed(codes, existingSong.code);
      int previousNumber = existing == null ? -1 : SongCodes.number(existing.code);
      String reference = previousNumber >= 0 && !codes.get(previousNumber) ? existing.code
          : SongCodes.format(SongCodes.claimGenerated(codes, SongCodes.FIRST_GENERATED));
      File destination = new File(media, id + "." + ext);
      boolean created = !destination.exists();
      if (created) {
        if (!temp.renameTo(destination)) throw new IOException("Cannot save imported song");
        installed = true;
        if (batching) batchFiles.add(destination);
      } else {
        verifyCopy(destination, id);
      }
      // Replace a row with missing private media in place, without changing its identity.
      Song song = new Song(id, title, artist, reference, destination.getName(), false);
      int existingIndex = existing == null ? -1 : songs.indexOf(existing);
      if (existingIndex < 0) songs.add(song);
      else songs.set(existingIndex, song);
      if (!batching) {
        try {
          persist();
        } catch (Exception e) {
          notifyComplete(false, e.getMessage());
          if (existingIndex < 0) songs.remove(song);
          else songs.set(existingIndex, existing);
          if (created) destination.delete();
          throw e;
        }
      }
      notifyProgress(fileSize, fileSize, "Import complete");
      notifyComplete(true, "Import complete");
      return new ImportOutcome(song, false, false);
    } finally {
      if (!installed) temp.delete();
    }
  }

  private static int readPrefix(InputStream in, byte[] bytes) throws IOException {
    int total = 0;
    while (total < bytes.length) {
      int count = in.read(bytes, total, bytes.length - total);
      if (count < 0) break;
      if (count == 0) {
        int value = in.read();
        if (value < 0) break;
        bytes[total++] = (byte) value;
      } else total += count;
    }
    return total;
  }

  private ImportOutcome importSoundFont(Uri uri) throws Exception {
    long fileSize = getFileSize(uri);
    notifyProgress(fileSize, 0, "Opening SoundFont...");

    File temp = null;
    boolean created = false;
    File destination = null;
    String oldBank = selectedBank, oldName = bankName;
    ArrayList<SoundFontEntry> oldSoundFonts = new ArrayList<>(soundFonts);
    try {
      long availableBytes = new android.os.StatFs(media.getAbsolutePath()).getAvailableBytes();
      if (fileSize > 0 && !SoundFontStoragePolicy.canCopy(availableBytes, fileSize))
        throw new IOException("Insufficient storage space for SoundFont");
      temp = File.createTempFile("bank-", ".tmp", media);
      MessageDigest hash = MessageDigest.getInstance("SHA-256");
      try (InputStream in = context.getContentResolver().openInputStream(uri);
          FileOutputStream out = new FileOutputStream(temp)) {
        if (in == null) throw new IOException("Cannot open SoundFont");
        byte[] bytes = new byte[65536];
        long total = 0;
        long nextSpaceCheck = 0;
        int count;
        while ((count = in.read(bytes)) != -1) {
          total += count;
          notifyProgress(fileSize, total, "Copying: " + formatBytes(total) + " / " + formatBytes(fileSize));
          if ((Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()))
            throw new InterruptedIOException();
          if (total >= nextSpaceCheck) {
            long currentAvailable = new android.os.StatFs(media.getAbsolutePath()).getAvailableBytes();
            long reportedRemaining = fileSize > 0 ? Math.max(0, fileSize - total) : -1;
            if (!SoundFontStoragePolicy.canContinueCopying(
                currentAvailable, reportedRemaining, count))
              throw new IOException("Insufficient storage space for SoundFont");
            nextSpaceCheck = total + SoundFontStoragePolicy.RECHECK_INTERVAL_BYTES;
          }
          hash.update(bytes, 0, count);
          out.write(bytes, 0, count);
        }
        notifyProgress(fileSize, total, "Validating...");
        long freeBytes = new android.os.StatFs(media.getAbsolutePath()).getAvailableBytes();
        if (!SoundFontStoragePolicy.hasReserve(freeBytes))
          throw new IOException("Insufficient storage space for SoundFont");
        out.getFD().sync();
      }
      notifyProgress(fileSize, fileSize, "Validating structure...");
      long test = NativeSoundFont.open(temp.getAbsolutePath());
      if (test == 0) throw new IOException("Unsupported SoundFont structure");
      NativeSoundFont.close(test);
      notifyProgress(fileSize, fileSize, "Finalizing...");
      StringBuilder key = new StringBuilder();
      for (byte b : hash.digest()) key.append(String.format(Locale.ROOT, "%02x", b & 255));
      destination = new File(media, key + ".sf2");
      if (!destination.exists()) {
        if (!temp.renameTo(destination)) throw new IOException("Cannot save SoundFont");
        created = true;
      }
      if (!created) verifyCopy(destination, key.toString());
      selectedBank = destination.getName();
      bankName = sf2InstrumentName(temp);
      if (bankName.isEmpty()) {
        String display = displayName(uri);
        if (!display.equals("Imported song") && !display.isEmpty())
          bankName = display;
      }
      if (bankName.isEmpty()) bankName = "Imported SoundFont";

      String destinationName = destination.getName();
      soundFonts.removeIf(value -> value.fileName.equals(destinationName));
      soundFonts.add(new SoundFontEntry(destinationName, bankName));
      if (!batching) persist();
      if (created && batching) batchFiles.add(destination);
    } catch (Exception | LinkageError e) {
      notifyComplete(false, e.getMessage());
      selectedBank = oldBank;
      bankName = oldName;
      soundFonts.clear();
      soundFonts.addAll(oldSoundFonts);
      if (created && destination != null) destination.delete();
      throw e;
    } finally {
      if (temp != null) temp.delete();
    }
    notifyProgress(fileSize, fileSize, "Import complete");
    notifyComplete(true, "Import complete");
    return new ImportOutcome(null, false, true);
  }

  /**
   * The size the provider reports for an import, or -1 when it cannot be queried. A provider
   * that will not report a size is not a reason to refuse the import; only the progress total
   * is unknown.
   */
  private long getFileSize(Uri uri) {
    try (Cursor c =
        context
            .getContentResolver()
            .query(uri, new String[] {OpenableColumns.SIZE}, null, null, null)) {
      if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
    } catch (RuntimeException unknown) {
      // Fall through to the unknown-size result.
    }
    return -1;
  }

  /** Reports import progress to the registered listener, if there is one. */
  private void notifyProgress(long total, long copied, String status) {
    ImportProgressListener listener = progressListener;
    if (listener != null) listener.onProgress(total, copied, status);
  }

  /**
   * Reports the end of a user-visible import, exactly once.
   *
   * <p>While a batch is running the individual files are not each an operation, so a completion
   * per file would dismiss the caller's dialog after the first one. The batch reports once at
   * the end instead. Outside a batch, a single import reports for itself.
   */
  private void notifyComplete(boolean success, String message) {
    if (batching) return;
    ImportProgressListener listener = progressListener;
    if (listener != null) listener.onComplete(success, message);
  }

  private static String formatBytes(long bytes) {
    if (bytes < 0) return "?";
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
  }

  ImportOutcome importMedia(Uri uri) throws Exception {
    boolean bank = false;
    try (InputStream in = context.getContentResolver().openInputStream(uri)) {
      byte[] signature = new byte[12];
      if (in != null && readPrefix(in, signature) == 12)
        bank =
            signature[0] == 'R'
                && signature[1] == 'I'
                && signature[2] == 'F'
                && signature[3] == 'F'
                && signature[8] == 's'
                && signature[9] == 'f'
                && signature[10] == 'b'
                && signature[11] == 'k';
    }
    return bank ? importSoundFont(uri) : importSong(uri);
  }

  ImportBatchResult importBatch(List<Uri> uris, java.util.function.BooleanSupplier cancellation)
      throws Exception {
    ArrayList<Song> before = new ArrayList<>(songs);
    String oldBank = selectedBank, oldName = bankName;
    ArrayList<SoundFontEntry> oldSoundFonts = new ArrayList<>(soundFonts);
    int imported = 0, duplicates = 0, failed = 0;
    ArrayList<String> reasons = new ArrayList<>();
    batching = true;
    cancelled = cancellation;
    batchFiles.clear();
    try {
      for (Uri uri : uris) {
        if ((Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()))
          throw new InterruptedIOException();
        try {
          ImportOutcome outcome = importMedia(uri);
          if (outcome.duplicate) duplicates++;
          else imported++;
        } catch (Exception | LinkageError e) {
          failed++;
          if (reasons.size() < 20) reasons.add(failureReason(uri, e));
        }
      }
      if (cancelled.getAsBoolean()) throw new InterruptedIOException();
      batching = false;
      persist();
      StringBuilder summary = new StringBuilder("Imported ").append(imported).append(" file(s)");
      if (duplicates > 0) summary.append(", ").append(duplicates).append(" already in your library");
      if (failed > 0) summary.append(", skipped ").append(failed);
      summary.append(".");
      notifyComplete(true, summary.toString());
      return new ImportBatchResult(imported, duplicates, failed, reasons);
    } catch (Exception e) {
      // Clear the flag before reporting: notifyComplete stays silent while a batch is running.
      batching = false;
      notifyComplete(false, String.valueOf(e.getMessage()));
      songs.clear();
      songs.addAll(before);
      selectedBank = oldBank;
      bankName = oldName;
      soundFonts.clear();
      soundFonts.addAll(oldSoundFonts);
      for (File file : batchFiles) file.delete();
      throw e;
    } finally {
      batching = false;
      cancelled = () -> false;
      batchFiles.clear();
    }
  }

  /** Removes an imported song and its references; an unsuccessful commit keeps the bytes. */
  boolean deleteSong(String id) throws Exception {
    Song target = find(id);
    if (target == null) throw new IOException("That song is no longer listed.");
    String stem = id.startsWith("legacy:") ? "legacy-" + id.substring(7) : id;
    if (target.starter || id.startsWith("starter:")
        || !target.fileName.matches(java.util.regex.Pattern.quote(stem) + "\\.(?:mid|mp3|wav)"))
      throw new IOException("Only imported songs can be removed here.");
    int index = songs.indexOf(target);
    List<ReservationQueue.Entry> queueBefore = new ArrayList<>(queue);
    ArrayList<SavedSet> setsBefore = new ArrayList<>(sets);
    songs.remove(index);
    queue.removeIf(e -> e.song.id.equals(id));
    for (int i = 0; i < sets.size(); i++) {
      SavedSet set = sets.get(i);
      if (!set.songs.contains(id)) continue;
      ArrayList<String> remaining = new ArrayList<>(set.songs);
      remaining.removeIf(id::equals);
      sets.set(i, new SavedSet(set.id, set.name, remaining));
    }
    try {
      persist();
    } catch (Exception failure) {
      songs.add(index, target);
      queue = queueBefore;
      sets.clear();
      sets.addAll(setsBefore);
      throw failure;
    }
    // Do not leave a durable row pointing at missing bytes if the process dies before commit.
    return songs.stream().anyMatch(song -> song.fileName.equals(target.fileName))
        || deletePrivateCopy(target.fileName);
  }

  /** Removes only imported or known downloaded SoundFonts, never bundled assets. */
  boolean deleteSoundFont(String fileName) throws Exception {
    if (!removableSoundFont(fileName))
      throw new IOException("Only imported or downloaded SoundFonts can be removed here.");
    int index = 0;
    while (index < soundFonts.size() && !soundFonts.get(index).fileName.equals(fileName)) index++;
    if (index == soundFonts.size()) throw new IOException("That SoundFont is not in your library.");
    String oldBank = selectedBank, oldName = bankName;
    SoundFontEntry target = soundFonts.remove(index);
    if (selectedBank.equals(fileName)) {
      selectedBank = "kanta-tone.sf2";
      bankName = "KANTA original tone";
    }
    try {
      persist();
    } catch (Exception failure) {
      selectedBank = oldBank;
      bankName = oldName;
      soundFonts.add(index, target);
      throw failure;
    }
    return deletePrivateCopy(fileName);
  }

  private boolean deletePrivateCopy(String name) {
    try {
      java.nio.file.Files.deleteIfExists(new File(media, name).toPath());
      return true;
    } catch (IOException | SecurityException failed) {
      return false;
    }
  }

  private String displayName(Uri uri) {
    String name = "Imported song";
    try (Cursor c =
        context
            .getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst() && !c.isNull(0)) name = c.getString(0);
    } catch (RuntimeException unavailable) {
      // Fall through to the placeholder name.
    }
    return name.length() > 120 ? name.substring(0, 120) : name;
  }

  private String failureReason(Uri uri, Throwable throwable) {
    String reason = throwable.getMessage();
    if (reason == null || reason.isEmpty()) reason = throwable.getClass().getSimpleName();
    return "\"" + displayName(uri) + "\": " + reason;
  }

  /**
   * Reads the SF2 INFO/INAM instrument name when the bank carries one, so an
   * imported bank is labelled by its real instrument name instead of the source
   * filename. Returns an empty string when the chunk is absent or malformed.
   */
  private static String sf2InstrumentName(File file) {
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      byte[] head = new byte[12];
      raf.readFully(head);
      if (!new String(head, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
          || !new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("sfbk"))
        return "";
      long size = raf.length();
      while (raf.getFilePointer() + 8 <= size) {
        raf.readFully(head, 0, 8);
        String id = new String(head, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        long len =
            (head[7] & 255L) << 24
                | (head[6] & 255L) << 16
                | (head[5] & 255L) << 8
                | (head[4] & 255L);
        if (id.equals("LIST")) {
          byte[] type = new byte[4];
          raf.readFully(type);
          long listEnd = raf.getFilePointer() + Math.max(0, len - 4);
          if (new String(type, java.nio.charset.StandardCharsets.US_ASCII).equals("INFO")) {
            while (raf.getFilePointer() + 8 <= Math.min(listEnd, size)) {
              raf.readFully(head, 0, 8);
              String sub = new String(head, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
              long sublen =
                  (head[7] & 255L) << 24
                      | (head[6] & 255L) << 16
                      | (head[5] & 255L) << 8
                      | (head[4] & 255L);
              if (sub.equals("INAM")) {
                int n = (int) Math.min(sublen, 4096L);
                byte[] bytes = new byte[n];
                raf.readFully(bytes);
                String name = new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
                int cut = name.indexOf('\0');
                if (cut >= 0) name = name.substring(0, cut);
                name = name.trim();
                return name.length() > 120 ? name.substring(0, 120) : name;
              }
              skipSf2Chunk(raf, sublen);
            }
          } else {
            raf.seek(listEnd + (len & 1));
          }
        } else {
          skipSf2Chunk(raf, len);
        }
      }
    } catch (IOException | RuntimeException malformed) {
      // A malformed bank keeps the filename label; native validation still gates it.
    }
    return "";
  }

  private static void skipSf2Chunk(RandomAccessFile raf, long bytes) throws IOException {
    if (bytes < 0 || bytes > 1L << 31) throw new IOException("Corrupt SF2 chunk size");
    raf.seek(raf.getFilePointer() + bytes + (bytes & 1));
  }

  void saveSet(String name, List<Song> values) throws Exception {
    ArrayList<String> ids = new ArrayList<>();
    for (Song s : values) ids.add(s.id);
    createSavedSet(name, ids);
  }

  void createPlaylist(String name, String firstSongId) throws Exception {
    if (firstSongId != null && find(firstSongId) == null)
      throw new IOException("Song is no longer in the library");
    createSavedSet(name, firstSongId == null ? Collections.emptyList()
        : Collections.singletonList(firstSongId));
  }

  /** Appends a library song by ID without changing the playlist ID, queue or legacy entries. */
  boolean addToPlaylist(String playlistId, String songId) throws Exception {
    if (find(songId) == null) throw new IOException("Song is no longer in the library");
    for (int i = 0; i < sets.size(); i++) {
      SavedSet current = sets.get(i);
      if (!current.id.equals(playlistId)) continue;
      if (current.songs.contains(songId)) return false;
      if (current.songs.size() >= 10000) throw new IOException("Playlist is full");
      ArrayList<String> ids = new ArrayList<>(current.songs);
      ids.add(songId);
      sets.set(i, new SavedSet(current.id, current.name, ids));
      try {
        persist();
      } catch (Exception e) {
        sets.set(i, current);
        throw e;
      }
      return true;
    }
    throw new IOException("Playlist is no longer available");
  }

  static final int PLAYLIST_NAME_LIMIT = 120;

  private void createSavedSet(String name, List<String> ids) throws Exception {
    if (sets.size() >= 1000 || ids.size() > 10000)
      throw new IOException("Saved-set limit reached");
    name = name.trim();
    if (name.isEmpty() || name.length() > PLAYLIST_NAME_LIMIT)
      throw new IOException("Enter a set name of 1 to 120 characters");
    SavedSet set = new SavedSet(UUID.randomUUID().toString(), name, ids);
    sets.add(set);
    try {
      persist();
    } catch (Exception e) {
      sets.remove(set);
      throw e;
    }
  }

  private void persist() throws Exception {
    if (batching) return;
    JSONObject root =
        new JSONObject()
            .put("version", 1)
            .put("soundfont", selectedBank)
            .put("soundfontName", bankName);
    JSONArray rows = new JSONArray();
    for (Song s : songs)
      rows.put(
          new JSONObject()
              .put("id", s.id)
              .put("title", s.title)
              .put("artist", s.artist)
              .put("code", s.code)
              .put("file", s.fileName)
              .put("starter", s.starter)
              .put("legacySourceNumber", s.legacySourceNumber));
    root.put("songs", rows);
    JSONArray banks = new JSONArray();
    for (SoundFontEntry entry : soundFonts)
      banks.put(new JSONObject().put("file", entry.fileName).put("name", entry.name));
    root.put("soundfonts", banks);
    JSONArray q = new JSONArray();
    for (ReservationQueue.Entry e : queue)
      q.put(
          new JSONObject()
              .put("id", e.id.toString())
              .put("song", e.song.id)
              .put("singer", e.singer));
    root.put("queue", q);
    JSONArray saved = new JSONArray();
    for (SavedSet s : sets)
      saved.put(
          new JSONObject()
              .put("id", s.id)
              .put("name", s.name)
              .put("songs", new JSONArray(s.songs)));
    root.put("sets", saved);
    FileOutputStream out = null;
    try {
      out = state.startWrite();
      byte[] encoded = root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
      if (encoded.length > 16 * 1024 * 1024)
        throw new IOException("Library metadata capacity reached");
      out.write(encoded);
      state.finishWrite(out);
    } catch (Exception e) {
      notifyComplete(false, e.getMessage());
      if (out != null) state.failWrite(out);
      throw e;
    }
  }

  private void verifyCopy(File destination, String expected) throws Exception {
    MessageDigest hash = MessageDigest.getInstance("SHA-256");
    try (InputStream bytes = new FileInputStream(destination)) {
      byte[] block = new byte[65536];
      int count;
      while ((count = bytes.read(block)) != -1) {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException();
        hash.update(block, 0, count);
      }
    }
    StringBuilder key = new StringBuilder();
    for (byte b : hash.digest()) key.append(String.format(Locale.ROOT, "%02x", b & 255));
    if (!expected.contentEquals(key))
      throw new IOException("Private media copy failed integrity check");
  }
}
