package ph.kanta.reborn.core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Standalone host-JDK checks, run by test_android_settings_backup.py. */
public final class PlaybackSettingsBackupChecks {
  public static void main(String[] args) throws Exception {
    roundTrip(.37f, 1.25f, -5);
    roundTrip(0f, .5f, -12);
    roundTrip(1f, 2f, 12);
    roundTrip(Float.MIN_VALUE, 1f, 0);

    String valid = "KANTA-SETTINGS 1\nmusicVolume=0.37\ndefaultTempo=1.25\ndefaultKey=-5";
    String encoded = new String(new PlaybackSettingsBackup(.37f, 1.25f, -5).encode(),
        StandardCharsets.UTF_8);
    require(encoded.equals(valid + "\n"), "Only the three allowlisted fields may be exported");
    require(PlaybackSettingsBackup.read(input(valid.replace("\n", "\r\n"))).pitch == -5,
        "CRLF and optional final newline must work");

    for (String invalid : new String[] {
        "", "{}", valid.replace("SETTINGS 1", "SETTINGS 2"), valid.replace("defaultKey=-5", ""),
        valid + "\ndefaultKey=3", valid + "\nsecret=content://local/library", "\uFEFF" + valid,
        valid.replace("0.37", "1.000000001"), valid.replace("0.37", "-1e-30"),
        valid.replace("0.37", "NaN"), valid.replace("0.37", "0x1.0p-2"),
        valid.replace("0.37", "1e99"), valid.replace("0.37", "0\u0000"),
        valid.replace("1.25", "0.499999999"), valid.replace("1.25", "2.01"),
        valid.replace("-5", "1.0"), valid.replace("-5", "13"), valid.replace("-5", "-13"),
        valid.replace("-5", "2147483648")
    }) {
      reject(input(invalid));
    }
    byte[] oversized = new byte[1024];
    Arrays.fill(oversized, (byte) 'x');
    ByteArrayInputStream large = new ByteArrayInputStream(oversized);
    reject(large);
    require(large.available() == oversized.length - 257, "Read must be bounded at 257 bytes");
  }

  private static void roundTrip(float volume, float tempo, int pitch) throws Exception {
    PlaybackSettingsBackup decoded = PlaybackSettingsBackup.read(new ByteArrayInputStream(
        new PlaybackSettingsBackup(volume, tempo, pitch).encode()));
    require(decoded.volume == volume && decoded.tempo == tempo && decoded.pitch == pitch,
        "Playback settings did not round-trip");
  }

  private static ByteArrayInputStream input(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }

  private static void reject(ByteArrayInputStream input) throws Exception {
    try {
      PlaybackSettingsBackup.read(input);
      throw new AssertionError("Accepted invalid backup");
    } catch (PlaybackSettingsBackup.InvalidBackup expected) {
      // No settings object can be returned for an invalid document.
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
