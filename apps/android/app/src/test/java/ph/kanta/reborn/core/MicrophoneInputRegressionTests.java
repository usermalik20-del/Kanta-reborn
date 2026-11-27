package ph.kanta.reborn.core;

import java.util.Arrays;

/** Hardware-independent executable DSP and route-policy regressions. */
public final class MicrophoneInputRegressionTests {
  private static int checks;
  private static void check(boolean condition, String label) {
    checks++;
    if (!condition) throw new AssertionError(label);
  }
  private static short[] constant(int value, int count) {
    short[] data = new short[count];
    Arrays.fill(data, (short) value);
    return data;
  }
  private static void settle(MicrophoneInputDsp dsp, int gain, int gate, int value, int blocks) {
    for (int i = 0; i < blocks; i++) {
      short[] pcm = constant(value, 480);
      dsp.applyGain(pcm, pcm.length, gain);
      dsp.applyMonitorGate(pcm, pcm.length, gate);
    }
  }
  public static int run() {
    checks = 0;
    MicrophoneInputDsp dsp = new MicrophoneInputDsp(48000);
    short[] all = new short[65536];
    for (int i = 0; i < all.length; i++) all[i] = (short) (i - 32768);
    short[] original = all.clone();
    dsp.applyGain(all, all.length, 0);
    dsp.applyMonitorGate(all, all.length, MicrophoneInputDsp.GATE_OFF);
    check(Arrays.equals(all, original), "neutral defaults are bit-exact for every PCM16 value");
    check(dsp.clipped(), "ADC rails detected even with unity gain");
    check(MicrophoneInputDsp.gainDb(Integer.MIN_VALUE) == -12, "gain floor");
    check(MicrophoneInputDsp.gainDb(Integer.MAX_VALUE) == 18, "gain ceiling");
    check(MicrophoneInputDsp.gateDb(-80) == -80, "gate off sentinel");
    check(MicrophoneInputDsp.gateDb(-100) == -60, "gate floor");
    check(MicrophoneInputDsp.gateDb(100) == -20, "gate ceiling");
    settle(dsp, 18, -80, 1000, 100);
    short[] pcm = constant(1000, 480);
    dsp.applyGain(pcm, pcm.length, 18);
    check(Math.abs(pcm[479] - 7943) <= 1, "+18dB amplitude");
    check(!dsp.clipped(), "unclipped boosted input");
    check(Math.abs(dsp.rms() - 7943.0 / 32768) < .0001, "gained pre-gate meter");
    pcm = new short[] {10000, -10000};
    dsp.applyGain(pcm, pcm.length, 18);
    check(pcm[0] == 32767 && pcm[1] == -32768 && dsp.clipped(), "saturates without wrapping");
    settle(dsp, -12, -80, 10000, 100);
    pcm = constant(10000, 480);
    dsp.applyGain(pcm, pcm.length, -12);
    check(Math.abs(pcm[479] - 2512) <= 1, "minus12dB amplitude");
    pcm = new short[] {32767, -32768};
    dsp.applyGain(pcm, pcm.length, -12);
    check(dsp.clipped(), "ADC clipping cannot be hidden by attenuation");
    dsp = new MicrophoneInputDsp(48000);
    pcm = constant(1000, 480);
    dsp.applyGain(pcm, pcm.length, 18);
    check(pcm[0] < 1020 && pcm[479] > pcm[0], "gain changes ramp rather than step");

    dsp = new MicrophoneInputDsp(48000);
    settle(dsp, 0, -40, 20, 120);
    pcm = constant(20, 480);
    dsp.applyGain(pcm, pcm.length, 0);
    short[] dry = pcm.clone();
    dsp.applyMonitorGate(pcm, pcm.length, -40);
    check(pcm[479] == 0, "quiet background closes gate");
    check(dry[479] == 20 && dsp.rms() > 0, "dry copy and meter remain upstream of gate");
    pcm = constant(12000, 480);
    dsp.applyMonitorGate(pcm, pcm.length, -40);
    check(pcm[0] < 100 && pcm[479] > 9000, "gate attack is smooth and opens promptly");
    int largestStep = 0;
    for (int i = 1; i < pcm.length; i++) largestStep = Math.max(largestStep, Math.abs(pcm[i] - pcm[i - 1]));
    check(largestStep < 100, "no hard gate edge on constant signal");
    settle(dsp, 0, -40, 12000, 30);
    pcm = constant(20, 480);
    dsp.applyMonitorGate(pcm, pcm.length, -40);
    check(pcm[479] > 15, "hold and release avoid instant closure");
    settle(dsp, 0, -40, 20, 150);
    settle(dsp, 0, -80, 1000, 20);
    pcm = constant(1000, 480);
    dsp.applyMonitorGate(pcm, pcm.length, -80);
    check(pcm[479] == 1000, "disabling gate restores unity smoothly");
    dsp.applyGain(new short[0], 0, 0);
    check(dsp.rms() == 0 && !dsp.clipped(), "empty block resets meter without nonfinite result");

    InputRouteGuard route = new InputRouteGuard(7, 100);
    check(route.observe(0, 100) == InputRouteGuard.State.WAITING, "null startup route withheld");
    check(route.observe(8, 200) == InputRouteGuard.State.WAITING, "preference is not actual routing");
    check(route.observe(7, 300) == InputRouteGuard.State.VERIFIED, "exact requested route verified");
    check(route.observe(7, 400) == InputRouteGuard.State.VERIFIED, "stable route retained");
    check(route.observe(8, 401) == InputRouteGuard.State.REJECTED, "route fallback retires activation");
    check(route.observe(7, 402) == InputRouteGuard.State.REJECTED, "lost route cannot self-reactivate");
    route = new InputRouteGuard(7, 100);
    check(route.observe(7, 1601) == InputRouteGuard.State.REJECTED, "late success rejected after deadline");
    route = new InputRouteGuard(0, 0);
    check(route.observe(9, 1) == InputRouteGuard.State.VERIFIED, "automatic input also verifies actual route");
    check(route.observe(0, 2) == InputRouteGuard.State.REJECTED, "null live route stops automatic input");
    return checks;
  }
  public static void main(String[] args) {
    System.out.println("Microphone input regressions: " + run() + " checks passed");
  }
}
