package ph.kanta.reborn.core;

import java.util.Arrays;

/**
 * Regression coverage for the SPICE pitch DSP.
 *
 * <p>No framework and no JUnit: this runs on a plain server JVM as part of coreRegression,
 * which is the only place the pure core can be exercised without an emulator.
 *
 * <p>The golden ramp vector was produced independently from the same filter design
 * (33-tap Hann-windowed sinc, 4.5 kHz cutoff, decimated by three). Its input is a ramp of
 * exact integers, so the reference and this implementation see identical samples and the
 * expected output is asserted bit-for-bit.
 */
public final class SpicePitchRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  /** The 21 output samples the reference produced for the 64-sample integer ramp. */
  private static final float[] GOLDEN_RAMP = {
    -0.05641041323542595f,
    -0.08983799070119858f,
    -0.08456899225711823f,
    -0.0698183998465538f,
    -0.06073519587516785f,
    -0.0518798828125f,
    -0.042724609375f,
    -0.0335693359375f,
    -0.0244140625f,
    -0.0152587890625f,
    -0.006103515625f,
    0.0030517578125f,
    0.01220703125f,
    0.0213623046875f,
    0.030517578125f,
    0.0396728515625f,
    0.048828125f,
    0.057692430913448334f,
    0.06677637994289398f,
    0.08136057108640671f,
    0.08676328510046005f
  };

  private static short[] sine(double frequency, double amplitude, int samples) {
    short[] pcm = new short[samples];
    for (int i = 0; i < samples; i++) {
      double value = amplitude * Math.sin(2 * Math.PI * frequency * i / SpicePitch.CAPTURE_RATE);
      pcm[i] = (short) Math.round(value * 32768.0);
    }
    return pcm;
  }

  private static float[] resampleAll(short[] pcm) {
    SpicePitch.Resampler resampler = new SpicePitch.Resampler();
    float[] out = new float[pcm.length / SpicePitch.DECIMATION + 16];
    int written = resampler.accept(pcm, pcm.length, out);
    written += resampler.flush(out, written);
    return Arrays.copyOf(out, written);
  }

  private static float rms(float[] samples) {
    double sum = 0;
    for (float sample : samples) sum += (double) sample * sample;
    return (float) Math.sqrt(sum / samples.length);
  }

  private static void resamplerGoldenVector() {
    short[] ramp = new short[64];
    for (int i = 0; i < ramp.length; i++) ramp[i] = (short) (i * 100 - 3200);

    SpicePitch.Resampler resampler = new SpicePitch.Resampler();
    float[] out = new float[64];
    int written = resampler.accept(ramp, ramp.length, out);
    written += resampler.flush(out, written);

    check(written == GOLDEN_RAMP.length, "Resampler output count: " + written);
    for (int i = 0; i < GOLDEN_RAMP.length; i++) {
      check(
          out[i] == GOLDEN_RAMP[i],
          "Resampler golden sample " + i + ": " + out[i] + " != " + GOLDEN_RAMP[i]);
    }
  }

  private static void resamplerKeepsVoiceBand() {
    float[] tone = resampleAll(sine(440.0, 0.5, SpicePitch.CAPTURE_RATE));
    check(Math.abs(rms(tone) - 0.35416514f) < 0.001f, "440 Hz level after resampling");
    float[] low = resampleAll(sine(60.0, 0.25, SpicePitch.CAPTURE_RATE));
    check(Math.abs(rms(low) - 0.17678294f) < 0.001f, "60 Hz level after resampling");
  }

  private static void resamplerRejectsAliasing() {
    // 20 kHz is above the 8 kHz Nyquist of the 16 kHz model input. Without the low-pass it
    // would fold down and the model would read a fundamental that is not in the audio.
    float[] alias = resampleAll(sine(20000.0, 0.5, SpicePitch.CAPTURE_RATE));
    check(rms(alias) < 0.001f, "20 kHz alias leaked into the model band");
  }

  private static void calibrationChecks() {
    check(
        SpicePitch.semitones(0f) == 24.3700008392334f,
        "Calibration offset: " + SpicePitch.semitones(0f));
    check(
        SpicePitch.semitones(1f) == 86.87999725341797f,
        "Calibration slope: " + SpicePitch.semitones(1f));
    // The calibration itself is pure arithmetic and is asserted exactly. Converting semitones
    // to Hz goes through Math.pow, which is not required to be correctly rounded across
    // implementations, so those are checked to a tolerance rather than bit-for-bit.
    check(
        Math.abs(SpicePitch.hz(24.3700008392334f) - 33.40965f) < 0.001f,
        "Lowest calibrated pitch: " + SpicePitch.hz(24.3700008392334f));
    check(
        Math.abs(SpicePitch.hz(86.87999725341797f) - 1235.9115f) < 0.01f,
        "Highest calibrated pitch: " + SpicePitch.hz(86.87999725341797f));
    check(SpicePitch.confidence(0.25f) == 0.75f, "Confidence is the complement of uncertainty");
    // A4 is 69 semitones above MIDI note 0.
    check(Math.abs(SpicePitch.hz(69f) - 440f) < 0.001f, "A4 is 440 Hz");
  }

  private static void selectionChecks() {
    // Frames 0 and 4 are the padded ends and are dropped; frame 2 is unconfident.
    float[] pitches = {0.1f, 0.5f, 0.9f, 0.5f, 0.1f};
    float[] uncertainties = {0.9f, 0.1f, 0.9f, 0.1f, 0.9f};
    float selected = SpicePitch.selectPitch(pitches, uncertainties, 0.5f);
    float expected = SpicePitch.hz(SpicePitch.semitones(0.5f));
    check(Math.abs(selected - expected) < 0.001f, "Confident frames are used: " + selected);

    check(SpicePitch.selectPitch(pitches, uncertainties, 0.95f) == 0f, "No confident frame");
    check(SpicePitch.selectPitch(new float[0], new float[0], 0.5f) == 0f, "Empty window");
    check(SpicePitch.selectPitch(null, null, 0.5f) == 0f, "Missing window");
  }

  public static int run() {
    resamplerGoldenVector();
    resamplerKeepsVoiceBand();
    resamplerRejectsAliasing();
    calibrationChecks();
    selectionChecks();
    return checks;
  }
}
