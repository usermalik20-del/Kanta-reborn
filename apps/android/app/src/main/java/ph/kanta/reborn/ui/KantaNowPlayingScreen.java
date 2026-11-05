package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import ph.kanta.reborn.ui.components.KantaIconButton;
import ph.kanta.reborn.ui.components.KantaProgressBar;
import ph.kanta.reborn.ui.components.KantaScreenHeader;
import ph.kanta.reborn.ui.components.KantaStepper;
import ph.kanta.reborn.ui.components.KantaTransportControls;

public class KantaNowPlayingScreen extends LinearLayout {
  public interface Listener {
    void onBack();
    void onFullscreen();
    void onPlayPause();
    void onPrevious();
    void onNext();
    void onShuffle();
    void onRepeat();
    void onQueue();
    void onEffects();
    void onCoach();
    void onKeyDown();
    void onKeyUp();
    void onTempoDown();
    void onTempoUp();
    void onFavorite();
    void onOverflow();
  }
  private final Context context;
  private final float density;
  private final Listener listener;
  private final View stageView;
  private String songTitle = "Nothing playing";
  private String songArtist = "Reserve a song to begin";
  private String currentTime = "0:00";
  private String duration = "—";
  private float progress = 0f;
  private boolean playing = false;
  private int key = 0;
  private float tempo = 1.0f;
  private TextView titleView, artistView, currentTimeView, durationView;
  private KantaProgressBar progressBar;
  private KantaTransportControls transport;
  private KantaStepper keyStepper, tempoStepper;
  private TextView lyricLine1, lyricLine2, lyricLine3, lyricLine4;
  public KantaNowPlayingScreen(Context context, Listener listener) {
    this(context, listener, null);
  }

