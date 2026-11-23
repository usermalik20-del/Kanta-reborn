package ph.kanta.reborn.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;
import ph.kanta.reborn.R;

/** Screen geometry checks inside the same body/scroll structure as MainActivity. */
public final class CompactLandscapeChecks {
  private CompactLandscapeChecks() {}

  public static void run(Context context) {
    Context shortWindow = configure(context, Configuration.ORIENTATION_LANDSCAPE, 400);
    Context tallWindow = configure(context, Configuration.ORIENTATION_LANDSCAPE, 740);
    Context portrait = configure(context, Configuration.ORIENTATION_PORTRAIT, 740);
    InlineKeyboardChecks.run(portrait);
    testHome(shortWindow, portrait);
    Configuration largeText = new Configuration(shortWindow.getResources().getConfiguration());
    largeText.fontScale = 2f;
    HomeScreen accessible = new HomeScreen(shortWindow.createConfigurationContext(largeText), null);
    accessible.setPauseResumeState(true, true, true);
    layout(pane(accessible, true), 225, 225);
    requireTextTransport(accessible, 225);
    testDockedLandscapeKeyboard(shortWindow, tallWindow);
    testGroups(shortWindow, tallWindow, portrait);
    testMenuViewport(portrait, shortWindow);
    testNowPlaying(shortWindow, portrait);
  }

  private static void testHome(Context compact, Context portrait) {
    HomeScreen screen = new HomeScreen(compact, null);
    ScrollView pane = pane(screen, true);
    View keyboard = screen.findViewWithTag("kanta.home.keyboard");
    View queue = screen.findViewWithTag("kanta.home.reservations");
    EditText search = screen.findViewWithTag("kanta.home.search");
    require(queue.getVisibility() == View.GONE,
        "Landscape Home must not duplicate the Stage Up next queue card");
    for (int width : new int[] {280, 320, 500}) {
      layout(pane, width, 225);
      View digit = screen.findViewWithTag("kanta.keyboard.digit.1");
      View letter = screen.findViewWithTag("kanta.keyboard.letter.q");
      require(digit != null && letter != null && bottomIn(digit, pane) <= bottomIn(letter, pane),
          "Number row should be above QWERTY at " + width);
      require(queue.getVisibility() == View.GONE,
          "Landscape Home must omit the duplicate queue card");
      require(keyboard.getBottom() <= pane.getHeight() || pane.canScrollVertically(1),
          "Compact landscape keypad must remain reachable without the queue card");
      requireTextTransport(screen, width);
      require(pane.canScrollVertically(1), "Tall Home content cannot scroll at " + width);
    }
    search.setText("1234");
    search.setSelection(4);
    screen.setReservedSongs(Arrays.asList("One", "Two", "Three"), 6);
    layout(pane, 320, 225);
    require(keyboard == screen.findViewWithTag("kanta.home.keyboard"), "Keyboard replaced on reflow");
    require("1234".equals(screen.getSearchText()) && search.getSelectionStart() == 4,
        "Reflow or queue update discarded Home entry");
    screen.findViewWithTag("kanta.keyboard.delete").performClick();
    require("123".equals(screen.getSearchText()), "Backspace no longer deletes one character");
    screen.findViewWithTag("kanta.stage.song-action.cancel").performClick();
    require(screen.getSearchText().isEmpty(), "Clear search button did not clear the entry");

    HomeScreen portraitHome = new HomeScreen(portrait, null);
    ScrollView portraitPane = pane(portraitHome, true);
    layout(portraitPane, 411, 225);
    requireTextTransport(portraitHome, 411);
    require(portraitHome.findViewWithTag("kanta.home.reservations").getBottom()
        <= portraitHome.findViewWithTag("kanta.home.keyboard").getTop(), "Portrait order changed");
    LinearLayout stage = new LinearLayout(portrait);
    stage.setOrientation(LinearLayout.VERTICAL);
    View lyrics = new View(portrait);
    stage.addView(lyrics);
    portraitHome.showSongLookup(stage);
    portraitHome.setSearchText("123");
    View dock = stage.findViewWithTag("kanta.stage.search-dock");
    require(stage.getChildAt(0) == dock && stage.getChildAt(1) == lyrics
        && dock.getBackground() == null, "Results must dock above lyrics without an opaque panel");
    layout(portraitPane, 411, 410);
    View firstDigit = portraitHome.findViewWithTag("kanta.keyboard.digit.1");
    View sixthDigit = portraitHome.findViewWithTag("kanta.keyboard.digit.6");
    View q = portraitHome.findViewWithTag("kanta.keyboard.letter.q");
    int initialHeight = firstDigit.getHeight();
    require(bottomIn(firstDigit, portraitPane) < bottomIn(sixthDigit, portraitPane)
        && bottomIn(sixthDigit, portraitPane) < bottomIn(q, portraitPane),
        "Both digit rows must sit above QWERTY");
    layout(portraitPane, 411, 540);
    require(firstDigit.getHeight() > initialHeight, "Keyboard failed to use the extra height");
  }

