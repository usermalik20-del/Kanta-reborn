package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;

import ph.kanta.reborn.ui.components.KantaIconButton;
import ph.kanta.reborn.ui.components.KantaStepper;

public class KantaPitchTempoScreen extends LinearLayout {
  public interface Listener {
    void onBack();
    void onReset();
    void onKeyDown();
    void onKeyUp();
    void onTempoDown();
    void onTempoUp();
    void onTempoChanged(float value);
  }
  private final Context context;
  private final float density;
  private final Listener listener;
  private int key = 0;
  private float tempo = 1.0f;
  private TextView keyValueView, tempoValueView;
  private KantaStepper keyStepper, tempoStepper;
  private SeekBar tempoSlider;
  public KantaPitchTempoScreen(Context context, Listener listener) {
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
    title.setText("Pitch & Tempo");
    title.setTextSize(18);
    title.setTypeface(null, Typeface.BOLD);
    title.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    tp.weight = 1;
    title.setLayoutParams(tp);
    Button reset = new Button(context);
    reset.setTag("kanta.pitch.reset");
    reset.setText("Reset");
    reset.setTextSize(12);
    reset.setTextColor(ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN);
    reset.setTypeface(null, Typeface.BOLD);
    reset.setBackground(null);
    reset.setPadding(0, 0, 0, 0);
    reset.setOnClickListener(v -> { if (listener != null) listener.onReset(); });
    top.addView(back); top.addView(title); top.addView(reset);
    addView(top);
    LinearLayout keyCard = card("Key", String.valueOf(key), "Original Key",
        new KantaStepper.OnChangeListener() {
          @Override public void onDecrement() {
            key = Math.max(-12, key - 1);
            updateKeyDisplay();
            if (listener != null) listener.onKeyDown();
          }
          @Override public void onIncrement() {
            key = Math.min(12, key + 1);
            updateKeyDisplay();
            if (listener != null) listener.onKeyUp();
          }
        });
    addView(keyCard);
    TextView divider = new TextView(context);
    divider.setText("\u266D\u266D   \u266D   0   \u266F   \u266F\u266F");
    divider.setTextSize(13);
    divider.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
    divider.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
    divider.setGravity(Gravity.CENTER);
    divider.setPadding(0, (int)(4*density), 0, (int)(16*density));
    addView(divider);
    LinearLayout tempoCard = card("Tempo", String.format(java.util.Locale.ROOT, "%.0f%%", tempo * 100f), "Original Tempo",
        new KantaStepper.OnChangeListener() {
          @Override public void onDecrement() {
            tempo = Math.max(0.5f, tempo - 0.05f);
            updateTempoDisplay();
            if (listener != null) listener.onTempoDown();
          }
          @Override public void onIncrement() {
            tempo = Math.min(2.0f, tempo + 0.05f);
            updateTempoDisplay();
            if (listener != null) listener.onTempoUp();
          }
        });
    addView(tempoCard);
    tempoSlider = new SeekBar(context);
    tempoSlider.setTag("kanta.pitch.tempo.slider");
    tempoSlider.setMax(150);
    tempoSlider.setProgress(Math.round((tempo - 0.5f) * 100));
    tempoSlider.setContentDescription("Tempo from 50 to 200 percent");
    tempoSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onStartTrackingTouch(SeekBar bar) {}
      @Override public void onStopTrackingTouch(SeekBar bar) {}
      @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        if (!fromUser) return;
        tempo = 0.5f + progress / 100f;
        updateTempoDisplay();
        if (listener != null) listener.onTempoChanged(tempo);
      }
    });
    LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    sliderParams.setMargins((int)(24*density), 0, (int)(24*density), 0);
    addView(tempoSlider, sliderParams);
    TextView sliderNote = new TextView(context);
    sliderNote.setText("50%                                      200%");
    sliderNote.setTextSize(12);
    sliderNote.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
    sliderNote.setGravity(Gravity.CENTER);
    sliderNote.setPadding(0, (int)(4*density), 0, (int)(24*density));
    addView(sliderNote);
  }
  private LinearLayout card(String label, String value, String subtitle, KantaStepper.OnChangeListener stepperListener) {
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(VERTICAL);
    card.setPadding((int)(16*density),(int)(16*density),(int)(16*density),(int)(16*density));
    card.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 16,
        ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
    TextView labelView = new TextView(context);
    labelView.setText(label);
    labelView.setTextSize(12);
    labelView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    labelView.setTypeface(null, Typeface.BOLD);
    card.addView(labelView);
    TextView valueView = new TextView(context);
    valueView.setText(value);
    if ("Key".equals(label)) keyValueView = valueView;
    if ("Tempo".equals(label)) tempoValueView = valueView;
    valueView.setTextSize(32);
    valueView.setTypeface(null, Typeface.BOLD);
    valueView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    valueView.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    vp.topMargin = (int)(8*density); vp.bottomMargin = (int)(8*density);
    valueView.setLayoutParams(vp);
    card.addView(valueView);
    TextView subView = new TextView(context);
    subView.setText(subtitle);
    subView.setTextSize(13);
    subView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
    card.addView(subView);
    KantaStepper stepper = new KantaStepper(context);
    stepper.setValue(value);
    stepper.setOnChangeListener(stepperListener);
    if ("Key".equals(label)) {
      keyStepper = stepper;
      stepper.setTag("kanta.pitch.key.stepper");
      stepper.getChildAt(0).setTag("kanta.pitch.key.decrement");
      stepper.getChildAt(2).setTag("kanta.pitch.key.increment");
    } else if ("Tempo".equals(label)) {
      tempoStepper = stepper;
      stepper.setTag("kanta.pitch.tempo.stepper");
      stepper.getChildAt(0).setTag("kanta.pitch.tempo.decrement");
      stepper.getChildAt(2).setTag("kanta.pitch.tempo.increment");
    }
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    sp.topMargin = (int)(12*density);
    stepper.setLayoutParams(sp);
    card.addView(stepper);
    return card;
  }
  private void updateKeyDisplay() {
    String value = key > 0 ? "+" + key : String.valueOf(key);
    if (keyValueView != null) keyValueView.setText(value);
    if (keyStepper != null) keyStepper.setValue(value);
  }

  private void updateTempoDisplay() {
    String value = String.format(java.util.Locale.ROOT, "%.0f%%", tempo * 100f);
    if (tempoValueView != null) tempoValueView.setText(value);
    if (tempoStepper != null) tempoStepper.setValue(value);
    if (tempoSlider != null) tempoSlider.setProgress(Math.round((tempo - 0.5f) * 100));
  }

  public void setKey(int k) {
    key = Math.max(-12, Math.min(12, k));
    updateKeyDisplay();
  }

  public void setTempo(float t) {
    if (!Float.isFinite(t)) return;
    tempo = Math.max(0.5f, Math.min(2.0f, t));
    updateTempoDisplay();
  }
}
