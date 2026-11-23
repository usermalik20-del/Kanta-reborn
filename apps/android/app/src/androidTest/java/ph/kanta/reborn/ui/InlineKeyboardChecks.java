package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import ph.kanta.reborn.R;
import ph.kanta.reborn.ui.components.InlineKeyboard;

/** Checks the inline keypad itself, independently of Home's search handling. */
final class InlineKeyboardChecks {
  private InlineKeyboardChecks() {}

  static void run(Context context) {
    ArrayList<String> calls = new ArrayList<>();
    InlineKeyboard keyboard = new InlineKeyboard(context, new InlineKeyboard.Listener() {
      @Override public void onText(String value) { calls.add("text:" + value); }
      @Override public void onBackspace() { calls.add("delete"); }
      @Override public void onSubmit() { calls.add("submit"); }
      @Override public void onKeyStep(int amount) { calls.add("key:" + amount); }
      @Override public void onTempoStep(int amount) { calls.add("tempo:" + amount); }
    });
    keyboard.setKeyFeedback(value -> calls.add("feedback:" + value));

    layout(keyboard, 411);
    require(keyboard.getChildCount() == 6, "Unexpected inline keypad row count");
    String digits = "1234567890";
    HashSet<Integer> colors = new HashSet<>();
    Button q = key(keyboard, "letter.q");
    int normalHeight = q.getHeight();
    for (int row = 0; row < 2; row++) {
      LinearLayout numbers = (LinearLayout) keyboard.getChildAt(row);
      require(numbers.getChildCount() == 5, "Expected five digits per row");
      for (int column = 0; column < 5; column++) {
        String value = digits.substring(5 * row + column, 5 * row + column + 1);
        Button digit = key(keyboard, "digit." + value);
        require(digit == numbers.getChildAt(column) && value.contentEquals(digit.getText()),
            "Digit duplicated or out of order: " + value);
        require(digit.getText() instanceof Spanned && ((Spanned) digit.getText())
            .getSpans(0, 1, ReplacementSpan.class).length == 1
            && digit.getTextSize() > q.getTextSize(), "Digit is not large and outlined: " + value);
        if (row == 1) {
          View above = key(keyboard, "digit." + digits.charAt(column));
          require(above.getLeft() == digit.getLeft() && above.getRight() == digit.getRight(),
              "Digit columns do not align");
        }
        int[] shades = shades(digit);
        require(colors.add(shades[0]) && shades[0] != shades[1] && shades[0] != shades[2],
            "Digit face lacks its own color, shading, or rim: " + value);
      }
    }
    require(shades(q)[0] == shades(key(keyboard, "letter.e"))[0]
        && shades(q)[0] != shades(key(keyboard, "letter.r"))[0]
        && shades(key(keyboard, "letter.r"))[0] != shades(key(keyboard, "letter.u"))[0],
        "QWERTY color groups changed");

    keyboard.setCompact(true);
    layout(keyboard, 280);
    Button delete = key(keyboard, "delete");
    require(q.getHeight() > 0 && q.getHeight() < normalHeight, "Compact keyboard height changed");
    require(q.getLeft() < key(keyboard, "letter.a").getLeft()
        && key(keyboard, "letter.a").getLeft() < key(keyboard, "letter.z").getLeft()
        && delete.getRight() == key(keyboard, "letter.p").getRight(), "QWERTY staggering changed");
    require(delete.getForeground() != null
        && context.getString(R.string.keyboard_delete).contentEquals(delete.getContentDescription()),
        "Backspace must have a visible icon and a spoken label");
    LinearLayout actions = (LinearLayout) keyboard.getChildAt(5);
    require(actions.getChildCount() == 7, "Bottom action order changed");
    require(context.getString(R.string.stage_key_short).contentEquals(((TextView) actions.getChildAt(2)).getText())
        && context.getString(R.string.stage_tempo_short).contentEquals(((TextView) actions.getChildAt(4)).getText()),
        "KEY and TEM no longer flank Space");
    String[] tags = {"key.down", "key.up", "space", "tempo.down", "tempo.up"};
    int[] positions = {0, 1, 3, 5, 6};
    key(keyboard, "digit.4").performClick();
    q.performClick();
    delete.performClick();
    for (int i = 0; i < tags.length; i++) {
      Button button = key(keyboard, tags[i]);
      require(button == actions.getChildAt(positions[i]), "Wrong position for " + tags[i]);
      button.performClick();
    }
    require(calls.equals(Arrays.asList("feedback:4", "text:4", "text:q", "feedback:delete",
        "delete", "key:-1", "key:1", "text: ", "tempo:-1", "tempo:1")),
        "Key feedback or callbacks changed: " + calls);
  }

  private static Button key(InlineKeyboard keyboard, String tag) {
    Button button = keyboard.findViewWithTag("kanta.keyboard." + tag);
    require(button != null && button.isClickable() && button.getContentDescription() != null,
        "Missing accessible button: " + tag);
    return button;
  }

  private static int[] shades(Button button) {
    // Sample the drawable only, so glyphs and font weight do not affect the colors.
    Bitmap bitmap = Bitmap.createBitmap(button.getWidth(), button.getHeight(), Bitmap.Config.ARGB_8888);
    Drawable background = button.getBackground();
    background.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
    background.draw(new Canvas(bitmap));
    int x = bitmap.getWidth() / 2;
    int top = bitmap.getPixel(x, bitmap.getHeight() / 4);
    int bottom = bitmap.getPixel(x, bitmap.getHeight() * 3 / 4);
    int rim = bitmap.getPixel(x, 0);
    bitmap.recycle();
    require(Color.alpha(top) == 255 && Color.alpha(rim) == 255, "Key face or rim is missing");
    return new int[] {top, bottom, rim};
  }

  private static void layout(InlineKeyboard keyboard, int widthDp) {
    int width = Math.round(widthDp * keyboard.getResources().getDisplayMetrics().density);
    keyboard.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    keyboard.layout(0, 0, width, keyboard.getMeasuredHeight());
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