  public KantaNowPlayingScreen(Context context, Listener listener, View stageView) {
    super(context);
    this.context = context;
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    this.stageView = stageView;
    setOrientation(VERTICAL);
    setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.BG_MAIN);
    build();
  }
  private void build() {
    KantaScreenHeader header = new KantaScreenHeader(
        context, "Now Playing", "Back to Home", "kanta.screen.now-playing.back",
        v -> { if (listener != null) listener.onBack(); });
    header.addAction("Full screen", "kanta.now-playing.fullscreen",
        v -> { if (listener != null) listener.onFullscreen(); });
    addView(header);
    FrameLayout stage = new FrameLayout(context);
    stage.setBackground(ph.kanta.reborn.ui.KantaTheme.stageBackground(context, false));
    LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0);
    stageParams.weight = 1;
    stage.setLayoutParams(stageParams);
    if (stageView != null) {
      if (stageView.getParent() instanceof ViewGroup) {
        ((ViewGroup) stageView.getParent()).removeView(stageView);
      }
      stage.addView(stageView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }
    LinearLayout lyrics = new LinearLayout(context);
    lyrics.setOrientation(VERTICAL);
    lyrics.setGravity(Gravity.CENTER);
    lyrics.setPadding((int)(32*density), 0, (int)(32*density), 0);
    lyricLine1 = lyric("", ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED, 17);
    lyricLine2 = lyric("No timed lyrics loaded", ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY, 23);
    lyricLine3 = lyric("", ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED, 17);
    lyricLine4 = lyric("", ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED, 17);
    lyrics.addView(lyricLine1); lyrics.addView(lyricLine2);
    lyrics.addView(lyricLine3); lyrics.addView(lyricLine4);
    if (stageView == null) {
      FrameLayout.LayoutParams lyricParams = new FrameLayout.LayoutParams(
          LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
      stage.addView(lyrics, lyricParams);
    } else {
      // StageSurface already owns the selected background, camera/video layers and timed lyrics.
      lyrics.setVisibility(View.GONE);
    }
    addView(stage);
    LinearLayout info = new LinearLayout(context);
    info.setOrientation(HORIZONTAL);
    info.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
    info.setPadding((int)(16*density),(int)(8*density),(int)(16*density),(int)(4*density));
    titleView = new TextView(context);
    titleView.setText(songTitle);
    titleView.setTextSize(20);
    titleView.setTypeface(null, Typeface.BOLD);
    titleView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    artistView = new TextView(context);
    artistView.setText(songArtist);
    artistView.setTextSize(14);
    artistView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    LinearLayout infoCol = new LinearLayout(context);
    infoCol.setOrientation(VERTICAL);
    infoCol.setGravity(Gravity.CENTER);
    infoCol.addView(titleView); infoCol.addView(artistView);
    info.addView(infoCol);
    ph.kanta.reborn.ui.components.KantaImageButton favorite =
        new ph.kanta.reborn.ui.components.KantaImageButton(
            context, ph.kanta.reborn.R.drawable.kanta_favorite_color, "Favorite song", false);
    favorite.setEnabled(false);
    favorite.setOnClickListener(v -> { if (listener != null) listener.onFavorite(); });
    LinearLayout.LayoutParams favParams = new LinearLayout.LayoutParams((int)(48*density), (int)(48*density));
    favParams.weight = 1; favParams.gravity = Gravity.END;
    favorite.setLayoutParams(favParams);
    info.addView(favorite);
    ph.kanta.reborn.ui.components.KantaImageButton overflow =
        new ph.kanta.reborn.ui.components.KantaImageButton(
            context, ph.kanta.reborn.R.drawable.kanta_more_color, "More playback options", false);
    overflow.setEnabled(false);
    overflow.setOnClickListener(v -> { if (listener != null) listener.onOverflow(); });
    info.addView(overflow);
    addView(info);
    LinearLayout progressRow = new LinearLayout(context);
    progressRow.setOrientation(HORIZONTAL);
    progressRow.setGravity(Gravity.CENTER_VERTICAL);
    progressRow.setPadding((int)(16*density), 0, (int)(16*density), (int)(8*density));
    currentTimeView = new TextView(context);
    currentTimeView.setText(currentTime);
    currentTimeView.setTextSize(12);
    currentTimeView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    progressRow.addView(currentTimeView);
    progressBar = new KantaProgressBar(context);
    progressBar.setProgress(progress);
    LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, (int)(6*density));
    pbParams.weight = 1;
    pbParams.leftMargin = (int)(12*density); pbParams.rightMargin = (int)(12*density);
    progressBar.setLayoutParams(pbParams);
    progressRow.addView(progressBar);
    durationView = new TextView(context);
    durationView.setText(duration);
    durationView.setTextSize(12);
    durationView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    progressRow.addView(durationView);
    addView(progressRow);
    transport = new KantaTransportControls(context, new KantaTransportControls.Listener() {
      @Override public void onShuffle() { if (listener != null) listener.onShuffle(); }
      @Override public void onPrevious() { if (listener != null) listener.onPrevious(); }
      @Override public void onPlayPause() { if (listener != null) listener.onPlayPause(); }
      @Override public void onNext() { if (listener != null) listener.onNext(); }
      @Override public void onRepeat() { if (listener != null) listener.onRepeat(); }
    });
    transport.setPlaying(playing);
    LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    tParams.topMargin = (int)(8*density); tParams.bottomMargin = (int)(4*density);
    addView(transport, tParams);
    LinearLayout perfRow = new LinearLayout(context);
    perfRow.setOrientation(HORIZONTAL);
    perfRow.setGravity(Gravity.CENTER);
    perfRow.setPadding((int)(16*density), 0, (int)(16*density), 0);
    LinearLayout keyCol = new LinearLayout(context);
    keyCol.setOrientation(VERTICAL);
    keyCol.setGravity(Gravity.CENTER);
    TextView keyLabel = new TextView(context);
    keyLabel.setText("Key");
    keyLabel.setTextSize(12);
    keyLabel.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    keyStepper = new KantaStepper(context);
    keyStepper.setValue(String.valueOf(key));
    keyStepper.setOnChangeListener(new KantaStepper.OnChangeListener() {
      @Override public void onDecrement() { if (listener != null) listener.onKeyDown(); }
      @Override public void onIncrement() { if (listener != null) listener.onKeyUp(); }
    });
    keyCol.addView(keyLabel); keyCol.addView(keyStepper);
    TextView divider = new TextView(context);
    divider.setText("|");
    divider.setTextSize(14);
    divider.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
    divider.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    dp.leftMargin = (int)(16*density); dp.rightMargin = (int)(16*density);
    divider.setLayoutParams(dp);
    LinearLayout tempoCol = new LinearLayout(context);
    tempoCol.setOrientation(VERTICAL);
    tempoCol.setGravity(Gravity.CENTER);
    TextView tempoLabel = new TextView(context);
    tempoLabel.setText("Tempo");
    tempoLabel.setTextSize(12);
    tempoLabel.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    tempoStepper = new KantaStepper(context);
    tempoStepper.setValue(String.format("%.0f%%", tempo * 100f));
    tempoStepper.setOnChangeListener(new KantaStepper.OnChangeListener() {
      @Override public void onDecrement() { if (listener != null) listener.onTempoDown(); }
      @Override public void onIncrement() { if (listener != null) listener.onTempoUp(); }
    });
    tempoCol.addView(tempoLabel); tempoCol.addView(tempoStepper);
    perfRow.addView(keyCol); perfRow.addView(divider); perfRow.addView(tempoCol);
    addView(perfRow);
    LinearLayout bottomNav = new LinearLayout(context);
    bottomNav.setOrientation(HORIZONTAL);
    bottomNav.setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.BG_ELEVATED);
    bottomNav.setPadding(0, (int)(8*density), 0, (int)(16*density));
    String[] navLabels = {"Queue", "Lyrics", "Effects", "Coach"};
    int[] navIcons = {
        ph.kanta.reborn.R.drawable.kanta_queue_color,
        ph.kanta.reborn.R.drawable.kanta_library_color,
        ph.kanta.reborn.R.drawable.kanta_visuals_color,
        ph.kanta.reborn.R.drawable.kanta_microphone_color
    };
    for (int i = 0; i < navLabels.length; i++) {
      final int destination = i;
      Button b = new Button(context);
      b.setText(navLabels[i]);
      b.setTextSize(11);
      b.setTypeface(null, Typeface.BOLD);
      b.setGravity(Gravity.CENTER);
      boolean selected = i == 1;
      b.setTextColor(selected ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
      android.graphics.drawable.Drawable icon = context.getDrawable(navIcons[i]).mutate();
      b.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
      b.setCompoundDrawablePadding((int)(3*density));
      b.setBackground(selected
          ? ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
              ph.kanta.reborn.ui.KantaTheme.SURFACE, 12,
              ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density)
          : null);
      b.setContentDescription(navLabels[i]);
      LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
      navParams.bottomMargin = (int)(6*density);
      b.setLayoutParams(navParams);
      switch (destination) {
        case 0: b.setTag("kanta.now-playing.queue"); b.setOnClickListener(v -> { if (listener != null) listener.onQueue(); }); break;
        case 1: b.setTag("kanta.now-playing.lyrics"); break;
        case 2: b.setTag("kanta.now-playing.effects"); b.setOnClickListener(v -> { if (listener != null) listener.onEffects(); }); break;
        case 3: b.setTag("kanta.now-playing.coach"); b.setOnClickListener(v -> { if (listener != null) listener.onCoach(); }); break;
      }
      bottomNav.addView(b);
    }
    addView(bottomNav);
  }
  private TextView lyric(String text, int color, float sizeSp) {
    TextView tv = new TextView(context);
    tv.setText(text);
    tv.setTextSize(sizeSp);
    tv.setTextColor(color);
    tv.setTypeface(null, sizeSp >= 22 ? Typeface.BOLD : Typeface.NORMAL);
    tv.setGravity(Gravity.CENTER);
    tv.setLineSpacing(4, 1.0f);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    p.topMargin = (int)(6*density); p.bottomMargin = (int)(6*density);
    tv.setLayoutParams(p);
    return tv;
  }
  public void setSongInfo(String title, String artist) {
    songTitle = title; songArtist = artist;
    if (titleView != null) titleView.setText(title);
    if (artistView != null) artistView.setText(artist);
  }
  public void setLyrics(String l1, String l2, String l3, String l4) {
    setLyrics(l1, l2, l3, l4, 0f);
  }
  public void setLyrics(String l1, String l2, String l3, String l4, float highlightedCharacters) {
    if (lyricLine1 != null) lyricLine1.setText(l1 == null ? "" : l1);
    if (lyricLine2 != null) {
      String line = l2 == null ? "" : l2;
      SpannableString styled = new SpannableString(line);
      int end = Math.max(0, Math.min(line.length(), (int) Math.floor(Math.max(0f, highlightedCharacters))));
      if (end > 0) styled.setSpan(new ForegroundColorSpan(
          ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      lyricLine2.setText(styled);
    }
    if (lyricLine3 != null) lyricLine3.setText(l3 == null ? "" : l3);
    if (lyricLine4 != null) lyricLine4.setText(l4 == null ? "" : l4);
  }
  public void setProgress(float p) { progress = p; if (progressBar != null) progressBar.setProgress(p); }
  public void setTimes(String current, String dur) {
    currentTime = current; duration = dur;
    if (currentTimeView != null) currentTimeView.setText(current);
    if (durationView != null) durationView.setText(dur);
  }
  public void setPlaying(boolean p) { if(playing==p)return;playing=p;if(transport!=null)transport.setPlaying(p); }
  public void setTransportCapabilities(boolean canNext){if(transport==null)return;transport.setShuffleEnabled(false);transport.setPreviousEnabled(false);transport.setNextEnabled(canNext);transport.setRepeatEnabled(false);}
  public void setKey(int k){key=k;if(keyStepper!=null)keyStepper.setValue(key>0?"+"+key:String.valueOf(key));}
  public void setTempo(float t){tempo=t;if(tempoStepper!=null)tempoStepper.setValue(String.format(java.util.Locale.ROOT,"%.0f%%",t*100f));}
}
