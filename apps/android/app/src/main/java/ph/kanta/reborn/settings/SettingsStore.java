package ph.kanta.reborn.settings;

import android.content.Context;
import android.content.SharedPreferences;
import ph.kanta.reborn.core.PlaybackSettingsBackup;

/**
 * Grouped settings store for KANTA Karaoke.
 *
 * <p>Uses the rebrand prefs file: {@code kanta_settings} (was {@code songhub_settings}).
 * Groups: audio, lyric, mic, score, behavior, bgv.
 */
public final class SettingsStore {

  private static final String PREFS_FILE = "kanta_settings";

  // Group: audio
  // Group: pitch (new group for H7)
  public static final String KEY_PITCH_TARGET_HZ = "pitch.targetHz";
  public static final String KEY_PITCH_TARGET_NAME = "pitch.targetName";
  public static final String KEY_ASSISTANT_SENSITIVITY = "pitch.assistantSensitivity";

  // Group: audio (existing)
  public static final String KEY_MASTER_VOLUME = "audio.masterVolume";
  public static final String KEY_TEMPO = "audio.tempo";
  public static final String KEY_PITCH = "audio.pitch";
  public static final String KEY_MELODY_VOLUME = "audio.melodyVolume";
  public static final String KEY_GUIDE_VOLUME = "audio.guideVolume";
  public static final String KEY_SOUNDFONT = "audio.soundfont";
  public static final String KEY_EQ_PRESET = "audio.eq.preset";
  public static final String KEY_EQ_BAND_PREFIX = "audio.eq.band.";

  // Group: lyric
  public static final String KEY_LYRIC_FONT = "lyric.font";
  public static final String KEY_AUTO_LYRICS = "lyric.autoFetch";
  public static final String KEY_LYRIC_FONT_SIZE = "lyric.fontSize";
  public static final String KEY_LYRIC_LINE_SPACING = "lyric.lineSpacing";
  public static final String KEY_LYRIC_ACTIVE_COLOR = "lyric.activeColor";
  public static final String KEY_LYRIC_UPCOMING_OPACITY = "lyric.upcomingOpacity";
  public static final String KEY_LYRIC_SHOW_UPCOMING = "lyric.showUpcoming";
  public static final String KEY_LYRIC_ALL_CAPS = "lyric.allCaps";
  public static final String KEY_LYRIC_BOLD = "lyric.bold";
  public static final String KEY_LYRIC_OUTLINE = "lyric.outline";
  public static final String KEY_LYRIC_ALIGNMENT = "lyric.alignment";
  public static final String KEY_LYRIC_LINES = "lyric.lines";
  public static final String KEY_LYRIC_SHOW_TITLE = "lyric.showTitle";
  public static final String KEY_LYRIC_SHOW_WATERMARK = "lyric.showWatermark";
  public static final String KEY_LYRIC_SHOW_COUNTDOWN = "lyric.showCountdown";
  public static final String KEY_LYRIC_SYNC_OFFSET_MS = "lyric.syncOffsetMs";
  public static final String KEY_LYRIC_SYNC_OFFSET_OVERRIDES = "lyric.syncOffsetOverrides";
  public static final String KEY_INTERLUDE_TEXT_COLOR = "interlude.textColor";
  public static final String KEY_INTERLUDE_ANIMATION = "interlude.animation";
  public static final String KEY_INTERLUDE_IMAGE_SCALE = "interlude.imageScale";
  public static final String KEY_INTERLUDE_IMAGE_PLACEMENT = "interlude.imagePlacement";
  public static final String KEY_INTERLUDE_SHOW_UP_NEXT = "interlude.showUpNext";
  public static final String KEY_INTERLUDE_SHOW_VISUALIZER = "interlude.showVisualizer";
  public static final String KEY_INTERLUDE_SHOW_WATERMARK = "interlude.showWatermark";

  // Group: mic
  public static final String KEY_MIC_MONITOR_LEVEL = "mic.monitorLevel";
  public static final String KEY_MIC_GATE_THRESHOLD = "mic.gateThreshold";
  public static final String KEY_MIC_REVERB = "mic.reverb";
  public static final String KEY_MIC_COMPRESSOR = "mic.compressor";

