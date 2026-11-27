package ph.kanta.reborn;

/** Native handles and their allocations belong to the calling synthesis worker. */
final class NativeSoundFont {
  /** Reasons for a failed {@link #open(String)}; 0 after a successful open. */
  static final int OPEN_OK = 0;
  static final int OPEN_UNREADABLE = 1;
  static final int OPEN_ADDRESS_SPACE = 2;
  static final int OPEN_BAD_STRUCTURE = 3;
  static final int OPEN_NO_MEMORY = 4;

  static {
    System.loadLibrary("kanta_audio");
  }

  static native long open(String path);

  /** Reason for the most recent failed open on this thread's bank loader. */
  static native int openReason();

  static native void close(long handle);

  static native void midi(long handle, int status, int a, int b);

  static native void tune(long handle, float semitones);

  static native void render(long handle, float[] output, int offset, int frames);
}
