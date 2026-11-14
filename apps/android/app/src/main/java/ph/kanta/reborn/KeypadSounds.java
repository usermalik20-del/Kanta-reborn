package ph.kanta.reborn;

import android.media.AudioAttributes;
import android.media.SoundPool;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Low-latency keypad press tones. One stream only; never overlaps karaoke. */
final class KeypadSounds {
  private static final String[] NAMES = {
      "Zero","One","Two","Three","Four","Five","Six","Seven","Eight","Nine","Delete","Reserve"
  };
  private final SoundPool pool;
  private final int[] ids = new int[NAMES.length];
  private final Map<String, Integer> keyToIndex = new HashMap<>();
  private final Set<Integer> ready = new HashSet<>();

  KeypadSounds() {
    pool = new SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
        .build();
    pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
      @Override
      public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
        if (status == 0) ready.add(sampleId);
      }
    });
    // Map human key strings to pool indices.
    for (int i = 0; i < 10; i++) keyToIndex.put(String.valueOf(i), i);
    keyToIndex.put("delete", 10);
    keyToIndex.put("reserve", 11);
  }

  void load(File dir) {
    for (int i = 0; i < NAMES.length; i++) {
      File f = new File(dir, NAMES[i] + ".mp3");
      ids[i] = pool.load(f.getAbsolutePath(), 1);
    }
  }

  /** Plays a tone if ready and the index maps to a known key. Returns true if played. */
  boolean play(String key) {
    Integer idx = keyToIndex.get(key);
    if (idx == null || !ready.contains(ids[idx])) return false;
    int stream = pool.play(ids[idx], 0.5f, 0.5f, 1, 0, 1.0f);
    return stream != 0;
  }

  int loadedCount() { return ready.size(); }
}
