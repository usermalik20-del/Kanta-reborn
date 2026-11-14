package ph.kanta.reborn;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The bundled standby interlude: a composed welcome track followed by the KANTA branding line.
 *
 * <p>It is the default between-song music, so the empty stage greets the room with sound without
 * the user importing anything. A user-imported track (the "loop song") overrides it. The asset is
 * packaged under assets/interlude/ and copied to private cache on startup, mirroring the party
 * pads, so {@code PartyAudio.loop} can stream it from a file path.
 */
public final class BundledInterlude {

  public static final String ID = "standby_welcome";
  public static final String TITLE = "Standby welcome";
  /** Relative to the assets root. */
  public static final String ASSET_PATH = "interlude/standby-loop.mp3";

  public static String fullAssetPath() {
    return ASSET_PATH;
  }

  /** Copies the bundled interlude to {@code dest}. Returns true on success. */
  public static boolean copyTo(Context context, File dest) {
    AssetManager assets = context.getAssets();
    try (InputStream in = assets.open(ASSET_PATH);
        FileOutputStream out = new FileOutputStream(dest)) {
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
      out.getFD().sync();
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private BundledInterlude() {}
}
