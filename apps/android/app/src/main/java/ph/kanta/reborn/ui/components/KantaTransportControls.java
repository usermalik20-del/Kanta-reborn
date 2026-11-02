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
  private KantaIconButton shuffleButton, previousButton, playButton, nextButton, repeatButton;
  private boolean shuffleEnabled, previousEnabled, nextEnabled=true, repeatEnabled;
  public KantaTransportControls(Context context, Listener listener) {
    super(context);
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER);
    build();
  }
  private void build() {
    shuffleButton=new KantaIconButton(getContext());shuffleButton.setText("\u27F3");shuffleButton.setTextSize(16);shuffleButton.setOnClickListener(v->{if(listener!=null)listener.onShuffle();});
    previousButton=new KantaIconButton(getContext());previousButton.setText("\u23EE");previousButton.setTextSize(18);previousButton.setOnClickListener(v->{if(listener!=null)listener.onPrevious();});
    playButton=new KantaIconButton(getContext());playButton.setText(playing?"\u23F8":"\u25B6");playButton.setTextSize(26);playButton.applyStyle();playButton.setActive(true);playButton.setTextColor(ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN);playButton.setOnClickListener(v->{if(listener!=null)listener.onPlayPause();});
    nextButton=new KantaIconButton(getContext());nextButton.setText("\u23ED");nextButton.setTextSize(18);nextButton.setOnClickListener(v->{if(listener!=null)listener.onNext();});
    repeatButton=new KantaIconButton(getContext());repeatButton.setText("\u27F5");repeatButton.setTextSize(16);repeatButton.setOnClickListener(v->{if(listener!=null)listener.onRepeat();});
    shuffleButton.setEnabled(shuffleEnabled);previousButton.setEnabled(previousEnabled);nextButton.setEnabled(nextEnabled);repeatButton.setEnabled(repeatEnabled);
    addView(shuffleButton,new LayoutParams((int)(48*density),(int)(48*density)));addView(previousButton,new LayoutParams((int)(48*density),(int)(48*density)));addView(playButton,new LayoutParams((int)(72*density),(int)(72*density)));addView(nextButton,new LayoutParams((int)(48*density),(int)(48*density)));addView(repeatButton,new LayoutParams((int)(48*density),(int)(48*density)));
  }
  public void setPlaying(boolean playing){if(this.playing==playing)return;this.playing=playing;if(playButton!=null)playButton.setText(playing?"\u23F8":"\u25B6");}
  public void setShuffleEnabled(boolean enabled){shuffleEnabled=enabled;if(shuffleButton!=null)shuffleButton.setEnabled(enabled);}
  public void setPreviousEnabled(boolean enabled){previousEnabled=enabled;if(previousButton!=null)previousButton.setEnabled(enabled);}
  public void setNextEnabled(boolean enabled){nextEnabled=enabled;if(nextButton!=null)nextButton.setEnabled(enabled);}
  public void setRepeatEnabled(boolean enabled){repeatEnabled=enabled;if(repeatButton!=null)repeatButton.setEnabled(enabled);}
}