  // Group: score
  public static final String KEY_SCORE_DIFFICULTY = "score.difficulty";
  public static final String KEY_SCORE_MODE = "score.mode";
  public static final String KEY_SCORE_SHOW_BREAKDOWN = "score.showBreakdown";

  // Group: behavior
  public static final String KEY_AUTO_RESERVE = "behavior.autoReserve";
  public static final String KEY_COUNTDOWN_LENGTH = "behavior.countdownLength";
  public static final String KEY_SHOW_INTRO_CARD = "behavior.showIntroCard";
  public static final String KEY_QUEUE_VISIBLE = "behavior.queueVisible";

  // Group: bgv
  public static final String KEY_BGV_SOURCE_PRIORITY = "bgv.sourcePriority";
  public static final String KEY_BGV_LOOP = "bgv.loop";
  public static final String KEY_BGV_SCRIM_OPACITY = "bgv.scrimOpacity";
  public static final String KEY_BGV_AUTO_SHUFFLE = "bgv.autoShuffle";
  public static final String KEY_BGP_AUTO_SHUFFLE = "bgp.autoShuffle";
  public static final String KEY_BGV_DEFAULT_SELECTED = "bgv.defaultSelected";

  private final SharedPreferences prefs;

  public SettingsStore(Context context) {
    this.prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
  }

  /** Replaces only the portable playback defaults in a single preference edit. */
  public void restorePlaybackDefaults(PlaybackSettingsBackup backup) {
    prefs.edit()
        .putFloat(KEY_MASTER_VOLUME, backup.volume)
        .putFloat(KEY_TEMPO, backup.tempo)
        .putInt(KEY_PITCH, backup.pitch)
        .apply();
  }

  // Audio
  public float getMasterVolume() { return prefs.getFloat(KEY_MASTER_VOLUME, 1.0f); }
  public void setMasterVolume(float v) { prefs.edit().putFloat(KEY_MASTER_VOLUME, v).apply(); }

  public float getTempo() { return prefs.getFloat(KEY_TEMPO, 1.0f); }
  public void setTempo(float v) { prefs.edit().putFloat(KEY_TEMPO, v).apply(); }

  public int getPitch() { return prefs.getInt(KEY_PITCH, 0); }
  public void setPitch(int v) { prefs.edit().putInt(KEY_PITCH, v).apply(); }
  // Per-song key/tempo overrides (remembered per song id).
  private static final String KEY_SONG_PITCH_PREFIX = "song.pitch.";
  private static final String KEY_SONG_TEMPO_PREFIX = "song.tempo.";

  public boolean hasSongOverride(String songId) {
    return prefs.contains(KEY_SONG_PITCH_PREFIX + songId)
        && prefs.contains(KEY_SONG_TEMPO_PREFIX + songId);
  }

  public int getSongPitch(String songId, int fallback) {
    return prefs.getInt(KEY_SONG_PITCH_PREFIX + songId, fallback);
  }

  public float getSongTempo(String songId, float fallback) {
    return prefs.getFloat(KEY_SONG_TEMPO_PREFIX + songId, fallback);
  }

  public void setSongOverride(String songId, int pitch, float tempo) {
    prefs.edit()
        .putInt(KEY_SONG_PITCH_PREFIX + songId, pitch)
        .putFloat(KEY_SONG_TEMPO_PREFIX + songId, tempo)
        .apply();
  }

  public void clearSongOverride(String songId) {
    prefs.edit()
        .remove(KEY_SONG_PITCH_PREFIX + songId)
        .remove(KEY_SONG_TEMPO_PREFIX + songId)
        .apply();
  }


  // Pitch (song-aware target + assistant sensitivity)
  public float getPitchTargetHz() { return prefs.getFloat(KEY_PITCH_TARGET_HZ, 440.0f); }
  public void setPitchTargetHz(float v) { prefs.edit().putFloat(KEY_PITCH_TARGET_HZ, v).apply(); }

  public String getPitchTargetName() { return prefs.getString(KEY_PITCH_TARGET_NAME, "A4"); }
  public void setPitchTargetName(String v) { prefs.edit().putString(KEY_PITCH_TARGET_NAME, v).apply(); }

