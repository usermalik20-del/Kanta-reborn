package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.ui.components.KantaIconButton;
import ph.kanta.reborn.ui.components.KantaProgressBar;
import ph.kanta.reborn.ui.components.KantaTransportControls;

public class KantaFullscreenStage extends LinearLayout {
  public interface Listener {
    void onExitFullscreen();
    void onPlayPause();
    void onPrevious();
    void onNext();
  }
  private final Context context;
  private final float density;
  private final Listener listener;
  private String songTitle = "Morning Light";
  private String songArtist = "KANTA";
  private String currentTime = "0:00";
  private String duration = "3:45";
  private float progress = 0f;
  private boolean playing = false;
  private boolean controlsVisible = true;
  private TextView titleView, artistView, currentTimeView, durationView;
  private KantaProgressBar progressBar;
  private KantaTransportControls transport;
  public KantaFullscreenStage(Context context, Listener listener) {
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
    top.setPadding((int)(16*density),(int)(10*density),(int)(16*density),(int)(4*density));
    KantaIconButton exit = new KantaIconButton(context);
    exit.setText("\u2190"); exit.setTextSize(16);
    exit.setOnClickListener(v -> { if (listener != null) listener.onExitFullscreen(); });
    TextView stageTitle = new TextView(context);
    stageTitle.setText("Fullscreen Stage");
    stageTitle.setTextSize(16);
    stageTitle.setTypeface(null, Typeface.BOLD);
    stageTitle.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    sp.weight = 1;
    stageTitle.setLayoutParams(sp);
    top.addView(exit); top.addView(stageTitle);
    addView(top);
    LinearLayout stage = new LinearLayout(context);
    stage.setOrientation(VERTICAL);
    stage.setGravity(Gravity.CENTER);
    boolean wide = getResources().getConfiguration().orientation
        == android.content.res.Configuration.ORIENTATION_LANDSCAPE
;
    stage.setBackground(KantaTheme.stageBackground(context, wide));
    LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0);
    stageParams.weight = 1;
    stage.setLayoutParams(stageParams);
    LinearLayout lyrics = new LinearLayout(context);
    lyrics.setOrientation(VERTICAL);
    lyrics.setGravity(Gravity.CENTER);
    lyrics.setPadding((int)(48*density), 0, (int)(48*density), 0);
    TextView line1 = new TextView(context);
    line1.setText("Previous lyric line");
    line1.setTextSize(18);
    line1.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
    line1.setGravity(Gravity.CENTER);
    TextView line2 = new TextView(context);
    line2.setText("Current lyric line \u2014 bright white");
    line2.setTextSize(32);
    line2.setTypeface(null, Typeface.BOLD);
    line2.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    line2.setGravity(Gravity.CENTER);
    TextView line3 = new TextView(context);
    line3.setText("Next upcoming line");
    line3.setTextSize(18);
    line3.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
    line3.setGravity(Gravity.CENTER);
    lyrics.addView(line1); lyrics.addView(line2); lyrics.addView(line3);
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
      @Override public void onShuffle() {}
      @Override public void onPrevious() { if (listener != null) listener.onPrevious(); }
      @Override public void onPlayPause() { if (listener != null) listener.onPlayPause(); }
      @Override public void onNext() { if (listener != null) listener.onNext(); }
      @Override public void onRepeat() {}
    });
    transport.setPlaying(playing);
    LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    tParams.topMargin = (int)(8*density); tParams.bottomMargin = (int)(24*density);
    addView(transport, tParams);
    setControlsVisible(controlsVisible);
  }
  public void setControlsVisible(boolean visible) {
    controlsVisible = visible;
    int vis = visible ? VISIBLE : GONE;
    for (int i = 0; i < getChildCount(); i++) {
      android.view.View child = getChildAt(i);
      if (i == 1) continue;
      child.setVisibility(vis);
    }
  }
  public void setSongInfo(String title, String artist) {
    songTitle = title; songArtist = artist;
    if (titleView != null) titleView.setText(title);
    if (artistView != null) artistView.setText(artist);
  }
  public void setProgress(float p) { progress = p; if (progressBar != null) progressBar.setProgress(p); }
  public void setTimes(String current, String dur) {
    currentTime = current; duration = dur;
    if (currentTimeView != null) currentTimeView.setText(current);
    if (durationView != null) durationView.setText(dur);
  }
  public void setPlaying(boolean p) { playing = p; if (transport != null) transport.setPlaying(p); }
}
