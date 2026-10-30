package ph.kanta.reborn.core;

/**
 * Turns a MIDI key signature into a key name and a practice target.
 *
 * <p>A MIDI key signature meta event carries a signed count of sharps (positive) or flats
 * (negative) and a mode flag. The count is not the tonic, so it has to be converted: the tonic
 * of a major key is seven fifths up from C for each sharp, and a minor key's tonic is a minor
 * third above its relative major.
 *
 * <p>Framework-free, so the host-JVM coreRegression task can check the conversions.
 */
public final class MidiKey {

  /** Returned when there is no usable key signature. */
  public static final int UNKNOWN = -1;

  private static final String[] MAJOR = {
    "Cb", "Gb", "Db", "Ab", "Eb", "Bb", "F", "C", "G", "D", "A", "E", "B", "F#", "C#"
  };

  private static final String[] MINOR = {
    "Ab", "Eb", "Bb", "F", "C", "G", "D", "A", "E", "B", "F#", "C#", "G#", "D#", "A#"
  };

  private MidiKey() {}

  /**
   * The tonic pitch class, where 0 is C and 11 is B.
   *
   * @param sharpsFlats signed sharps/flats count from the key signature, -7 to +7
   * @param minor true for a minor key
   * @return the pitch class, or {@link #UNKNOWN} when the count is out of range
   */
  public static int pitchClass(int sharpsFlats, boolean minor) {
    if (sharpsFlats < -7 || sharpsFlats > 7) return UNKNOWN;
    int fifths = sharpsFlats * 7 + (minor ? 9 : 0);
    return ((fifths % 12) + 12) % 12;
  }

  /**
   * The key's name, for display and for the learning assistant.
   *
   * @return a name such as "G major" or "A minor", or null when the count is out of range
   */
  public static String name(int sharpsFlats, boolean minor) {
    int index = sharpsFlats + 7;
    if (index < 0 || index >= MAJOR.length) return null;
    return (minor ? MINOR[index] : MAJOR[index]) + (minor ? " minor" : " major");
  }

  /**
   * A practice target for the key: its tonic in the octave that contains middle C, which keeps
   * the target inside a comfortable singing range for every key.
   *
   * @return the frequency in Hz, or 0 when the key is unknown
   */
  public static float practiceTargetHz(int sharpsFlats, boolean minor) {
    int pitchClass = pitchClass(sharpsFlats, minor);
    if (pitchClass == UNKNOWN) return 0f;
    int midiNote = 60 + pitchClass;
    return (float) (440.0 * Math.pow(2.0, (midiNote - 69) / 12.0));
  }
}