  public float getAssistantSensitivity() { return prefs.getFloat(KEY_ASSISTANT_SENSITIVITY, 1.0f); }
  public void setAssistantSensitivity(float v) { prefs.edit().putFloat(KEY_ASSISTANT_SENSITIVITY, v).apply(); }

  public float getMelodyVolume() { return prefs.getFloat(KEY_MELODY_VOLUME, 0.8f); }
  public void setMelodyVolume(float v) { prefs.edit().putFloat(KEY_MELODY_VOLUME, v).apply(); }

  public float getGuideVolume() { return prefs.getFloat(KEY_GUIDE_VOLUME, 0.6f); }
  public void setGuideVolume(float v) { prefs.edit().putFloat(KEY_GUIDE_VOLUME, v).apply(); }

  public String getSoundFont() { return prefs.getString(KEY_SOUNDFONT, ""); }
  public void setSoundFont(String v) { prefs.edit().putString(KEY_SOUNDFONT, v).apply(); }

  public String getEqPreset() { return prefs.getString(KEY_EQ_PRESET, "off"); }
  public void setEqPreset(String v) { prefs.edit().putString(KEY_EQ_PRESET, v).apply(); }

  public short getEqBand(int centerHz, short fallback) {
    return (short) prefs.getInt(KEY_EQ_BAND_PREFIX + centerHz, fallback);
  }
  public void setEqBand(int centerHz, short hundredthsDb) {
    prefs.edit().putInt(KEY_EQ_BAND_PREFIX + centerHz, hundredthsDb).apply();
  }

  // Lyric
  public boolean getAutoLyrics() { return prefs.getBoolean(KEY_AUTO_LYRICS, false); }
  public void setAutoLyrics(boolean v) { prefs.edit().putBoolean(KEY_AUTO_LYRICS, v).apply(); }

  public String getLyricFont() { return prefs.getString(KEY_LYRIC_FONT, ""); }
  public void setLyricFont(String uri) { prefs.edit().putString(KEY_LYRIC_FONT, uri == null ? "" : uri).apply(); }

  /** Interlude = the standby message the Stage shows while waiting for the song to start. */
  public String getInterludeText() { return prefs.getString("interlude.text", ""); }
  public void setInterludeText(String v) {
    prefs.edit().putString("interlude.text", v == null ? "" : v).apply();
  }
  public int getInterludeTextSize() { return prefs.getInt("interlude.textSize", 0); }
  public void setInterludeTextSize(int v) { prefs.edit().putInt("interlude.textSize", v).apply(); }
  public String getInterludeMode() { return prefs.getString("interlude.mode", "text"); }
  public void setInterludeMode(String v) {
    prefs.edit().putString("interlude.mode", v == null ? "text" : v).apply();
  }
  public String getInterludeImage() { return prefs.getString("interlude.image", ""); }
  public void setInterludeImage(String v) {
    prefs.edit().putString("interlude.image", v == null ? "" : v).apply();
  }

  public float getLyricFontSize() { return prefs.getFloat(KEY_LYRIC_FONT_SIZE, 1.0f); }
  public void setLyricFontSize(float v) { prefs.edit().putFloat(KEY_LYRIC_FONT_SIZE, v).apply(); }

  public float getLyricLineSpacing() { return prefs.getFloat(KEY_LYRIC_LINE_SPACING, 1.0f); }
  public void setLyricLineSpacing(float v) { prefs.edit().putFloat(KEY_LYRIC_LINE_SPACING, v).apply(); }

  public int getLyricActiveColor() { return prefs.getInt(KEY_LYRIC_ACTIVE_COLOR, 0xFFFFFFFF); }
  public void setLyricActiveColor(int v) { prefs.edit().putInt(KEY_LYRIC_ACTIVE_COLOR, v).apply(); }

  public float getLyricUpcomingOpacity() { return prefs.getFloat(KEY_LYRIC_UPCOMING_OPACITY, 0.8f); }
  public void setLyricUpcomingOpacity(float v) { prefs.edit().putFloat(KEY_LYRIC_UPCOMING_OPACITY, v).apply(); }

  public boolean getLyricShowUpcoming() { return prefs.getBoolean(KEY_LYRIC_SHOW_UPCOMING, true); }
  public void setLyricShowUpcoming(boolean v) { prefs.edit().putBoolean(KEY_LYRIC_SHOW_UPCOMING, v).apply(); }

