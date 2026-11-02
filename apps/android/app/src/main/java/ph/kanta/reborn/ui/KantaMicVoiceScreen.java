package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Mic and voice controls backed by the app's existing capture, monitor and scoring owners. */
public final class KantaMicVoiceScreen extends LinearLayout {
  public interface Listener {
    void onBack();
    void onMicToggle(boolean enabled);
    void onMonitorToggle(boolean enabled);
    void onMonitorGainChanged(float value);
    void onMonitorModeSelected(String mode);
    void onScoringToggle(boolean enabled);
  }

  private final Context context;
  private final float density;
  private final Listener listener;
  private TextView micStatus, monitorStatus, levelLabel, gainValue, scoreStatus;
  private Button micToggle, monitorToggle, scoringToggle;
  private SeekBar gainSlider;
  private final Button[] modeButtons = new Button[4];
  private float inputLevel;
  private int lastActiveBars = -1;
  private String lastInputMessage;
  private String selectedMode;

  public KantaMicVoiceScreen(Context context, Listener listener) {
    super(context);
    this.context = context;
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    setOrientation(VERTICAL);
    setBackgroundColor(KantaTheme.BG_MAIN);
    build();
  }

  private void build() {
    LinearLayout top = new LinearLayout(context);
    top.setOrientation(HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(16), dp(12), dp(16), dp(8));
    ph.kanta.reborn.ui.components.KantaIconButton back =
        new ph.kanta.reborn.ui.components.KantaIconButton(context);
    back.setText("\u2190");
    back.setTextSize(18);
    back.setContentDescription("Back to Home");
    back.setTag("kanta.screen.mic-voice.back");
    back.setOnClickListener(v -> { if (listener != null) listener.onBack(); });
    TextView title = KantaTheme.titleText(context, "Mic & Voice", 20);
    top.addView(back);
    top.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    addView(top);

    android.widget.ScrollView scroll = new android.widget.ScrollView(context);
    scroll.setFillViewport(false);
    LinearLayout content = new LinearLayout(context);
    content.setOrientation(VERTICAL);
    content.setPadding(dp(16), dp(4), dp(16), dp(24));
    scroll.addView(content);
    addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

    LinearLayout micCard = card();
    LinearLayout micHeader = new LinearLayout(context);
    micHeader.setGravity(Gravity.CENTER_VERTICAL);
    TextView micTitle = KantaTheme.titleText(context, "Mic Input", 16);
    micHeader.addView(micTitle, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    micToggle = actionButton("Turn on");
    micToggle.setTag("kanta.mic.toggle");
    micToggle.setOnClickListener(v -> {
      if (listener != null) listener.onMicToggle(micStatus != null
          && micStatus.getText().toString().startsWith("Microphone off"));
    });
    micHeader.addView(micToggle);
    micCard.addView(micHeader);
    micStatus = bodyText("Microphone off");
    micStatus.setTag("kanta.mic.status");
    micCard.addView(micStatus);
    levelLabel = bodyText("Input level · waiting");
    levelLabel.setPadding(0, dp(12), 0, dp(4));
    micCard.addView(levelLabel);
    LinearLayout meter = new LinearLayout(context);
    meter.setGravity(Gravity.CENTER_VERTICAL);
    for (int i = 0; i < 12; i++) {
      TextView bar = new TextView(context);
      bar.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE_ELEVATED, 3, KantaTheme.BORDER, 1, density));
      LinearLayout.LayoutParams p = new LayoutParams(0, dp(12), 1);
      p.leftMargin = dp(2);
      p.rightMargin = dp(2);
      meter.addView(bar, p);
    }
    meter.setTag("kanta.mic.level-meter");
    micCard.addView(meter);
    addViewCard(content, micCard);

