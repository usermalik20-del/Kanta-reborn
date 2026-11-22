package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import ph.kanta.reborn.R;

/** Mic and voice controls backed by the app's existing capture, monitor and scoring owners. */
// Programmatic-only view: callers supply runtime dependencies; not inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class KantaMicVoiceScreen extends LinearLayout {
  public interface Listener {
    void onBack();
    void onMicToggle();
    void onMonitorToggle();
    void onMonitorGainChanged(float value);
    void onMonitorModeSelected(String mode);
    void onScoringToggle(boolean enabled);
  }

  private final Context context;
  private final float density;
  private final Listener listener;
  private final boolean menuSurface;
  private TextView micStatus, monitorStatus, levelLabel, gainValue, scoreStatus;
  private Button micToggle, monitorToggle, scoringToggle;
  private SeekBar gainSlider;
  /**
   * Monitor-effect presets, taken straight from the engine so a chip can never desynchronise from
   * the preset it selects. The label is the engine's own user-facing name.
   */
  private static final ph.kanta.reborn.core.VocalMonitorEffects.Mode[] MODES =
      ph.kanta.reborn.core.VocalMonitorEffects.Mode.values();

  private final Button[] modeButtons = new Button[MODES.length];
  private float inputLevel;
  private int lastActiveBars = -1;
  private String selectedMode;

  public KantaMicVoiceScreen(Context context, Listener listener) {
    this(context, listener, false);
  }

  public KantaMicVoiceScreen(Context context, Listener listener, boolean menuSurface) {
    super(context);
    this.context = context;
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    this.menuSurface = menuSurface;
    setOrientation(VERTICAL);
    if (!menuSurface) setBackgroundColor(KantaTheme.BG_MAIN);
    build(menuSurface);
  }

  private void build(boolean menuSurface) {
    Runnable onBack = () -> { if (listener != null) listener.onBack(); };
    String title = context.getString(R.string.menu_voice_scoring);
    if (menuSurface) {
      addView(MenuSurface.header(context, title, context.getString(R.string.menu_back),
          "kanta.screen.mic-voice.back", onBack));
    } else {
      addView(new ph.kanta.reborn.ui.components.KantaScreenHeader(
          context, title, context.getString(R.string.menu_back), "kanta.screen.mic-voice.back", v -> onBack.run()));
    }

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
    TextView micTitle = sectionTitle("Mic Input", R.drawable.kanta_submenu_mic);
    micHeader.addView(micTitle, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    micToggle = actionButton("Turn on");
    micToggle.setTag("kanta.mic.toggle");
    micToggle.setOnClickListener(v -> { if (listener != null) listener.onMicToggle(); });
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
    TextView inputNote = bodyText("Android chooses the microphone. Speak and check the input "
        + "meter. If it does not move, check microphone access in Android Settings.");
    inputNote.setTextSize(12);
    micCard.addView(inputNote);
    addViewCard(content, micCard);

    LinearLayout monitorCard = card();
    TextView monitorTitle = sectionTitle("Hear My Voice", R.drawable.kanta_submenu_waveform);
    monitorCard.addView(monitorTitle);
    monitorStatus = bodyText("Voice playback is off");
    monitorStatus.setTag("kanta.monitor.status");
    monitorStatus.setPadding(0, dp(6), 0, dp(8));
    monitorCard.addView(monitorStatus);
    monitorToggle = actionButton("Choose output");
    if (menuSurface) MenuSurface.setLeadingIcon(monitorToggle, R.drawable.kanta_submenu_headphones);
    monitorToggle.setTag("kanta.monitor.toggle");
    monitorToggle.setOnClickListener(v -> { if (listener != null) listener.onMonitorToggle(); });
    monitorCard.addView(monitorToggle);
    TextView gainTitle = bodyText("Voice output level");
    monitorCard.addView(gainTitle);
    LinearLayout gainHeader = new LinearLayout(context);
    gainHeader.setGravity(Gravity.CENTER_VERTICAL);
    gainValue = bodyText("10%");
    gainHeader.addView(gainValue);
    monitorCard.addView(gainHeader);
    gainSlider = ph.kanta.reborn.ui.KantaTheme.adjustmentSlider(context);
    gainSlider.setTag("kanta.monitor.gain");
    gainSlider.setMax(35);
    gainSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onStartTrackingTouch(SeekBar seekBar) {}
      @Override public void onStopTrackingTouch(SeekBar seekBar) {}
      @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && listener != null) {
          gainValue.setText(gainValue.getContext().getString(ph.kanta.reborn.R.string.gain_percent, progress));
          listener.onMonitorGainChanged(progress / 100f);
        }
      }
    });
    monitorCard.addView(gainSlider);
    TextView fxTitle = bodyText("Voice Sound");
    fxTitle.setPadding(0, dp(10), 0, dp(6));
    monitorCard.addView(fxTitle);
    // Ten presets do not fit one phone row; three per line stays readable at 12sp.
    LinearLayout modes = null;
    for (int i = 0; i < MODES.length; i++) {
      if (i % 3 == 0) {
        modes = new LinearLayout(context);
        modes.setGravity(Gravity.CENTER_VERTICAL);
        monitorCard.addView(modes);
      }
      final String name = MODES[i].label();
      final String displayName = voiceSoundName(name);
      Button chip = actionButton(displayName);
      chip.setTextSize(12);
      chip.setTag(
          "kanta.monitor.mode."
              + name.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
      chip.setOnClickListener(v -> {
        if (listener != null) listener.onMonitorModeSelected(name);
        selectMode(name);
      });
      modeButtons[i] = chip;
      LinearLayout.LayoutParams p = new LayoutParams(0, menuSurface ? LayoutParams.WRAP_CONTENT : dp(44), 1);
      p.leftMargin = dp(3);
      p.rightMargin = dp(3);
      modes.addView(chip, p);
    }
    TextView monitorNote = bodyText(
        "Connect wired headphones, USB headphones, or headphones plugged into an audio "
            + "interface. Wear them and start at a low volume. Do not connect speakers to the "
            + "interface or line output. If you do not hear yourself, speak while checking the "
            + "input meter, then check both volume controls and choose the output again. "
            + "Bluetooth headsets may work between songs on Android 12 or later, with "
            + "call-like sound and a delay. Use wired or USB headphones to hear yourself "
            + "during songs. Phone and Bluetooth speakers are not supported.");
    monitorNote.setTextSize(12);
    monitorCard.addView(monitorNote);
    addViewCard(content, monitorCard);

    LinearLayout coachCard = card();
    TextView coachTitle = sectionTitle("Vocal Coach", R.drawable.kanta_submenu_score);
    coachCard.addView(coachTitle);
    scoreStatus = bodyText("Song scoring is off");
    scoreStatus.setTag("kanta.score.status");
    scoreStatus.setPadding(0, dp(8), 0, dp(8));
    coachCard.addView(scoreStatus);
    scoringToggle = actionButton("Enable song scoring");
    if (menuSurface) MenuSurface.setLeadingIcon(scoringToggle, R.drawable.kanta_submenu_score);
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

  private String voiceSoundName(String engineName) {
    if (engineName == null) return "Natural";
    String key = engineName.toUpperCase(java.util.Locale.ROOT);
    if ("DRY".equals(key)) return "Natural";
    if ("SMALL_ROOM".equals(key)) return "Studio";
    if ("ROOM".equals(key)) return "Big Room";
    if ("STUDIO".equals(key)) return "Studio";
    if ("CONCERT_HALL".equals(key) || "HALL".equals(key)) return "Hall";
    if ("CATHEDRAL".equals(key)) return "Echo";
    if ("PLATE_REVERB".equals(key) || "SLAP_ECHO".equals(key)) return "More Echo";
    if ("WARM_TAPE".equals(key)) return "Warm";
    return engineName;
  }

  private TextView sectionTitle(String title, int icon) {
    return menuSurface ? MenuSurface.heading(context, title, 16, icon)
        : KantaTheme.titleText(context, title, 16);
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
    button.setBackgroundResource(R.drawable.kanta_button_secondary);
    button.setMinHeight(dp(44));
    if (menuSurface) MenuSurface.actionStyle(button, false);
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

  public void setMicrophoneState(boolean enabled, boolean capturing, boolean permissionPending,
      String notice) {
    String status = permissionPending ? "Waiting for microphone permission…"
        : capturing ? "Microphone input active" : enabled ? "Starting microphone…"
        : notice != null ? notice : "Microphone off";
    if (!String.valueOf(micStatus.getText()).equals(status)) micStatus.setText(status);
    String toggle = enabled ? "Turn off" : "Turn on";
    if (!String.valueOf(micToggle.getText()).equals(toggle)) micToggle.setText(toggle);
    micToggle.setEnabled(!permissionPending);
  }

  public void setInputLevel(float rms, String message) {
    inputLevel = Float.isFinite(rms) ? Math.max(0f, Math.min(1f, rms * 8f)) : 0f;
    if (levelLabel != null && !String.valueOf(levelLabel.getText()).equals(message)) {
      levelLabel.setText(message);
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

  public void setMonitorState(boolean starting, boolean enabled, boolean ready, boolean audible,
      float gain, float maxGain, String mode, String notice) {
    String status = starting ? "Starting the microphone for voice playback…"
        : audible ? "Voice playback is on"
        : enabled && !ready ? "Checking your headphones. Voice playback is muted."
        : enabled && gain == 0 ? "Voice volume is at 0%. Turn it up slowly."
        : enabled ? "Headphones connected. Waiting for microphone sound…"
        : notice != null ? notice : "Voice playback is off";
    if (!String.valueOf(monitorStatus.getText()).equals(status)) monitorStatus.setText(status);
    String toggle = starting ? "Cancel setup" : enabled ? "Turn off voice playback" : "Choose output";
    if (!String.valueOf(monitorToggle.getText()).equals(toggle)) monitorToggle.setText(toggle);
    int progress = Math.round(Math.max(0f, Math.min(maxGain, gain)) * 100);
    if (gainSlider != null) {
      int ceiling = Math.round(maxGain * 100);
      if (gainSlider.getMax() != ceiling) gainSlider.setMax(ceiling);
      if (!gainSlider.isPressed() && gainSlider.getProgress() != progress) gainSlider.setProgress(progress);
    }
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
      boolean active = MODES[i].name().equals(selected);
      if (menuSurface) {
        modeButtons[i].setSelected(active);
        continue;
      }
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
