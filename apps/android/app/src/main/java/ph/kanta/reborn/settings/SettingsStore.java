package ph.kanta.reborn.settings;

import android.content.Context;
import android.content.SharedPreferences;

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

  // Group: lyric
  public static final String KEY_LYRIC_FONT_SIZE = "lyric.fontSize";
  public static final String KEY_LYRIC_LINE_SPACING = "lyric.lineSpacing";
  public static final String KEY_LYRIC_ACTIVE_COLOR = "lyric.activeColor";
  public static final String KEY_LYRIC_UPCOMING_OPACITY = "lyric.upcomingOpacity";
  public static final String KEY_LYRIC_SHOW_UPCOMING = "lyric.showUpcoming";

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

  private final SharedPreferences prefs;

  public SettingsStore(Context context) {
    this.prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
  }

  // Audio
  public float getMasterVolume() { return prefs.getFloat(KEY_MASTER_VOLUME, 1.0f); }
  public void setMasterVolume(float v) { prefs.edit().putFloat(KEY_MASTER_VOLUME, v).apply(); }

  public float getTempo() { return prefs.getFloat(KEY_TEMPO, 1.0f); }
  public void setTempo(float v) { prefs.edit().putFloat(KEY_TEMPO, v).apply(); }

  public int getPitch() { return prefs.getInt(KEY_PITCH, 0); }
  public void setPitch(int v) { prefs.edit().putInt(KEY_PITCH, v).apply(); }

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

  // Lyric
  public float getLyricFontSize() { return prefs.getFloat(KEY_LYRIC_FONT_SIZE, 1.0f); }
  public void setLyricFontSize(float v) { prefs.edit().putFloat(KEY_LYRIC_FONT_SIZE, v).apply(); }

  public float getLyricLineSpacing() { return prefs.getFloat(KEY_LYRIC_LINE_SPACING, 1.0f); }
  public void setLyricLineSpacing(float v) { prefs.edit().putFloat(KEY_LYRIC_LINE_SPACING, v).apply(); }

  public int getLyricActiveColor() { return prefs.getInt(KEY_LYRIC_ACTIVE_COLOR, 0xFFFF3D8B); }
  public void setLyricActiveColor(int v) { prefs.edit().putInt(KEY_LYRIC_ACTIVE_COLOR, v).apply(); }

  public float getLyricUpcomingOpacity() { return prefs.getFloat(KEY_LYRIC_UPCOMING_OPACITY, 0.4f); }
  public void setLyricUpcomingOpacity(float v) { prefs.edit().putFloat(KEY_LYRIC_UPCOMING_OPACITY, v).apply(); }

  public boolean getLyricShowUpcoming() { return prefs.getBoolean(KEY_LYRIC_SHOW_UPCOMING, true); }
  public void setLyricShowUpcoming(boolean v) { prefs.edit().putBoolean(KEY_LYRIC_SHOW_UPCOMING, v).apply(); }

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

  public int getCountdownLength() { return prefs.getInt(KEY_COUNTDOWN_LENGTH, 3); }
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
  public void setBgvScrimOpacity(float v) { prefs.edit().putFloat(KEY_BGV_SCRIM_OPACITY, v).apply(); }

  /** Resets all settings to defaults. */
  public void resetAll() {
    prefs.edit().clear().apply();
  }
}
