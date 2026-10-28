package ph.kanta.reborn.stage;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;

/**
 * On-screen numeric keypad for song code entry (TV/D-pad).
 *
 * <p>Per {@link replica/design/components.md} Keypad spec.
 */
public final class Keypad extends FrameLayout {

  private static final int ANIM_DURATION_MS = 150;

  private final GridLayout grid;
  private final KeypadListener listener;
  private int focusedIndex = 0;
  private static final String[] KEYS = {
      "1", "2", "3",
      "4", "5", "6",
      "7", "8", "9",
      "⌫", "0", "✓"
  };

  public Keypad(Context context) {
    this(context, null, null);
  }

  public Keypad(Context context, AttributeSet attrs) {
    this(context, attrs, null);
  }

  public Keypad(Context context, AttributeSet attrs, KeypadListener listener) {
    super(context, attrs);
    this.listener = listener;
    setWillNotDraw(false);
    setFocusable(true);
    setFocusableInTouchMode(true);

    float density = context.getResources().getDisplayMetrics().density;

    grid = new GridLayout(context);
    grid.setColumnCount(3);
    grid.setRowCount(4);
    grid.setPadding((int)(20 * density), (int)(20 * density), (int)(20 * density), (int)(20 * density));
    LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
    addView(grid, params);

    for (int i = 0; i < KEYS.length; i++) {
      TextView key = new TextView(context);
      key.setText(KEYS[i]);
      key.setTextSize(20);
      key.setTextColor(0xFFFFFFFF);
      key.setTypeface(null, android.graphics.Typeface.BOLD);
      key.setGravity(android.view.Gravity.CENTER);
      key.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_keypad_key_bg);
      key.setMinWidth((int)(64 * density));
      key.setMinHeight((int)(64 * density));
      key.setTag(i);
      key.setOnClickListener(v -> onKeyClick((Integer) v.getTag()));

      GridLayout.LayoutParams keyParams = new GridLayout.LayoutParams();
      keyParams.width = 0;
      keyParams.height = 0;
      keyParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
      keyParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
      keyParams.setMargins((int)(8 * density), (int)(8 * density), (int)(8 * density), (int)(8 * density));
      grid.addView(key, keyParams);

      // Special styling for submit and backspace
      if (i == 10) { // Backspace
        key.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_keypad_key_backspace_bg);
      } else if (i == 11) { // Submit
        key.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_keypad_key_submit_bg);
      }
    }

    updateFocus();
  }

  private void onKeyClick(int index) {
    if (listener == null) return;
    String key = KEYS[index];
    if (key.equals("⌫")) {
      listener.onBackspace();
    } else if (key.equals("✓")) {
      listener.onSubmit();
    } else {
      listener.onKey(key);
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_UP:
        moveFocus(-3);
        return true;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        moveFocus(3);
        return true;
      case KeyEvent.KEYCODE_DPAD_LEFT:
        moveFocus(-1);
        return true;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        moveFocus(1);
        return true;
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_ENTER:
        onKeyClick(focusedIndex);
        return true;
      case KeyEvent.KEYCODE_BACK:
        if (listener != null) listener.onDismiss();
        return true;
      default:
        // Handle numeric keys
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
          if (listener != null) listener.onKey(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
          return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
          if (listener != null) listener.onBackspace();
          return true;
        }
        return super.onKeyDown(keyCode, event);
    }
  }

  private void moveFocus(int delta) {
    int newIndex = focusedIndex + delta;
    if (newIndex < 0) newIndex = 0;
    if (newIndex >= KEYS.length) newIndex = KEYS.length - 1;
    focusedIndex = newIndex;
    updateFocus();
  }

  private void updateFocus() {
    for (int i = 0; i < grid.getChildCount(); i++) {
      View child = grid.getChildAt(i);
      if (child instanceof TextView) {
        TextView key = (TextView) child;
        boolean focused = (i == focusedIndex);
        key.setSelected(focused);
        if (focused) {
          key.setScaleX(1.08f);
          key.setScaleY(1.08f);
        } else {
          key.setScaleX(1f);
          key.setScaleY(1f);
        }
      }
    }
  }

  /** Shows the keypad with slide-up animation. */
  public void show() {
    setTranslationY(getHeight());
    setVisibility(VISIBLE);
    requestFocus();
    animate().translationY(0).setDuration(ANIM_DURATION_MS).start();
  }

  /** Hides the keypad with slide-down animation. */
  public void hide() {
    animate().translationY(getHeight()).setDuration(ANIM_DURATION_MS)
        .withEndAction(() -> setVisibility(GONE))
        .start();
  }

  /** Listener for keypad events. */
  public interface KeypadListener {
    void onKey(String digit);
    void onBackspace();
    void onSubmit();
    void onDismiss();
  }
}
