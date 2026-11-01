package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;

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
  private boolean playing = false;
  public KantaTransportControls(Context context, Listener listener) {
    super(context);
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER);
    build();
  }
  private void build() {
    KantaIconButton shuffle = new KantaIconButton(getContext());
    shuffle.setText("\u27F3"); shuffle.setTextSize(16);
    shuffle.setOnClickListener(v -> { if (listener != null) listener.onShuffle(); });
    KantaIconButton prev = new KantaIconButton(getContext());
    prev.setText("\u23EE"); prev.setTextSize(18);
    prev.setOnClickListener(v -> { if (listener != null) listener.onPrevious(); });
    KantaIconButton play = new KantaIconButton(getContext());
    play.setText(playing ? "\u23F8" : "\u25B6"); play.setTextSize(26);
    play.applyStyle();
    play.setActive(true);
    play.setTextColor(ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN);
    play.setOnClickListener(v -> {
      playing = !playing;
      play.setText(playing ? "\u23F8" : "\u25B6");
      if (listener != null) listener.onPlayPause();
    });
    KantaIconButton next = new KantaIconButton(getContext());
    next.setText("\u23ED"); next.setTextSize(18);
    next.setOnClickListener(v -> { if (listener != null) listener.onNext(); });
    KantaIconButton repeat = new KantaIconButton(getContext());
    repeat.setText("\u27F5"); repeat.setTextSize(16);
    repeat.setOnClickListener(v -> { if (listener != null) listener.onRepeat(); });
    addView(shuffle, new LayoutParams((int)(48*density),(int)(48*density)));
    addView(prev, new LayoutParams((int)(48*density),(int)(48*density)));
    addView(play, new LayoutParams((int)(72*density),(int)(72*density)));
    addView(next, new LayoutParams((int)(48*density),(int)(48*density)));
    addView(repeat, new LayoutParams((int)(48*density),(int)(48*density)));
  }
  public void setPlaying(boolean playing) {
    this.playing = playing;
    removeAllViews();
    build();
  }
}
