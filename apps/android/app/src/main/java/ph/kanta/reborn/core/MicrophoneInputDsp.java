package ph.kanta.reborn.core;

/** Capture-worker-owned PCM16 conditioning. Gain precedes dry analysis; gate is monitor-only. */
public final class MicrophoneInputDsp {
  public static final int GATE_OFF = -80;

  public static int gainDb(int value) { return Math.max(-12, Math.min(18, value)); }
  public static int gateDb(int value) {
    return value == GATE_OFF ? GATE_OFF : Math.max(-60, Math.min(-20, value));
  }

  private final double gainStep, attack, release;
  private final int holdSamples;
  private double gain = 1, detectorPower, gate = 1;
  private int hold;
  private double rms;
  private boolean clipped;

  public MicrophoneInputDsp(int sampleRate) {
    if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate");
    gainStep = 1 - Math.exp(-1.0 / (sampleRate * .010));
    attack = 1 - Math.exp(-1.0 / (sampleRate * .005));
    release = 1 - Math.exp(-1.0 / (sampleRate * .080));
    holdSamples = Math.max(1, sampleRate / 20);
  }

  /** No allocations; unity is bit-exact. Detect ADC rails and gain clipping before saturation. */
  public void applyGain(short[] pcm, int count, int decibels) {
    double target = Math.pow(10, gainDb(decibels) / 20.0);
    double energy = 0;
    clipped = false;
    for (int i = 0; i < count; i++) {
      gain += gainStep * (target - gain);
      double value = pcm[i] * gain;
      if (pcm[i] == Short.MIN_VALUE || pcm[i] == Short.MAX_VALUE
          || value <= Short.MIN_VALUE || value >= Short.MAX_VALUE) clipped = true;
      pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value)));
      double normalized = pcm[i] / 32768.0;
      energy += normalized * normalized;
    }
    rms = count == 0 ? 0 : Math.sqrt(energy / count);
  }

  /** Apply only AFTER dry pitch/SPICE/frame copies. Off is an exact bypass at the neutral default. */
  public void applyMonitorGate(short[] pcm, int count, int thresholdDb) {
    int threshold = gateDb(thresholdDb);
    double thresholdPower = Math.pow(10, threshold / 10.0);
    for (int i = 0; i < count; i++) {
      double value = pcm[i] / 32768.0;
      double power = value * value;
      detectorPower += (power > detectorPower ? attack : release) * (power - detectorPower);
      if (detectorPower >= thresholdPower) hold = holdSamples;
      else if (hold > 0) hold--;
      double target = threshold == GATE_OFF || hold > 0 ? 1 : 0;
      gate += (target > gate ? attack : release) * (target - gate);
      pcm[i] = (short) Math.round(pcm[i] * gate);
    }
  }

  public double rms() { return rms; }
  public boolean clipped() { return clipped; }
}
