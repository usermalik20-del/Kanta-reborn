package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KantaSearchBar extends LinearLayout {
  private final EditText input;
  private final Context context;
  private final float density;
  public KantaSearchBar(Context context) { this(context, null); }
  public KantaSearchBar(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
    this.density = context.getResources().getDisplayMetrics().density;
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    TextView iconView = new TextView(context);
    iconView.setText("\uD83D\uDD0E");
    iconView.setTextSize(20);
    iconView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    iconParams.gravity = Gravity.CENTER_VERTICAL;
    addView(iconView, iconParams);
    input = new EditText(context);
    input.setSingleLine(true);
    input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    input.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    input.setHintTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
    input.setHint("Search songs, artists, or enter code");
    input.setTextSize(16);
    input.setTypeface(null, Typeface.NORMAL);
    LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    inputParams.weight = 1;
    inputParams.leftMargin = (int)(8*density);
    addView(input, inputParams);
    GradientDrawable normal = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 10,
        ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[]{android.R.attr.state_focused}, ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE, 10,
        ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN, 1, density));
    states.addState(new int[]{}, normal);
    setBackground(states);
    setPadding((int)(14*density),(int)(12*density),(int)(14*density),(int)(12*density));
  }
  public String getText() { return input.getText().toString(); }
  public void setHint(String hint) { input.setHint(hint); }
  public void setEnabled(boolean enabled) { input.setEnabled(enabled); setAlpha(enabled ? 1.0f : 0.45f); }
  public void requestFocusInput() { input.requestFocus(); }
  public void addTextChangedListener(TextWatcher watcher) { input.addTextChangedListener(watcher); }
  public void setOnEditorActionListener(android.widget.TextView.OnEditorActionListener listener) { input.setOnEditorActionListener(listener); }
  public EditText getEditText() { return input; }
}
