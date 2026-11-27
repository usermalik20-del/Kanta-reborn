package ph.kanta.reborn.settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named, serializable snapshot of every lyric styling, interlude and
 * stage-background setting the editors expose.
 *
 * <p>Three built-ins ship in code and cannot be deleted; user presets are
 * persisted by the {@link SettingsStore} as a JSON array of
 * {@code {name, data}} objects. Applying a preset writes the snapshot back
 * through the store and publishes once.
 */
public final class KantaThemePreset {
  public final String name;
  public final boolean builtin;
  public final JSONObject data;

  public KantaThemePreset(String name, boolean builtin, JSONObject data) {
    this.name = name == null || name.isEmpty() ? "Preset" : name;
    this.builtin = builtin;
    this.data = data == null ? new JSONObject() : data;
  }

  /** Captures the store's current lyric/interlude/background state. */
  public static JSONObject capture(SettingsStore store) {
    JSONObject d = new JSONObject();
    try {
      d.put("font", store.getLyricFont());
      d.put("allCaps", store.getLyricAllCaps());
      d.put("bold", store.getLyricBold());
      d.put("outline", store.getLyricOutline());
      d.put("activeColor", store.getLyricActiveColor());
      d.put("upcomingOpacity", store.getLyricUpcomingOpacity());
      d.put("alignment", store.getLyricAlignment());
      d.put("lines", store.getLyricLines());
      d.put("showTitle", store.getLyricShowTitle());
      d.put("showWatermark", store.getLyricShowWatermark());
      d.put("showCountdown", store.getLyricShowCountdown());
      d.put("syncOffsetMs", store.getLyricSyncOffsetMs());
      d.put("interludeText", store.getInterludeText());
      d.put("interludeSizeSp", store.getInterludeTextSize());
      d.put("interludeColor", store.getInterludeTextColor());
      d.put("interludeAnimation", store.getInterludeAnimation());
      d.put("imageScale", store.getInterludeImageScale());
      d.put("imagePlacement", store.getInterludeImagePlacement());
      d.put("showUpNext", store.getInterludeShowUpNext());
      d.put("showVisualizer", store.getInterludeShowVisualizer());
      d.put("interludeWatermark", store.getInterludeShowWatermark());
      d.put("dimAlpha", store.getStageDimAlpha());
      d.put("blurDp", store.getStageBlurDp());
      d.put("slideshow", store.getStageSlideshow());
      d.put("slideshowIntervalSec", store.getSlideshowIntervalSec());
    } catch (Exception unavailable) {
      // A malformed snapshot degrades to an empty preset, never a crash.
    }
    return d;
  }

  /** Writes the snapshot back into the store (no publish; the session owns it). */
  public void applyTo(SettingsStore store) {
    try {
      store.setLyricFont(data.optString("font", ""));
      store.setLyricAllCaps(data.optBoolean("allCaps", false));
      store.setLyricBold(data.optBoolean("bold", true));
      store.setLyricOutline(data.optInt("outline", 1));
      store.setLyricActiveColor(data.optInt("activeColor", 0xFFFFFFFF));
      store.setLyricUpcomingOpacity((float) data.optDouble("upcomingOpacity", 0.8));
      store.setLyricAlignment(data.optString("alignment", "center"));
      store.setLyricLines(data.optString("lines", "two"));
      store.setLyricShowTitle(data.optBoolean("showTitle", true));
      store.setLyricShowWatermark(data.optBoolean("showWatermark", true));
      store.setLyricShowCountdown(data.optBoolean("showCountdown", false));
      store.setLyricSyncOffsetMs(data.optInt("syncOffsetMs", 0));
      store.setInterludeText(data.optString("interludeText", ""));
      store.setInterludeTextSize(data.optInt("interludeSizeSp", 0));
      store.setInterludeTextColor(data.optInt("interludeColor", 0xFFFFFFFF));
      store.setInterludeAnimation(data.optString("interludeAnimation", "static"));
      store.setInterludeImageScale((float) data.optDouble("imageScale", 1.0));
      store.setInterludeImagePlacement(data.optString("imagePlacement", "above"));
      store.setInterludeShowUpNext(data.optBoolean("showUpNext", true));
      store.setInterludeShowVisualizer(data.optBoolean("showVisualizer", true));
      store.setInterludeShowWatermark(data.optBoolean("interludeWatermark", true));
      store.setStageDimAlpha(data.optInt("dimAlpha", 0));
      store.setStageBlurDp(data.optInt("blurDp", 0));
      store.setStageSlideshow(data.optBoolean("slideshow", false));
      store.setSlideshowIntervalSec(data.optInt("slideshowIntervalSec", 10));
    } catch (Exception unavailable) {
    }
  }

  private static JSONObject data(String font, boolean allCaps, boolean bold, int outline,
      int activeColor, float upcoming, String alignment, String lines, boolean showTitle,
      boolean showWatermark, boolean showCountdown, int dim, int blur) {
    JSONObject d = new JSONObject();
    try {
      d.put("font", font);
      d.put("allCaps", allCaps);
      d.put("bold", bold);
      d.put("outline", outline);
      d.put("activeColor", activeColor);
      d.put("upcomingOpacity", upcoming);
      d.put("alignment", alignment);
      d.put("lines", lines);
      d.put("showTitle", showTitle);
      d.put("showWatermark", showWatermark);
      d.put("showCountdown", showCountdown);
      d.put("syncOffsetMs", 0);
      d.put("interludeText", "");
      d.put("interludeSizeSp", 0);
      d.put("interludeColor", 0xFFFFFFFF);
      d.put("interludeAnimation", "static");
      d.put("imageScale", 1.0);
      d.put("imagePlacement", "above");
      d.put("showUpNext", true);
      d.put("showVisualizer", true);
      d.put("interludeWatermark", true);
      d.put("dimAlpha", dim);
      d.put("blurDp", blur);
      d.put("slideshow", false);
      d.put("slideshowIntervalSec", 10);
    } catch (Exception unavailable) {
    }
    return d;
  }

  /** The three non-deletable built-ins, in display order. */
  public static List<KantaThemePreset> builtIns() {
    List<KantaThemePreset> presets = new ArrayList<>();
    presets.add(new KantaThemePreset("Classic Videoke", true,
        data("", true, true, 2, 0xFFFFFF00, 0.8f, "center", "two", true, true, true, 20, 0)));
    presets.add(new KantaThemePreset("High-Contrast TV", true,
        data("", false, true, 2, 0xFFFFFFFF, 0.9f, "center", "multi", true, true, false, 40, 4)));
    presets.add(new KantaThemePreset("Minimal Dark", true,
        data("", false, false, 0, 0xFFFFFFFF, 0.6f, "left", "multi", false, false, false, 10, 0)));
    return Collections.unmodifiableList(presets);
  }
}