  private static void requireTextTransport(HomeScreen screen, int width) {
    ViewGroup transport = screen.findViewWithTag("kanta.stage.transport");
    require(transport.getChildCount() == 4, "Duplicate Stage transport at " + width);
    for (int i = 0; i < transport.getChildCount(); i++) {
      TextView button = (TextView) transport.getChildAt(i);
      require(button.getWidth() >= dp(button, 48) && button.getHeight() >= dp(button, 48),
          "Stage transport touch target too small at " + width);
      require(button.getLayout() != null && button.getLayout().getLineCount() == 1
              && button.getLayout().getEllipsisCount(0) == 0,
          "Stage transport label clipped at " + width + ": " + button.getText());
    }
  }

  private static void testDockedLandscapeKeyboard(Context compact, Context tall) {
    HomeScreen shortHome = new HomeScreen(compact, null);
    shortHome.showSongLookup(new LinearLayout(compact));
    ScrollView shortPane = pane(shortHome, true);
    layout(shortPane, 280, 225);
    View shortKeyboard = shortHome.findViewWithTag("kanta.home.keyboard");
    require(shortKeyboard.getWidth() == shortHome.getWidth() && shortPane.canScrollVertically(1),
        "Compact docked keypad should fit a narrow pane and stay scrollable");

    HomeScreen tallHome = new HomeScreen(tall, null);
    tallHome.showSongLookup(new LinearLayout(tall));
    ScrollView tallPane = pane(tallHome, true);
    View tallKeyboard = tallHome.findViewWithTag("kanta.home.keyboard");
    View digit = tallKeyboard.findViewWithTag("kanta.keyboard.digit.1");
    layout(tallPane, 760, 540);
    int regularRow = digit.getHeight();
    int regularHeight = tallKeyboard.getHeight();
    require(regularRow > dp(digit, 36) && tallKeyboard.getWidth() == dp(tallKeyboard, 500)
        && Math.abs(tallKeyboard.getLeft() * 2 + tallKeyboard.getWidth() - tallHome.getWidth()) <= 1,
        "Wide landscape keypad should be bounded and centered");
    layout(tallPane, 760, 740);
    require(tallKeyboard.getHeight() == regularHeight && digit.getHeight() == regularRow,
        "Docked landscape keypad stretched when height increased");
    layout(tallPane, 320, 540);
    require(tallKeyboard.getWidth() == tallHome.getWidth(),
        "Resized landscape keypad did not recover full narrow-pane width");
  }

  private static void testGroups(Context compact, Context tall, Context portrait) {
    GroupScreen group = group(compact);
    ScrollView pane = pane(group, false);
    for (int width : new int[] {180, 320, 500, 700}) {
      layout(pane, width, 225);
      View first = group.findViewWithTag("kanta.group.item0");
      int expected = width < 208 ? 1 : width < 456 ? 2 : width < 656 ? 3 : 4;
      require(((ViewGroup) first.getParent()).getChildCount() == expected,
          "Wrong column count at " + width);
      checkTouchTargets(group, dp(pane, 48));
      if (width >= 500) {
        require(bottomIn(group.findViewWithTag("kanta.group.item5"), pane) <= pane.getHeight(),
            "First six group actions do not fit");
      }
    }
    GroupScreen resizable = group(tall);
    ScrollView tallPane = pane(resizable, false);
    layout(tallPane, 500, 225);
    View first = resizable.findViewWithTag("kanta.group.item0");
    require(first.getHeight() < dp(first, 116), "Ignored short viewport in tall landscape window");
    layout(tallPane, 500, 530);
    require(first.getHeight() == dp(first, 116), "Tall landscape did not recover regular cards");

    GroupScreen portraitGroup = group(portrait);
    layout(pane(portraitGroup, false), 500, 225);
    View firstPortrait = portraitGroup.findViewWithTag("kanta.group.item0");
    require(firstPortrait.getHeight() == dp(firstPortrait, 116), "Portrait cards unexpectedly compact");
  }

