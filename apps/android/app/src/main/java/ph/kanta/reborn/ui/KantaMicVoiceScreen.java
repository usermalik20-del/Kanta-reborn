package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.ui.components.KantaIconButton;

public class KantaMicVoiceScreen extends LinearLayout {
  public interface Listener {
    void onBack();
    void onMicToggle(boolean enabled);
    void onReverbChanged(float value);
    void onEchoChanged(float value);
    void onFxSelected(String fx);
  }
  private final Context context;
  private final float density;
  private final Listener listener;
  private boolean micEnabled = false;
  private String reverbValue = "0";
  private String echoValue = "0";
  public KantaMicVoiceScreen(Context context, Listener listener) {
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
    back.setText("\u2190"); back.setTextSize(18);
    back.setOnClickListener(v -> { if (listener != null) listener.onBack(); });
    TextView title = new TextView(context);
    title.setText("Mic & Voice");
    title.setTextSize(18);
    title.setTypeface(null, Typeface.BOLD);
    title.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    tp.weight = 1;
    title.setLayoutParams(tp);
    top.addView(back); top.addView(title);
    addView(top);
    LinearLayout content = new LinearLayout(context);
    content.setOrientation(VERTICAL);
    content.setPadding((int)(16*density), 0, (int)(16*density), 0);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0);
    cp.weight = 1;
    addView(content, cp);
    LinearLayout micRow = new LinearLayout(context);
    micRow.setOrientation(HORIZONTAL);
    micRow.setGravity(Gravity.CENTER_VERTICAL);
    micRow.setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(12*density));
    micRow.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 16,
        ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
    TextView micLabel = new TextView(context);
    micLabel.setText("Mic Input");
    micLabel.setTextSize(16);
    micLabel.setTypeface(null, Typeface.BOLD);
    micLabel.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    mlp.weight = 1;
    micLabel.setLayoutParams(mlp);
    Button micToggle = new Button(context);
    micToggle.setText(micEnabled ? "ON" : "OFF");
    micToggle.setTextSize(14);
    micToggle.setTypeface(null, Typeface.BOLD);
    micToggle.setTextColor(micEnabled ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
    micToggle.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        micEnabled ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.SURFACE,
        20, 0, 0, density));
    micToggle.setPadding((int)(16*density),(int)(8*density),(int)(16*density),(int)(8*density));
    micToggle.setOnClickListener(v -> {
      micEnabled = !micEnabled;
      micToggle.setText(micEnabled ? "ON" : "OFF");
      micToggle.setTextColor(micEnabled ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
      micToggle.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
          micEnabled ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.SURFACE,
          20, 0, 0, density));
      if (listener != null) listener.onMicToggle(micEnabled);
    });
    micRow.addView(micLabel); micRow.addView(micToggle);
    content.addView(micRow);
    LinearLayout levelRow = new LinearLayout(context);
    levelRow.setOrientation(VERTICAL);
    levelRow.setPadding((int)(16*density),(int)(16*density),(int)(16*density), 0);
    TextView levelLabel = new TextView(context);
    levelLabel.setText("Input Level");
    levelLabel.setTextSize(12);
    levelLabel.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    levelRow.addView(levelLabel);
    LinearLayout meter = new LinearLayout(context);
    meter.setOrientation(HORIZONTAL);
    meter.setGravity(Gravity.BOTTOM);
    meter.setPadding(0, (int)(8*density), 0, (int)(8*density));
    for (int i = 0; i < 10; i++) {
      android.view.View bar = new android.view.View(context);
      bar.setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
      LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, (int)(20*density), 1);
      bp.bottomMargin = (int)(2*density);
      meter.addView(bar, bp);
    }
    levelRow.addView(meter);
    content.addView(levelRow);
    content.addView(sliderRow("Reverb", reverbValue, (v) -> {
      reverbValue = v;
      if (listener != null) listener.onReverbChanged(Float.parseFloat(v));
    }));
    content.addView(sliderRow("Echo", echoValue, (v) -> {
      echoValue = v;
      if (listener != null) listener.onEchoChanged(Float.parseFloat(v));
    }));
    LinearLayout fxLabelRow = new LinearLayout(context);
    fxLabelRow.setOrientation(HORIZONTAL);
    fxLabelRow.setPadding((int)(16*density),(int)(20*density),(int)(16*density),(int)(8*density));
    TextView fxLabel = new TextView(context);
    fxLabel.setText("Voice FX");
    fxLabel.setTextSize(12);
    fxLabel.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    fxLabel.setTypeface(null, Typeface.BOLD);
    fxLabelRow.addView(fxLabel);
    content.addView(fxLabelRow);
    LinearLayout fxRow = new LinearLayout(context);
    fxRow.setOrientation(HORIZONTAL);
    fxRow.setGravity(Gravity.CENTER_VERTICAL);
    fxRow.setPadding((int)(16*density), 0, (int)(16*density), (int)(16*density));
    String[] fx = {"Off", "Warm", "Bright", "Hall"};
    for (String f : fx) {
      Button chip = new Button(context);
      chip.setText(f);
      chip.setTextSize(13);
      chip.setTypeface(null, Typeface.BOLD);
      chip.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
      chip.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
          ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 20,
          ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
      chip.setPadding((int)(14*density),(int)(10*density),(int)(14*density),(int)(10*density));
      final String fxName = f;
      chip.setOnClickListener(v -> { if (listener != null) listener.onFxSelected(fxName); });
      fxRow.addView(chip);
    }
    content.addView(fxRow);
    LinearLayout coachLabelRow = new LinearLayout(context);
    coachLabelRow.setOrientation(HORIZONTAL);
    coachLabelRow.setPadding((int)(16*density),(int)(24*density),(int)(16*density),(int)(8*density));
    TextView coachLabel = new TextView(context);
    coachLabel.setText("Coach");
    coachLabel.setTextSize(12);
    coachLabel.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    coachLabel.setTypeface(null, Typeface.BOLD);
    coachLabelRow.addView(coachLabel);
    content.addView(coachLabelRow);
    LinearLayout coachRow = new LinearLayout(context);
    coachRow.setOrientation(HORIZONTAL);
    coachRow.setGravity(Gravity.CENTER);
    coachRow.setPadding((int)(16*density), 0, (int)(16*density), (int)(24*density));
    String[] coach = {"Pitch", "Timing", "Control"};
    for (String c : coach) {
      LinearLayout col = new LinearLayout(context);
      col.setOrientation(VERTICAL);
      col.setGravity(Gravity.CENTER);
      TextView score = new TextView(context);
      score.setText("--");
      score.setTextSize(24);
      score.setTypeface(null, Typeface.BOLD);
      score.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
      TextView label = new TextView(context);
      label.setText(c);
      label.setTextSize(11);
      label.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
      col.addView(score); col.addView(label);
      coachRow.addView(col, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    }
    content.addView(coachRow);
  }
  private LinearLayout sliderRow(String label, String value, SliderListener callback) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(VERTICAL);
    row.setPadding((int)(16*density),(int)(16*density),(int)(16*density),(int)(8*density));
    LinearLayout header = new LinearLayout(context);
    header.setOrientation(HORIZONTAL);
    TextView l = new TextView(context);
    l.setText(label);
    l.setTextSize(12);
    l.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    l.setTypeface(null, Typeface.BOLD);
    header.addView(l);
    TextView v = new TextView(context);
    v.setText(value);
    v.setTextSize(12);
    v.setTextColor(ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN);
    v.setTypeface(null, Typeface.BOLD);
    header.addView(v);
    row.addView(header);
    LinearLayout track = new LinearLayout(context);
    track.setOrientation(HORIZONTAL);
    track.setGravity(Gravity.CENTER_VERTICAL);
    track.setPadding(0, (int)(8*density), 0, 0);
    track.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.BORDER, 4, 0, 0, density));
    android.view.View thumb = new android.view.View(context);
    thumb.setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams((int)(24*density), (int)(24*density));
    track.addView(thumb, tp);
    row.addView(track);
    return row;
  }
  private interface SliderListener {
    void onChanged(String value);
  }
}
