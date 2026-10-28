package ph.kanta.reborn.core.legacy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;

public final class MkmdDecoderRegressionTests {
  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  private static void append(ByteArrayOutputStream out, int... values) {
    for (int value : values) out.write(value);
  }

  private static void appendLE32(ByteArrayOutputStream out, long value) {
    out.write((int) (value & 0xFF));
    out.write((int) ((value >> 8) & 0xFF));
    out.write((int) ((value >> 16) & 0xFF));
    out.write((int) ((value >> 24) & 0xFF));
  }

  private static byte[] aesKey = new byte[32];
  private static byte[] aesIv = new byte[16];

  static {
    Arrays.fill(aesKey, (byte) 0x42);
    Arrays.fill(aesIv, (byte) 0x17);
  }

  private static byte[] buildMkmd(byte[] rawPayload) {
    try {
      Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
      deflater.setInput(rawPayload);
      deflater.finish();
      ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      while (!deflater.finished()) {
        int n = deflater.deflate(buf);
        if (n > 0) compressedOut.write(buf, 0, n);
      }
      deflater.end();
      byte[] compressed = compressedOut.toByteArray();

      ByteArrayOutputStream plain = new ByteArrayOutputStream();
      append(plain, 0x4D, 0x4B, 0x4D, 0x44);
      append(plain, 0x01);
      appendLE32(plain, rawPayload.length);
      appendLE32(plain, compressed.length);
      plain.write(compressed);

      javax.crypto.Cipher cipher =
          javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
      cipher.init(
          javax.crypto.Cipher.ENCRYPT_MODE,
          new javax.crypto.spec.SecretKeySpec(aesKey, "AES"),
          new javax.crypto.spec.IvParameterSpec(aesIv));
      return cipher.doFinal(plain.toByteArray());
    } catch (Exception e) {
      throw new RuntimeException("Failed to build test fixture", e);
    }
  }

