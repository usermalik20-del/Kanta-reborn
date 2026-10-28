package ph.kanta.reborn;

import android.media.*;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.concurrent.*;
import ph.kanta.reborn.core.MidiLyrics;
import ph.kanta.reborn.core.RenderedPlaybackClock;

/** One worker owns synthesis, AudioTrack, MIDI scheduling and the audible clock. */
final class SoundFontPlayback implements PlaybackBackend {
  private static final int RATE = 44100, FRAMES = 512;
  private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Callback callback;
  private final MidiLyrics.Document document;
  private final RenderedPlaybackClock clock = new RenderedPlaybackClock(RATE);
  private final float[] buffer = new float[FRAMES * 2];
  private AudioTrack output;
  private long bank, framesWritten, framesRendered;
  private int event, buffered = buffer.length;
  private double sourceTime;
  private float tempo = 1;
  private boolean playing, completed, pumpQueued;
  private volatile boolean disposed;
  private volatile long audiblePosition;
  private final ph.kanta.reborn.core.AudibleControlFence controlFence =
      new ph.kanta.reborn.core.AudibleControlFence();
  private float requestedRate = 1;
  private int requestedPitch;

  SoundFontPlayback(File file, MidiLyrics.Document document, Callback callback) {
    this.document = document;
    this.callback = callback;
    worker.execute(
        () -> {
          try {
            bank = NativeSoundFont.open(file.getAbsolutePath());
            if (bank == 0) throw new IllegalArgumentException("Unsupported SoundFont");
            int min =
                AudioTrack.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
            if (min <= 0) throw new IllegalStateException("Audio output unavailable");
            output =
                new AudioTrack.Builder()
                    .setAudioAttributes(
                        new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(
                        new AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, FRAMES * 2 * 4 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            if (output.getState() != AudioTrack.STATE_INITIALIZED)
              throw new IllegalStateException();
            main.post(
                () -> {
                  if (!disposed) callback.ready(this);
                });
          } catch (Exception | LinkageError e) {
            fail();
          }
        });
  }

  private void enqueue(Runnable work) {
    if (!disposed) {
      try {
        worker.execute(
            () -> {
              if (!disposed) work.run();
            });
      } catch (RejectedExecutionException ignored) {
      }
    }
  }

  public void start() {
    enqueue(
        () -> {
          try {
            playing = true;
            output.play();
            main.post(
                () -> {
                  if (!disposed) callback.started(this);
                });
            schedule();
          } catch (Exception e) {
            fail();
          }
        });
  }

  public void pause() {
    enqueue(
        () -> {
          playing = false;
          if (output != null) {
            output.pause();
            audiblePosition =
                clock.position(Integer.toUnsignedLong(output.getPlaybackHeadPosition()));
          }
        });
  }

  public void controls(float volume, float rate, int pitch) {
    boolean changed = requestedRate != rate || requestedPitch != pitch;
    requestedRate = rate;
    requestedPitch = pitch;
    long request = changed ? controlFence.request() : 0;
    enqueue(
        () -> {
          tempo = rate;
          if (output != null) output.setVolume(volume);
          if (bank != 0) NativeSoundFont.tune(bank, pitch);
          if (changed) controlFence.rendered(request, framesRendered);
        });
  }

  public boolean controlsSettled() {
    return controlFence.settled();
  }

  public long position() {
    return Math.min(document.durationMilliseconds, audiblePosition);
  }

  /** Returns the audible clock for lyric synchronization. */
  public RenderedPlaybackClock getClock() {
    return clock;
  }

  public void release() {
    if (disposed) return;
    disposed = true;
    worker.execute(
        () -> {
          close();
          worker.shutdown();
        });
  }

  private void close() {
    playing = false;
    if (output != null) {
      output.release();
      output = null;
    }
    if (bank != 0) {
      NativeSoundFont.close(bank);
      bank = 0;
    }
  }

  private void fail() {
    if (disposed) return;
    close();
    main.post(
        () -> {
          if (!disposed) callback.failed(this);
        });
  }

  private void schedule() {
    if (pumpQueued || disposed || !playing) return;
    pumpQueued = true;
    worker.schedule(
        () -> {
          pumpQueued = false;
          pump();
        },
        3,
        TimeUnit.MILLISECONDS);
  }

  private void pump() {
    if (disposed || !playing || output == null) return;
    try {
      long head = Integer.toUnsignedLong(output.getPlaybackHeadPosition());
      audiblePosition = clock.position(head);
      controlFence.heard(head);
      if (sourceTime >= document.durationMilliseconds + 800 && buffered == buffer.length) {
        if (head >= framesWritten && !completed) {
          completed = true;
          playing = false;
          output.pause();
          main.post(
              () -> {
                if (!disposed) callback.completed(this);
              });
          return;
        }
        schedule();
        return;
      }
      if (buffered == buffer.length) {
        clock.append(framesRendered, sourceTime, tempo);
        int offset = 0;
        while (offset < FRAMES) {
          while (event < document.messages.size()
              && document.messages.get(event).milliseconds <= sourceTime) {
            MidiLyrics.Message message = document.messages.get(event++);
            NativeSoundFont.midi(bank, message.status, message.a, message.b);
          }
          int count = FRAMES - offset;
          if (event < document.messages.size()) {
            double until =
                (document.messages.get(event).milliseconds - sourceTime) * RATE / (1000.0 * tempo);
            count = Math.min(count, Math.max(1, (int) Math.ceil(until)));
          }
          NativeSoundFont.render(bank, buffer, offset * 2, count);
          sourceTime += count * 1000.0 * tempo / RATE;
          offset += count;
        }
        framesRendered += FRAMES;
        buffered = 0;
      }
      int written =
          output.write(buffer, buffered, buffer.length - buffered, AudioTrack.WRITE_NON_BLOCKING);
      if (written < 0) throw new IllegalStateException("Audio output stopped");
      buffered += written;
      framesWritten += written / 2;
      schedule();
    } catch (Exception e) {
      fail();
    }
  }
}
