package ph.kanta.reborn.core;

import java.util.Arrays;

/** Worker-confined 48-kHz dry-PCM analysis. No Android, capture, output or storage owner. */
public final class SpectrumAnalyzer {
  public static final int FRAME_SIZE = 4096, BAND_COUNT = 16, SAMPLE_RATE = 48000;
  private final double[] window = new double[FRAME_SIZE];
  private final double[] real = new double[FRAME_SIZE], imaginary = new double[FRAME_SIZE];
  private final int[] edges = new int[BAND_COUNT + 1];
  private final double normalization;

  public SpectrumAnalyzer() {
    double windowPower = 0;
    for (int i = 0; i < FRAME_SIZE; i++) {
      window[i] = .5 * (1 - Math.cos(2 * Math.PI * i / (FRAME_SIZE - 1)));
      windowPower += window[i] * window[i];
    }
    normalization = 2.0 / (FRAME_SIZE * windowPower);
    // Half-open bin intervals: each positive-frequency bin belongs to at most one band.
    for (int band = 0; band <= BAND_COUNT; band++) {
      edges[band] = (int) Math.ceil(80 * Math.pow(100, band / (double) BAND_COUNT)
          * FRAME_SIZE / SAMPLE_RATE);
    }
  }

  /** Immutable power snapshot; energy is mean-square PCM, not calibrated sound pressure. */
  public static final class Spectrum {
    private final double[] power;

    private Spectrum(double[] power) { this.power = power; }

    public double power(int band) { return power[band]; }
  }

  public double lowFrequency(int band) {
    if (band < 0 || band >= BAND_COUNT) throw new IndexOutOfBoundsException();
    return edges[band] * (double) SAMPLE_RATE / FRAME_SIZE;
  }

  public double highFrequency(int band) {
    if (band < 0 || band >= BAND_COUNT) throw new IndexOutOfBoundsException();
    return edges[band + 1] * (double) SAMPLE_RATE / FRAME_SIZE;
  }

  /** Invalid/nonfinite frames are unavailable; samples are never modified or retained. */
  public Spectrum analyze(float[] samples) {
    if (samples == null || samples.length != FRAME_SIZE) return null;
    // Validate the entire input before copying any PCM into the reusable workspace.
    for (float sample : samples)
      if (!Float.isFinite(sample) || Math.abs(sample) > 1) return null;
    try {
      for (int i = 0; i < FRAME_SIZE; i++) real[i] = samples[i] * window[i];
      for (int i = 1, j = 0; i < FRAME_SIZE; i++) {
        int bit = FRAME_SIZE >> 1;
        for (; (j & bit) != 0; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) {
          double swap = real[i]; real[i] = real[j]; real[j] = swap;
        }
      }
      for (int length = 2; length <= FRAME_SIZE; length <<= 1) {
        double angle = -2 * Math.PI / length;
        double stepReal = Math.cos(angle), stepImaginary = Math.sin(angle);
        for (int start = 0; start < FRAME_SIZE; start += length) {
          double wr = 1, wi = 0;
          for (int offset = 0; offset < length / 2; offset++) {
            int left = start + offset, right = left + length / 2;
            double tr = wr * real[right] - wi * imaginary[right];
            double ti = wr * imaginary[right] + wi * real[right];
            real[right] = real[left] - tr;
            imaginary[right] = imaginary[left] - ti;
            real[left] += tr;
            imaginary[left] += ti;
            double nextReal = wr * stepReal - wi * stepImaginary;
            wi = wr * stepImaginary + wi * stepReal;
            wr = nextReal;
          }
        }
      }
      double[] power = new double[BAND_COUNT];
      for (int band = 0; band < BAND_COUNT; band++) {
        for (int bin = edges[band]; bin < edges[band + 1]; bin++)
          power[band] += real[bin] * real[bin] + imaginary[bin] * imaginary[bin];
        power[band] *= normalization;
      }
      return new Spectrum(power);
    } finally {
      Arrays.fill(real, 0);
      Arrays.fill(imaginary, 0);
    }
  }
}
// Android validation trigger