  public boolean getLyricAllCaps() { return prefs.getBoolean(KEY_LYRIC_ALL_CAPS, false); }
  public void setLyricAllCaps(boolean v) { prefs.edit().putBoolean(KEY_LYRIC_ALL_CAPS, v).apply(); }

  public boolean getLyricBold() { return prefs.getBoolean(KEY_LYRIC_BOLD, true); }
  public void setLyricBold(boolean v) { prefs.edit().putBoolean(KEY_LYRIC_BOLD, v).apply(); }

  /** 0 = none, 1 = thin, 2 = thick drop shadow/outline. */
  public int getLyricOutline() { return prefs.getInt(KEY_LYRIC_OUTLINE, 1); }
  public void setLyricOutline(int v) {
    prefs.edit().putInt(KEY_LYRIC_OUTLINE, Math.max(0, Math.min(2, v))).apply();
  }

  /** center | bottom | left. */
  public String getLyricAlignment() { return prefs.getString(KEY_LYRIC_ALIGNMENT, "center"); }
  public void setLyricAlignment(String v) {
    prefs.edit().putString(KEY_LYRIC_ALIGNMENT, v == null ? "center" : v).apply();
  }

  /** two = active + upcoming cue, multi = the active cue alone. */
  public String getLyricLines() { return prefs.getString(KEY_LYRIC_LINES, "two"); }
  public void setLyricLines(String v) {
    prefs.edit().putString(KEY_LYRIC_LINES, v == null ? "two" : v).apply();
  }

  public boolean getLyricShowTitle() { return prefs.getBoolean(KEY_LYRIC_SHOW_TITLE, true); }
  public void setLyricShowTitle(boolean v) { prefs.edit().putBoolean(KEY_LYRIC_SHOW_TITLE, v).apply(); }

  /** Global lyric sync offset in milliseconds; clamped to +-2000 by the session. */
  public int getLyricSyncOffsetMs() { return prefs.getInt(KEY_LYRIC_SYNC_OFFSET_MS, 0); }
  public void setLyricSyncOffsetMs(int ms) { prefs.edit().putInt(KEY_LYRIC_SYNC_OFFSET_MS, ms).apply(); }

  /** Per-song override keyed by stable song id; null when the song has none. */
  public Integer getLyricSyncOffsetOverride(String songId) {
    if (songId == null || songId.isEmpty()) return null;
    String raw = prefs.getString(KEY_LYRIC_SYNC_OFFSET_OVERRIDES, "");
    if (raw == null || raw.isEmpty()) return null;
    try {
      org.json.JSONObject map = new org.json.JSONObject(raw);
      return map.isNull(songId) ? null : map.optInt(songId);
    } catch (Exception unavailable) {
      return null;
    }
  }
  public void setLyricSyncOffsetOverride(String songId, int ms) {
    if (songId == null || songId.isEmpty()) return;
    String raw = prefs.getString(KEY_LYRIC_SYNC_OFFSET_OVERRIDES, "");
    org.json.JSONObject map;
    try {
      map = new org.json.JSONObject(raw == null || raw.isEmpty() ? "{}" : raw);
    } catch (Exception unavailable) {
      map = new org.json.JSONObject();
    }
    try {
      if (ms == 0) map.remove(songId); else map.put(songId, ms);
      prefs.edit().putString(KEY_LYRIC_SYNC_OFFSET_OVERRIDES, map.toString()).apply();
    } catch (Exception unavailable) {
    }
  }

  public boolean getLyricShowWatermark() { return prefs.getBoolean(KEY_LYRIC_SHOW_WATERMARK, true); }
  public void setLyricShowWatermark(boolean v) { prefs.edit().putBoolean(KEY_LYRIC_SHOW_WATERMARK, v).apply(); }

  public boolean getLyricShowCountdown() { return prefs.getBoolean(KEY_LYRIC_SHOW_COUNTDOWN, false); }
  public void setLyricShowCountdown(boolean v) { prefs.edit().putBoolean(KEY_LYRIC_SHOW_COUNTDOWN, v).apply(); }

