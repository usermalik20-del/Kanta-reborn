package ph.kanta.reborn.core;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Regression coverage for the MIDI key signature and the practice target derived from it.
 *
 * <p>The pitch classes below were computed independently from the fifths rule (a major key's
 * tonic is seven fifths up from C per sharp; a minor key's tonic is a minor third above its
 * relative major) rather than copied out of the implementation.
 */
public final class MidiKeyRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  /** Tonic pitch classes for key signatures -7..+7, major. */
  private static final int[] MAJOR = {11, 6, 1, 8, 3, 10, 5, 0, 7, 2, 9, 4, 11, 6, 1};

  /** Tonic pitch classes for key signatures -7..+7, minor. */
  private static final int[] MINOR = {8, 3, 10, 5, 0, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10};

  /** A SMF with a key-signature meta event on every track. */
  private static byte[] midiWithKey(int sharpsFlats, boolean minor, int trackCount)
      throws IOException {
    ByteArrayOutputStream file = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(file);
    out.writeInt(0x4d546864);
    out.writeInt(6);
    out.writeShort(1);
    out.writeShort(trackCount);
    out.writeShort(480);
    for (int track = 0; track < trackCount; track++) {
      ByteArrayOutputStream body = new ByteArrayOutputStream();
      // Later tracks carry a different key, so "first wins" is distinguishable from "last wins".
      int sf = sharpsFlats + (track == 0 ? 0 : 1);
      body.write(0);
      body.write(0xFF);
      body.write(0x59);
      body.write(0x02);
      body.write(sf & 0xFF);
      body.write(minor ? 1 : 0);
      body.write(0);
      body.write(0xFF);
      body.write(0x2F);
      body.write(0x00);
      byte[] bytes = body.toByteArray();
      out.writeInt(0x4d54726b);
      out.writeInt(bytes.length);
      out.write(bytes);
    }
    return file.toByteArray();
  }

  /** A SMF with no key-signature meta event at all. */
  private static byte[] midiWithoutKey() throws IOException {
    ByteArrayOutputStream file = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(file);
    out.writeInt(0x4d546864);
    out.writeInt(6);
    out.writeShort(0);
    out.writeShort(1);
    out.writeShort(480);
    byte[] body = {(byte) 0x00, (byte) 0xFF, (byte) 0x2F, (byte) 0x00};
    out.writeInt(0x4d54726b);
    out.writeInt(body.length);
    out.write(body);
    return file.toByteArray();
  }

  private static void pitchClassChecks() {
    for (int sf = -7; sf <= 7; sf++) {
      int index = sf + 7;
      check(
          MidiKey.pitchClass(sf, false) == MAJOR[index],
          "Major pitch class for " + sf + ": " + MidiKey.pitchClass(sf, false));
      check(
          MidiKey.pitchClass(sf, true) == MINOR[index],
          "Minor pitch class for " + sf + ": " + MidiKey.pitchClass(sf, true));
    }
    check(MidiKey.pitchClass(8, false) == MidiKey.UNKNOWN, "Eight sharps is not a key");
    check(MidiKey.pitchClass(-8, false) == MidiKey.UNKNOWN, "Eight flats is not a key");
  }

  private static void nameChecks() {
    check("C major".equals(MidiKey.name(0, false)), "C major");
    check("A minor".equals(MidiKey.name(0, true)), "A minor");
    check("G major".equals(MidiKey.name(1, false)), "G major");
    check("F major".equals(MidiKey.name(-1, false)), "F major");
    check("Eb major".equals(MidiKey.name(-3, false)), "Eb major");
    check("F# major".equals(MidiKey.name(6, false)), "F# major");
    check("A# minor".equals(MidiKey.name(7, true)), "A# minor");
    check(MidiKey.name(9, false) == null, "No name for an impossible signature");
  }

  private static void targetChecks() {
    check(Math.abs(MidiKey.practiceTargetHz(0, true) - 440f) < 0.001f, "A minor targets A4");
    check(
        Math.abs(MidiKey.practiceTargetHz(0, false) - 261.62558f) < 0.001f,
        "C major targets middle C");
    check(
        Math.abs(MidiKey.practiceTargetHz(1, false) - 391.99542f) < 0.001f, "G major targets G4");
    check(
        Math.abs(MidiKey.practiceTargetHz(-3, false) - 311.12698f) < 0.001f,
        "Eb major targets Eb4");
    check(
        Math.abs(MidiKey.practiceTargetHz(6, false) - 369.99442f) < 0.001f,
        "F# major targets F#4");
    check(MidiKey.practiceTargetHz(9, false) == 0f, "Impossible signature has no target");
    // Every real key must land inside a range a singer can actually reach.
    for (int sf = -7; sf <= 7; sf++) {
      for (boolean minor : new boolean[] {false, true}) {
        float hz = MidiKey.practiceTargetHz(sf, minor);
        check(hz >= 260f && hz <= 495f, "Target out of range for " + sf + " minor=" + minor);
      }
    }
  }

  private static void decodeChecks() throws IOException {
    MidiLyrics.Document aMinor = MidiLyrics.decode(midiWithKey(0, true, 1));
    check(aMinor.keyPresent, "A minor key event was not read");
    check(aMinor.keySharpsFlats == 0 && aMinor.keyMinor, "A minor signature");
    check(
        Math.abs(MidiKey.practiceTargetHz(aMinor.keySharpsFlats, aMinor.keyMinor) - 440f) < 0.001f,
        "A minor file targets A4");

    // A negative count survives the round trip as a signed byte.
    MidiLyrics.Document eFlat = MidiLyrics.decode(midiWithKey(-3, false, 1));
    check(eFlat.keyPresent && eFlat.keySharpsFlats == -3 && !eFlat.keyMinor, "Eb major signature");

    // Every track repeats the key; the first one must win, not the last.
    MidiLyrics.Document repeated = MidiLyrics.decode(midiWithKey(1, false, 2));
    check(
        repeated.keyPresent && repeated.keySharpsFlats == 1,
        "First key event must win, got " + repeated.keySharpsFlats);

    MidiLyrics.Document none = MidiLyrics.decode(midiWithoutKey());
    check(!none.keyPresent, "A file without a key event reported one");
    check(none.keySharpsFlats == 0 && !none.keyMinor, "Absent key defaults");
  }

  public static int run() throws IOException {
    pitchClassChecks();
    nameChecks();
    targetChecks();
    decodeChecks();
    return checks;
  }
}
