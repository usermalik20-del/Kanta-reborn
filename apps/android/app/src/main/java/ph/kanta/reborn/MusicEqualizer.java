package ph.kanta.reborn;

import android.media.audiofx.Equalizer;
import ph.kanta.reborn.settings.SettingsStore;

/**
 * Single owner of the media equalizer for KANTA's music sessions (SoundFont AudioTrack and
 * file playback). It never touches the party audio or microphone monitor paths, and a failed
 * effect must never block playback.
 */
public final class MusicEqualizer {
  public static final String PRESET_OFF = "off";
  public static final String PRESET_VOCAL = "vocal-clarity";
  public static final String PRESET_WARM = "warm";
  public static final String PRESET_CUSTOM = "custom";

  private final SettingsStore settings;
  private Equalizer equalizer;
  private int sessionId;
  /** Consecutive live-session attach refusals; resets on any successful attach. */
  private int attachRefusals;

  public MusicEqualizer(SettingsStore settings) {
    this.settings = settings;
  }

  /** True while attached to a live USAGE_MEDIA session. */
  public boolean isAttached() {
    return equalizer != null;
  }

  /**
   * Attaches to the given media session and applies the stored configuration.
   *
   * @return false when this device does not expose a media equalizer for the session
   */
  public boolean attach(int audioSessionId) {
    detach();
    if (audioSessionId <= 0) return false;
    try {
      // 0 is AudioEffect.TYPE_NULL (not present in this SDK's stub); it selects a null effect
      // type so the equalizer binds directly to the given media session.
      Equalizer candidate = new Equalizer(0, audioSessionId);
      candidate.setEnabled(true);
      equalizer = candidate;
      sessionId = audioSessionId;
      attachRefusals = 0;
    } catch (RuntimeException | LinkageError unavailable) {
      ++attachRefusals;
      return false;
    }
    applyStored();
    return true;
  }

  /**
   * True once a live media session has been refused repeatedly, i.e. this device does not expose
   * a media equalizer. A session that is not active yet is refused once or twice, so the
   * threshold leaves room for the startup race before the UI reports unavailability.
   */
  public boolean attachRefused() {
    return attachRefusals >= 4;
  }

  public void detach() {
    if (equalizer != null) {
      try {
        equalizer.release();
      } catch (RuntimeException ignored) {
      }
      equalizer = null;
    }
    sessionId = 0;
  }

  public int session() {
    return sessionId;
  }

  /** Centre frequency (Hz) of every band on this device, in band order. */
  public int[] bandHz() {
    if (equalizer == null) return new int[0];
    int count = equalizer.getNumberOfBands();
    int[] hz = new int[count];
    for (int i = 0; i < count; i++) hz[i] = equalizer.getCenterFreq((short) i);
    return hz;
  }

  public int bandForHz(int centerHz) {
    if (equalizer == null) return -1;
    int count = equalizer.getNumberOfBands();
    for (int i = 0; i < count; i++) {
      if (equalizer.getCenterFreq((short) i) == centerHz) return i;
    }
    return -1;
  }

  /** {@code [min, max]} in hundredths of a dB, shared by all bands on this device. */
  public short[] bandRange() {
    if (equalizer == null) return new short[] {0, 0};
    try {
      return equalizer.getBandLevelRange();
    } catch (RuntimeException unavailable) {
      return new short[] {0, 0};
    }
  }

  /** Current band gains in hundredths of a dB, in band order. */
  public short[] gains() {
    if (equalizer == null) return new short[0];
    int count = equalizer.getNumberOfBands();
    short[] values = new short[count];
    for (int i = 0; i < count; i++) {
      try {
        values[i] = equalizer.getBandLevel((short) i);
      } catch (RuntimeException unavailable) {
        values[i] = 0;
      }
    }
    return values;
  }

  public void setBandLevel(int band, short hundredthsDb) {
    if (equalizer == null) return;
    int count = equalizer.getNumberOfBands();
    if (band < 0 || band >= count) return;
    try {
      short[] range = equalizer.getBandLevelRange();
      short clamped = (short) Math.max(range[0], Math.min(range[1], hundredthsDb));
      equalizer.setBandLevel((short) band, clamped);
      settings.setEqBand(equalizer.getCenterFreq((short) band), clamped);
    } catch (RuntimeException ignored) {
    }
    settings.setEqPreset(PRESET_CUSTOM);
  }

  /**
   * Applies a named preset across all bands. The choice is always stored — even before a music
   * session exists — so a preset picked while waiting is applied as soon as one attaches.
   * {@link #PRESET_CUSTOM} is a no-op.
   */
  public void applyPreset(String name) {
    if (PRESET_CUSTOM.equals(name)) return;
    settings.setEqPreset(name);
    if (equalizer == null) return;
    int count = equalizer.getNumberOfBands();
    short[] range = bandRange();
    for (int i = 0; i < count; i++) {
      int hz = equalizer.getCenterFreq((short) i);
      short target =
          (short) Math.max(range[0], Math.min(range[1], presetGain(name, hz)));
      try {
        equalizer.setBandLevel((short) i, target);
      } catch (RuntimeException ignored) {
      }
      settings.setEqBand(hz, target);
    }
  }

  public String preset() {
    return settings.getEqPreset();
  }

  /** Deterministic preset curve in hundredths of a dB; 0 leaves a band untouched. */
  static short presetGain(String name, int centerHz) {
    if (PRESET_VOCAL.equals(name)) {
      if (centerHz >= 200 && centerHz <= 500) return -250;
      if (centerHz >= 2000 && centerHz <= 5000) return 250;
      return 0;
    }
    if (PRESET_WARM.equals(name)) {
      if (centerHz <= 250) return 200;
      if (centerHz >= 8000) return -100;
      return 0;
    }
    return 0;
  }

  /** Re-applies whatever is stored: the named preset curve or per-band custom gains. */
  private void applyStored() {
    if (equalizer == null) return;
    String preset = settings.getEqPreset();
    if (PRESET_CUSTOM.equals(preset)) {
      int count = equalizer.getNumberOfBands();
      short[] range = bandRange();
      for (int i = 0; i < count; i++) {
        int hz = equalizer.getCenterFreq((short) i);
        short stored = settings.getEqBand(hz, (short) 0);
        short clamped = (short) Math.max(range[0], Math.min(range[1], stored));
        try {
          equalizer.setBandLevel((short) i, clamped);
        } catch (RuntimeException ignored) {
        }
      }
    } else {
      applyPreset(PRESET_OFF.equals(preset) ? PRESET_OFF : preset);
    }
  }
}