  public int getInterludeTextColor() { return prefs.getInt(KEY_INTERLUDE_TEXT_COLOR, 0xFFFFFFFF); }
  public void setInterludeTextColor(int v) { prefs.edit().putInt(KEY_INTERLUDE_TEXT_COLOR, v).apply(); }

  /** static | pulse | marquee. */
  public String getInterludeAnimation() { return prefs.getString(KEY_INTERLUDE_ANIMATION, "static"); }
  public void setInterludeAnimation(String v) {
    prefs.edit().putString(KEY_INTERLUDE_ANIMATION, v == null ? "static" : v).apply();
  }

  public float getInterludeImageScale() { return prefs.getFloat(KEY_INTERLUDE_IMAGE_SCALE, 1.0f); }
  public void setInterludeImageScale(float v) {
    prefs.edit().putFloat(KEY_INTERLUDE_IMAGE_SCALE, Math.max(0.2f, Math.min(1.6f, v))).apply();
  }

  /** above = above the message, background = dimmed full-bleed. */
  public String getInterludeImagePlacement() {
    return prefs.getString(KEY_INTERLUDE_IMAGE_PLACEMENT, "above");
  }
  public void setInterludeImagePlacement(String v) {
    prefs.edit().putString(KEY_INTERLUDE_IMAGE_PLACEMENT, v == null ? "above" : v).apply();
  }

  public boolean getInterludeShowUpNext() { return prefs.getBoolean(KEY_INTERLUDE_SHOW_UP_NEXT, true); }
  public void setInterludeShowUpNext(boolean v) { prefs.edit().putBoolean(KEY_INTERLUDE_SHOW_UP_NEXT, v).apply(); }

  public boolean getInterludeShowVisualizer() {
    return prefs.getBoolean(KEY_INTERLUDE_SHOW_VISUALIZER, false);
  }
  public void setInterludeShowVisualizer(boolean v) {
    prefs.edit().putBoolean(KEY_INTERLUDE_SHOW_VISUALIZER, v).apply();
  }

  public boolean getInterludeShowWatermark() {
    return prefs.getBoolean(KEY_INTERLUDE_SHOW_WATERMARK, true);
  }
  public void setInterludeShowWatermark(boolean v) {
    prefs.edit().putBoolean(KEY_INTERLUDE_SHOW_WATERMARK, v).apply();
  }

  // Mic
  public float getMicMonitorLevel() { return prefs.getFloat(KEY_MIC_MONITOR_LEVEL, 0.1f); }
  public void setMicMonitorLevel(float v) { prefs.edit().putFloat(KEY_MIC_MONITOR_LEVEL, v).apply(); }

  public float getMicGateThreshold() { return prefs.getFloat(KEY_MIC_GATE_THRESHOLD, 0.01f); }
  public void setMicGateThreshold(float v) { prefs.edit().putFloat(KEY_MIC_GATE_THRESHOLD, v).apply(); }

  public boolean getMicReverb() { return prefs.getBoolean(KEY_MIC_REVERB, false); }
  public void setMicReverb(boolean v) { prefs.edit().putBoolean(KEY_MIC_REVERB, v).apply(); }

  public boolean getMicCompressor() { return prefs.getBoolean(KEY_MIC_COMPRESSOR, false); }
  public void setMicCompressor(boolean v) { prefs.edit().putBoolean(KEY_MIC_COMPRESSOR, v).apply(); }

  // Score
  public int getScoreDifficulty() { return prefs.getInt(KEY_SCORE_DIFFICULTY, 2); }
  public void setScoreDifficulty(int v) { prefs.edit().putInt(KEY_SCORE_DIFFICULTY, v).apply(); }

  public int getScoreMode() { return prefs.getInt(KEY_SCORE_MODE, 1); }
  public void setScoreMode(int v) { prefs.edit().putInt(KEY_SCORE_MODE, v).apply(); }

  public boolean getScoreShowBreakdown() { return prefs.getBoolean(KEY_SCORE_SHOW_BREAKDOWN, true); }
  public void setScoreShowBreakdown(boolean v) { prefs.edit().putBoolean(KEY_SCORE_SHOW_BREAKDOWN, v).apply(); }

