package ph.kanta.reborn.core.legacy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Inflater;

public final class MkmdDecoder {

  private static final int MKMD_MAGIC = 0x444B4D4D;
  private static final int VERSION_V1 = 0x01;
  private static final int AES_BLOCK = 16;
  private static final int MAX_COMPRESSED_BYTES = 64 * 1024 * 1024;
  private static final int MAX_DECODED_BYTES = 64 * 1024 * 1024;

  private MkmdDecoder() {}

  public static final class DecodedPayload {
    public final byte[] payload;
    public final Signature signature;
    public DecodedPayload(byte[] payload, Signature signature) {
      this.payload = payload;
      this.signature = signature;
    }
  }

  public enum Signature {
    STANDARD_MIDI, WAVE, MP3_ID3, UNKNOWN
  }

  public static class DecodeException extends Exception {
    public DecodeException(String message) { super(message); }
    public DecodeException(String message, Throwable cause) { super(message, cause); }
  }

  public static DecodedPayload decode(byte[] ciphertext, byte[] key, byte[] iv)
      throws DecodeException {
    try {
      if (ciphertext.length == 0 || ciphertext.length % AES_BLOCK != 0) {
        throw new DecodeException("MKMD ciphertext is not AES block-aligned: " + ciphertext.length);
      }
      if (ciphertext.length > MAX_COMPRESSED_BYTES) {
        throw new DecodeException("MKMD ciphertext exceeds 64 MB cap: " + ciphertext.length);
      }

      javax.crypto.Cipher cipher =
          javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
      cipher.init(
          javax.crypto.Cipher.DECRYPT_MODE,
          new javax.crypto.spec.SecretKeySpec(key, "AES"),
          new javax.crypto.spec.IvParameterSpec(iv));
      byte[] decrypted = cipher.doFinal(ciphertext);

      if (decrypted.length < 13) {
        throw new DecodeException("Decrypted MKMD payload too short: " + decrypted.length);
      }

      int magic = (decrypted[0] & 0xFF) | ((decrypted[1] & 0xFF) << 8)
          | ((decrypted[2] & 0xFF) << 16) | ((decrypted[3] & 0xFF) << 24);
      if (magic != MKMD_MAGIC) {
        throw new DecodeException("Bad MKMD magic: 0x" + Integer.toHexString(magic));
      }

      int version = decrypted[4] & 0xFF;
      if (version != VERSION_V1) {
        throw new DecodeException("Unsupported MKMD version: " + version);
      }

      long decodedBytes = readU32LE(decrypted, 5);
      long compressedBytes = readU32LE(decrypted, 9);

      if (decodedBytes > MAX_DECODED_BYTES) {
        throw new DecodeException("MKMD decoded bytes exceed 64 MB cap: " + decodedBytes);
      }
      if (compressedBytes > MAX_COMPRESSED_BYTES) {
        throw new DecodeException("MKMD compressed bytes exceed 64 MB cap: " + compressedBytes);
      }

      int headerSize = 13;
      int compressedLen = decrypted.length - headerSize;
      if (compressedLen != (int) compressedBytes) {
        throw new DecodeException("MKMD compressed length mismatch: declared="
            + compressedBytes + " actual=" + compressedLen);
      }

      Inflater inflater = new Inflater(true);
      inflater.setInput(decrypted, headerSize, compressedLen);

      ByteArrayOutputStream output =
          new ByteArrayOutputStream((int) Math.min(decodedBytes, 1 << 20));
      byte[] buffer = new byte[8192];
      while (!inflater.finished()) {
        int read = inflater.inflate(buffer);
        if (read > 0) {
          output.write(buffer, 0, read);
          if (output.size() > MAX_DECODED_BYTES) {
            throw new DecodeException("MKMD inflated output exceeds 64 MB cap");
          }
        }
      }
      inflater.end();

      byte[] inflated = output.toByteArray();
      if (inflated.length != decodedBytes) {
        throw new DecodeException("MKMD inflated size mismatch: declared="
            + decodedBytes + " actual=" + inflated.length);
      }

      Signature signature = classifyPayload(inflated);
      return new DecodedPayload(inflated, signature);
    } catch (DecodeException e) {
      throw e;
    } catch (Exception e) {
      throw new DecodeException("MKMD decode failed", e);
    }
  }

  private static long readU32LE(byte[] data, int offset) {
    return (data[offset] & 0xFFL) | ((data[offset + 1] & 0xFFL) << 8)
        | ((data[offset + 2] & 0xFFL) << 16) | ((data[offset + 3] & 0xFFL) << 24);
  }

  static Signature classifyPayloadForTest(byte[] payload) {
    return classifyPayload(payload);
  }

  static Signature classifyPayload(byte[] payload) {
    if (payload.length >= 4 && payload[0] == 'M' && payload[1] == 'T'
        && payload[2] == 'h' && payload[3] == 'd') {
      return Signature.STANDARD_MIDI;
    }
    if (payload.length >= 12 && payload[0] == 'R' && payload[1] == 'I'
        && payload[2] == 'F' && payload[3] == 'F' && payload[8] == 'W'
        && payload[9] == 'A' && payload[10] == 'V' && payload[11] == 'E') {
      return Signature.WAVE;
    }
    if (payload.length >= 3 && payload[0] == 'I' && payload[1] == 'D' && payload[2] == '3') {
      return Signature.MP3_ID3;
    }
    return Signature.UNKNOWN;
  }
}
