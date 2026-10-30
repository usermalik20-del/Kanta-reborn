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
  static final String[] STARTERS = {"morning-light", "sing-together", "starlight-echo"};
  static final String[] TITLES = {"Morning Light", "Sing Together", "Starlight Echo"};

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
  private final Set<String> removed = new HashSet<>();
  private List<ReservationQueue.Entry> queue = new ArrayList<>();
  private String selectedBank = "kanta-tone.sf2", bankName = "KANTA original tone";
  private boolean batching;
  private java.util.function.BooleanSupplier cancelled = () -> false;
  private final ArrayList<File> batchFiles = new ArrayList<>();
  private ImportProgressListener progressListener;

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
      if (bank.matches("[a-f0-9]{64}\\.sf2") && new File(media, bank).isFile()) {
        selectedBank = bank;
        bankName = root.optString("soundfontName", "Imported SoundFont");
      }
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
                o.optBoolean("starter"));
        if (!ids.add(s.id)) throw new IOException("Duplicate song ID");
        songs.add(s);
      }
      JSONArray hidden = root.optJSONArray("removed");
      if (hidden != null)
        for (int i = 0; i < hidden.length(); i++) removed.add(hidden.getString(i));
      JSONArray collections = root.optJSONArray("sets");
      if (collections != null) {
        if (collections.length() > 1000) throw new IOException("Too many saved sets");
        for (int i = 0; i < collections.length(); i++) {
          JSONObject o = collections.getJSONObject(i);
          ArrayList<String> idsInSet = new ArrayList<>();
          JSONArray a = o.getJSONArray("songs");
          if (a.length() > 10000) throw new IOException("Saved set too large");
          for (int j = 0; j < a.length(); j++) idsInSet.add(a.getString(j));
          sets.add(new SavedSet(o.getString("id"), o.getString("name"), idsInSet));
        }
      }
      JSONArray reservations = root.optJSONArray("queue");
      if (reservations != null) {
        if (reservations.length() > 1000) throw new IOException("Queue too large");
        for (int i = 0; i < reservations.length(); i++) {
          JSONObject o = reservations.getJSONObject(i);
          Song s = find(o.getString("song"));
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
    for (int i = 0; i < STARTERS.length; i++) {
      String id = "starter:" + STARTERS[i], file = STARTERS[i] + ".mid";
      File target = new File(media, file);
      if (!target.exists()) {
        File temp = File.createTempFile("starter-", ".tmp", media);
        try (InputStream in = context.getAssets().open("starter/" + STARTERS[i] + ".kar");
            FileOutputStream out = new FileOutputStream(temp)) {
          byte[] b = new byte[8192];
          int n;
          while ((n = in.read(b)) != -1) out.write(b, 0, n);
          out.getFD().sync();
        }
        if (!temp.renameTo(target)) {
          temp.delete();
          throw new IOException("Cannot install starter song");
        }
      }
      if (!removed.contains(id) && find(id) == null)
        songs.add(new Song(id, TITLES[i], "KANTA Originals", "S00" + (i + 1), file, true));
    }
    persist();
  }

  private Song find(String id) {
    for (Song s : songs) if (s.id.equals(id)) return s;
    return null;
  }

  List<Song> songs() {
    return Collections.unmodifiableList(new ArrayList<>(songs));
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
    queue = new ArrayList<>(next);
    try {
      persist();
    } catch (Exception e) {
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

private Song importSong(Uri uri) throws Exception {
    long fileSize = getFileSize(uri);
    notifyProgress(fileSize, 0, "Opening song...");

    String name = "Imported song";
    try (Cursor c =
        context
            .getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst() && !c.isNull(0)) name = c.getString(0);
    }
    if (songs.size() >= 150000) throw new IOException("Library capacity reached");
    String lower = name.toLowerCase(Locale.ROOT);
    String ext =
        lower.endsWith(".kar") || lower.endsWith(".mid") || lower.endsWith(".midi")
            ? "mid"
            : lower.endsWith(".mp3") ? "mp3" : lower.endsWith(".wav") ? "wav" : null;
    if (ext == null) throw new IOException("Choose MIDI, KAR, MP3 or WAV music");
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
          if (ext.equals("mid") && total > 16 * 1024 * 1024)
            throw new IOException("MIDI file exceeds 16 MB");
          else if (ext.equals("mp3") || ext.equals("wav")) {
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
      if (ext.equals("mid")) {
        MidiLyrics.parse(java.nio.file.Files.readAllBytes(temp.toPath()));
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
      if (existing != null) return existing;
      File destination = new File(media, id + "." + ext);
      boolean created = !destination.exists();
      if (created) {
        if (!temp.renameTo(destination)) throw new IOException("Cannot save imported song");
        installed = true;
        if (batching) batchFiles.add(destination);
      } else {
        verifyCopy(destination, id);
      }
      String title = name.replaceFirst("\\.[^.]+$", "");
      Set<String> codes = new HashSet<>();
      for (Song existingSong : songs) codes.add(existingSong.code);
      int reference = 100000;
      while (codes.contains(String.valueOf(reference))) reference++;
      Song song = new Song(id, title, "", String.valueOf(reference), destination.getName(), false);
      songs.add(song);
      try {
        persist();
      } catch (Exception e) {
        songs.remove(song);
        if (created) destination.delete();
        throw e;
      }
      notifyProgress(fileSize, fileSize, "Import complete");
      return song;
    } finally {
      if (!installed) temp.delete();
    }
  }  private static int readPrefix(InputStream in, byte[] bytes) throws IOException {
    int total = 0;
    while (total < bytes.length) {
      int count = in.read(bytes, total, bytes.length - total);
      if (count < 0) break;
private void importSoundFont(Uri uri) throws Exception {
    long fileSize = getFileSize(uri);
    notifyProgress(fileSize, 0, "Opening SoundFont...");

    File temp = File.createTempFile("bank-", ".tmp", media);
    boolean created = false;
    File destination = null;
    String oldBank = selectedBank, oldName = bankName;
    try {
      MessageDigest hash = MessageDigest.getInstance("SHA-256");
      try (InputStream in = context.getContentResolver().openInputStream(uri);
          FileOutputStream out = new FileOutputStream(temp)) {
        if (in == null) throw new IOException("Cannot open SoundFont");
        byte[] bytes = new byte[65536];
        long total = 0;
        int count;
        while ((count = in.read(bytes)) != -1) {
          total += count;
          notifyProgress(fileSize, total, "Copying: " + formatBytes(total) + " / " + formatBytes(fileSize));
          if ((Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()))
            throw new InterruptedIOException();
          hash.update(bytes, 0, count);
          out.write(bytes, 0, count);
        }
        notifyProgress(fileSize, total, "Validating...");
        // Size validated by native loader; check disk space only
        long freeBytes = new android.os.StatFs(media.getAbsolutePath()).getAvailableBytes();
        if (total > freeBytes - 64 * 1024 * 1024) throw new IOException("Insufficient storage space for SoundFont");
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
      bankName = "Imported SoundFont";
      try (Cursor c =
          context
              .getContentResolver()
              .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
        if (c != null && c.moveToFirst() && !c.isNull(0))
          bankName = c.getString(0).substring(0, Math.min(120, c.getString(0).length()));
      }
      persist();
      if (created && batching) batchFiles.add(destination);
    } catch (Exception | LinkageError e) {
      selectedBank = oldBank;
      bankName = oldName;
      if (created && destination != null) destination.delete();
      throw e;
    } finally {
      temp.delete();
    }
    notifyProgress(fileSize, fileSize, "Import complete");
  }

  private static String formatBytes(long bytes) {
    if (bytes < 0) return "?";
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
  }      if (count == 0) {
        int value = in.read();
        if (value < 0) break;
        bytes[total++] = (byte) value;
      } else total += count;
    }
    return total;
  }

  boolean importMedia(Uri uri) throws Exception {
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
    if (bank) importSoundFont(uri);
    else importSong(uri);
    return bank;
  }

  int[] importBatch(List<Uri> uris, java.util.function.BooleanSupplier cancellation)
      throws Exception {
    ArrayList<Song> before = new ArrayList<>(songs);
    String oldBank = selectedBank, oldName = bankName;
    int good = 0, failed = 0;
    batching = true;
    cancelled = cancellation;
    batchFiles.clear();
    try {
      for (Uri uri : uris) {
        if ((Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()))
          throw new InterruptedIOException();
        try {
          importMedia(uri);
          good++;
        } catch (Exception | LinkageError e) {
          failed++;
        }
      }
      if (cancelled.getAsBoolean()) throw new InterruptedIOException();
      batching = false;
      persist();
      return new int[] {good, failed};
    } catch (Exception e) {
      songs.clear();
      songs.addAll(before);
      selectedBank = oldBank;
      bankName = oldName;
      for (File file : batchFiles) file.delete();
      throw e;
    } finally {
      batching = false;
      cancelled = () -> false;
      batchFiles.clear();
    }
  }

