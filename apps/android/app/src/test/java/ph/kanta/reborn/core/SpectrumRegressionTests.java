package ph.kanta.reborn.core;

import java.util.Arrays;

/** Framework-free signal regressions, called by coreRegression. */
public final class SpectrumRegressionTests {
  private static int checks;
  private static void check(boolean valid, String message) {
    checks++;
    if (!valid) throw new AssertionError(message);
  }

  private static float[] tone(double frequency, double amplitude, double phase) {
    float[] frame = new float[SpectrumAnalyzer.FRAME_SIZE];
    for (int i = 0; i < frame.length; i++)
      frame[i] = (float) (amplitude * Math.sin(2 * Math.PI * frequency * i
          / SpectrumAnalyzer.SAMPLE_RATE + phase));
    return frame;
  }

  private static double total(SpectrumAnalyzer.Spectrum spectrum) {
    double total = 0;
    for (int band = 0; band < SpectrumAnalyzer.BAND_COUNT; band++) total += spectrum.power(band);
    return total;
  }

  public static int run() {
    checks = 0;
    SpectrumAnalyzer analyzer = new SpectrumAnalyzer();
    for (int band = 0; band < SpectrumAnalyzer.BAND_COUNT; band++) {
      check(analyzer.highFrequency(band) > analyzer.lowFrequency(band), "Positive band width");
      if (band > 0) check(analyzer.lowFrequency(band) == analyzer.highFrequency(band - 1),
          "Half-open bands do not overlap or skip bins");
    }
    check(Math.abs(analyzer.lowFrequency(0) - 80) < 12, "First edge within one bin");
    check(Math.abs(analyzer.highFrequency(15) - 8000) < 12, "Last edge within one bin");
    check(total(analyzer.analyze(new float[SpectrumAnalyzer.FRAME_SIZE])) == 0, "Silent PCM");
    for (double hz : new double[] {94, 220, 440, 880, 1800, 4000, 7000}) {
      float[] frame = tone(hz, .5, 0);
      float[] original = frame.clone();
      SpectrumAnalyzer.Spectrum spectrum = analyzer.analyze(frame);
      check(Arrays.equals(original, frame), "Analysis must not mutate dry input");
      int peak = 0;
      for (int band = 0; band < SpectrumAnalyzer.BAND_COUNT; band++) {
        check(Double.isFinite(spectrum.power(band)) && spectrum.power(band) >= 0,
            "Finite nonnegative band energy");
        if (spectrum.power(band) > spectrum.power(peak)) peak = band;
      }
      check(hz >= analyzer.lowFrequency(peak) - 12 && hz < analyzer.highFrequency(peak) + 12,
          "Peak contains tone " + hz);
      check(Math.abs(total(spectrum) - .125) < .004, "Window-normalized sine power");
      SpectrumAnalyzer.Spectrum phase = analyzer.analyze(tone(hz, .5, Math.PI / 3));
      SpectrumAnalyzer.Spectrum quieter = analyzer.analyze(tone(hz, .25, 0));
      for (int band = 0; band < SpectrumAnalyzer.BAND_COUNT; band++) {
        check(Math.abs(phase.power(band) - spectrum.power(band)) < .0001,
            "Band power independent of tone phase within finite-window tolerance");
        check(Math.abs(quieter.power(band) * 4 - spectrum.power(band)) < 1e-8,
            "Doubling amplitude quadruples power");
      }
      analyzer.analyze(new float[SpectrumAnalyzer.FRAME_SIZE]);
      check(total(spectrum) > .12, "Published snapshot survives workspace reuse");
    }
    float[] mixed = tone(220, .3, 0), high = tone(1800, .3, 0);
    for (int i = 0; i < mixed.length; i++) mixed[i] += high[i];
    SpectrumAnalyzer.Spectrum mix = analyzer.analyze(mixed);
    for (double hz : new double[] {220, 1800}) {
      int band = 0;
      while (analyzer.highFrequency(band) <= hz) band++;
      check(mix.power(band) > .035, "Both mixed tones contribute energy");
    }
    check(analyzer.analyze(null) == null, "Null frame rejected");
    check(analyzer.analyze(new float[100]) == null, "Wrong frame length rejected");
    for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1.1f, 1.1f}) {
      float[] frame = tone(440, .5, 0); frame[100] = invalid;
      check(analyzer.analyze(frame) == null, "Invalid sample rejected");
      check(total(analyzer.analyze(new float[SpectrumAnalyzer.FRAME_SIZE])) == 0,
          "Rejected input cannot leak into next analysis");
    }
    return checks;
  }
}
