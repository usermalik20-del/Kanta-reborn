package ph.kanta.reborn.core;

import ph.kanta.reborn.core.bgv.ScenicCatalog;
import ph.kanta.reborn.core.bgv.ScenicClip;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Domain regression checks for the built-in scenic video catalogue.
 *
 * <p>Framework-free: runs on a host JVM inside {@code coreRegression}. The catalogue is a
 * security boundary — every entry opens a socket to a third party — so its constraints are
 * asserted here rather than trusted at download time.
 */
public final class ScenicCatalogRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static void catalogueIsWellFormed() {
    List<ScenicClip> clips = ScenicCatalog.BUILT_IN;
    check(clips.size() >= 8, "The catalogue offers a usable choice of backgrounds");
    Set<String> ids = new HashSet<>();
    Set<String> fileNames = new HashSet<>();
    for (ScenicClip clip : clips) {
      check(ids.add(clip.id), "Duplicate clip id: " + clip.id);
      check(fileNames.add(clip.fileName()), "Two clips would share a file: " + clip.fileName());
      check(clip.title.length() >= 2, "Every clip needs a readable title");
      check(clip.attribution().contains(clip.licence), "Attribution names its licence");
      check(clip.pageUrl.startsWith("https://"), "Source page is https only");
      check(clip.downloadUrl.startsWith("https://"), "Download is https only");
      check(clip.expectedBytes > 0, "Expected size is known before downloading");
    }
  }

  private static void everyEntryPassesTheDownloadGate() {
    for (ScenicClip clip : ScenicCatalog.BUILT_IN) {
      check(ScenicCatalog.isAllowed(clip), "Catalogue entry is blocked by its own gate: " + clip.id);
      check(
          clip.expectedBytes <= ScenicCatalog.MAX_BYTES,
          "Catalogue entry exceeds the download cap: " + clip.id);
      String host = ScenicCatalog.hostOf(clip.downloadUrl);
      check(host != null, "Download host could not be read for " + clip.id);
      check(host.indexOf('@') < 0, "Credentials in a URL are refused: " + clip.id);
    }
  }

  private static void unknownHostsAreRefused() {
    check(!ScenicCatalog.isAllowed(null), "A missing clip is not downloadable");
    // Entries are validated on construction, so an unsafe one can only arrive as a new instance.
    ScenicClip unlistedHost =
        new ScenicClip(
            "hostile-01",
            "Unlisted host",
            "Sea",
            "Unknown",
            "CC0",
            "https://example.invalid/page",
            "https://evil.example.invalid/video.mp4",
            1024L);
    check(!ScenicCatalog.isAllowed(unlistedHost), "An unlisted host was accepted");
    ScenicClip unknownContainer =
        new ScenicClip(
            "blob-01",
            "No container",
            "Sea",
            "Unknown",
            "CC0",
            "https://commons.wikimedia.org/wiki/File:x",
            "https://upload.wikimedia.org/wikipedia/commons/x.bin",
            1024L);
    check(!ScenicCatalog.isAllowed(unknownContainer), "An unknown container was accepted");
    ScenicClip oversized =
        new ScenicClip(
            "huge-01",
            "Too large",
            "Sea",
            "Unknown",
            "CC0",
            "https://commons.wikimedia.org/wiki/File:y",
            "https://upload.wikimedia.org/wikipedia/commons/y.mp4",
            ScenicCatalog.MAX_BYTES + 1);
    check(!ScenicCatalog.isAllowed(oversized), "A clip above the cap was accepted");
  }

  private static void lookupAndChannelsStayConsistent() {
    check(ScenicCatalog.byId(null) == null, "A null id resolves to nothing");
    check(ScenicCatalog.byId("no-such-clip") == null, "An unknown id resolves to nothing");
    Set<String> grouped = new HashSet<>();
    for (String channel : ScenicCatalog.channels()) {
      List<ScenicClip> inChannel = ScenicCatalog.inChannel(channel);
      check(!inChannel.isEmpty(), "An advertised channel is empty: " + channel);
      for (ScenicClip clip : inChannel) {
        check(clip.channel.equals(channel), "A clip leaked into the wrong channel");
        check(ScenicCatalog.byId(clip.id) == clip, "An indexed clip is not resolvable by id");
        grouped.add(clip.id);
      }
    }
    check(
        grouped.size() == ScenicCatalog.BUILT_IN.size(),
        "Every catalogue entry belongs to exactly one channel");
  }

  private static void hostsAndSizesAreReadable() {
    check(
        "upload.wikimedia.org".equals(
            ScenicCatalog.hostOf("https://upload.wikimedia.org/wikipedia/commons/x.webm")),
        "A plain https host is read back");
    check(
        "images-assets.nasa.gov".equals(
            ScenicCatalog.hostOf("https://images-assets.nasa.gov:443/video/x.mp4")),
        "An explicit port is stripped from the host");
    check(ScenicCatalog.hostOf(null) == null, "A missing URL has no host");
    check(ScenicCatalog.hostOf("not a url") == null, "A URL without a scheme has no host");
    check(ScenicCatalog.hostOf("https://user@host/x.mp4") == null, "Userinfo is refused");
    check("1 KB".equals(ScenicCatalog.sizeLabel(1024)), "A small size reads in KB");
    check(ScenicCatalog.sizeLabel(14L * 1024 * 1024).endsWith(" MB"), "A large size reads in MB");
    check(!ScenicCatalog.sizeLabel(1).isEmpty(), "A tiny size still renders a label");
  }

  public static int run() {
    int before = checks;
    catalogueIsWellFormed();
    everyEntryPassesTheDownloadGate();
    unknownHostsAreRefused();
    lookupAndChannelsStayConsistent();
    hostsAndSizesAreReadable();
    return checks - before;
  }
}
