package ph.kanta.reborn.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The eight first-party party effects.
 *
 * <p>Every pad is synthesized here, so KANTA distributes no third-party sound recording and the
 * APK carries no audio asset. The eight are deliberately distinct: an earlier revision emitted
 * the same noise burst for Applause, Cheering and Drum Roll, because the shared branch ignored
 * the pad index.
 *
 * <p>Framework-free and deterministic: the same index always produces the same WAV bytes, which
 * is what the host-JVM {@code coreRegression} task checks.
 */
public final class PartySounds {

  public static final List<String> TITLES =
      Collections.unmodifiableList(
          Arrays.asList(
              "Applause",
              "Cheering",
              "Drum Roll",
              "Air Horn",
              "Laughter",
              "Ta-Da!",
              "Ding",
              "Sad Trombone"));

  /** Effect slots the score reveal reuses by name, so the two stay in step. */
  public static final int APPLAUSE = 0, CHEERING = 1, DRUM_ROLL = 2, AIR_HORN = 3, LAUGHTER = 4, TA_DA = 5, DING = 6, SAD_TROMBONE = 7;

  private static final int RATE = 22050;
  private static final int FRAMES = RATE * 2;
  /** Deterministic noise source: the same LCG every call, never java.util.Random. */
  private static final int NOISE_SEED = 0x4B414E54;

  private static final class Noise {
    private int seed = NOISE_SEED;

    /** Uniform white noise in -1..1. */
    float next() {
      seed = seed * 1664525 + 1013904223;
      return (seed >>> 16) / 32768.0f - 1f;
    }
  }

  /** One-pole low-pass: the cheap way to colour a noise burst. */
  private static final class LowPass {
    private final float coefficient;
    private float state;

    LowPass(float cutoffHz) {
      double x = Math.exp(-2 * Math.PI * cutoffHz / RATE);
      coefficient = (float) x;
    }

    float run(float input) {
      state = input * (1f - coefficient) + state * coefficient;
      return state;
    }
  }

  /** One-pole high-pass, built from a low-pass so the two stay consistent. */
  private static final class HighPass {
    private final LowPass low;

    HighPass(float cutoffHz) {
      low = new LowPass(cutoffHz);
    }

    float run(float input) {
      return input - low.run(input);
    }
  }

  /** Two-pole resonant band-pass, used for crowd and snare colour. */
  private static final class BandPass {
    private final float f, q1, q2;
    private float d1, d2;

    BandPass(float centreHz, float resonance) {
      f = 2f * (float) Math.sin(Math.PI * centreHz / RATE);
      q1 = 1f / resonance;
      q2 = q1;
    }

    float run(float input) {
      float out = f * (input - d2) + 0.6f * d1;
      d2 = d1;
      d1 = out;
      return out * q1;
    }
  }

  /** Returns a complete 44.1 kHz 16-bit mono RIFF WAV for one pad. */
  public static byte[] wave(int sound) {
    if (sound < 0 || sound >= TITLES.size()) throw new IllegalArgumentException("Unknown pad");
    float[] samples = render(sound);
    normalise(samples, 0.72f);
    return riff(samples);
  }

  private static float[] render(int sound) {
    switch (sound) {
      case APPLAUSE:
        return applause();
      case CHEERING:
        return cheering();
      case DRUM_ROLL:
        return drumRoll();
      case AIR_HORN:
        return airHorn();
      case LAUGHTER:
        return laughter();
      case TA_DA:
        return taDa();
      case DING:
        return ding();
      default:
        return sadTrombone();
    }
  }

  /** A dense clap field: many short transient bursts over a swelling crowd bed. */
  private static float[] applause() {
    float[] out = new float[FRAMES];
    Noise noise = new Noise();
    BandPass clap = new BandPass(1400f, 0.55f);
    LowPass bed = new LowPass(2600f);
    HighPass bedCut = new HighPass(220f);
    int nextClap = 0;
    float bedGain;
    for (int i = 0; i < FRAMES; i++) {
      float time = i / (float) RATE;
      if (i >= nextClap) {
        // Clap density rises then falls: the room fills at the start and thins out.
        float density = 42f + 260f * (float) Math.exp(-time * 1.1);
        nextClap = i + Math.max(1, (int) (RATE / density));
        clap.run(noise.next());
      }
      float clapHit = clap.run(noise.next());
      bedGain = (1f - (float) Math.exp(-time * 3f)) * (float) Math.exp(-time * 0.55) * 0.5f;
      out[i] = clapHit * 0.55f + bedCut.run(bed.run(noise.next())) * bedGain;
    }
    return out;
  }

