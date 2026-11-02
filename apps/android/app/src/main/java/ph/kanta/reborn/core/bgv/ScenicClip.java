package ph.kanta.reborn.core.bgv;

import java.util.Locale;

/**
 * One downloadable background video: where it comes from, what it is licensed under and where
 * the bytes live.
 *
 * <p>Framework-free, so the catalogue contract (https only, known host, known container, bounded
 * size) is checked by the host-JVM {@code coreRegression} task instead of at download time. Every
 * field is final and validated: a catalogue entry that cannot be trusted fails loudly when the
 * class is loaded, not silently when a user taps it.
 */
public final class ScenicClip {

  public final String id;
  public final String title;
  public final String channel;
  public final String credit;
  public final String licence;
  public final String pageUrl;
  public final String downloadUrl;
  public final long expectedBytes;

  public ScenicClip(
      String id,
      String title,
      String channel,
      String credit,
      String licence,
      String pageUrl,
      String downloadUrl,
      long expectedBytes) {
    this.id = require(id, "id", 3, 32, "[a-z0-9-]+");
    this.title = require(title, "title", 2, 80, ".*");
    this.channel = require(channel, "channel", 2, 24, ".*");
    this.credit = require(credit, "credit", 2, 40, ".*");
    this.licence = require(licence, "licence", 2, 24, ".*");
    this.pageUrl = require(pageUrl, "pageUrl", 12, 300, "https://.*");
    this.downloadUrl = require(downloadUrl, "downloadUrl", 12, 400, "https://.*");
    if (expectedBytes <= 0) throw new IllegalArgumentException("expectedBytes");
    this.expectedBytes = expectedBytes;
  }

  private static String require(
      String value, String name, int min, int max, String pattern) {
    if (value == null) throw new IllegalArgumentException(name);
    String trimmed = value.trim();
    if (trimmed.length() < min || trimmed.length() > max || !trimmed.matches(pattern))
      throw new IllegalArgumentException(name);
    return trimmed;
  }

  /** The container this clip is stored in, lower case and including the dot. */
  public String extension() {
    String lower = downloadUrl.toLowerCase(Locale.ROOT);
    if (lower.contains(".mp4")) return ".mp4";
    if (lower.contains(".webm")) return ".webm";
    if (lower.contains(".mkv")) return ".mkv";
    return ".bin";
  }

  /** Deterministic local file name; the id is already restricted to safe characters. */
  public String fileName() {
    return id + extension();
  }

  /** The credit line shown next to the download, e.g. "Wikimedia Commons · CC0". */
  public String attribution() {
    return credit + " · " + licence;
  }

  @Override
  public String toString() {
    return "ScenicClip[" + id + " " + title + " " + attribution() + "]";
  }
}
