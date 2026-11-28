package ph.kanta.reborn.font;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Stable saved IDs for curated styles of the unchanged fonts in assets/lyric-fonts. */
final class BundledLyricFonts {
  static final String SCHEME = "kanta-font";

  static final class Option {
    final String label;
    final Uri uri;
    private final String family;
    private final int weight;

    Option(String id, String label, String family, int weight) {
      this.label = label;
      this.uri = Uri.fromParts(SCHEME, id, null);
      this.family = family;
      this.weight = weight;
    }

    Typeface load(Context context) {
      // Build from a temp file first: this is the proven, portable path (the same
      // one the custom-font loader uses) and it completes on software-rendered
      // x86_64 emulators where the stored-asset mmap for Typeface.Builder does not.
      // The one-time small copy is cached by FontStore, so it happens once per face.
      Typeface fromFile = buildFromTempFile(context);
      if (fromFile != null) return fromFile;
      return buildFromAsset(context);
    }

    private Typeface buildFromAsset(Context context) {
      try {
        return build(new Typeface.Builder(context.getAssets(), "lyric-fonts/" + family + ".ttf"));
      } catch (RuntimeException malformedOrUnsupported) {
        return null;
      }
    }

    private Typeface buildFromTempFile(Context context) {
      // Typeface.Builder(File) is API 28+; below it the stored-asset path is the
      // only supported route, so there is no file fallback to offer.
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null;
      File temporary = null;
      try {
        temporary = File.createTempFile("kanta-bundled-font-", ".ttf", context.getCacheDir());
        try (InputStream input = context.getAssets().open("lyric-fonts/" + family + ".ttf");
             FileOutputStream output = new FileOutputStream(temporary)) {
          byte[] buffer = new byte[8192];
          int count;
          while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return build(new Typeface.Builder(temporary));
      } catch (IOException | RuntimeException failed) {
        return null;
      } finally {
        if (temporary != null) temporary.delete();
      }
    }

    /** Applies the curated weight/variation to a builder and builds it. */
    private Typeface build(Typeface.Builder builder) {
      if (weight != 0) {
        builder.setFontVariationSettings("'wght' " + weight);
        builder.setWeight(weight);
      }
      return builder.build();
    }
  }

  static final List<Option> OPTIONS = Collections.unmodifiableList(Arrays.asList(
      new Option("alegreya-regular", "Alegreya", "alegreya", 400),
      new Option("alegreya-bold", "Alegreya Bold", "alegreya", 700),
      new Option("anton-regular", "Anton", "anton", 0),
      new Option("bebasneue-regular", "Bebas Neue", "bebasneue", 0),
      new Option("bitter-regular", "Bitter", "bitter", 400),
      new Option("bitter-bold", "Bitter Bold", "bitter", 700),
      new Option("caveat-regular", "Caveat", "caveat", 400),
      new Option("caveat-bold", "Caveat Bold", "caveat", 700),
      new Option("cinzel-regular", "Cinzel", "cinzel", 400),
      new Option("cinzel-bold", "Cinzel Bold", "cinzel", 700),
      new Option("dmsans-regular", "DM Sans", "dmsans", 400),
      new Option("dmsans-bold", "DM Sans Bold", "dmsans", 700),
      new Option("fraunces-regular", "Fraunces", "fraunces", 400),
      new Option("fraunces-bold", "Fraunces Bold", "fraunces", 700),
      new Option("lexend-regular", "Lexend", "lexend", 400),
      new Option("lexend-semibold", "Lexend Semibold", "lexend", 600),
      new Option("lora-regular", "Lora", "lora", 400),
      new Option("lora-bold", "Lora Bold", "lora", 700),
      new Option("manrope-regular", "Manrope", "manrope", 400),
      new Option("manrope-extrabold", "Manrope Extra Bold", "manrope", 800),
      new Option("nunito-regular", "Nunito", "nunito", 400),
      new Option("nunito-extrabold", "Nunito Extra Bold", "nunito", 800),
      new Option("oswald-regular", "Oswald", "oswald", 400),
      new Option("oswald-bold", "Oswald Bold", "oswald", 700),
      new Option("outfit-regular", "Outfit", "outfit", 400),
      new Option("outfit-bold", "Outfit Bold", "outfit", 700),
      new Option("playfairdisplay-regular", "Playfair Display", "playfairdisplay", 400),
      new Option("robotocondensed-regular", "Roboto Condensed", "robotocondensed", 400),
      new Option("sora-regular", "Sora", "sora", 400),
      new Option("spacegrotesk-regular", "Space Grotesk", "spacegrotesk", 400),
      new Option("vt323-regular", "VT323", "vt323", 0)));

  static Typeface load(Context context, Uri uri) {
    for (Option option : OPTIONS) {
      if (option.uri.equals(uri)) return option.load(context);
    }
    return null;
  }
}