    LinearLayout monitorCard = card();
    TextView monitorTitle = KantaTheme.titleText(context, "Voice Monitoring", 16);
    monitorCard.addView(monitorTitle);
    monitorStatus = bodyText("Monitoring off · connect wired, USB or Bluetooth headphones");
    monitorStatus.setPadding(0, dp(6), 0, dp(8));
    monitorCard.addView(monitorStatus);
    monitorToggle = actionButton("Choose output");
    monitorToggle.setTag("kanta.monitor.toggle");
    monitorToggle.setOnClickListener(v -> {
      boolean enabled = monitorStatus != null
          && monitorStatus.getText().toString().startsWith("Monitoring off");
      if (listener != null) listener.onMonitorToggle(enabled);
    });
    monitorCard.addView(monitorToggle);
    TextView gainTitle = bodyText("Voice output level");
    monitorCard.addView(gainTitle);
    LinearLayout gainHeader = new LinearLayout(context);
    gainHeader.setGravity(Gravity.CENTER_VERTICAL);
    gainValue = bodyText("10%");
    gainHeader.addView(gainValue);
    monitorCard.addView(gainHeader);
    gainSlider = new SeekBar(context);
    gainSlider.setTag("kanta.monitor.gain");
    gainSlider.setMax(35);
    gainSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onStartTrackingTouch(SeekBar seekBar) {}
      @Override public void onStopTrackingTouch(SeekBar seekBar) {}
      @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && listener != null) {
          gainValue.setText(progress + "%");
          listener.onMonitorGainChanged(progress / 100f);
        }
      }
    });
    monitorCard.addView(gainSlider);
    TextView fxTitle = bodyText("Monitor effect");
    fxTitle.setPadding(0, dp(10), 0, dp(6));
    monitorCard.addView(fxTitle);
    LinearLayout modes = new LinearLayout(context);
    modes.setGravity(Gravity.CENTER_VERTICAL);
    String[] names = {"Dry", "Room", "Hall", "Echo"};
    for (int i = 0; i < names.length; i++) {
      final String name = names[i];
      Button chip = actionButton(name);
      chip.setTextSize(12);
      chip.setTag("kanta.monitor.mode." + name.toLowerCase(java.util.Locale.ROOT));
      chip.setOnClickListener(v -> {
        if (listener != null) listener.onMonitorModeSelected(name);
        selectMode(name);
      });
      modeButtons[i] = chip;
      LinearLayout.LayoutParams p = new LayoutParams(0, dp(44), 1);
      p.leftMargin = dp(3);
      p.rightMargin = dp(3);
      modes.addView(chip, p);
    }
    monitorCard.addView(modes);
    TextView monitorNote = bodyText(
        "Monitoring is opt-in and requires a verified wired, USB or Bluetooth headset route. "
            + "Speaker outputs are blocked to prevent feedback.");
    monitorNote.setTextSize(12);
    monitorCard.addView(monitorNote);
    addViewCard(content, monitorCard);

    LinearLayout coachCard = card();
    TextView coachTitle = KantaTheme.titleText(context, "Vocal Coach", 16);
    coachCard.addView(coachTitle);
    scoreStatus = bodyText("Song scoring is off");
    scoreStatus.setTag("kanta.score.status");
    scoreStatus.setPadding(0, dp(8), 0, dp(8));
    coachCard.addView(scoreStatus);
    scoringToggle = actionButton("Enable song scoring");
    scoringToggle.setTag("kanta.score.toggle");
    scoringToggle.setOnClickListener(v -> {
      boolean enabled = scoringToggle.getText().toString().startsWith("Enable");
      if (listener != null) listener.onScoringToggle(enabled);
    });
    coachCard.addView(scoringToggle);
    TextView note = bodyText(
        "Scores appear only when the current song has a supported melody reference and reliable "
            + "microphone input. No example scores are displayed.");
    note.setTextSize(12);
    coachCard.addView(note);
    addViewCard(content, coachCard);
  }

  private LinearLayout card() {
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(VERTICAL);
    card.setPadding(dp(16), dp(14), dp(16), dp(14));
    card.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE_ELEVATED, 16, KantaTheme.BORDER, 1, density));
    return card;
  }

  private void addViewCard(LinearLayout parent, LinearLayout card) {
    LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    p.bottomMargin = dp(12);
    parent.addView(card, p);
  }

  private Button actionButton(String label) {
    Button button = new Button(context);
    button.setText(label);
    button.setAllCaps(false);
    button.setTextSize(13);
    button.setTypeface(null, Typeface.BOLD);
    button.setTextColor(KantaTheme.TEXT_PRIMARY);
    button.setBackground(KantaTheme.backgroundDrawable(KantaTheme.SURFACE, 12, KantaTheme.PRIMARY_BLUE, 1, density));
    button.setMinHeight(dp(44));
    return button;
  }

  private TextView bodyText(String value) {
    TextView view = new TextView(context);
    view.setText(value);
    view.setTextSize(13);
    view.setTextColor(KantaTheme.TEXT_SECONDARY);
    view.setLineSpacing(dp(2), 1f);
    return view;
  }

  private int dp(int value) { return (int) (value * density); }

  public void setState(
      boolean micEnabled, boolean micCapturing, boolean monitorEnabled, boolean monitorAudible,
      float gain, String mode, boolean scoringEnabled, String scoringMessage) {
    if (micStatus != null) {
      micStatus.setText(!micEnabled ? "Microphone off"
          : micCapturing ? "Microphone input active" : "Starting microphone…");
      micToggle.setText(micEnabled ? "Turn off" : "Turn on");
      micToggle.setEnabled(!micEnabled || micCapturing);
    }
    setMonitorState(monitorEnabled, monitorAudible, gain, mode);
    setScoreState(scoringEnabled, scoringMessage);
  }

  public void setInputLevel(float rms, String message) {
    inputLevel = Float.isFinite(rms) ? Math.max(0f, Math.min(1f, rms * 8f)) : 0f;
    if (levelLabel != null && !String.valueOf(levelLabel.getText()).equals(message)) {
      levelLabel.setText(message);
      lastInputMessage = message;
    }
    if (getChildCount() < 2) return;
    android.view.ViewGroup scrollContent = (android.view.ViewGroup) ((android.widget.ScrollView) getChildAt(1)).getChildAt(0);
    if (scrollContent == null) return;
    android.view.View meter = scrollContent.findViewWithTag("kanta.mic.level-meter");
    if (!(meter instanceof LinearLayout)) return;
    LinearLayout bars = (LinearLayout) meter;
    int active = Math.round(inputLevel * bars.getChildCount());
    if (active == lastActiveBars) return;
    lastActiveBars = active;
    for (int i = 0; i < bars.getChildCount(); i++) {
      TextView bar = (TextView) bars.getChildAt(i);
      int color = i < active ? (i >= 9 ? KantaTheme.WARNING : KantaTheme.SUCCESS) : KantaTheme.SURFACE_ELEVATED;
      bar.setBackground(KantaTheme.backgroundDrawable(color, 3, KantaTheme.BORDER, 1, density));
    }
  }

  public void setMonitorState(boolean enabled, boolean audible, float gain, String mode) {
    if (monitorStatus == null) return;
    String status = !enabled ? "Monitoring off · connect a supported headset"
        : audible ? "Monitoring active · route verified" : "Monitoring muted · verifying output";
    if (!String.valueOf(monitorStatus.getText()).equals(status)) monitorStatus.setText(status);
    String toggle = enabled ? "Turn off monitoring" : "Choose output";
    if (!String.valueOf(monitorToggle.getText()).equals(toggle)) monitorToggle.setText(toggle);
    boolean canToggle = enabled || micStatus.getText().toString().equals("Microphone input active");
    if (monitorToggle.isEnabled() != canToggle) monitorToggle.setEnabled(canToggle);
    int progress = Math.round(Math.max(0f, Math.min(.35f, gain)) * 100);
    if (gainSlider != null && !gainSlider.isPressed() && gainSlider.getProgress() != progress) gainSlider.setProgress(progress);
    String gainText = progress + "%";
    if (gainValue != null && !String.valueOf(gainValue.getText()).equals(gainText)) gainValue.setText(gainText);
    selectMode(mode);
  }

  private void selectMode(String mode) {
    String selected = mode == null ? "DRY" : mode.toUpperCase(java.util.Locale.ROOT);
    if (selected.equals(selectedMode)) return;
    selectedMode = selected;
    for (int i = 0; i < modeButtons.length; i++) {
      if (modeButtons[i] == null) continue;
      String key = new String[] {"DRY", "ROOM", "HALL", "ECHO"}[i];
      boolean active = key.equals(selected);
      modeButtons[i].setTextColor(active ? KantaTheme.PRIMARY_CYAN : KantaTheme.TEXT_SECONDARY);
      modeButtons[i].setBackground(KantaTheme.backgroundDrawable(
          active ? KantaTheme.SURFACE_ELEVATED : KantaTheme.SURFACE, 10,
          active ? KantaTheme.PRIMARY_CYAN : KantaTheme.BORDER, active ? 2 : 1, density));
    }
  }

  public void setScoreState(boolean enabled, String message) {
    if (scoreStatus == null) return;
    String status = message == null ? "Scoring status unavailable" : message;
    if (!String.valueOf(scoreStatus.getText()).equals(status)) scoreStatus.setText(status);
    String toggle = enabled ? "Turn off song scoring" : "Enable song scoring";
    if (!String.valueOf(scoringToggle.getText()).equals(toggle)) scoringToggle.setText(toggle);
  }
}
