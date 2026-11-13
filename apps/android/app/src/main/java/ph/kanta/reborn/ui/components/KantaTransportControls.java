package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.view.View;

import ph.kanta.reborn.R;

// Programmatic-only view: callers supply runtime dependencies; not inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public class KantaTransportControls extends LinearLayout {
  public interface Listener {
    void onShuffle();
    void onPrevious();
    void onPlayPause();
    void onNext();
    void onRepeat();
  }
  private final float density;
  private final Listener listener;
  private boolean playing;
  private KantaImageButton shuffleButton, previousButton, playButton, nextButton, repeatButton;
  private boolean shuffleEnabled, previousEnabled, nextEnabled = true, repeatEnabled;

  public KantaTransportControls(Context context, Listener listener) {
    super(context);
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER);
    build();
  }

  private void build() {
    shuffleButton = new KantaImageButton(getContext(), R.drawable.kanta_shuffle_color, "Shuffle", false);
    shuffleButton.setTag("kanta.transport.shuffle");
    shuffleButton.setOnClickListener(v -> { if (listener != null) listener.onShuffle(); });
    previousButton = new KantaImageButton(getContext(), R.drawable.kanta_previous_color, "Previous track", false);
    previousButton.setTag("kanta.transport.previous");
    previousButton.setOnClickListener(v -> { if (listener != null) listener.onPrevious(); });
    playButton = new KantaImageButton(getContext(),
        playing ? R.drawable.kanta_pause_color : R.drawable.kanta_play_color,
        playing ? "Pause" : "Play", true);
    playButton.setTag("kanta.transport.play-pause");
    playButton.setOnClickListener(v -> { if (listener != null) listener.onPlayPause(); });
    nextButton = new KantaImageButton(getContext(), R.drawable.kanta_next_color, "Next track", false);
    nextButton.setTag("kanta.transport.next");
    nextButton.setOnClickListener(v -> { if (listener != null) listener.onNext(); });
    repeatButton = new KantaImageButton(getContext(), R.drawable.kanta_repeat_color, "Repeat", false);
    repeatButton.setTag("kanta.transport.repeat");
    repeatButton.setOnClickListener(v -> { if (listener != null) listener.onRepeat(); });
    shuffleButton.setEnabled(shuffleEnabled);
    previousButton.setEnabled(previousEnabled);
    nextButton.setEnabled(nextEnabled);
    repeatButton.setEnabled(repeatEnabled);

    // Fixed button widths: 48+48+72+48+48 = 264dp. Use weighted SPACE views between for responsive gaps.
    // Total min gap budget = 4dp * 4 spacers = 16dp. 264 + 16 = 280dp fits 320dp screens.
    int minGapDp = 4;
    int minGap = (int) (minGapDp * density);

    addView(shuffleButton, fixedParams(48));
    addView(space(minGap));
    addView(previousButton, fixedParams(48));
    addView(space(minGap));
    addView(playButton, fixedParams(72));
    addView(space(minGap));
    addView(nextButton, fixedParams(48));
    addView(space(minGap));
    addView(repeatButton, fixedParams(48));
  }

  private LayoutParams fixedParams(int sizeDp) {
    int size = (int) (sizeDp * density);
    return new LayoutParams(size, size);
  }

  private View space(int minGap) {
    View spacer = new View(getContext());
    LayoutParams lp = new LayoutParams(minGap, 0, 1f);
    spacer.setLayoutParams(lp);
    return spacer;
  }

  public void setPlaying(boolean playing) {
    if (this.playing == playing) return;
    this.playing = playing;
    if (playButton != null) {
      playButton.setIcon(
          playing ? R.drawable.kanta_pause_color : R.drawable.kanta_play_color,
          playing ? "Pause" : "Play");
    }
  }

  public void setShuffleEnabled(boolean enabled) {
    shuffleEnabled = enabled;
    if (shuffleButton != null) shuffleButton.setEnabled(enabled);
  }

  public void setPreviousEnabled(boolean enabled) {
    previousEnabled = enabled;
    if (previousButton != null) previousButton.setEnabled(enabled);
  }

  public void setNextEnabled(boolean enabled) {
    nextEnabled = enabled;
    if (nextButton != null) nextButton.setEnabled(enabled);
  }

  public void setRepeatEnabled(boolean enabled) {
    repeatEnabled = enabled;
    if (repeatButton != null) repeatButton.setEnabled(enabled);
  }
}