  private static void decodeRoundTripMidi() throws Exception {
    byte[] midi = new byte[] {
      (byte) 0x4D, (byte) 0x54, (byte) 0x68, (byte) 0x64,
      (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x06,
      (byte) 0x00, (byte) 0x00,
      (byte) 0x00, (byte) 0x01,
      (byte) 0x01, (byte) 0xE0,
      (byte) 0x4D, (byte) 0x54, (byte) 0x72, (byte) 0x6B,
      (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04,
      (byte) 0x00, (byte) 0x90, (byte) 0x3C, (byte) 0x40
    };
    byte[] ciphertext = buildMkmd(midi);
    MkmdDecoder.DecodedPayload decoded = MkmdDecoder.decode(ciphertext, aesKey, aesIv);
    check(Arrays.equals(decoded.payload, midi), "MIDI round-trip preserves bytes");
    check(decoded.signature == MkmdDecoder.Signature.STANDARD_MIDI, "MIDI signature");
  }

  private static void decodeRoundTripWave() throws Exception {
    byte[] wave = new byte[] {
      (byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F', (byte) 0x24, (byte) 0x00, (byte) 0x00, (byte) 0x00,
      (byte) 'W', (byte) 'A', (byte) 'V', (byte) 'E',
      (byte) 0x66, (byte) 0x6D, (byte) 0x74, (byte) 0x20, (byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0x00,
      (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00,
      (byte) 0x44, (byte) 0xAC, (byte) 0x00, (byte) 0x00, (byte) 0x88, (byte) 0x58, (byte) 0x01, (byte) 0x00,
      (byte) 0x02, (byte) 0x00, (byte) 0x10, (byte) 0x00,
      (byte) 0x64, (byte) 0x61, (byte) 0x74, (byte) 0x61, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };
    byte[] ciphertext = buildMkmd(wave);
    MkmdDecoder.DecodedPayload decoded = MkmdDecoder.decode(ciphertext, aesKey, aesIv);
    check(Arrays.equals(decoded.payload, wave), "WAVE round-trip preserves bytes");
    check(decoded.signature == MkmdDecoder.Signature.WAVE, "WAVE signature");
  }

  private static void decodeRoundTripMp3() throws Exception {
    byte[] mp3 = new byte[] {
      (byte) 'I', (byte) 'D', (byte) '3', (byte) 0x04, (byte) 0x00, (byte) 0x00,
      (byte) 0x00, (byte) 0x00, (byte) 0x00,
      (byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0x44
    };
    byte[] ciphertext = buildMkmd(mp3);
    MkmdDecoder.DecodedPayload decoded = MkmdDecoder.decode(ciphertext, aesKey, aesIv);
    check(Arrays.equals(decoded.payload, mp3), "MP3 round-trip preserves bytes");
    check(decoded.signature == MkmdDecoder.Signature.MP3_ID3, "MP3 signature");
  }

  private static void rejectsNonBlockAlignedCiphertext() {
    try {
      MkmdDecoder.decode(new byte[] {1, 2, 3}, aesKey, aesIv);
      check(false, "Should reject non-block-aligned");
    } catch (MkmdDecoder.DecodeException expected) {
      check(expected.getMessage().contains("block-aligned"), "Block alignment error");
    }
  }

  private static void rejectsEmptyCiphertext() {
    try {
      MkmdDecoder.decode(new byte[0], aesKey, aesIv);
      check(false, "Should reject empty");
    } catch (MkmdDecoder.DecodeException expected) {
    }
  }

  private static void rejectsBadMagic() throws Exception {
    ByteArrayOutputStream plain = new ByteArrayOutputStream();
    append(plain, 0x42, 0x41, 0x44, 0x44);
    append(plain, 0x01);
    appendLE32(plain, 4);
    appendLE32(plain, 0);
    byte[] padded = padToBlock(plain.toByteArray());
    javax.crypto.Cipher cipher =
        javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(
        javax.crypto.Cipher.ENCRYPT_MODE,
        new javax.crypto.spec.SecretKeySpec(aesKey, "AES"),
        new javax.crypto.spec.IvParameterSpec(aesIv));
    byte[] ciphertext = cipher.doFinal(padded);
    try {
      MkmdDecoder.decode(ciphertext, aesKey, aesIv);
      check(false, "Should reject bad magic");
    } catch (MkmdDecoder.DecodeException expected) {
      check(expected.getMessage().contains("magic"), "Bad magic error");
    }
  }

  private static void rejectsUnsupportedVersion() throws Exception {
    ByteArrayOutputStream plain = new ByteArrayOutputStream();
    append(plain, 0x4D, 0x4B, 0x4D, 0x44);
    append(plain, 0x02);
    appendLE32(plain, 4);
    appendLE32(plain, 0);
    byte[] padded = padToBlock(plain.toByteArray());
    javax.crypto.Cipher cipher =
        javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(
        javax.crypto.Cipher.ENCRYPT_MODE,
        new javax.crypto.spec.SecretKeySpec(aesKey, "AES"),
        new javax.crypto.spec.IvParameterSpec(aesIv));
    byte[] ciphertext = cipher.doFinal(padded);
    try {
      MkmdDecoder.decode(ciphertext, aesKey, aesIv);
      check(false, "Should reject unsupported version");
    } catch (MkmdDecoder.DecodeException expected) {
      check(expected.getMessage().contains("version"), "Unsupported version error");
    }
  }

  private static void rejectsWrongKey() throws Exception {
    byte[] midi = new byte[] {(byte) 0x4D, (byte) 0x54, (byte) 0x68, (byte) 0x64,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x06};
    byte[] ciphertext = buildMkmd(midi);
    byte[] wrongKey = new byte[32];
    Arrays.fill(wrongKey, (byte) 0x99);
    try {
      MkmdDecoder.decode(ciphertext, wrongKey, aesIv);
      check(false, "Should reject wrong key");
    } catch (MkmdDecoder.DecodeException expected) {
    }
  }

  private static void sizeCapRejectsOversizedCompressed() throws Exception {
    ByteArrayOutputStream plain = new ByteArrayOutputStream();
    append(plain, 0x4D, 0x4B, 0x4D, 0x44);
    append(plain, 0x01);
    appendLE32(plain, 100);
    appendLE32(plain, (long) 64 * 1024 * 1024 + 1);
    byte[] padded = padToBlock(plain.toByteArray());
    javax.crypto.Cipher cipher =
        javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(
        javax.crypto.Cipher.ENCRYPT_MODE,
        new javax.crypto.spec.SecretKeySpec(aesKey, "AES"),
        new javax.crypto.spec.IvParameterSpec(aesIv));
    byte[] ciphertext = cipher.doFinal(padded);
    try {
      MkmdDecoder.decode(ciphertext, aesKey, aesIv);
      check(false, "Should reject oversized compressed");
    } catch (MkmdDecoder.DecodeException expected) {
    }
  }

  private static void classifiesKnownSignatures() {
    check(MkmdDecoder.classifyPayloadForTest(new byte[] {(byte) 'M', (byte) 'T', (byte) 'h', (byte) 'd'})
        == MkmdDecoder.Signature.STANDARD_MIDI, "MIDI signature");
    check(MkmdDecoder.classifyPayloadForTest(
        new byte[] {(byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F', (byte) 0, (byte) 0, (byte) 0, (byte) 0,
            (byte) 'W', (byte) 'A', (byte) 'V', (byte) 'E'})
        == MkmdDecoder.Signature.WAVE, "WAVE signature");
    check(MkmdDecoder.classifyPayloadForTest(new byte[] {(byte) 'I', (byte) 'D', (byte) '3'})
        == MkmdDecoder.Signature.MP3_ID3, "MP3 signature");
    check(MkmdDecoder.classifyPayloadForTest(new byte[] {(byte) 9, (byte) 9, (byte) 9})
        == MkmdDecoder.Signature.UNKNOWN, "Unknown signature");
  }

  private static byte[] padToBlock(byte[] data) {
    int padded = (data.length + 15) / 16 * 16;
    byte[] result = new byte[padded];
    System.arraycopy(data, 0, result, 0, data.length);
    return result;
  }

  public static int run() throws Exception {
    checks = 0;
    decodeRoundTripMidi();
    decodeRoundTripWave();
    decodeRoundTripMp3();
    rejectsNonBlockAlignedCiphertext();
    rejectsEmptyCiphertext();
    rejectsBadMagic();
    rejectsUnsupportedVersion();
    rejectsWrongKey();
    sizeCapRejectsOversizedCompressed();
    classifiesKnownSignatures();
    return checks;
  }
}
