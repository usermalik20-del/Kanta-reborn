package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import ph.kanta.reborn.R;
import ph.kanta.reborn.ui.KantaTheme;

/** Persistent number row and QWERTY keyboard with KEY/TEM; never opens the system IME. */
@android.annotation.SuppressLint("ViewConstructor")
public final class InlineKeyboard extends LinearLayout {
  public interface Listener {
    void onText(String value);
    void onBackspace();
    void onSubmit();
    void onKeyStep(int amount);
    void onTempoStep(int amount);
  }

  private static final int ROW_HEIGHT_DP = 48;
  private static final int COMPACT_ROW_HEIGHT_DP = 38;
  private static final int KEY_GAP_DP = 2;

  private enum Tone {
    RED(0xFFF83D66, 0xFFA10520, 0xFFFF7088, 0xFFFF93A5),
    ORANGE(0xFFFFA321, 0xFFD74700, 0xFFFFBA4A, 0xFFFFC079),
    YELLOW(0xFFFFED50, 0xFFBD9400, 0xFFFFEF7B, 0xFFFFEAA3),
    GREEN(0xFF44AD4A, 0xFF00511D, 0xFF68C771, 0xFF91DE79),
    CYAN(0xFF18BDF5, 0xFF0064B3, 0xFF58D0F5, 0xFF9DEAFF),
    VIOLET(0xFF8C3FBB, 0xFF44086B, 0xFFAC67DA, 0xFFE3A4FF),
    RED_ORANGE(0xFFFF743E, 0xFFAF1909, 0xFFFF9367, 0xFFFFB38B),
    AMBER(0xFFFFCB41, 0xFFAC6D00, 0xFFFFD86C, 0xFFFFE4A3),
    LIME(0xFF9CD440, 0xFF407404, 0xFFB0E36A, 0xFFC7F482),
    TEAL(0xFF278BA2, 0xFF033D56, 0xFF50ABC0, 0xFF8AD6E5),
    LETTER_TEAL(0xFF105C63, 0xFF043C45, 0xFF46A9AE),
    LETTER_VIOLET(0xFF503285, 0xFF35225E, 0xFF9463C7),
    LETTER_MAGENTA(0xFF67156C, 0xFF47094D, 0xFFB456B1),
    ACTION_BLUE(0xFF22274F, 0xFF171539, 0xFF67BDDF),
    ACTION_VIOLET(0xFF28234E, 0xFF181235, 0xFF9361D1),
    SPACE(0xFF221F4E, 0xFF171539, 0xFF67BDDF),
    DELETE(0xFF261F48, 0xFF171534, 0xFFBC55B1);

    final int top, bottom, rim, ink;

    Tone(int top, int bottom, int rim) {
      this(top, bottom, rim, KantaTheme.TEXT_PRIMARY);
    }

    Tone(int top, int bottom, int rim, int ink) {
      this.top = top;
      this.bottom = bottom;
      this.rim = rim;
      this.ink = ink;
    }
  }

  private final float density;
  private final ArrayList<LinearLayout> rows = new ArrayList<>();
  private Consumer<String> keyFeedback = key -> {};

  public InlineKeyboard(Context context, Listener listener) {
    super(context);
    density = getResources().getDisplayMetrics().density;
    setOrientation(VERTICAL);
    setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    setPadding(dp(5), dp(6), dp(5), dp(6));

    // The reference repeats 4; use five equal columns and one button per digit.
    digits(row(), "12345", listener, Tone.RED, Tone.ORANGE, Tone.YELLOW, Tone.GREEN, Tone.CYAN);
    digits(row(), "67890", listener, Tone.VIOLET, Tone.RED_ORANGE, Tone.AMBER, Tone.LIME, Tone.TEAL);

    letters(row(), "qwertyuiop", 3, 3, listener);

    LinearLayout middle = row();
    space(middle, .5f);
    letters(middle, "asdfghjkl", 3, 2, listener);
    space(middle, .5f);

    LinearLayout lower = row();
    space(lower, 1);
    letters(lower, "zxcvbnm", 2, 3, listener);
    addKey(lower, context.getString(R.string.keyboard_delete_short),
        context.getString(R.string.keyboard_delete), "delete", 2, Tone.DELETE, 14, 0, () -> {
          keyFeedback.accept("delete");
          listener.onBackspace();
        });

    // Bottom row: − + KEY | Space | TEM − +.
    LinearLayout actions = row();
    addAdjustments(actions, R.string.stage_key_short, "key", true, R.string.stage_key_lower,
        R.string.stage_key_raise, listener::onKeyStep);
    String spaceLabel = context.getString(R.string.keyboard_space);
    addKey(actions, spaceLabel, spaceLabel, "space", 4.3f, Tone.SPACE, 14, 0,
        () -> listener.onText(" "));
    addAdjustments(actions, R.string.stage_tempo_short, "tempo", false, R.string.stage_tempo_slower,
        R.string.stage_tempo_faster, listener::onTempoStep);
  }

  public void setKeyFeedback(Consumer<String> feedback) { keyFeedback = feedback; }

  public void setCompact(boolean compact) {
    for (LinearLayout row : rows) {
      row.getLayoutParams().height = dp(compact ? COMPACT_ROW_HEIGHT_DP : ROW_HEIGHT_DP);
    }
    requestLayout();
  }

  private int dp(float value) { return Math.round(value * density); }

  private LinearLayout row() {
    LinearLayout row = new LinearLayout(getContext());
    row.setBaselineAligned(false);
    addView(row, new LayoutParams(-1, dp(ROW_HEIGHT_DP), 1));
    rows.add(row);
    return row;
  }

