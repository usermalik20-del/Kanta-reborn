package ph.kanta.reborn.lyrics;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import ph.kanta.reborn.core.MidiLyrics;

public final class LyricCache {
  private static final String TAG = "LyricCache";
  private static final long MAX_CACHE_BYTES = 8L * 1024 * 1024;
  private static final long ENTRY_TTL_MS = 30L * 24 * 60 * 60 * 1000;
  private static final int MAX_ENTRIES = 256;

  private final File cacheDir;
  private final Map<String, CacheEntry> index = new HashMap<>();
  private long currentBytes = 0;

  public LyricCache(Context context) {
    cacheDir = new File(context.getCacheDir(), "lyric-cache");
    if (!cacheDir.exists()) cacheDir.mkdirs();
    loadIndex();
  }

  private void loadIndex() {
    for (File file : cacheDir.listFiles()) {
      if (file == null || !file.getName().endsWith(".bin")) continue;
      try (FileInputStream fis = new FileInputStream(file);
           ObjectInputStream ois = new ObjectInputStream(fis)) {
        CacheEntry entry = (CacheEntry) ois.readObject();
        if (System.currentTimeMillis() - entry.timestamp > ENTRY_TTL_MS) {
          file.delete(); continue;
        }
        index.put(entry.key, entry);
        currentBytes += file.length();
      } catch (IOException | ClassNotFoundException corrupt) {
        file.delete();
      }
    }
  }

  public synchronized void put(String trackName, String artistName, long durationMs,
      java.util.List<ph.kanta.reborn.core.MidiLyrics.Cue> cues) {
    if (cues == null || cues.isEmpty()) return;
    String key = hashKey(trackName, artistName, durationMs);
    if (index.containsKey(key)) return;

    CacheEntry entry = new CacheEntry(key, trackName, artistName, durationMs, cues,
        System.currentTimeMillis());
    try {
      byte[] data = serialize(entry);
      if (data.length > 256 * 1024) return;
      if (index.size() >= MAX_ENTRIES) evictOldest();
      File file = new File(cacheDir, key + ".bin");
      try (FileOutputStream fos = new FileOutputStream(file);
           ObjectOutputStream oos = new ObjectOutputStream(fos)) {
        oos.writeObject(entry);
      }
      index.put(key, entry);
      currentBytes += file.length();
      enforceSizeLimit();
    } catch (IOException e) {
      Log.w(TAG, "Failed to write lyric cache entry", e);
    }
  }

  public synchronized java.util.List<ph.kanta.reborn.core.MidiLyrics.Cue> get(
      String trackName, String artistName, long durationMs) {
    String key = hashKey(trackName, artistName, durationMs);
    CacheEntry entry = index.get(key);
    if (entry == null) return null;
    if (System.currentTimeMillis() - entry.timestamp > ENTRY_TTL_MS) {
      remove(key);
      return null;
    }
    return entry.cues;
  }

  private synchronized void remove(String key) {
    CacheEntry entry = index.remove(key);
    if (entry != null) {
      File file = new File(cacheDir, key + ".bin");
      if (file.exists()) {
        currentBytes -= file.length();
        file.delete();
      }
    }
  }

  private void evictOldest() {
    String oldestKey = null;
    long oldestTime = Long.MAX_VALUE;
    for (Map.Entry<String, CacheEntry> e : index.entrySet()) {
      if (e.getValue().timestamp < oldestTime) {
        oldestTime = e.getValue().timestamp;
        oldestKey = e.getKey();
      }
    }
    if (oldestKey != null) remove(oldestKey);
  }

  private void enforceSizeLimit() {
    while (currentBytes > MAX_CACHE_BYTES && !index.isEmpty()) evictOldest();
  }

  private static String hashKey(String track, String artist, long duration) {
    try {
      String raw = track.trim().toLowerCase() + "|" + artist.trim().toLowerCase() + "|" + (duration / 1000);
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(32);
      for (int i = 0; i < 16; i++) sb.append(String.format("%02x", hash[i]));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      return String.valueOf(raw.hashCode());
    }
  }

  private static byte[] serialize(CacheEntry entry) throws IOException {
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(bos)) { oos.writeObject(entry); }
    return bos.toByteArray();
  }

  private static final class CacheEntry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    final String key, trackName, artistName;
    final long durationMs;
    final java.util.List<ph.kanta.reborn.core.MidiLyrics.Cue> cues;
    final long timestamp;

    CacheEntry(String key, String trackName, String artistName, long durationMs,
        java.util.List<ph.kanta.reborn.core.MidiLyrics.Cue> cues, long timestamp) {
      this.key = key; this.trackName = trackName; this.artistName = artistName;
      this.durationMs = durationMs; this.cues = cues; this.timestamp = timestamp;
    }
  }
}
