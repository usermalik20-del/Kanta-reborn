package ph.kanta.reborn;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;

/** Host checks driven by tools/tests/test_android_soundfont_download.py. */
public final class SoundFontDownloadRegressionTests {
  private static final byte[] BANK = {1, 2, 3, 4};
  private static final byte[] LICENSE = {5, 6, 7};

  public static void main(String[] args) throws Exception {
    File media = new File(args[0]);
    String base = args[1];
    SoundFontDownload.Bank fluid = SoundFontDownload.Bank.FLUID;
    SoundFontDownload.Bank muse = SoundFontDownload.Bank.MUSESCORE;
    check(install(media, muse, base + "/redirect", false));
    check(Arrays.equals(BANK, Files.readAllBytes(new File(media, muse.fileName).toPath())));
    check(Arrays.equals(LICENSE, Files.readAllBytes(new File(media, muse.licenseName).toPath())));
    check(!install(media, muse, base + "/missing", true));
    check(install(media, fluid, base + "/zip", false));
    check(Arrays.equals(BANK, Files.readAllBytes(new File(media, fluid.fileName).toPath())));
    for (File file : Objects.requireNonNull(media.listFiles())) Files.delete(file.toPath());

    for (String route : new String[] {"missing", "short", "wrong-zip", "loop"}) {
      expectFailure(media, route.equals("wrong-zip") ? fluid : muse, base + "/" + route, false);
    }
    expectFailure(media, muse, base + "/direct", true);
  }

  private static boolean install(File media, SoundFontDownload.Bank bank, String source,
      boolean reject) throws Exception {
    return SoundFontDownload.install(media, bank, new URL(source), new ByteArrayInputStream(LICENSE),
        (phase, bytes, total) -> check(total <= 0 || bytes <= total), file -> {
          check(!new File(media, bank.fileName).exists());
          check(Arrays.equals(BANK, Files.readAllBytes(file.toPath())));
          if (reject) throw new IOException("Test validator rejection");
        });
  }

  private static void expectFailure(File media, SoundFontDownload.Bank bank, String source,
      boolean reject) throws Exception {
    try {
      install(media, bank, source, reject);
      throw new AssertionError("Expected failure: " + source);
    } catch (IOException expected) {
      check(Objects.requireNonNull(media.list()).length == 0);
    }
  }

  private static void check(boolean condition) {
    if (!condition) throw new AssertionError("SoundFont download contract failed");
  }
}
