package ph.kanta.reborn.core;

import java.util.Arrays;

/**
 * Monitor-only vocal effect presets for microphone pass-through.
 *
 * <p>Each reverb preset feeds one predelayed input through eight Schroeder comb filters and
 * three allpass diffusers, so the tail is a dense reverberation instead of the four discrete
 * echoes a bare delay bank produces. Slap echo and warm tape take dedicated paths. The
 * microphone tap used for pitch estimates and scoring is upstream of this processor and never
 * passes through it.
 *
 * <p>Framework-free, so the preset behaviour is exercised by the host-JVM {@code coreRegression}
 * task. {@link #process(float[])} waits on a lock, allocates nothing and is safe to call from the
 * audio thread while the main thread changes the preset.
 */
public final class VocalMonitorEffects {

  /**
   * The selectable monitor presets, in on-screen order.
   *
   * <p>{@code ROOM} and {@code HALL} are kept because existing monitor controls already select
   * them by enum constant; they share their delay settings with {@code SMALL_ROOM} and
   * {@code CONCERT_HALL} but keep their own names, so no two presets present the same label.
   *
   * <p>This constant {@code label()} is the only user-facing name. Callers must not render
   * {@code name()}: underscores are an implementation detail.
   */
  public enum Mode {
    DRY,
    SMALL_ROOM,
    ROOM,
    STUDIO,
    CONCERT_HALL,
    HALL,
    CATHEDRAL,
    PLATE_REVERB,
    SLAP_ECHO,
    WARM_TAPE;

    /** Human-readable name; underscores never reach the user. */
    public String label() {
      switch (this) {
        case SMALL_ROOM:
          return "Small Room";
        case ROOM:
          return "Room";
        case STUDIO:
          return "Studio";
        case CONCERT_HALL:
          return "Concert Hall";
        case HALL:
          return "Hall";
        case CATHEDRAL:
          return "Cathedral";
        case PLATE_REVERB:
          return "Plate Reverb";
        case SLAP_ECHO:
          return "Slap Echo";
        case WARM_TAPE:
          return "Warm Tape";
        default:
          return "Dry";
      }
    }
  }

  /** Freeverb comb tunings scaled from 44.1 kHz to the 48 kHz capture rate. */
  private static final int[] COMB_TUNING = {1214, 1293, 1390, 1476, 1548, 1623, 1695, 1760};
  /** Freeverb allpass tunings, same scaling. */
  private static final int[] ALLPASS_TUNING = {612, 665, 787};
  private static final float ALLPASS_FEEDBACK = 0.5f;
  /** 1/sqrt(8): keeps the summed combs at roughly unity RMS instead of +9 dB. */
  private static final float COMB_NORMALISATION = 0.35355339f;
  private static final int PREDELAY_FRAMES = 2880; // 60 ms at 48 kHz
  private static final int ECHO_FRAMES = 14400; // 300 ms at 48 kHz

  private final float[][] comb = new float[COMB_TUNING.length][];
  private final int[] combIndex = new int[COMB_TUNING.length];
  private final float[] combStore = new float[COMB_TUNING.length];
  private final float[][] allpass = new float[ALLPASS_TUNING.length][];
  private final int[] allpassIndex = new int[ALLPASS_TUNING.length];
  private final float[] preDelay = new float[PREDELAY_FRAMES];
  private final float[] echo = new float[ECHO_FRAMES];
  private int preDelayIndex, echoIndex;
  private float previousInput, previousOutput;

  private Mode mode = Mode.DRY;
  private float feedback = 0.70f, damping = 0.50f, wetMix = 0.16f, echoMix = 0.18f;
  private int preDelayFrames;
  private float drive = 1.0f;

  public VocalMonitorEffects() {
    for (int index = 0; index < COMB_TUNING.length; index++) comb[index] = new float[COMB_TUNING[index]];
    for (int index = 0; index < ALLPASS_TUNING.length; index++)
      allpass[index] = new float[ALLPASS_TUNING[index]];
    apply(Mode.DRY);
  }

  /** Selects a preset. Changing it clears every tail so no previous preset bleeds through. */
  public void mode(Mode value) {
    if (value == null) throw new IllegalArgumentException();
    if (value == mode) return;
    apply(value);
    clear();
  }

  /** The active preset. */
  public Mode mode() {
    return mode;
  }

  /** Clears every delay line and filter state. */
  public void reset() {
    clear();
  }

