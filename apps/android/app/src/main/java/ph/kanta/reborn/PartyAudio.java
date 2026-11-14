package ph.kanta.reborn;

import android.media.*;
import android.os.Handler;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/** Main-Looper auxiliary audio. It never consumes karaoke reservations. */
final class PartyAudio {
  private final SoundPool pads =
      new SoundPool.Builder()
          .setMaxStreams(4)
          .setAudioAttributes(
              new AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_MEDIA)
                  .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                  .build())
          .build();
  private final int[] builtins = new int[8], selected = new int[8];
  private final Set<Integer> ready = new HashSet<>();
  private final Map<Integer, Consumer<Boolean>> pending = new HashMap<>();
  private final ArrayDeque<Integer> voices = new ArrayDeque<>();
  private final Consumer<String> failed;
  private final Handler mainHandler = new Handler(android.os.Looper.getMainLooper());
  private int builtinLoads;
  private final long[] versions = new long[8];
  private MediaPlayer loop;
  private File loopFile;
  private boolean loopStarted;
  private boolean welcomePlayed = false;
  float sfxVolume = .6f, loopVolume = .35f;

  PartyAudio(Runnable changed, Consumer<String> failed) {
    this.failed = failed;
    pads.setOnLoadCompleteListener(
        (pool, id, status) -> {
          if (status == 0) ready.add(id);
          Consumer<Boolean> callback = pending.remove(id);
          if (callback != null) callback.accept(status == 0);
          for (int builtin : builtins) if (builtin == id && ++builtinLoads == 8) changed.run();
        });
  }

  void loadBuiltins(List<File> files) {
    for (int i = 0; i < 8; i++) {
      builtins[i] = pads.load(files.get(i).getAbsolutePath(), 1);
      selected[i] = builtins[i];
    }
  }

  boolean ready(int slot) {
    return slot >= 0 && slot < selected.length && ready.contains(selected[slot]);
  }

  boolean trigger(int slot) {
    if (!ready(slot)) return false;
    if (voices.size() == 4) pads.stop(voices.removeFirst());
    int stream = pads.play(selected[slot], sfxVolume, sfxVolume, 1, 0, 1);
    if (stream == 0) return false;
    voices.addLast(stream);
    return true;
  }

  void assign(int slot, File file, Consumer<Boolean> done) {
    if (slot < 0 || slot >= 8) {
      done.accept(false);
      return;
    }
    long version = ++versions[slot];
    int id = pads.load(file.getAbsolutePath(), 1);
    if (id == 0) {
      done.accept(false);
      return;
    }
    pending.put(
        id,
        success -> {
          if (version != versions[slot]) {
            pads.unload(id);
            ready.remove(id);
            done.accept(false);
            return;
          }
          if (success) {
            if (selected[slot] != builtins[slot]) {
              pads.unload(selected[slot]);
              ready.remove(selected[slot]);
            }
            selected[slot] = id;
          } else pads.unload(id);
          done.accept(success);
        });
  }

  void reset(int slot) {
    if (slot < 0 || slot >= 8) return;
    versions[slot]++;
    if (selected[slot] != builtins[slot]) {
      pads.unload(selected[slot]);
      ready.remove(selected[slot]);
    }
    selected[slot] = builtins[slot];
  }

  void volumes(float sfx, float intermission) {
    sfxVolume = Math.max(0, Math.min(1, Float.isFinite(sfx) ? sfx : 0));
    loopVolume = Math.max(0, Math.min(1, Float.isFinite(intermission) ? intermission : 0));
    for (int voice : voices) pads.setVolume(voice, sfxVolume, sfxVolume);
    if (loop != null) loop.setVolume(loopVolume, loopVolume);
  }

  /** Plays the bundled welcome interlude once on the first allowed call. Subsequent calls are no-ops. */
  void playWelcomeOnce(File file, boolean allowed) {
    if (!allowed || file == null || welcomePlayed) return;
    welcomePlayed = true;
    MediaPlayer candidate = new MediaPlayer();
    try {
      candidate.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build());
      candidate.setDataSource(file.getAbsolutePath());
      candidate.setVolume(loopVolume, loopVolume);
      candidate.setOnPreparedListener(
          player -> {
            if (player == candidate) {
              try {
                player.start();
              } catch (RuntimeException error) {
                failed.accept("Between-song audio output is unavailable.");
              }
            }
          });
      candidate.setOnCompletionListener(player -> player.release());
      candidate.setOnErrorListener(
          (player, what, extra) -> {
            failed.accept("Between-song music could not be played.");
            return true;
          });
      candidate.prepareAsync();
    } catch (Exception error) {
      failed.accept("Between-song music is unavailable.");
    }
  }

  /** Resets the welcome-played flag (e.g., when user re-enables intermission). */
  void resetWelcomePlayed() {
    welcomePlayed = false;
  }

  void loop(File file, boolean allowed) {
    if (!allowed || file == null) {
      stopLoop();
      return;
    }
    if (loop != null && file.equals(loopFile)) return;
    stopLoop();
    MediaPlayer candidate = new MediaPlayer();
    loop = candidate;
    loopFile = file;
    try {
      candidate.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build());
      candidate.setDataSource(file.getAbsolutePath());
      candidate.setLooping(true);
      candidate.setVolume(loopVolume, loopVolume);
      candidate.setOnPreparedListener(
          player -> {
            if (loop == player) {
              try {
                player.start();
                loopStarted = true;
              } catch (RuntimeException error) {
                stopLoop();
                failed.accept("Between-song audio output is unavailable.");
              }
            }
          });
      candidate.setOnErrorListener(
          (player, what, extra) -> {
            if (loop == player) {
              stopLoop();
              failed.accept("Between-song music could not be played.");
            }
            return true;
          });
      candidate.prepareAsync();
    } catch (Exception error) {
      stopLoop();
      failed.accept("Between-song music is unavailable.");
    }
  }

  boolean loopPlaying() {
    return loopStarted;
  }

  int activeVoices() {
    return voices.size();
  }

  void stopLoop() {
    loopStarted = false;
    if (loop != null) {
      loop.release();
      loop = null;
      loopFile = null;
    }
  }

  void stopAll() {
    for (int voice : voices) pads.stop(voice);
    voices.clear();
    stopLoop();
  }
}