  /** A crowd roar: slow swell, resonant formant band and two whoops on top. */
  private static float[] cheering() {
    float[] out = new float[FRAMES];
    Noise noise = new Noise();
    BandPass voice = new BandPass(820f, 0.72f);
    BandPass shimmer = new BandPass(2400f, 0.5f);
    float[] whoopCents = {0f, 0.55f, 1.15f};
    float[] whoopPitch = {520f, 660f, 430f};
    for (int i = 0; i < FRAMES; i++) {
      float time = i / (float) RATE;
      float swell = (1f - (float) Math.exp(-time * 4f)) * (float) Math.exp(-time * 0.5) * 0.85f;
      float body = voice.run(noise.next()) * swell;
      float air = shimmer.run(noise.next()) * swell * 0.25f;
      float whoops = 0f;
      for (int w = 0; w < whoopPitch.length; w++) {
        float start = whoopCents[w];
        if (time < start || time > start + 0.45f) continue;
        float local = (time - start) / 0.45f;
        double rise = 1.0 + 0.6 * local;
        double cycle = 2 * Math.PI * whoopPitch[w] * rise * (time - start);
        float envelope = (float) Math.sin(Math.PI * local);
        whoops += (float) Math.sin(cycle) * envelope * 0.22f;
      }
      out[i] = body + air + whoops;
    }
    return out;
  }

  /** A snare roll that accelerates, ending on a crash. */
  private static float[] drumRoll() {
    float[] out = new float[FRAMES];
    Noise noise = new Noise();
    BandPass snare = new BandPass(2600f, 0.42f);
    LowPass crashCut = new LowPass(7000f);
    int rollFrames = (int) (FRAMES * 0.9f);
    int nextHit = 0;
    int hits = 0;
    float roll = 0f, crash = 0f;
    for (int i = 0; i < FRAMES; i++) {
      float time = i / (float) RATE;
      if (i < rollFrames) {
        if (i >= nextHit) {
          // 11 hits/s accelerating to 30 hits/s, which is what reads as a roll.
          float rate = 11f + 19f * (i / (float) rollFrames);
          nextHit = i + Math.max(2, (int) (RATE / rate));
          roll = 1f;
          hits++;
        }
        roll *= 0.982f;
      } else {
        roll = 0f;
        if (crash == 0f) crash = 1f;
        crash *= 0.99965f;
      }
      float body = snare.run(noise.next()) * roll * 0.9f;
      float shimmer = crashCut.run(noise.next()) * crash * 0.55f;
      float skin = (float) Math.sin(2 * Math.PI * 190 * time) * roll * 0.18f;
      out[i] = body + shimmer + skin;
      if (hits == 0) out[i] *= 0.0f;
    }
    return out;
  }

  /** A stadium horn: three detuned saws, a pitch bend and soft clipping. */
  private static float[] airHorn() {
    float[] out = new float[FRAMES];
    float fundamental = 233f;
    float[] detune = {0.985f, 1f, 1.014f};
    double[] phase = new double[detune.length];
    for (int i = 0; i < FRAMES; i++) {
      float time = i / (float) RATE;
      float bend = 1f + 0.035f * (1f - (float) Math.exp(-time * 9f));
      float value = 0f;
      for (int voice = 0; voice < detune.length; voice++) {
        double frequency = fundamental * detune[voice] * bend;
        phase[voice] += frequency / RATE;
        double cycle = phase[voice] - Math.floor(phase[voice]);
        // A short harmonic series is a cheap band-limited saw.
        double saw = 0;
        for (int harmonic = 1; harmonic <= 9; harmonic++) {
          saw += Math.sin(2 * Math.PI * cycle * harmonic) / harmonic;
        }
        value += (float) saw * (2.0 / Math.PI) / detune.length;
      }
      float envelope;
      if (time < 0.05f) envelope = time / 0.05f;
      else if (time < 1.35f) envelope = 1f;
      else envelope = Math.max(0f, 1f - (time - 1.35f) / 0.55f);
      out[i] = (float) Math.tanh(value * 1.6f) * envelope;
    }
    return out;
  }

  /** A chuckle sequence: seven falling pulses, not a gated buzzer. */
  private static float[] laughter() {
    float[] out = new float[FRAMES];
    Noise noise = new Noise();
    BandPass breath = new BandPass(1800f, 0.6f);
    int pulses = 7;
    float pitch = 196f;
    for (int i = 0; i < FRAMES; i++) {
      float time = i / (float) RATE;
      float value = 0f;
      for (int pulse = 0; pulse < pulses; pulse++) {
        float start = 0.05f + pulse * 0.22f;
        float length = 0.15f;
        if (time < start || time > start + length) continue;
        float local = (time - start) / length;
        float envelope = (float) Math.sin(Math.PI * local);
        float voice = pitch * (1f - 0.10f * pulse);
        double cycle = 2 * Math.PI * voice * (time - start);
        // Two formants over a glottal-ish pulse is enough to read as a human "ha".
        float format1 = (float) Math.sin(cycle);
        float format2 = (float) Math.sin(cycle * 3) * 0.35f;
        float format3 = (float) Math.sin(cycle * 5.4) * 0.15f;
        value += (format1 + format2 + format3) * envelope * 0.42f;
        value += breath.run(noise.next()) * envelope * 0.12f;
      }
      out[i] = value;
    }
    return out;
  }