  private void apply(Mode value) {
    mode = value;
    switch (value) {
      case SMALL_ROOM:
      case ROOM:
        feedback = 0.70f; damping = 0.55f; wetMix = 0.20f; echoMix = 0.0f; preDelayFrames = 0; drive = 1.0f;
        break;
      case STUDIO:
        feedback = 0.78f; damping = 0.40f; wetMix = 0.24f; echoMix = 0.0f; preDelayFrames = 288; drive = 1.0f;
        break;
      case CONCERT_HALL:
      case HALL:
        feedback = 0.86f; damping = 0.32f; wetMix = 0.30f; echoMix = 0.0f; preDelayFrames = 960; drive = 1.0f;
        break;
      case CATHEDRAL:
        feedback = 0.92f; damping = 0.20f; wetMix = 0.36f; echoMix = 0.0f; preDelayFrames = 2160; drive = 1.0f;
        break;
      case PLATE_REVERB:
        feedback = 0.84f; damping = 0.10f; wetMix = 0.32f; echoMix = 0.0f; preDelayFrames = 96; drive = 1.0f;
        break;
      case SLAP_ECHO:
        feedback = 0.28f; damping = 0.50f; wetMix = 0.0f; echoMix = 0.34f; preDelayFrames = 0; drive = 1.0f;
        break;
      case WARM_TAPE:
        feedback = 0.80f; damping = 0.62f; wetMix = 0.28f; echoMix = 0.0f; preDelayFrames = 480; drive = 1.15f;
        break;
      case DRY:
      default:
        feedback = 0.0f; damping = 0.50f; wetMix = 0.0f; echoMix = 0.0f; preDelayFrames = 0; drive = 1.0f;
        break;
    }
  }

  private void clear() {
    for (float[] line : comb) Arrays.fill(line, 0f);
    for (float[] line : allpass) Arrays.fill(line, 0f);
    Arrays.fill(combIndex, 0);
    Arrays.fill(allpassIndex, 0);
    Arrays.fill(combStore, 0f);
    Arrays.fill(preDelay, 0f);
    Arrays.fill(echo, 0f);
    preDelayIndex = 0;
    echoIndex = 0;
    previousInput = 0f;
    previousOutput = 0f;
  }

  /** Processes one block in place. Callers must not share the array with another thread. */
  public void process(float[] samples) {
    if (samples == null) throw new IllegalArgumentException();
    synchronized (this) {
      for (int index = 0; index < samples.length; index++) {
        samples[index] = sample(samples[index]);
      }
    }
  }

  private float sample(float raw) {
    float dry = Float.isFinite(raw) ? Math.max(-1f, Math.min(1f, raw)) : 0f;
    if (mode == Mode.DRY) return Math.max(-0.9f, Math.min(0.9f, dry));

    float value;
    if (mode == Mode.SLAP_ECHO) {
      float delayed = echo[echoIndex];
      echo[echoIndex] = dry + delayed * feedback;
      echoIndex = (echoIndex + 1) % ECHO_FRAMES;
      value = dry * (1f - echoMix) + delayed * echoMix;
    } else {
      float delayed = preDelay[preDelayIndex];
      preDelay[preDelayIndex] = dry;
      preDelayIndex = (preDelayIndex + 1) % PREDELAY_FRAMES;
      int read = preDelayIndex - preDelayFrames;
      if (read < 0) read += PREDELAY_FRAMES;
      float feed = preDelayFrames == 0 ? dry : preDelay[read];

      float wet = 0f;
      for (int index = 0; index < comb.length; index++) {
        float out = comb[index][combIndex[index]];
        combStore[index] = out * (1f - damping) + combStore[index] * damping;
        comb[index][combIndex[index]] = feed + combStore[index] * feedback;
        combIndex[index] = (combIndex[index] + 1) % comb[index].length;
        wet += out;
      }
      wet *= COMB_NORMALISATION;
      for (int index = 0; index < allpass.length; index++) {
        float buffered = allpass[index][allpassIndex[index]];
        float out = buffered - wet;
        allpass[index][allpassIndex[index]] = wet + buffered * ALLPASS_FEEDBACK;
        allpassIndex[index] = (allpassIndex[index] + 1) % allpass[index].length;
        wet = out;
      }
      if (mode == Mode.WARM_TAPE) wet = (float) Math.tanh(wet * drive) * 0.9f;
      value = dry + wet * wetMix;
    }

    // One-pole DC blocker: removes the offset a resonant comb bank can accumulate.
    float blocked = value - previousInput + 0.995f * previousOutput;
    if (!Float.isFinite(blocked)) {
      clear();
      blocked = dry;
    }
    previousInput = value;
    previousOutput = blocked;
    return Math.max(-0.9f, Math.min(0.9f, blocked));
  }
}
