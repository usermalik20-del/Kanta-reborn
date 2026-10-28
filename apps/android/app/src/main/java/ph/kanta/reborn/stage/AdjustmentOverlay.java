package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Real-time key/melody/tempo/volume adjustment overlay.
 *
 * <p>Per {@link replica/design/components.md} AdjustmentOverlay spec.
 * Bottom sheet on handset, side panel on TV.
 */
public final class AdjustmentOverlay extends FrameLayout {

  private static final int SHEET_HEIGHT_PERCENT = 40;

  private final Paint trackPaint;
  private final Paint thumbPaint;
  private final int thumbSizePx;
  private final int trackHeightPx;

  private SeekBar keySeek;
  private SeekBar tempoSeek;
  private SeekBar melodySeek;
  private SeekBar volumeSeek;
  private AdjustmentListener listener;

  public AdjustmentOverlay(Context context) {
    this(context, null);
  }

  public AdjustmentOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);

    float density = context.getResources().getDisplayMetrics().density;
    thumbSizePx = (int) (24 * density);
    trackHeightPx = (int) (4 * density);

    trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    trackPaint.setColor(0xFF2A1A47); // adjustmentTrack

    thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    thumbPaint.setColor(0xFFA24BFF); // adjustmentKnob

    initViews(context);
  }

  private void initViews(Context context) {
    float density = context.getResources().getDisplayMetrics().density;

    LinearLayout container = new LinearLayout(context);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setPadding((int)(20 * density), (int)(16 * density), (int)(20 * density), (int)(16 * density));
    container.setBackgroundColor(0xFF2A1A47); // elevated
    LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    params.gravity = android.view.Gravity.BOTTOM;
    addView(container, params);

    // Key adjustment
    addAdjustmentRow(container, "Key", "semitones", -12, 12, 0,
        new SeekBar.OnSeekBarChangeListener() {
          @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && listener != null) listener.onKeyChange(-12 + progress);
          }
          @Override public void onStartTrackingTouch(SeekBar seekBar) {}
          @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    // Melody volume
    addAdjustmentRow(container, "Melody", "volume", 0, 100, 80,
        new SeekBar.OnSeekBarChangeListener() {
          @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && listener != null) listener.onMelodyVolumeChange(progress / 100f);
          }
          @Override public void onStartTrackingTouch(SeekBar seekBar) {}
          @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    // Tempo
    addAdjustmentRow(container, "Tempo", "factor", 50, 150, 100,
        new SeekBar.OnSeekBarChangeListener() {
          @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && listener != null) listener.onTempoChange((50 + progress) / 100f);
          }
          @Override public void onStartTrackingTouch(SeekBar seekBar) {}
          @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    // Guide volume
    addAdjustmentRow(container, "Guide", "volume", 0, 100, 60,
        new SeekBar.OnSeekBarChangeListener() {
          @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && listener != null) listener.onGuideVolumeChange(progress / 100f);
          }
          @Override public void onStartTrackingTouch(SeekBar seekBar) {}
          @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
  }

  private void addAdjustmentRow(LinearLayout container, String label, String unit,
      int min, int max, int defaultValue, SeekBar.OnSeekBarChangeListener callback) {

    float density = getContext().getResources().getDisplayMetrics().density;

    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(LinearLayout.VERTICAL);
    row.setPadding(0, (int)(12 * density), 0, (int)(12 * density));

    // Label and value
    LinearLayout header = new LinearLayout(getContext());
    header.setOrientation(LinearLayout.HORIZONTAL);

    TextView labelView = new TextView(getContext());
    labelView.setText(label);
    labelView.setTextSize(12);
    labelView.setTextColor(0xFFCCCCCC);
    labelView.setLayoutParams(new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

    TextView valueView = new TextView(getContext());
    valueView.setText(formatValue(defaultValue, unit));
    valueView.setTextSize(18);
    valueView.setTextColor(0xFFFFFFFF);
    valueView.setTypeface(null, android.graphics.Typeface.BOLD);
    valueView.setGravity(android.view.Gravity.END);

    header.addView(labelView);
    header.addView(valueView);
    row.addView(header);

    // SeekBar
    SeekBar seekBar = new SeekBar(getContext());
    seekBar.setMax(max - min);
    seekBar.setProgress(defaultValue - min);
    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        int value = min + progress;
        valueView.setText(formatValue(value, unit));
        if (fromUser && callback != null) {
          callback.onProgressChanged(seekBar, progress, fromUser);
        }
      }
      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {}
      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {}
    });

    // Customize seekbar appearance
    seekBar.setProgressDrawable(getContext().getResources().getDrawable(
        ph.kanta.reborn.R.drawable.karaoke_knob_track, getContext().getTheme()));
    seekBar.setThumb(getContext().getResources().getDrawable(
        ph.kanta.reborn.R.drawable.karaoke_knob_thumb, getContext().getTheme()));
    seekBar.setSplitTrack(false);

    // Store references
    switch (label) {
      case "Key": keySeek = seekBar; break;
      case "Tempo": tempoSeek = seekBar; break;
      case "Melody": melodySeek = seekBar; break;
      case "Guide": volumeSeek = seekBar; break;
    }

    row.addView(seekBar);
    container.addView(row);
  }

  private String formatValue(int value, String unit) {
    if ("semitones".equals(unit)) {
      return (value >= 0 ? "+" : "") + value;
    } else if ("factor".equals(unit)) {
      return String.format("%.2fx", value / 100f);
    } else {
      return value + "%";
    }
  }

  /** Sets the adjustment listener. */
  public void setListener(AdjustmentListener listener) {
    this.listener = listener;
  }

  /** Updates the key adjustment. */
  public void setKey(int semitones) {
    if (keySeek != null) keySeek.setProgress(semitones + 12);
  }

  /** Updates the tempo adjustment. */
  public void setTempo(float factor) {
    if (tempoSeek != null) tempoSeek.setProgress((int)(factor * 100) - 50);
  }

  /** Updates the melody volume. */
  public void setMelodyVolume(float volume) {
    if (melodySeek != null) melodySeek.setProgress((int)(volume * 100));
  }

  /** Updates the guide volume. */
  public void setGuideVolume(float volume) {
    if (volumeSeek != null) volumeSeek.setProgress((int)(volume * 100));
  }

  /** Shows the overlay with slide-up animation. */
  public void show() {
    setTranslationY(getHeight());
    setVisibility(VISIBLE);
    animate().translationY(0).setDuration(250).start();
  }

  /** Hides the overlay with slide-down animation. */
  public void hide() {
    animate().translationY(getHeight()).setDuration(250)
        .withEndAction(() -> setVisibility(GONE))
        .start();
  }

  /** Listener for adjustment changes. */
  public interface AdjustmentListener {
    void onKeyChange(int semitones);
    void onTempoChange(float factor);
    void onMelodyVolumeChange(float volume);
    void onGuideVolumeChange(float volume);
  }
}
