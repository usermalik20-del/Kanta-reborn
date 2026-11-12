package ph.kanta.reborn.lyrics;

import java.io.IOException;
import java.util.List;

/** Offline response-contract tests; no requests to LRCLIB during instrumentation. */
public final class LrclibClientChecks {
  public static int run() throws Exception {
    List<LrclibClient.Result> values = LrclibClient.parse("[{\"id\":1,\"trackName\":\"Fixture\",\"artistName\":\"Test\",\"syncedLyrics\":null,\"plainLyrics\":\"Example\",\"duration\":12},{\"id\":0}]");
    if (values.size() != 1 || !values.get(0).syncedLyrics.isEmpty()
        || !values.get(0).plainLyrics.equals("Example") || values.get(0).duration != 12)
      throw new AssertionError("LRCLIB response normalization failed");
    boolean immutable = false;
    try { values.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
    if (!immutable) throw new AssertionError("LRCLIB result list must be immutable");
    boolean rejected = false;
    try { LrclibClient.parse("not JSON"); } catch (IOException expected) { rejected = true; }
    if (!rejected) throw new AssertionError("Malformed LRCLIB response accepted");
    if (!LrclibClient.parse("[]").isEmpty()) throw new AssertionError("No-match response contract");
    return 4;
  }
}
