package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import ph.kanta.reborn.ui.components.KantaIconButton;
import ph.kanta.reborn.ui.components.KantaProgressBar;
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
    super(context);
    this.context = context;
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    setOrientation(VERTICAL);
    setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.BG_MAIN);
    build();
  }
  private void build() {
    LinearLayout top = new LinearLayout(context);
    top.setOrientation(HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(8*density));
    KantaIconButton back = new KantaIconButton(context);
    back.setTag("kanta.screen.now-playing.back");
    back.setText("\u2190"); back.setTextSize(18);
    back.setOnClickListener(v -> { if (listener != null) listener.onBack(); });
    TextView nowPlaying = new TextView(context);
    nowPlaying.setText("Now Playing");
    nowPlaying.setTextSize(18);
    nowPlaying.setTypeface(null, Typeface.BOLD);
    nowPlaying.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    np.weight = 1;
    nowPlaying.setLayoutParams(np);
    KantaIconButton fullscreen = new KantaIconButton(context);
    fullscreen.setTag("kanta.now-playing.fullscreen");
    fullscreen.setText("\u2306"); fullscreen.setTextSize(18);
    fullscreen.setOnClickListener(v -> { if (listener != null) listener.onFullscreen(); });
    top.addView(back); top.addView(nowPlaying); top.addView(fullscreen);
    addView(top);
    LinearLayout stage = new LinearLayout(context);
    stage.setOrientation(VERTICAL);
    stage.setGravity(Gravity.CENTER);
    stage.setBackground(ph.kanta.reborn.ui.KantaTheme.stageBackground(context, false));
    LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0);
    stageParams.weight = 1;
    stage.setLayoutParams(stageParams);
    LinearLayout lyrics = new LinearLayout(context);
    lyrics.setOrientation(VERTICAL);
    lyrics.setGravity(Gravity.CENTER);
    lyrics.setPadding((int)(32*density), 0, (int)(32*density), 0);
    lyricLine1 = lyric("Previous line lyric text here", ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED, 18);
    lyricLine2 = lyric("Current line \u2014 bright white", ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY, 24);
    lyricLine3 = lyric("Next upcoming line", ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED, 18);
    lyricLine4 = lyric("Following upcoming line", ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED, 18);
    lyrics.addView(lyricLine1); lyrics.addView(lyricLine2);
    lyrics.addView(lyricLine3); lyrics.addView(lyricLine4);
    stage.addView(lyrics);
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
    KantaIconButton favorite = new KantaIconButton(context);
    favorite.setText("\u2605"); favorite.setTextSize(20);
    favorite.setEnabled(false); favorite.setAlpha(.4f);
    favorite.setOnClickListener(v -> { if (listener != null) listener.onFavorite(); });
    LinearLayout.LayoutParams favParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    favParams.weight = 1; favParams.gravity = Gravity.END;
    favorite.setLayoutParams(favParams);
    info.addView(favorite);
    KantaIconButton overflow = new KantaIconButton(context);
    overflow.setText("\u22EE"); overflow.setTextSize(20);
    overflow.setEnabled(false); overflow.setAlpha(.4f);
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
    String[][] nav = {
        {"Queue", "\uD83D\uDC51", "onQueue", "false"},
        {"Lyrics", "\ud83c\udf1e", "onLyrics", "true"},
        {"Effects", "\ud83c\udfab", "onEffects", "false"},
        {"Coach", "\ud83d\udcbb", "onCoach", "false"},
    };
    for (String[] n : nav) {
      Button b = new Button(context);
      b.setText(n[0] + "\n" + n[1]);
      b.setTextSize(12);
      b.setTypeface(null, Typeface.BOLD);
      b.setGravity(Gravity.CENTER);
      b.setTextColor(n[3].equals("true") ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
      b.setBackground(null);
      LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
      navParams.bottomMargin = (int)(6*density);
      b.setLayoutParams(navParams);
      switch (n[2]) {
        case "onQueue": b.setTag("kanta.now-playing.queue"); b.setOnClickListener(v -> { if (listener != null) listener.onQueue(); }); break;
        case "onLyrics": b.setTag("kanta.now-playing.lyrics"); break;
        case "onEffects": b.setTag("kanta.now-playing.effects"); b.setOnClickListener(v -> { if (listener != null) listener.onEffects(); }); break;
        case "onCoach": b.setTag("kanta.now-playing.coach"); b.setOnClickListener(v -> { if (listener != null) listener.onCoach(); }); break;
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
  public void setLyrics(String l1, String l2, String l3, String l4) { setLyrics(l1,l2,l3,l4,0); }
  public void setLyrics(String l1,String l2,String l3,String l4,int highlightedCharacters) {
    if(lyricLine1!=null)lyricLine1.setText(l1==null?"":l1);
    if(lyricLine2!=null){String line=l2==null?"":l2;SpannableString styled=new SpannableString(line);int end=Math.max(0,Math.min(line.length(),highlightedCharacters));if(end>0)styled.setSpan(new ForegroundColorSpan(ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN),0,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);lyricLine2.setText(styled);}
    if(lyricLine3!=null)lyricLine3.setText(l3==null?"":l3);
    if(lyricLine4!=null)lyricLine4.setText(l4==null?"":l4);
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