  private void addAdjustments(LinearLayout row, int title, String tag, boolean labelLast, int lower,
      int raise, IntConsumer change) {
    TextView label = new TextView(getContext());
    label.setText(title);
    fitLabel(label, 12);
    LayoutParams labelParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, .8f);
    if (!labelLast) row.addView(label, labelParams);
    addKey(row, "−", getContext().getString(lower), tag + ".down", 1,
        labelLast ? Tone.ACTION_BLUE : Tone.ACTION_VIOLET, 19, 0, () -> change.accept(-1));
    addKey(row, "+", getContext().getString(raise), tag + ".up", 1,
        Tone.ACTION_VIOLET, 19, 0, () -> change.accept(1));
    if (labelLast) row.addView(label, labelParams);
  }

  private void space(LinearLayout row, float weight) {
    row.addView(new View(getContext()), new LayoutParams(0, 0, weight));
  }

  private void digits(LinearLayout row, String digits, Listener listener, Tone... tones) {
    for (int i = 0; i < digits.length(); i++) {
      String value = digits.substring(i, i + 1);
      Tone tone = tones[i];
      SpannableString caption = new SpannableString(value);
      caption.setSpan(new DigitOutline(tone.ink), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      addKey(row, caption, value, "digit." + value, 1, tone, 33, 0, () -> {
        keyFeedback.accept(value);
        listener.onText(value);
      });
    }
  }

  private void letters(LinearLayout row, String letters, int tealCount, int violetCount,
      Listener listener) {
    for (int i = 0; i < letters.length(); i++) {
      String value = letters.substring(i, i + 1);
      Tone tone = i < tealCount ? Tone.LETTER_TEAL
          : i < tealCount + violetCount ? Tone.LETTER_VIOLET : Tone.LETTER_MAGENTA;
      addKey(row, value, value, "letter." + value, 1, tone, 17, 0, () -> listener.onText(value));
    }
  }

  private void fitLabel(TextView label, int maxSp) {
    label.setMaxLines(1);
    label.setIncludeFontPadding(false);
    label.setGravity(Gravity.CENTER);
    label.setTextColor(KantaTheme.TEXT_PRIMARY);
    label.setAutoSizeTextTypeUniformWithConfiguration(9, maxSp, 1, TypedValue.COMPLEX_UNIT_SP);
  }

  private void addKey(LinearLayout row, CharSequence label, String description, String tag,
      float weight, Tone tone, int sizeSp, int icon, Runnable click) {
    Button key = new Button(getContext());
    key.setAllCaps(false);
    key.setText(label);
    key.setContentDescription(description);
    key.setTag("kanta.keyboard." + tag);
    key.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    fitLabel(key, sizeSp);
    key.setMinWidth(0);
    key.setMinimumWidth(0);
    key.setMinHeight(0);
    key.setMinimumHeight(0);
    key.setBackgroundTintList(null);
    key.setStateListAnimator(null);
    key.setBackground(keyBackground(tone));
    key.setPadding(0, 0, 0, 0);
    if (icon != 0) {
      key.setForeground(getContext().getDrawable(icon));
      key.setForegroundGravity(Gravity.CENTER);
    }
    key.setOnClickListener(v -> click.run());
    LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
    params.setMargins(dp(KEY_GAP_DP), dp(KEY_GAP_DP), dp(KEY_GAP_DP), dp(KEY_GAP_DP));
    row.addView(key, params);
  }

  private RippleDrawable keyBackground(Tone tone) {
    // A gradient rim behind an inset face keeps each key distinct against the dark stage.
    int rimEnd = tone == Tone.SPACE ? Tone.ACTION_VIOLET.rim : tone.rim;
    GradientDrawable rim = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
        new int[] {tone.rim, rimEnd});
    rim.setCornerRadius(dp(7));
    GradientDrawable face = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        new int[] {tone.top, tone.bottom});
    face.setCornerRadius(dp(6));
    LayerDrawable cap = new LayerDrawable(new Drawable[] {rim, face});
    int edge = dp(1);
    cap.setLayerInset(1, edge, edge, edge, edge);
    return new RippleDrawable(ColorStateList.valueOf(0x66FFFFFF), cap, null);
  }

  /** Outline the native digit text without replacing its label or clickable semantics. */
  private static final class DigitOutline extends ReplacementSpan {
    private final int ink;

    DigitOutline(int ink) { this.ink = ink; }

    private float strokeWidth(Paint paint) { return paint.getTextSize() * .085f; }

    @Override public int getSize(Paint paint, CharSequence text, int start, int end,
        Paint.FontMetricsInt metrics) {
      if (metrics != null) paint.getFontMetricsInt(metrics);
      return (int) Math.ceil(paint.measureText(text, start, end) + strokeWidth(paint));
    }

    @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
        int top, int y, int bottom, Paint paint) {
      Paint.Style style = paint.getStyle();
      float width = paint.getStrokeWidth();
      int color = paint.getColor();
      float edge = strokeWidth(paint);
      paint.setColor(0xFF141021);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(edge);
      canvas.drawText(text, start, end, x + edge / 2, y, paint);
      paint.setColor(ink);
      paint.setStyle(Paint.Style.FILL);
      canvas.drawText(text, start, end, x + edge / 2, y, paint);
      paint.setStyle(style);
      paint.setStrokeWidth(width);
      paint.setColor(color);
    }
  }
}
