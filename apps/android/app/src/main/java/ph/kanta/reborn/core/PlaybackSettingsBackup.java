package ph.kanta.reborn.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Versioned, settings-only file; no preference keys, paths or user data are serialized. */
public final class PlaybackSettingsBackup {
  public static final String FILE_NAME = "KANTA.kanta-settings";
  private static final String HEADER = "KANTA-SETTINGS 1";
  private static final int MAX_BYTES = 256;
  private static final Pattern DECIMAL =
      Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
  private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

  public final float volume, tempo;
  public final int pitch;

  public PlaybackSettingsBackup(float volume, float tempo, int pitch) {
    if (!Float.isFinite(volume) || volume < 0 || volume > 1
        || !Float.isFinite(tempo) || tempo < .5f || tempo > 2f || pitch < -12 || pitch > 12) {
      throw new IllegalArgumentException("Invalid playback settings");
    }
    this.volume = volume;
    this.tempo = tempo;
    this.pitch = pitch;
  }

  public byte[] encode() {
    return (HEADER + "\nmusicVolume=" + volume + "\ndefaultTempo=" + tempo
        + "\ndefaultKey=" + pitch + "\n").getBytes(StandardCharsets.UTF_8);
  }

  /** Reads at most one byte past the limit. Leaves the stream open for the caller. */
  public static PlaybackSettingsBackup read(InputStream input) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] chunk = new byte[MAX_BYTES + 1];
    while (bytes.size() <= MAX_BYTES) {
      int count = input.read(chunk, 0, chunk.length - bytes.size());
      if (count < 0) break;
      if (count == 0) throw invalid();
      bytes.write(chunk, 0, count);
    }
    // All valid files use the ASCII subset of UTF-8. This also rejects malformed text and BOMs.
    byte[] raw = bytes.toByteArray();
    if (raw.length > MAX_BYTES) throw invalid();
    for (byte b : raw) if (b < 0) throw invalid();
    String[] lines = new String(raw, StandardCharsets.UTF_8).split("\\r?\\n", -1);
    if ((lines.length != 4 && (lines.length != 5 || !lines[4].isEmpty()))
        || !HEADER.equals(lines[0])) throw invalid();
    try {
      float volume = decimal(field(lines[1], "musicVolume="), BigDecimal.ZERO, BigDecimal.ONE);
      float tempo = decimal(field(lines[2], "defaultTempo="), BigDecimal.valueOf(.5),
          BigDecimal.valueOf(2));
      String pitch = field(lines[3], "defaultKey=");
      if (!INTEGER.matcher(pitch).matches()) throw invalid();
      return new PlaybackSettingsBackup(volume, tempo, Integer.parseInt(pitch));
    } catch (IllegalArgumentException | ArithmeticException invalidValue) {
      throw invalid();
    }
  }

  private static String field(String line, String prefix) throws IOException {
    if (!line.startsWith(prefix)) throw invalid();
    return line.substring(prefix.length());
  }

  private static float decimal(String text, BigDecimal min, BigDecimal max) throws IOException {
    if (!DECIMAL.matcher(text).matches()) throw invalid();
    BigDecimal number = new BigDecimal(text);
    if (number.compareTo(min) < 0 || number.compareTo(max) > 0) throw invalid();
    return number.floatValue();
  }

  private static InvalidBackup invalid() {
    return new InvalidBackup();
  }

  public static final class InvalidBackup extends IOException {
    private static final long serialVersionUID = 1L;

    private InvalidBackup() {
      super("Invalid or unsupported KANTA settings backup");
    }
  }
}
