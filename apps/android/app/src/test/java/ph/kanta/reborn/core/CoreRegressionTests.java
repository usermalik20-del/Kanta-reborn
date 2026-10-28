package ph.kanta.reborn.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import ph.kanta.reborn.core.legacy.MkmdDecoderRegressionTests;

/** No framework/JUnit dependency: run on a server JVM before allocating cloud CI. */
public final class CoreRegressionTests {
  private static int checks = 0;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static Song song() {
    return new Song("one", "Morning Light", "KANTA Originals", "001", "morning-light.mid", true);
  }

  private static MidiLyrics.Document midiTrack(byte[] track) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    out.writeInt(0x4d546864);
    out.writeInt(6);
    out.writeShort(0);
    out.writeShort(1);
    out.writeShort(480);
    out.writeInt(0x4d54726b);
    out.writeInt(track.length);
    out.write(track);
    return MidiLyrics.decode(bytes.toByteArray());
  }

  private static float[] tone(double frequency, double amplitude) {
    float[] samples = new float[DryPitchDetector.FRAME_SIZE];
    for (int i = 0; i < samples.length; i++)
      samples[i] = (float) (amplitude * Math.sin(2 * Math.PI * frequency * i / 48000));
    return samples;
  }

  private static void microphonePitchChecks() {
    DryPitchDetector detector = new DryPitchDetector();
    for (double hz : new double[] {72, 82.41, 110, 220, 440, 523.25, 880, 990}) {
      DryPitchDetector.Pitch pitch = detector.detect(tone(hz, .2), 48000);
      check(
          pitch != null && Math.abs(1200 * Math.log(pitch.frequencyHz / hz) / Math.log(2)) < 8,
          "Dry pitch frequency: " + hz);
      check(
          pitch.confidence > .9 && Math.abs(pitch.rms - .2 / Math.sqrt(2)) < .01,
          "Dry pitch confidence/energy: " + hz);
    }
    for (double hz : new double[] {40, 53, 1200, 1801.3, 2000, 4999})
      check(
          detector.detect(tone(hz, .2), 48000) == null,
          "Out-of-range fundamental accepted as vocal pitch: " + hz);
    check(detector.detect(new float[4096], 48000) == null, "Silent pitch accepted");
    check(detector.detect(tone(440, .001), 48000) == null, "Subthreshold input accepted");
    float[] invalid = tone(440, .2);
    invalid[100] = Float.NaN;
    check(detector.detect(invalid, 48000) == null, "Nonfinite input accepted");
    invalid[100] = Float.POSITIVE_INFINITY;
    check(detector.detect(invalid, 48000) == null, "Infinite input accepted");
    check(detector.detect(null, 48000) == null, "Null pitch input accepted");
    check(detector.detect(new float[100], 48000) == null, "Partial frame accepted");
    check(detector.detect(tone(440, .2), 0) == null, "Invalid sample rate accepted");
    float[] noise = new float[4096];
    Random random = new Random(71);
    for (int i = 0; i < noise.length; i++) noise[i] = random.nextFloat() * .4f - .2f;
    check(detector.detect(noise, 48000) == null, "Aperiodic noise accepted as pitch");
    Arrays.fill(noise, .5f);
    check(detector.detect(noise, 48000) == null, "DC input accepted as pitch");
    float[] harmonic = tone(220, .2);
    for (int i = 0; i < harmonic.length; i++)
      harmonic[i] += (float) (.1 * Math.sin(2 * Math.PI * 440 * i / 48000));
    DryPitchDetector.Pitch pitch = detector.detect(harmonic, 48000);
    check(
        pitch != null && Math.abs(pitch.frequencyHz - 220) < 2,
        "Harmonic-rich input lost fundamental");
  }

  private static void monitorChecks() {
    MonitorRouteGuard guard = new MonitorRouteGuard(7, 100);
    check(
        guard.verify(new int[0], 101) == MonitorRouteGuard.Decision.WAIT,
        "Unknown route was audible");
    check(
        guard.verify(new int[] {7}, 200) == MonitorRouteGuard.Decision.ALLOW,
        "Selected route rejected");
    check(
        guard.verify(new int[0], 201) == MonitorRouteGuard.Decision.STOP,
        "Lost route remained audible");
    check(
        guard.verify(new int[] {7}, 202) == MonitorRouteGuard.Decision.STOP,
        "Reconnect reenabled monitoring");
    check(
        new MonitorRouteGuard(7, 100).verify(new int[] {8}, 101) == MonitorRouteGuard.Decision.STOP,
        "Preferred route substituted for actual route");
    check(
        new MonitorRouteGuard(7, 100).verify(new int[] {7, 8}, 101)
            == MonitorRouteGuard.Decision.STOP,
        "Multiple simultaneous outputs accepted");
    check(
        new MonitorRouteGuard(7, 100).verify(new int[] {-1}, 101)
            == MonitorRouteGuard.Decision.STOP,
        "Unsafe route accepted");
    check(
        new MonitorRouteGuard(7, 100).verify(new int[0], 1600) == MonitorRouteGuard.Decision.STOP,
        "Route readiness exceeded bound");
    check(
        new MonitorRouteGuard(7, 100).verify(new int[] {7}, 1600)
            == MonitorRouteGuard.Decision.STOP,
        "Late initial route bypassed readiness deadline");
    check(
        new MonitorRouteGuard(7, 100).verify(new int[] {7}, 99) == MonitorRouteGuard.Decision.STOP,
        "Invalid monotonic time accepted");
    MonitorRouteGuard fresh = new MonitorRouteGuard(7, 300);
    check(
        fresh.verify(new int[] {7}, 301) == MonitorRouteGuard.Decision.ALLOW,
        "Explicit new activation inherited old rejection");
    fresh.close();
    check(
        fresh.verify(new int[] {7}, 302) == MonitorRouteGuard.Decision.STOP,
        "Closed route reopened");

    MonitorBuffer buffer = new MonitorBuffer();
    float[] output = new float[480];
    check(!buffer.drain(output), "Empty monitor buffer produced audio");
    short[] input = new short[480];
    Arrays.fill(input, (short) 8192);
    buffer.offer(input, input.length);
    check(!buffer.drain(output), "Monitor bypassed warmup floor");
    buffer.offer(input, input.length);
    buffer.offer(input, input.length);
    check(
        buffer.drain(output) && Math.abs(output[100] - .25f) < .00001,
        "Nominal monitor interpolation changed level");
    check(input[100] == 8192, "Monitor modified dry capture bytes");
    buffer.clear();
    check(
        !buffer.drain(output) && output[100] == 0 && buffer.bufferedFrames() == 0,
        "Clearing monitor retained old voice");
    long before = buffer.discontinuities();
    for (int i = 0; i < 25; i++) buffer.offer(input, input.length);
    check(
        buffer.bufferedFrames() <= 8192 && buffer.discontinuities() > before,
        "Monitor overrun was unbounded or undetected");
    for (int ppm : new int[] {-2000, -500, 500, 2000}) {
      MonitorBuffer drift = new MonitorBuffer();
      drift.offer(new short[1440], 1440);
      double produced = 0;
      short[] incoming = new short[484];
      int failures = 0, maximum = 0;
      for (int block = 0; block < 10000; block++) {
        produced += 480 * (1 + ppm / 1000000.0);
        int count = (int) produced;
        produced -= count;
        drift.offer(incoming, count);
        if (!drift.drain(output)) failures++;
        maximum = Math.max(maximum, drift.bufferedFrames());
      }
      check(
          failures == 0 && maximum < 3000 && drift.discontinuities() == 0,
          "Independent audio clocks lost bounded continuity at " + ppm + " ppm");
    }
    boolean invalid = false;
    try {
      buffer.offer(input, 481);
    } catch (IllegalArgumentException expected) {
      invalid = true;
    }
    check(invalid, "Invalid dry block accepted");

    for (VocalMonitorEffects.Mode mode : VocalMonitorEffects.Mode.values()) {
      VocalMonitorEffects effects = new VocalMonitorEffects();
      effects.mode(mode);
      float[] impulse = new float[48000];
      impulse[0] = .5f;
      effects.process(impulse);
      double tail = 0;
      for (int i = 1; i < impulse.length; i++) tail += impulse[i] * impulse[i];
      check(
          mode == VocalMonitorEffects.Mode.DRY ? tail == 0 : tail > .00001,
          "Monitor effect impulse contract: " + mode);
      effects.reset();
      float[] zero = new float[48000];
      effects.process(zero);
      boolean silent = true;
      for (float value : zero) silent &= value == 0;
      check(silent, "Stopped/reset effect retained a voice tail: " + mode);
      Arrays.fill(impulse, 1000);
      impulse[17] = Float.NaN;
      impulse[18] = Float.POSITIVE_INFINITY;
      effects.process(impulse);
      boolean bounded = true;
      for (float value : impulse) bounded &= Float.isFinite(value) && Math.abs(value) <= .9f;
      check(bounded, "Monitor effect limiter/nonfinite contract: " + mode);
    }
  }

  private static final class MidiFixture {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    void vlq(int value) {
      int buffer = value & 127;
      while ((value >>>= 7) > 0) buffer = (buffer << 8) | 128 | (value & 127);
      for (; ; ) {
        bytes.write(buffer & 255);
        if ((buffer & 128) == 0) break;
        buffer >>>= 8;
      }
    }

    MidiFixture event(int delta, int... event) {
      vlq(delta);
      for (int value : event) bytes.write(value);
      return this;
    }

    MidiFixture text(int kind, String text) {
      byte[] value = text.getBytes(StandardCharsets.UTF_8);
      event(0, 255, kind);
      vlq(value.length);
      bytes.write(value, 0, value.length);
      return this;
    }

    byte[] end() {
      return event(0, 255, 47, 0).bytes.toByteArray();
    }
  }

  private static MidiLyrics.Document midiTracks(byte[]... tracks) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    out.writeInt(0x4d546864);
    out.writeInt(6);
    out.writeShort(tracks.length == 1 ? 0 : 1);
    out.writeShort(tracks.length);
    out.writeShort(480);
    for (byte[] track : tracks) {
      out.writeInt(0x4d54726b);
      out.writeInt(track.length);
      out.write(track);
    }
    return MidiLyrics.decode(bytes.toByteArray());
  }

  private static MelodyTarget flatTarget(int ticks) throws IOException {
    return MelodyTarget.extract(
        midiTracks(
            new MidiFixture()
                .text(3, "Melody")
                .event(0, 144, 69, 100)
                .event(ticks, 128, 69, 0)
                .end()));
  }

  private static double spectralPeak(float[] samples, double low, double high) {
    double best = -1, frequency = 0;
    // Independent sine projection, decimated below the synthetic fixtures' Nyquist limit.
    for (double hz = low; hz <= high; hz += .1) {
      double angle = 2 * Math.PI * hz / 12000, c = Math.cos(angle), v = Math.sin(angle);
      double re = 0, im = 0, cosine = 1, sine = 0;
      for (int i = samples.length - 96000; i < samples.length; i += 4) {
        re += samples[i] * cosine;
        im += samples[i] * sine;
        double next = cosine * c - sine * v;
        sine = sine * c + cosine * v;
        cosine = next;
      }
      double power = re * re + im * im;
      if (power > best) {
        best = power;
        frequency = hz;
      }
    }
    return frequency;
  }

  private static void pitchCorrectionChecks() {
    ChromaticPitchCorrector corrector = new ChromaticPitchCorrector();
    check(!corrector.hasTarget() && corrector.targetRatio() == 1, "Fresh correction has a target");
    for (double center : new double[] {73.41619198, 110, 440, 880}) {
      double detuning = center == 440 || center < 100 ? .3 : -.4;
      double input = center * Math.pow(2, detuning / 12);
      corrector.reset();
      DryPitchDetector.Pitch detected = new DryPitchDetector().detect(tone(input, .2), 48000);
      check(detected != null, "Detuned voice fixture had no dry pitch estimate");
      float[] output = new float[288000], block = new float[480];
      double energy = 0;
      float previous = 0, maximumStep = 0;
      for (int start = 0; start < output.length; start += block.length) {
        long now = start / 48;
        corrector.update(detected.frequencyHz, detected.confidence, detected.rms, now, now);
        for (int i = 0; i < block.length; i++)
          block[i] = (float) (.2 * Math.sin(2 * Math.PI * input * (start + i) / 48000));
        corrector.process(block);
        System.arraycopy(block, 0, output, start, block.length);
        for (float value : block) {
          checkFinite(value);
          if (start > 96000) {
            energy += value * value;
            maximumStep = Math.max(maximumStep, Math.abs(value - previous));
          }
          previous = value;
        }
      }
      double actual = spectralPeak(output, center * .96, center * 1.04);
      double cents = Math.abs(1200 * Math.log(actual / center) / Math.log(2));
      check(cents < 8, "Corrected spectral pitch " + actual + " differs from target " + center);
      System.out.printf(
          java.util.Locale.ROOT,
          "Chromatic correction: %.2f -> %.2f Hz (target %.2f, error %.2f cents)%n",
          input,
          actual,
          center,
          cents);
      check(
          energy > 200 && maximumStep < .2, "Correction dropped audio or created a discontinuity");
    }
    corrector.reset();
    double oscillator = 0;
    float last = 0, maximumTransition = 0;
    float[] transition = new float[480];
    for (int block = 0; block < 400; block++) {
      double hz = 440 * Math.pow(2, (block / 25 % 2 == 0 ? -.4 : .4) / 12);
      corrector.update(hz, .99, .1, block * 10, block * 10);
      for (int i = 0; i < transition.length; i++) {
        transition[i] = (float) (.2 * Math.sin(oscillator));
        oscillator = (oscillator + 2 * Math.PI * hz / 48000) % (2 * Math.PI);
      }
      corrector.process(transition);
      for (float value : transition) {
        checkFinite(value);
        maximumTransition = Math.max(maximumTransition, Math.abs(value - last));
        last = value;
      }
    }
    check(maximumTransition < .2, "Retuning introduced a large output discontinuity");
    ChromaticPitchCorrector a = new ChromaticPitchCorrector(), b = new ChromaticPitchCorrector();
    a.update(432, .99, .1, 0, 0);
    b.update(432, .99, .1, 0, 0);
    float[] signal = new float[30720],
        whole = new float[signal.length],
        combined = new float[signal.length];
    for (int i = 0; i < signal.length; i++) signal[i] = (float) (.2 * Math.sin(i * .04));
    float[] large = new float[960], small = new float[240];
    for (int offset = 0; offset < signal.length; offset += large.length) {
      System.arraycopy(signal, offset, large, 0, large.length);
      a.process(large);
      System.arraycopy(large, 0, whole, offset, large.length);
    }
    for (int offset = 0; offset < signal.length; offset += small.length) {
      System.arraycopy(signal, offset, small, 0, small.length);
      b.process(small);
      System.arraycopy(small, 0, combined, offset, small.length);
    }
    check(
        java.util.Arrays.equals(whole, combined), "Correction depended on output block partition");
    double partitionEnergy = 0;
    for (float value : whole) partitionEnergy += value * value;
    check(partitionEnergy > 100, "Partition fixture did not exercise audible history");
    corrector.reset();
    corrector.update(440, .99, .1, 100, 100);
    corrector.update(440 * Math.pow(2, .51 / 12), .99, .1, 110, 110);
    check(corrector.targetRatio() < 1, "Semitone boundary jitter bypassed target hysteresis");
    corrector.update(440 * Math.pow(2, .61 / 12), .99, .1, 120, 120);
    check(corrector.targetRatio() > 1, "Stable next semitone did not replace the target");
    for (double bad : new double[] {0, 40, 1200, Double.NaN, Double.POSITIVE_INFINITY}) {
      corrector.update(bad, .99, .1, 1000, 1000);
      check(
          !corrector.hasTarget() && corrector.targetRatio() == 1, "Invalid pitch stayed corrected");
    }
    for (double bad : new double[] {.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
      corrector.update(440, bad, .1, 1000, 1000);
      check(!corrector.hasTarget(), "Unreliable confidence selected a correction target");
    }
    corrector.update(440, .99, .001, 1000, 1000);
    check(!corrector.hasTarget(), "Silence selected a correction target");
    corrector.update(440, .99, Double.NaN, 1000, 1000);
    check(!corrector.hasTarget(), "Nonfinite energy selected a correction target");
    corrector.update(440, .99, .1, 1000, 1301);
    check(!corrector.hasTarget(), "Stale pitch stayed corrected");
    corrector.update(440, .99, .1, 1001, 1000);
    check(!corrector.hasTarget(), "Future pitch stayed corrected");
    corrector.update(440, .99, .1, 1, Long.MAX_VALUE);
    check(!corrector.hasTarget(), "Extreme observation age overflowed");
    corrector.reset();
    float[] raw = new float[480], original = new float[6000];
    for (int offset = 0; offset < original.length; offset += raw.length) {
      for (int i = 0; i < raw.length && offset + i < original.length; i++)
        original[offset + i] = raw[i] = (float) (.2 * Math.sin((offset + i) * .13));
      corrector.process(raw);
      for (int i = 0; i < raw.length && offset + i < original.length; i++) {
        int source = offset + i - 2528;
        float expected = source < 0 ? 0 : original[source];
        if (raw[i] != expected)
          throw new AssertionError("Uncertain correction altered unshifted input");
      }
    }
    check(true, "Uncertain correction's fixed delay");
    float[] hostile = new float[480];
    java.util.Arrays.fill(hostile, Float.POSITIVE_INFINITY);
    hostile[1] = Float.NaN;
    hostile[2] = Float.MAX_VALUE;
    hostile[3] = -Float.MAX_VALUE;
    corrector.process(hostile);
    for (int i = 0; i < 30; i++) {
      float[] zeros = new float[480];
      corrector.process(zeros);
      for (float value : zeros) checkFinite(value);
    }
    check(true, "Correction sanitizes hostile input");
    corrector.reset();
    float[] silence = new float[480];
    for (int i = 0; i < 30; i++) {
      corrector.process(silence);
      for (float value : silence)
        if (value != 0) throw new AssertionError("Reset retained voice history");
    }
    check(!corrector.hasTarget(), "Correction reset retained a target");
    boolean oversized = false;
    try {
      corrector.process(new float[1441]);
    } catch (IllegalArgumentException expected) {
      oversized = true;
    }
    check(oversized, "Unbounded correction block accepted");
  }

  private static void checkFinite(float value) {
    if (!Float.isFinite(value) || Math.abs(value) > .9f)
      throw new AssertionError("Correction emitted a nonfinite or unbounded sample");
  }

  private static void scoringChecks(String starter) throws Exception {
    for (String name : new String[] {"morning-light", "sing-together", "starlight-echo"}) {
      MidiLyrics.Document document =
          MidiLyrics.decode(Files.readAllBytes(Paths.get(starter, name + ".kar")));
      MelodyTarget target = MelodyTarget.extract(document);
      check(
          target.available()
              && target.windows.size() == 16
              && target.track == 0
              && target.channel == 0,
          "Original starter melody evidence lost: " + name);
      check(
          document.tracks.get(0).hasLyrics && !document.tracks.get(0).name.isEmpty(),
          "MIDI track identity/lyric evidence lost: " + name);
    }
    check(!MelodyTarget.extract(null).available(), "Audio file received invented target");
    byte[] named =
        new MidiFixture()
            .text(3, "GUIDE-VOCAL")
            .event(0, 144, 69, 100)
            .event(480, 128, 69, 0)
            .end();
    MelodyTarget namedTarget = MelodyTarget.extract(midiTracks(named));
    check(
        namedTarget.available() && namedTarget.windows.get(0).end == 500,
        "Explicit melody name/timing failed");
    check(
        !MelodyTarget.extract(
                midiTracks(new MidiFixture().event(0, 144, 90, 100).event(480, 128, 90, 0).end()))
            .available(),
        "Highest note guessed as melody");
    check(
        !MelodyTarget.extract(
                midiTracks(
                    named,
                    new MidiFixture()
                        .text(3, "Vocals")
                        .event(0, 145, 67, 100)
                        .event(480, 129, 67, 0)
                        .end()))
            .available(),
        "Ambiguous melody accepted");
    check(
        !MelodyTarget.extract(
                midiTracks(
                    new MidiFixture()
                        .text(3, "Melody")
                        .event(0, 153, 60, 100)
                        .event(480, 137, 60, 0)
                        .end()))
            .available(),
        "Drum channel accepted as melody");
    check(
        !MelodyTarget.extract(
                midiTracks(
                    new MidiFixture()
                        .text(3, "Melody")
                        .event(0, 144, 60, 100)
                        .event(240, 144, 64, 100)
                        .event(240, 128, 60, 0)
                        .event(0, 128, 64, 0)
                        .end()))
            .available(),
        "Polyphonic target accepted");
    check(
        !MelodyTarget.extract(
                midiTracks(
                    new MidiFixture()
                        .text(3, "Melody")
                        .event(0, 144, 60, 100)
                        .event(480, 128, 61, 0)
                        .end()))
            .available(),
        "Unmatched note-off accepted");
    check(
        !MelodyTarget.extract(
                midiTracks(new MidiFixture().text(3, "Melody").event(0, 144, 60, 100).end()))
            .available(),
        "Unclosed melody note accepted");
    check(
        !MelodyTarget.extract(
                midiTracks(
                    new MidiFixture()
                        .text(3, "Melody")
                        .event(0, 144, 60, 100)
                        .event(0, 128, 60, 0)
                        .end()))
            .available(),
        "Zero-duration melody accepted");
    check(
        !MelodyTarget.extract(midiTracks(named, new MidiFixture().event(0, 224, 1, 64).end()))
            .available(),
        "Cross-track instrument pitch bend ignored");
    check(
        MelodyTarget.extract(
                midiTracks(
                    new MidiFixture()
                        .text(4, "voice")
                        .event(0, 144, 69, 100)
                        .event(480, 144, 69, 0)
                        .end()))
            .available(),
        "Instrument-name/zero-velocity-off evidence failed");
    check(
        !MelodyTarget.extract(
                midiTracks(
                    new MidiFixture()
                        .text(5, "@metadata")
                        .event(0, 144, 60, 100)
                        .event(480, 128, 60, 0)
                        .end()))
            .available(),
        "Metadata mistaken for lyric evidence");
    AudibleControlFence fence = new AudibleControlFence();
    check(fence.settled(), "Unchanged audio controls were unstable");
    long firstControl = fence.request();
    fence.rendered(firstControl, 100);
    fence.heard(99);
    check(!fence.settled(), "Render-ahead control treated as audible");
    fence.heard(100);
    check(fence.settled(), "Audible frame fence never acknowledged");
    long newerControl = fence.request();
    fence.heard(1000);
    check(!fence.settled(), "Old acknowledgement settled a newer control");
    fence.rendered(newerControl, 2000);
    fence.rendered(firstControl, 1);
    fence.heard(1999);
    check(!fence.settled(), "Stale rendered control replaced current fence");
    fence.heard(2000);
    check(fence.settled(), "Latest audible control remained unstable");
    boolean badFence = false;
    try {
      fence.rendered(newerControl + 1, 3000);
    } catch (IllegalArgumentException expected) {
      badFence = true;
    }
    check(badFence, "Unknown control acknowledgement accepted");
    MelodyTarget adjacent =
        MelodyTarget.extract(
            midiTracks(
                new MidiFixture()
                    .text(3, "Melody")
                    .event(0, 144, 60, 100)
                    .event(480, 128, 60, 0)
                    .event(0, 144, 62, 100)
                    .event(480, 128, 62, 0)
                    .end()));
    UUID adjacentId = UUID.randomUUID();
    MelodyScoreSession boundary = new MelodyScoreSession(adjacentId, adjacent, 0, 1);
    boundary.observe(adjacentId, 500, 440 * Math.pow(2, (62 - 69) / 12.0), .95, .1);
    check(
        boundary.estimate().matchedSlots == 1 && boundary.estimate().pitchAccuracy > 99.99,
        "Adjacent half-open notes counted a boundary twice or used the preceding pitch");
    MidiLyrics.Document oversizedLabel =
        midiTracks(
            new MidiFixture()
                .text(3, "x".repeat(65537))
                .event(0, 144, 69, 100)
                .event(480, 128, 69, 0)
                .end());
    check(
        oversizedLabel.messages.size() == 2 && !MelodyTarget.extract(oversizedLabel).available(),
        "Oversized optional track label broke playback or invented melody evidence");
    MelodyTarget truncatedName =
        MelodyTarget.extract(
            midiTracks(
                new MidiFixture()
                    .text(3, "Melody" + " ".repeat(300) + "Accompaniment")
                    .event(0, 144, 69, 100)
                    .event(480, 128, 69, 0)
                    .end()));
    check(!truncatedName.available(), "Truncated label manufactured an explicit melody name");
    MelodyTarget target = flatTarget(3840);
    UUID id = UUID.randomUUID();
    MelodyScoreSession perfect = new MelodyScoreSession(id, target, 0, 1);
    for (int i = 0; i < 20; i++) perfect.observe(id, i * 200, 440, .95, .1);
    MelodyScoreSession.Estimate estimate = perfect.estimate();
    check(
        estimate.reportable
            && estimate.matchedSlots == 20
            && Math.abs(estimate.overall - 100) < .0001,
        "Complete on-target score failed");
    check(perfect.finish(UUID.randomUUID()) == null, "Foreign completion accepted");
    check(
        perfect.finish(id) != null && perfect.finish(id) == null,
        "Completion was not exactly once");
    check(!perfect.observe(id, 3999, 440, .99, .1), "Completed score accepted later input");
    MelodyScoreSession partial = new MelodyScoreSession(id, flatTarget(7680), 0, 1);
    for (int i = 0; i < 20; i++) partial.observe(id, i * 200, 440, .95, .1);
    check(
        Math.abs(partial.estimate().coverage - 50) < .0001,
        "Unheard ending removed from coverage denominator");
    MelodyScoreSession burst = new MelodyScoreSession(id, target, 0, 1);
    for (int i = 0; i < 100; i++) burst.observe(id, 50, 440, .9, .1);
    check(
        burst.estimate().matchedSlots == 1 && !burst.estimate().reportable,
        "Burst impersonated sustained singing");
    check(
        burst.observe(id, 50, 466.1638, .99, .1) && burst.estimate().pitchAccuracy < .01,
        "Higher-confidence slot replacement duplicated or favored pitch");
    check(!burst.observe(id, 49, 440, 1, .1), "Out-of-order input accepted");
    check(
        !burst.observe(UUID.randomUUID(), 200, 440, .95, .1), "Another playback's input accepted");
    check(!burst.observe(id, Long.MAX_VALUE, 440, .95, .1), "Extreme time poisoned score");
    check(burst.observe(id, 200, 440, .95, .1), "Rejected extreme time changed valid ordering");
    check(!burst.observe(id, 4000, 440, .95, .1), "Exclusive target ending counted");
    check(
        !new MelodyScoreSession(id, target, 0, 1).estimate().reportable,
        "Silent score was reportable");
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 0}) {
      MelodyScoreSession session = new MelodyScoreSession(id, target, 0, 1);
      check(
          !session.observe(id, 100, invalid, .95, .1) && !session.estimate().reportable,
          "Invalid frequency accepted");
    }
    MelodyScoreSession invalid = new MelodyScoreSession(id, target, 0, 1);
    check(!invalid.observe(id, 100, 440, Double.NaN, .1), "Nonfinite confidence accepted");
    check(!invalid.observe(id, 100, 440, .7, .1), "Unreliable confidence accepted");
    check(!invalid.observe(id, 100, 440, .95, .001), "Silent input energy accepted");
    check(!invalid.observe(id, 100, 440, .95, Double.NaN), "Nonfinite energy accepted");
    invalid.cancel();
    check(
        invalid.finish(id) == null && !invalid.observe(id, 100, 440, .95, .1),
        "Cancelled playback produced a score");
    MelodyScoreSession transposed = new MelodyScoreSession(id, target, 2, 1);
    for (int i = 0; i < 20; i++)
      transposed.observe(id, i * 200, 440 * Math.pow(2, 2.0 / 12), .95, .1);
    check(Math.abs(transposed.estimate().overall - 100) < .0001, "Transposed target ignored key");
    MelodyScoreSession faster = new MelodyScoreSession(id, flatTarget(7680), 0, 2);
    for (int i = 0; i < 20; i++) faster.observe(id, i * 400, 440, .95, .1);
    check(
        Math.abs(faster.estimate().overall - 100) < .0001,
        "Observation coverage ignored source tempo");
    check(MelodyScoreSession.align(1000, 1100, 2000, 1, 50) == 1850, "Input-delay alignment");
    check(MelodyScoreSession.align(1000, 1100, 2000, 2, 50) == 1700, "Tempo alignment");
    check(
        MelodyScoreSession.align(1000, 1100, 2000, 1, -200) == 2100,
        "Negative calibration alignment");
    check(
        MelodyScoreSession.align(1000, Long.MAX_VALUE, 2000, 1, 0) == -1,
        "Extreme capture age overflowed");
    check(MelodyScoreSession.align(1100, 1000, 2000, 1, 0) == -1, "Future observation accepted");
    check(
        MelodyScoreSession.align(1000, 1100, 2000, Double.NaN, 0) == -1,
        "Nonfinite clock accepted");
    MelodyScoreSession concurrent = new MelodyScoreSession(id, target, 0, 1);
    java.util.concurrent.atomic.AtomicInteger completions =
        new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
    ArrayList<Thread> threads = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  ready.await();
                } catch (InterruptedException e) {
                  throw new AssertionError(e);
                }
                if (concurrent.finish(id) != null) completions.incrementAndGet();
              });
      thread.setDaemon(true);
      threads.add(thread);
      thread.start();
    }
    ready.countDown();
    long joinDeadline = System.nanoTime() + 3000000000L;
    boolean finished = true;
    for (Thread thread : threads) {
      thread.join(Math.max(1, (joinDeadline - System.nanoTime()) / 1000000));
      finished &= !thread.isAlive();
    }
    check(finished, "Concurrent score completion deadlocked");
    check(completions.get() == 1, "Concurrent completion emitted more than one result");
  }

  private static void youtubeLifecycleChecks() {
    YouTubeVideoReference a = YouTubeVideoReference.parse("https://youtu.be/abcdefghijk");
    YouTubeVideoReference b = YouTubeVideoReference.parse("https://youtu.be/12345678901");
    YouTubePreviewLifecycle preview = new YouTubePreviewLifecycle();
    YouTubePreviewLifecycle.Session first = preview.open(a);
    YouTubePreviewLifecycle.Session replacement = preview.open(b);
    check(!preview.close(first.id) && preview.active() == replacement, "Stale online failure closed replacement");
    YouTubePreviewLifecycle.Session repeated = preview.open(b);
    check(!repeated.id.equals(replacement.id), "Repeated link reused a disposed player identity");
    check(!preview.close(replacement.id), "Stale repeated-link callback closed current player");
    check(preview.close(repeated.id) && !preview.close(repeated.id), "Repeated dismissal was not idempotent");
    preview.open(a);
    preview.close();
    check(preview.active() == null, "Backgrounded player survived");
    YouTubePreviewLifecycle.Session reopened = preview.open(b);
    check(!preview.close(first.id) && preview.active() == reopened, "Old callback resurrected/closed a reopened player");
    ReservationQueue queue = new ReservationQueue();
    ReservationQueue.Entry one = queue.reserve(UUID.randomUUID(), song(), "Ana");
    ReservationQueue.Entry two = queue.reserve(UUID.randomUUID(), song(), "Ben");
    if (YouTubeVideoReference.parse("https://example.com/abcdefghijk") != null) throw new AssertionError("Invalid link accepted");
    preview.close();
    preview.open(a);
    check(queue.snapshot().size() == 2 && queue.snapshot().get(0).id.equals(one.id)
        && queue.snapshot().get(1).id.equals(two.id), "Online lifecycle changed local FIFO identities");
    queue.remove(one.id);
    check(queue.peek().id.equals(two.id) && preview.active() != null, "Explicit local mutation affected online state");
  }

  public static void main(String[] args) throws Exception {
    youtubeLifecycleChecks();
    microphonePitchChecks();
    monitorChecks();
    scoringChecks(args[0]);
    pitchCorrectionChecks();
    ReservationQueue queue = new ReservationQueue();
    UUID request = UUID.randomUUID();
    ReservationQueue.Entry first = queue.reserve(request, song(), "Ana"),
        retry = queue.reserve(request, song(), "Ana");
    check(first.id.equals(retry.id), "Replay duplicates a reservation");
    ReservationQueue.Entry second = queue.reserve(UUID.randomUUID(), song(), "Ben");
    check(!first.id.equals(second.id), "Intentional duplicate song lost");
    check(queue.snapshot().size() == 2, "Queue size");
    queue.move(second.id, 0);
    check(queue.peek().id.equals(second.id), "Reorder failed");
    queue.remove(first.id);
    queue.reserve(request, song(), "Ana");
    check(queue.snapshot().size() == 1, "Removed request was replayed");
    ArrayList<ReservationQueue.Entry> restored = new ArrayList<>(queue.snapshot());
    ReservationQueue reopened = new ReservationQueue();
    reopened.restore(restored);
    check(reopened.peek().id.equals(second.id), "Reservation identity changed");
    boolean duplicateRejected = false;
    restored.add(restored.get(0));
    try {
      reopened.restore(restored);
    } catch (IllegalArgumentException e) {
      duplicateRejected = true;
    }
    check(duplicateRejected, "Duplicate queue identity accepted");
    check(song().matches("morning originals"), "Multi-term search");
    check(song().matches("001"), "Code search");
    check(!song().matches("missing"), "False search match");
    boolean traversal = false;
    try {
      new Song("x", "x", "", "", "../outside.mid", false);
    } catch (IllegalArgumentException e) {
      traversal = true;
    }
    check(traversal, "Unsafe media path accepted");
    for (String name : new String[] {"morning-light", "sing-together", "starlight-echo"}) {
      byte[] midi = Files.readAllBytes(Paths.get(args[0], name + ".kar"));
      MidiLyrics.Document document = MidiLyrics.decode(midi);
      List<MidiLyrics.Cue> cues = document.cues;
      check(!document.messages.isEmpty(), "Starter MIDI events missing");
      check(
          document.durationMilliseconds > cues.get(cues.size() - 1).milliseconds,
          "End of track duration lost");
      boolean noteOn = false, noteOff = false;
      long previousTime = -1;
      for (MidiLyrics.Message message : document.messages) {
        check(message.milliseconds >= previousTime, "Nonmonotonic MIDI event time");
        previousTime = message.milliseconds;
        noteOn |= (message.status & 0xf0) == 0x90 && message.b > 0;
        noteOff |=
            (message.status & 0xf0) == 0x80 || (message.status & 0xf0) == 0x90 && message.b == 0;
      }
      check(noteOn && noteOff, "Starter note lifetime missing");
      check(cues.size() >= 3, "Starter lyrics missing " + name);
      check(cues.get(0).milliseconds == 0, "First lyric time");
      for (int i = 1; i < cues.size(); i++)
        check(
            cues.get(i).milliseconds >= cues.get(i - 1).milliseconds, "Nonmonotonic lyric timing");
      check(MidiLyrics.activeIndex(cues, -1) == -1, "Early lyric displayed");
      check(MidiLyrics.activeIndex(cues, cues.get(1).milliseconds) == 1, "Lyric boundary");
      boolean bad = false;
      try {
        MidiLyrics.parse(Arrays.copyOf(midi, midi.length - 6));
      } catch (IOException e) {
        bad = true;
      }
      check(bad, "Truncated MIDI accepted");
    }
    java.util.concurrent.ExecutorService workers =
        java.util.concurrent.Executors.newFixedThreadPool(8);
    ReservationQueue concurrentQueue = new ReservationQueue();
    UUID sharedRequest = UUID.randomUUID();
    Set<UUID> responseIds = new HashSet<>();
    List<java.util.concurrent.Future<ReservationQueue.Entry>> futures = new ArrayList<>();
    for (int i = 0; i < 32; i++)
      futures.add(workers.submit(() -> concurrentQueue.reserve(sharedRequest, song(), "Singer")));
    for (java.util.concurrent.Future<ReservationQueue.Entry> future : futures)
      responseIds.add(future.get().id);
    workers.shutdown();
    check(responseIds.size() == 1, "Concurrent replay returned different identities");
    check(concurrentQueue.snapshot().size() == 1, "Concurrent replay duplicated queue state");
    check(
        new Song("accent", "Kántá", "", "", "accent.mid", false).matches("kanta"),
        "Accent-insensitive search");
    ByteArrayOutputStream midiBytes = new ByteArrayOutputStream();
    DataOutputStream midiOut = new DataOutputStream(midiBytes);
    midiOut.writeInt(0x4d546864);
    midiOut.writeInt(6);
    midiOut.writeShort(1);
    midiOut.writeShort(2);
    midiOut.writeShort(480);
    byte[] tempoTrack = {
      0,
      (byte) 255,
      81,
      3,
      7,
      (byte) 161,
      32,
      (byte) 131,
      96,
      (byte) 255,
      81,
      3,
      15,
      66,
      64,
      0,
      (byte) 255,
      47,
      0
    };
    byte[] lyricTrack = {
      0, (byte) 255, 5, 2, 47, 65, (byte) 135, 64, (byte) 255, 5, 2, 47, 66, 0, (byte) 255, 47, 0
    };
    for (byte[] track : new byte[][] {tempoTrack, lyricTrack}) {
      midiOut.writeInt(0x4d54726b);
      midiOut.writeInt(track.length);
      midiOut.write(track);
    }
    check(
        MidiLyrics.parse(midiBytes.toByteArray()).get(1).milliseconds == 1500,
        "Cross-track tempo mapping");
    boolean malformed = false;
    try {
      MidiLyrics.parse(new byte[20]);
    } catch (IOException e) {
      malformed = true;
    }
    check(malformed, "Invalid MIDI magic accepted");
    RenderedPlaybackClock clock = new RenderedPlaybackClock(1000);
    check(clock.position(0) == 0, "Empty playback clock");
    clock.append(0, 0, 1);
    clock.append(1000, 1000, 2);
    clock.append(2000, 3000, .5);
    check(clock.position(500) == 500, "Buffered tempo applied before audible boundary");
    check(clock.position(1000) == 1000, "Tempo boundary discontinuity");
    check(clock.position(1500) == 2000, "Audible fast tempo clock");
    check(clock.position(2500) == 3250, "Audible slow tempo clock");
    boolean invalidClock = false;
    try {
      clock.append(3000, 3500, Double.NaN);
    } catch (IllegalArgumentException e) {
      invalidClock = true;
    }
    check(invalidClock, "Nonfinite tempo accepted");
    boolean backwardsClock = false;
    try {
      clock.append(2000, 3500, 1);
    } catch (IllegalArgumentException e) {
      backwardsClock = true;
    }
    check(backwardsClock, "Backwards render clock accepted");
    byte[] channelTrack = {
      0,
      (byte) 0xc0,
      12,
      0,
      (byte) 0xb0,
      7,
      90,
      0,
      (byte) 0xe0,
      0,
      64,
      0,
      (byte) 0x90,
      69,
      127,
      (byte) 0x83,
      0x60,
      69,
      0,
      (byte) 0x83,
      0x60,
      (byte) 0xff,
      0x2f,
      0
    };
    ByteArrayOutputStream channelBytes = new ByteArrayOutputStream();
    DataOutputStream channelOut = new DataOutputStream(channelBytes);
    channelOut.writeInt(0x4d546864);
    channelOut.writeInt(6);
    channelOut.writeShort(0);
    channelOut.writeShort(1);
    channelOut.writeShort(480);
    channelOut.writeInt(0x4d54726b);
    channelOut.writeInt(channelTrack.length);
    channelOut.write(channelTrack);
    MidiLyrics.Document channels = MidiLyrics.decode(channelBytes.toByteArray());
    check(channels.messages.size() == 5, "Typed channel events lost");
    check(
        channels.messages.get(0).status == 0xc0 && channels.messages.get(0).a == 12,
        "Program event");
    check(
        channels.messages.get(1).status == 0xb0 && channels.messages.get(1).b == 90,
        "Control event");
    check(
        channels.messages.get(2).status == 0xe0 && channels.messages.get(2).b == 64,
        "Pitch wheel event");
    check(
        channels.messages.get(4).milliseconds == 500 && channels.messages.get(4).b == 0,
        "Running-status note off");
    check(channels.durationMilliseconds == 1000, "Silent ending duration lost");
    byte[] fragmentsTrack = {
      0,
      (byte) 255,
      5,
      4,
      '/',
      'H',
      'e',
      'l',
      (byte) 0x83,
      0x60,
      (byte) 255,
      5,
      3,
      'l',
      'o',
      ' ',
      (byte) 0x83,
      0x60,
      (byte) 255,
      5,
      10,
      'w',
      'o',
      'r',
      'l',
      'd',
      '\n',
      'N',
      'e',
      'x',
      't',
      0,
      (byte) 255,
      47,
      0
    };
    MidiLyrics.Document fragmented = midiTrack(fragmentsTrack);
    check(
        fragmented.cues.size() == 2
            && fragmented.cues.get(0).line.equals("Hello world")
            && fragmented.cues.get(1).line.equals("Next"),
        "Inline lyric line markers");
    MidiLyrics.Cue timed = fragmented.cues.get(0);
    check(timed.highlightedCharacters(-1) == 0, "Early fragment highlight");
    check(
        timed.highlightedCharacters(0) == 3 && timed.highlightedCharacters(499) == 3,
        "Invented fragment timing");
    check(
        timed.highlightedCharacters(500) == 6 && timed.highlightedCharacters(1000) == 11,
        "Source fragment boundary");
    byte[] unmarked = {
      0,
      (byte) 255,
      5,
      3,
      'O',
      'n',
      'e',
      (byte) 0x83,
      0x60,
      (byte) 255,
      5,
      3,
      'T',
      'w',
      'o',
      0,
      (byte) 255,
      47,
      0
    };
    check(midiTrack(unmarked).cues.size() == 2, "Unmarked lyrics collapsed into one line");
    for (int sound = 0; sound < PartySounds.TITLES.size(); sound++) {
      byte[] wave = PartySounds.wave(sound);
      check(Arrays.equals(wave, PartySounds.wave(sound)), "Nondeterministic first-party sound");
      try (javax.sound.sampled.AudioInputStream audio =
          javax.sound.sampled.AudioSystem.getAudioInputStream(new ByteArrayInputStream(wave))) {
        check(
            audio.getFormat().getSampleRate() == 22050 && audio.getFrameLength() == 44100,
            "Invalid party WAV format/duration");
        byte[] pcm = audio.readAllBytes();
        int peak = 0;
        for (int offset = 0; offset < pcm.length; offset += 2)
          peak = Math.max(peak, Math.abs((short) ((pcm[offset] & 255) | (pcm[offset + 1] << 8))));
        check(peak > 100 && peak <= 24000, "Silent or clipped party sound");
      }
    }
    check(ShortClipBounds.accepts(2000, 44100, 2), "Small stereo clip rejected");
    check(!ShortClipBounds.accepts(30000, 44100, 2), "Truncated SoundPool clip accepted");
    check(!ShortClipBounds.accepts(Long.MAX_VALUE, 44100, 2), "Clip duration overflow");
    check(
        !ShortClipBounds.accepts(1000, -1, 2) && !ShortClipBounds.accepts(1000, 44100, 8),
        "Invalid clip format accepted");
    boolean invalidPad = false;
    try {
      PartySounds.wave(8);
    } catch (IllegalArgumentException e) {
      invalidPad = true;
    }
    check(invalidPad, "Unknown pad accepted");
    Song dangerous = new Song("x", "=SUM(1,2)", "A\"B", "7", "x.wav", false);
    String csv = PrivateSongbook.csv(Arrays.asList(dangerous));
    check(csv.contains("\"'=SUM(1,2)\""), "Formula not neutralized");
    check(csv.contains("\"A\"\"B\""), "CSV quote escape");
    PrivateSongbook.Export export = PrivateSongbook.seal(csv), another = PrivateSongbook.seal(csv);
    check(export.accessCode.length() == 32, "Code entropy representation");
    check(!export.accessCode.equals(another.accessCode), "Export secret reused");
    String marker = "\"payload\":\"";
    int start = export.envelope.indexOf(marker) + marker.length();
    byte[] combined =
        Base64.getDecoder()
            .decode(export.envelope.substring(start, export.envelope.indexOf('"', start)));
    byte[] key =
        MessageDigest.getInstance("SHA-256")
            .digest(("KANTA-SONGBOOK-V1:" + export.accessCode).getBytes(StandardCharsets.UTF_8));
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(key, "AES"),
        new GCMParameterSpec(128, Arrays.copyOf(combined, 12)));
    String plain =
        new String(
            cipher.doFinal(Arrays.copyOfRange(combined, 12, combined.length)),
            StandardCharsets.UTF_8);
    check(plain.equals(csv), "Browser envelope round trip");
    combined[combined.length - 1] ^= 1;
    boolean tampered = false;
    try {
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(128, Arrays.copyOf(combined, 12)));
      cipher.doFinal(Arrays.copyOfRange(combined, 12, combined.length));
    } catch (AEADBadTagException e) {
      tampered = true;
    }
    check(tampered, "Tampering accepted");
    if (args.length > 1) {
      String fixture =
          "{\"code\":\""
              + export.accessCode
              + "\",\"envelope\":"
              + export.envelope
              + ",\"csvBase64\":\""
              + Base64.getEncoder().encodeToString(csv.getBytes(StandardCharsets.UTF_8))
              + "\"}";
      Files.write(Paths.get(args[1]), fixture.getBytes(StandardCharsets.UTF_8));
    }
    checks += ph.kanta.reborn.core.songbook.ShubCompatibilityRegressionTests.run();
    checks += CompanionRegressionTests.run();
    checks += BrowserRemoteRegressionTests.run();
    checks += MkmdDecoderRegressionTests.run();
    System.out.println("KANTA Android core: " + checks + " regression checks passed");
  }
}