  /** A four-note major arpeggio with a cymbal shimmer: the classic "ta-da". */
  private static float[] taDa() {
    float[] out = new float[FRAMES];
    Noise noise = new Noise();
    LowPass shine = new LowPass(6500f);
    float[] notes = {523.25f, 659.26f, 783.99f, 1046.5f};
    float[] starts = {0f, 0.16f, 0.32f, 0.50f};
    for (int i = 0; i < FRAMES; i++) {
      float time = i / (float) RATE;
      float value = 0f;
      for (int note = 0; note < notes.length; note++) {
        float local = time - starts[note];
        if (local < 0f) continue;
        float envelope = Math.min(1f, local / 0.012f) * (float) Math.exp(-local * (note == 3 ? 1.6 : 3.4));
        double cycle = 2 * Math.PI * notes[note] * local;
        float brass =
            (float) Math.sin(cycle) * 0.6f
                + (float) Math.sin(cycle * 2) * 0.22f
                + (float) Math.sin(cycle * 3) * 0.12f
                + (float) Math.sin(cycle * 4) * 0.06f;
        value += brass * envelope * 0.5f;
      }
      float hit = time < 0.50f ? 0f : (float) Math.exp(-(time - 0.50f) * 2.2);
      out[i] = value + shine.run(noise.next()) * hit * 0.28f;
    }
    return out;
  }

  /** A two-operator FM bell with inharmonic partials and a long decay. */
  private static float[] ding() {
    float[] out = new float[FRAMES];
    for (int i = 0; i < FRAMES; i++) {
      float time = i / (float) RATE;
      double carrier = 2 * Math.PI * 1046.5 * time;
      float envelope = (float) Math.exp(-time * 2.4);
      float modulator = (float) Math.exp(-time * 5.5);
      float fm = (float) Math.sin(carrier + 2.6 * modulator);
      float partial =
          (float) Math.sin(2 * Math.PI * 2637.0 * time) * 0.18f
              + (float) Math.sin(2 * Math.PI * 3718.0 * time) * 0.08f;
      out[i] = (fm * 0.72f + partial) * envelope;
    }
    return out;
  }

  /** Four descending "wah" slides with vibrato on the last note. */
  private static float[] sadTrombone() {
    float[] out = new float[FRAMES];
    float[] notes = {233.08f, 220f, 207.65f, 196f};
    float[] starts = {0f, 0.42f, 0.84f, 1.26f};
    for (int i = 0; i < FRAMES; i++) {
      float time = i / (float) RATE;
      float value = 0f;
      for (int note = 0; note < notes.length; note++) {
        float local = time - starts[note];
        if (local < 0f || local > 0.42f) continue;
        float glide = 1f + (local < 0.06f ? 0.05f * (1f - local / 0.06f) : 0f);
        float vibrato = note == 3 ? 1f + 0.012f * (float) Math.sin(2 * Math.PI * 5.4f * local) : 1f;
        double frequency = notes[note] * glide * vibrato;
        float envelope = Math.min(1f, local / 0.02f) * (float) Math.exp(-local * 3.2);
        // A moving resonant formant is the "wah" of the trombone.
        float formant = 1f + 0.55f * (float) Math.sin(2 * Math.PI * 2.4f * local);
        double cycle = 2 * Math.PI * frequency * local;
        float brass =
            (float) Math.sin(cycle)
                + (float) Math.sin(cycle * 2) * formant * 0.35f
                + (float) Math.sin(cycle * 3) * 0.18f;
        value += (float) Math.tanh(brass * 0.8f) * envelope * 0.42f;
      }
      out[i] = value;
    }
    return out;
  }

  /** Scales the buffer to a target peak, so all eight pads are equally loud. */
  private static void normalise(float[] samples, float peak) {
    float max = 0f;
    for (float sample : samples) {
      float magnitude = Math.abs(sample);
      if (magnitude > max) max = magnitude;
    }
    if (max <= 0f) return;
    float gain = peak / max;
    for (int index = 0; index < samples.length; index++) {
      float value = samples[index] * gain;
      samples[index] = Float.isFinite(value) ? Math.max(-0.99f, Math.min(0.99f, value)) : 0f;
    }
  }

  private static byte[] riff(float[] samples) {
    ByteBuffer bytes = ByteBuffer.allocate(44 + samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
    bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + samples.length * 2);
    bytes.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1);
    bytes.putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
    bytes.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples.length * 2);
    for (float sample : samples) {
      bytes.putShort((short) Math.round(sample * 32767f));
    }
    return bytes.array();
  }

  private PartySounds() {}
}