  private static void testMenuViewport(Context portrait, Context compact) {
    for (Context context : new Context[] {portrait, compact}) {
      GroupScreen.Item[] cards = new GroupScreen.Item[4];
      for (int i = 0; i < cards.length; i++) {
        cards[i] = new GroupScreen.Item("menu" + i, "Menu item", "A longer description",
            R.drawable.kanta_menu_gear);
      }
      GroupScreen menu = new GroupScreen(context, "Menu", "Songs, stage and settings", cards, null);
      menu.setFillViewport();
      ScrollView pane = pane(menu, false);
      ((LinearLayout.LayoutParams) menu.getLayoutParams()).weight = 1;
      int width = context == portrait ? 411 : 270;
      layout(pane, width, 660);
      View last = menu.findViewWithTag("kanta.group.menu3");
      require(bottomIn(last, pane) >= pane.getHeight() - dp(pane, 40),
          "Menu leaves unused space below its last row");
      checkTouchTargets(menu, dp(pane, 48));
      layout(pane, width, 135);
      require(bottomIn(last, pane) > pane.getHeight(), "Short Menu lost its scrollable content");
      checkTouchTargets(menu, dp(pane, 48));
    }
  }

  private static void testNowPlaying(Context compact, Context portrait) {
    KantaNowPlayingScreen screen = new KantaNowPlayingScreen(compact, null);
    ScrollView pane = pane(screen, false);
    screen.setSongInfo("A long song title that truncates gracefully", "Singer");
    screen.setKey(-12);
    screen.setTempo(2f);
    for (int width : new int[] {245, 288, 320, 500}) {
      layout(pane, width, 225);
      for (String action : new String[] {"shuffle", "previous", "play-pause", "next", "repeat"}) {
        View control = screen.findViewWithTag("kanta.transport." + action);
        require(bottomIn(control, pane) <= pane.getHeight(), "Transport below fold at " + width);
        require(control.getLeft() >= 0 && control.getRight() <= ((View) control.getParent()).getWidth(),
            "Transport overlaps its row bounds at " + width);
      }
      checkTouchTargets(screen, dp(pane, 48));
      View key = screen.findViewWithTag("kanta.now-playing.key");
      View tempo = screen.findViewWithTag("kanta.now-playing.tempo");
      require(tempo.getTop() > key.getBottom() || tempo.getLeft() > key.getRight(),
          "Key and tempo overlap at " + width);
      require(key.getRight() <= ((View) key.getParent()).getWidth()
          && tempo.getRight() <= ((View) tempo.getParent()).getWidth(), "Stepper row overflow");
    }
    KantaNowPlayingScreen portraitPlayer = new KantaNowPlayingScreen(portrait, null);
    layout(pane(portraitPlayer, false), 411, 225);
    View play = portraitPlayer.findViewWithTag("kanta.transport.play-pause");
    require(play.getHeight() == dp(play, 72), "Portrait transport changed size");
  }

  private static GroupScreen group(Context context) {
    GroupScreen.Item[] items = new GroupScreen.Item[7];
    for (int i = 0; i < items.length; i++) {
      items[i] = new GroupScreen.Item("item" + i, "Playback options", "A short description",
          R.drawable.kanta_lyrics_color);
    }
    return new GroupScreen(context, "Performance", "Help me while I’m singing.", items, null);
  }

  private static Context configure(Context context, int orientation, int height) {
    Configuration config = new Configuration(context.getResources().getConfiguration());
    config.orientation = orientation;
    config.screenWidthDp = orientation == Configuration.ORIENTATION_PORTRAIT ? 411 : 1000;
    config.screenHeightDp = height;
    config.fontScale = 1f;
    return context.createConfigurationContext(config);
  }

  private static ScrollView pane(View screen, boolean matchHeight) {
    ScrollView pane = new ScrollView(screen.getContext());
    pane.setFillViewport(true);
    LinearLayout body = new LinearLayout(screen.getContext());
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(0, 0, 0, dp(screen, 14));
    pane.addView(body);
    body.addView(screen, new LinearLayout.LayoutParams(-1, matchHeight ? -1 : -2));
    return pane;
  }

  private static void layout(View view, int width, int height) {
    int w = dp(view, width), h = dp(view, height);
    view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, w, h);
  }

  private static int bottomIn(View child, View pane) {
    int bottom = child.getBottom();
    for (View parent = (View) child.getParent(); parent != pane; parent = (View) parent.getParent()) {
      bottom += parent.getTop();
    }
    return bottom;
  }

  private static void checkTouchTargets(View view, int min) {
    // Shared components truncate dp conversion; tolerate a pixel on fractional densities.
    if (view.isClickable() && (view.getTag() != null || view instanceof TextView)) {
      require(view.getWidth() >= min - 1 && view.getHeight() >= min - 1,
          "Control under 48dp: " + view.getTag());
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) checkTouchTargets(group.getChildAt(i), min);
    }
  }

  private static int dp(View view, int dp) {
    return Math.round(dp * view.getResources().getDisplayMetrics().density);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