  // Behavior
  public boolean getAutoReserve() { return prefs.getBoolean(KEY_AUTO_RESERVE, false); }
  public void setAutoReserve(boolean v) { prefs.edit().putBoolean(KEY_AUTO_RESERVE, v).apply(); }

  public int getCountdownLength() { return prefs.getInt(KEY_COUNTDOWN_LENGTH, 4); }
  public void setCountdownLength(int v) { prefs.edit().putInt(KEY_COUNTDOWN_LENGTH, v).apply(); }

  public boolean getShowIntroCard() { return prefs.getBoolean(KEY_SHOW_INTRO_CARD, true); }
  public void setShowIntroCard(boolean v) { prefs.edit().putBoolean(KEY_SHOW_INTRO_CARD, v).apply(); }

  public boolean getQueueVisible() { return prefs.getBoolean(KEY_QUEUE_VISIBLE, true); }
  public void setQueueVisible(boolean v) { prefs.edit().putBoolean(KEY_QUEUE_VISIBLE, v).apply(); }

  // BGV
  public int getBgvSourcePriority() { return prefs.getInt(KEY_BGV_SOURCE_PRIORITY, 0); }
  public void setBgvSourcePriority(int v) { prefs.edit().putInt(KEY_BGV_SOURCE_PRIORITY, v).apply(); }

  public boolean getBgvLoop() { return prefs.getBoolean(KEY_BGV_LOOP, true); }
  public void setBgvLoop(boolean v) { prefs.edit().putBoolean(KEY_BGV_LOOP, v).apply(); }

  public float getBgvScrimOpacity() { return prefs.getFloat(KEY_BGV_SCRIM_OPACITY, 0.8f); }

  public boolean getBgvAutoShuffle() { return prefs.getBoolean(KEY_BGV_AUTO_SHUFFLE, false); }
  public void setBgvAutoShuffle(boolean v) { prefs.edit().putBoolean(KEY_BGV_AUTO_SHUFFLE, v).apply(); }

  public boolean getBgpAutoShuffle() { return prefs.getBoolean(KEY_BGP_AUTO_SHUFFLE, false); }
  public void setBgpAutoShuffle(boolean v) { prefs.edit().putBoolean(KEY_BGP_AUTO_SHUFFLE, v).apply(); }

  public boolean getBgvDefaultSelected() { return prefs.getBoolean(KEY_BGV_DEFAULT_SELECTED, false); }
  public void setBgvDefaultSelected(boolean v) { prefs.edit().putBoolean(KEY_BGV_DEFAULT_SELECTED, v).apply(); }
  public void setBgvScrimOpacity(float v) { prefs.edit().putFloat(KEY_BGV_SCRIM_OPACITY, v).apply(); }

  /** Clears every lyric-stage-style preference back to its default. */
  public void clearLyricStyle() {
    prefs.edit().remove(KEY_LYRIC_ALL_CAPS).remove(KEY_LYRIC_BOLD).remove(KEY_LYRIC_OUTLINE)
        .remove(KEY_LYRIC_ACTIVE_COLOR).remove(KEY_LYRIC_UPCOMING_OPACITY)
        .remove(KEY_LYRIC_SHOW_UPCOMING).remove(KEY_LYRIC_ALIGNMENT).remove(KEY_LYRIC_LINES)
        .remove(KEY_LYRIC_SHOW_TITLE).remove(KEY_LYRIC_SHOW_WATERMARK)
        .remove(KEY_LYRIC_SHOW_COUNTDOWN).apply();
  }

  /** Clears every interlude-style preference back to its default. */
  public void clearInterludeStyle() {
    prefs.edit().remove(KEY_INTERLUDE_TEXT_COLOR).remove(KEY_INTERLUDE_ANIMATION)
        .remove(KEY_INTERLUDE_IMAGE_SCALE).remove(KEY_INTERLUDE_IMAGE_PLACEMENT)
        .remove(KEY_INTERLUDE_SHOW_UP_NEXT).remove(KEY_INTERLUDE_SHOW_VISUALIZER)
        .remove(KEY_INTERLUDE_SHOW_WATERMARK).apply();
  }

  /** Resets all settings to defaults. */
  public void resetAll() {
    prefs.edit().clear().apply();
  }
}
