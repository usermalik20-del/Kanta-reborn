package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;

import ph.kanta.reborn.R;

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
    addView(shuffleButton, buttonParams(48));
    addView(previousButton, buttonParams(48));
    addView(playButton, buttonParams(72));
    addView(nextButton, buttonParams(48));
    addView(repeatButton, buttonParams(48));
  }

  private LayoutParams buttonParams(int sizeDp) {
    int size = (int) (sizeDp * density);
    return new LayoutParams(size, size);
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
