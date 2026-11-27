package ph.kanta.reborn;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import ph.kanta.reborn.core.*;

/**
 * Instrumentation exercises real JNI, AudioTrack and activity lifecycle on a clean test install.
 */
public final class RuntimeChecks extends Instrumentation {
  private int checks;
  private MainActivity activity;
  private SessionController session;
  private View persistentPrimaryStage;
  private int persistentStageOrientation = -1;

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    start();
  }

  private void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  private boolean state(BooleanSupplier predicate) {
    AtomicReference<Boolean> result = new AtomicReference<>(false);
    runOnMainSync(() -> result.set(predicate.getAsBoolean()));
    return result.get();
  }

  private void await(BooleanSupplier predicate, String message) {
    long deadline = SystemClock.uptimeMillis() + 30000;
    while (!state(predicate) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
    check(state(predicate), message);
  }

  private boolean contains(View view, String text) {
    if (view instanceof TextView && ((TextView) view).getText().toString().contains(text))
      return true;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (contains(group.getChildAt(i), text)) return true;
    }
    return false;
  }

  private boolean containsImageAsset(View view) {
    if (view instanceof ImageView && ((ImageView) view).getDrawable() != null) return true;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (containsImageAsset(group.getChildAt(i))) return true;
    }
    return false;
  }

  private void checkIconTile(View view, String label) {
    check(view != null && view.isClickable(), label + " tile is not clickable");
    check(containsImageAsset(view), label + " tile is missing its themed icon");
    check(view.getContentDescription() != null, label + " tile is missing its accessible label");
  }

  private void checkIconImageButton(View view, String label) {
    check(view instanceof ImageButton, label + " is not an image button");
    check(((ImageButton) view).getDrawable() != null, label + " icon is missing");
    check(view.getContentDescription() != null, label + " is missing its accessible label");
  }

  private void checkStageTextButton(View view, int text) {
    String label = activity.getString(text);
    check(view instanceof android.widget.Button && label.contentEquals(((TextView) view).getText()),
        "Stage control must show its text: " + label);
    check(view.createAccessibilityNodeInfo().getText() != null
            || view.createAccessibilityNodeInfo().getContentDescription() != null,
        "Missing accessible action label: " + label);
  }

  private void checkIconBack(View view, String label) {
    checkIconImageButton(view, label + " back control");
  }

  private boolean clickText(View view, String text) {
    if (view instanceof android.widget.Button
        && ((android.widget.Button) view).getText().toString().equals(text))
      return view.performClick();
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (clickText(group.getChildAt(i), text)) return true;
    }
    return false;
  }

  private void upNextTickerChecks() {
    runOnMainSync(() -> {
      ph.kanta.reborn.ui.components.UpNextTicker ticker = persistentPrimaryStage.findViewWithTag("kanta.stage.up-next");
      check(ticker != null, "Primary stage next-song strip missing");
      ReservationQueue fixture = new ReservationQueue();
      Song repeated = new Song("ticker-fixture", "A repeated song", "Artist", "007", "fixture.mid", false);
      for (int i = 0; i < 10; i++) fixture.reserve(java.util.UUID.randomUUID(), repeated, "Singer " + (i + 1));
      try {
        ticker.updateFromSnapshot(fixture.snapshot());
        TextView label = ticker.findViewWithTag("kanta.stage.up-next.text");
        check(ticker.getVisibility() == View.VISIBLE, "Reserved songs did not reveal stage hint");
        check(label.getText().toString().contains("1. A repeated song — Singer 1")
            && label.getText().toString().contains("2. A repeated song — Singer 2"),
            "Intentional repeated song disappeared");
        check(label.getText().toString().contains("+2 more"), "Ticker overflow count is incorrect");
        CharSequence before = label.getText();
        ticker.updateFromSnapshot(fixture.snapshot());
        check(before == label.getText(), "Unchanged snapshot reset the marquee text");
        ticker.setVisualActive(false);
        check(!label.isSelected(), "Ticker did not stop for inactive host");
        ticker.setVisualActive(true);
        fixture.move(fixture.snapshot().get(1).id, 0);
        ticker.updateFromSnapshot(fixture.snapshot());
        check(label.getText().toString().startsWith("Up next · 1. A repeated song — Singer 2"),
            "Ticker did not follow reservation reorder");
        fixture.clear();
        ticker.updateFromSnapshot(fixture.snapshot());
        check(ticker.getVisibility() == View.GONE, "Ticker empty queue is still visible");
      } finally {
        ticker.updateFromSnapshot(session.queue.snapshot());
        ticker.setVisualActive(true);
      }
    });
  }

  private void playerLayoutChecks() {
    runOnMainSync(() -> {
      float density = activity.getResources().getDisplayMetrics().density;
      for (int width : new int[] {288, 320, 411, 720}) {
        ph.kanta.reborn.ui.KantaNowPlayingScreen screen = new ph.kanta.reborn.ui.KantaNowPlayingScreen(activity, null);
        screen.setSongInfo("A long song title that should leave room for the actions", "Artist");
        screen.setTempo(2f);
        int w = Math.round(width * density);
        screen.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        screen.layout(0, 0, w, screen.getMeasuredHeight());
        View previous = null;
        for (String tag : new String[] {"kanta.transport.shuffle", "kanta.transport.previous",
            "kanta.transport.play-pause", "kanta.transport.next", "kanta.transport.repeat"}) {
          View button = screen.findViewWithTag(tag);
          check(button != null && button.getWidth() >= Math.round(48 * density),
              "Transport touch target too small at " + width);
          if (previous != null) check(button.getLeft() > previous.getRight(),
              "Transport controls touch or overlap at " + width);
          check(button.getLeft() >= 0 && button.getRight() <= ((View) button.getParent()).getWidth(),
              "Transport control overflows at " + width);
          previous = button;
        }
        View key = screen.findViewWithTag("kanta.now-playing.key");
        View tempo = screen.findViewWithTag("kanta.now-playing.tempo");
        check(key != null && tempo != null, "Key/tempo layout identities missing");
        check(tempo.getTop() > key.getBottom() || tempo.getLeft() > key.getRight(),
            "Key and tempo controls are not separated at " + width);
        View adjustments = (View) key.getParent();
        check(key.getRight() <= adjustments.getWidth() && tempo.getRight() <= adjustments.getWidth(),
            "Key or tempo overflows horizontally at " + width);
        if (width == 411) check(tempo.getTop() == key.getTop(),
            "Normal phone unnecessarily stacks fitting key/tempo controls");
      }
    });
  }

  private void homeLayoutChecks() {
    runOnMainSync(() -> {
      android.widget.EditText search = activity.getWindow().getDecorView()
          .findViewWithTag("kanta.home.search");
      check(search != null && search.getText().length() == 0,
          "Home search placeholder became editable query text");
      check(!search.getShowSoftInputOnFocus(), "Home field must use the inline keyboard only");
      float density = activity.getResources().getDisplayMetrics().density;
      for (int width : new int[] {320, 411, 720}) {
        for (int height : new int[] {320, 620}) {
          // Exercise the same fill-viewport arrangement without touching the live controller queue.
          android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
          scroll.setFillViewport(true);
          ph.kanta.reborn.ui.HomeScreen sample = new ph.kanta.reborn.ui.HomeScreen(activity, null);
          scroll.addView(sample);
          sample.setReservedSongs(java.util.Arrays.asList("First song", "Second song", "Third song"), 5);
          int w = Math.round(width * density), h = Math.round(height * density);
          scroll.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
              View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
          scroll.layout(0, 0, w, h);
          View queue = sample.findViewWithTag("kanta.home.reservations");
          View keyboard = sample.findViewWithTag("kanta.home.keyboard");
          View searchField = sample.findViewWithTag("kanta.home.search");
          ViewGroup searchRow = (ViewGroup) searchField.getParent();
          View controls = sample.findViewWithTag("kanta.stage.controls");
          View fullscreen = sample.findViewWithTag("kanta.stage.fullscreen");
          check(queue.getWidth() == w && keyboard.getWidth() == w && searchRow.getWidth() == w,
              "Home search row, reservation panel or keyboard has a side gutter");
          check(searchRow.getChildCount() == 3 && searchRow.getChildAt(0) == controls
              && searchRow.getChildAt(1) == searchField && searchRow.getChildAt(2) == fullscreen,
              "Stage row must contain Controls, the real editor, then fullscreen");
          int edge = Math.round(16 * density), margin = Math.round(12 * density);
          check(searchRow.getPaddingStart() == edge && searchRow.getPaddingEnd() == edge
              && searchField.getHeight() == Math.round(40 * density)
              && searchField.getWidth() > 0, "Stage search pill dimensions are incorrect");
          android.widget.LinearLayout.LayoutParams fieldParams =
              (android.widget.LinearLayout.LayoutParams) searchField.getLayoutParams();
          check(fieldParams.width == 0 && fieldParams.weight == 1f
              && fieldParams.getMarginStart() == margin && fieldParams.getMarginEnd() == margin,
              "Stage search must fill remaining width with 12dp horizontal margins");
          View actions = sample.findViewWithTag("kanta.stage.transport");
          View actionScroll = (View) actions.getParent();
          check(searchRow.getBottom() == actionScroll.getTop(),
              "Stage search row is not immediately above transport");
          check(keyboard.findViewWithTag("kanta.keyboard.key.down") != null
              && keyboard.findViewWithTag("kanta.keyboard.tempo.up") != null,
              "Inline key and tempo buttons are missing");
          check(queue.getBottom() <= keyboard.getTop(), "Home queue overlaps keypad");
          check(keyboard.getBottom() <= sample.getHeight(), "Home keypad clipped by content height");
          check(sample.getHeight() >= h, "Home does not fill the available utility height");
          check(contains(sample, "1. First song") && contains(sample, "3. Third song")
              && contains(sample, "Reserved songs · 5"), "Home queue preview lost FIFO order or total");
          sample.setReservedSongs(java.util.Collections.emptyList(), 0);
          check(sample.findViewWithTag("kanta.home.queue.empty") != null, "Home queue empty state missing");
        }
      }
      View decor = activity.getWindow().getDecorView();
      ViewGroup transport = decor.findViewWithTag("kanta.stage.transport");
      String[] actions = {"pause-resume", "submit", "next", "stop"};
      int[] labels = {R.string.stage_play, R.string.stage_reserve, R.string.stage_next, R.string.stage_stop};
      check(transport != null && transport.getChildCount() == actions.length,
          "Stage should have one Play/Pause, Reserve, Next and Stop");
      for (int i = 0; i < actions.length; i++) {
        View action = transport.getChildAt(i);
        check(("kanta.stage.song-action." + actions[i]).equals(action.getTag()),
            "Wrong action in Stage transport row: " + actions[i]);
        checkStageTextButton(action, labels[i]);
      }
      check(decor.findViewWithTag("kanta.stage.song-action.cancel") == null,
          "Stage row must not add a fourth clear action beside the editor");
      // Icon-only fullscreen action: the accessible name stays, the caption text does not.
      View fullscreenAction = decor.findViewWithTag("kanta.stage.fullscreen");
      boolean fullscreenIconOnly = fullscreenAction instanceof Button
          && ((Button) fullscreenAction).getText().length() == 0
          && fullscreenAction.getContentDescription()
              .equals(activity.getString(R.string.stage_fullscreen));
      check(fullscreenIconOnly, "Fullscreen shortcut must be icon-only with its accessible label");
      checkStageTextButton(decor.findViewWithTag("kanta.stage.controls"), R.string.stage_controls);
      check(fullscreenAction.getParent() == search.getParent()
          && decor.findViewWithTag("kanta.stage.controls").getParent() == search.getParent(),
          "Controls, search and fullscreen must share one row");
      View logo = decor.findViewWithTag("kanta.header.logo");
      View remote = decor.findViewWithTag("kanta.stage.remote-status");
      check(remote != null && remote.getParent() == logo.getParent() && remote.getTop() >= logo.getBottom(),
          "The Offline badge does not sit below the Stage logo");
      check(((ViewGroup) logo.getParent()).getChildCount() == 2,
          "Stage logo must have exactly one connection status badge");
      check(decor.findViewWithTag("kanta.stage.camera") != null
          && decor.findViewWithTag("kanta.stage.fullscreen") != null
          && decor.findViewWithTag("kanta.stage.song-action.next") != null
          && decor.findViewWithTag("kanta.stage.song-action.stop") != null,
          "Stage transport or camera/fullscreen shortcuts are missing");
      check(decor.findViewWithTag("kanta.home.keyboard-toggle") == null
          && decor.findViewWithTag("kanta.keyboard.digit.1") != null
          && decor.findViewWithTag("kanta.keyboard.letter.q") != null,
          "Number and letter keys must share the same keyboard");
      int originalPitch = session.pitch;
      float originalTempo = session.tempo;
      check(decor.findViewWithTag("kanta.keyboard.key.down").performClick()
          && session.pitch == originalPitch - 1, "KEY minus did not lower the key");
      check(decor.findViewWithTag("kanta.keyboard.tempo.up").performClick()
          && Math.abs(session.tempo - originalTempo - .05f) < .001f, "TEM plus did not raise tempo");
      session.controls(session.volume, originalTempo, originalPitch);
      search.setText("test");
      search.setSelection(0, search.length());
      check(decor.findViewWithTag("kanta.keyboard.delete").performClick(),
          "Inline Delete did not accept the selected search text");
      check(search.length() == 0 && search.hasFocus(),
          "Deleting selected search text did not empty and refocus the field");
      search.setText("1234");
      search.setSelection(4);
      activity.getWindow().getDecorView().findViewWithTag("kanta.keyboard.delete").performClick();
      check("123".contentEquals(search.getText()), "DEL did not remove exactly one character");
      TextView stageCode = persistentPrimaryStage.findViewWithTag("kanta.stage.presence.code");
      check(stageCode != null && "123".contentEquals(stageCode.getText()),
          "Home digits did not reach the standby stage");

      try {
        java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod("render");
        render.setAccessible(true);
        render.invoke(activity);
      } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
      check(activity.getWindow().getDecorView().findViewWithTag("kanta.home.search") == search
          && "123".contentEquals(search.getText()), "Queue refresh discarded in-progress Home entry");
      search.setText("");
    });
  }

  private void diagnosticCaptureChecks() {
    DiagnosticEvents events = new DiagnosticEvents(getTargetContext());
    for (int i = 0; i < 40; i++) events.record("Test event " + i + ";", null);
    events.record("Test failure", new java.io.IOException("private-file-name.pdf"));
    String report = events.snapshot();
    check(!report.contains("Test event 0;") && report.contains("Test event 39;"),
        "Diagnostic event history was not bounded");
    check(report.contains("Test failure · java.io.IOException")
        && !report.contains("private-file-name.pdf"), "Diagnostic capture exposed exception text");

    android.content.Context target = getTargetContext();
    android.content.SharedPreferences prefs = target.getSharedPreferences(
        "diagnostics-instrumentation", android.content.Context.MODE_PRIVATE);
    java.io.File marker = new java.io.File(target.getNoBackupFilesDir(), "diagnostics-instrumentation");
    prefs.edit().clear().commit();
    AutomaticDiagnostics gated = new AutomaticDiagnostics(prefs, marker, false);
    check(!gated.setEnabled(true) && !gated.enabled() && prefs.getAll().isEmpty(),
        "Disabled build allowed an automatic diagnostics opt-in");
    AutomaticDiagnostics reporting = new AutomaticDiagnostics(prefs, marker, true);
    reporting.markCrash();
    check(!reporting.enabled() && !marker.exists(), "Crash marker saved without consent");
    check(reporting.setEnabled(true), "Explicit consent was not saved");
    String firstId = prefs.getString("installation", "");
    String firstKey = prefs.getString("consent-key", "");
    reporting.markCrash();
    check(marker.isFile() && marker.length() == 36,
        "Automatic crash capture is not a bounded marker");
    check(java.util.UUID.fromString(firstId).version() == 4 && firstKey.matches("[a-f0-9]{64}"),
        "Diagnostics identity or bearer is malformed");
    check(!reporting.setEnabled(false) && !marker.exists() && prefs.getAll().isEmpty(),
        "Opt-out did not remove local identity and crash marker");
    reporting.markCrash();
    check(!marker.exists(), "Crash marker saved after opt-out");
    check(reporting.setEnabled(true) && !firstId.equals(prefs.getString("installation", ""))
        && !firstKey.equals(prefs.getString("consent-key", "")), "New consent reused old identity");
    reporting.setEnabled(false);
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      Intent launch =
          new Intent(getTargetContext(), MainActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity = (MainActivity) startActivitySync(launch);
      session = ((KantaApplication) activity.getApplication()).session;
      await(() -> session.ready && session.startupAudioPrepared && session.stageReady
          && field(activity, "startupDialog") == null, "Startup did not open Stage after loading");
      diagnosticCaptureChecks();
      checks += ph.kanta.reborn.font.FontStoreChecks.run(getTargetContext());
      checks += ph.kanta.reborn.lyrics.LrclibClientChecks.run();
      checks += ph.kanta.reborn.stage.StageLyricLayoutChecks.run();
      runOnMainSync(() -> checks += ph.kanta.reborn.pdf.PdfPageViewChecks.run(activity));
      capture("library_ready.png");
      check(
          state(() -> activity.getWindow().getDecorView().findViewWithTag("kanta.home.search") != null),
          "Reference-led Home dashboard was not mounted at launch");
      runOnMainSync(() -> {
        persistentPrimaryStage = activity.getWindow().getDecorView()
            .findViewWithTag("kanta.stage.primary");
        persistentStageOrientation = (Integer) field(activity, "lastOrientation");
      });
      assertPersistentStage(null);
      // The empty stage must read as a karaoke machine waiting for a song code, and its
      // background must be the moving BGV video rather than a frozen wallpaper. Runs only
      // after persistentPrimaryStage is resolved above, because the standby checks inspect
      // that view rather than looking the stage up themselves.
      stageStandbyChecks();
      homeLayoutChecks();
      playerLayoutChecks();
      runOnMainSync(() -> ph.kanta.reborn.ui.CompactLandscapeChecks.run(activity));
      upNextTickerChecks();
      waitForIdleSync();
      await(() -> {
        android.view.WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        return android.os.Build.VERSION.SDK_INT < 30 || insets == null
            || !insets.isVisible(android.view.WindowInsets.Type.ime());
      }, "Home inline keyboard unexpectedly opened the system IME");
      capture("home_dashboard.png");
      runOnMainSync(() -> {
        View logo = activity.getWindow().getDecorView().findViewWithTag("kanta.header.logo");
        check(logo != null && !logo.isClickable(), "Stage logo still opens the removed deck");
      });
      runOnMainSync(() -> {
        View decor = activity.getWindow().getDecorView();
        decor.findViewWithTag("kanta.nav.More").performClick();
        for (String removed : new String[]{"sing-menu", "performance-menu", "experience-menu",
            "find-song", "key-tempo", "queue", "background", "party", "stage", "visual", "voice", "scoring",
            "backup", "app-update", "legal", "help"}) {
          check(decor.findViewWithTag("kanta.group." + removed) == null, "Redundant Menu row: " + removed);
        }
        String[] ordered = {"my-songs", "import-pdf", "stage-visual", "intermission", "party-pads", "voice-scoring",
            "settings", "browser-remote"};
        for (String action : ordered) checkIconTile(decor.findViewWithTag("kanta.group." + action), action);
        checkIconBack(decor.findViewWithTag("kanta.group.back"), "Menu");
      });
      await(() -> visibleOnScreen("kanta.group.my-songs"), "Menu Presets row is not visible");
      capture("menu_screen.png", "kanta.group.my-songs");
      runOnMainSync(() -> {
        View decor = activity.getWindow().getDecorView();
        int lastTop = Integer.MIN_VALUE;
        for (String action : new String[]{"my-songs", "import-pdf", "stage-visual", "intermission", "party-pads", "voice-scoring",
            "settings", "browser-remote"}) {
          int[] location = new int[2];
          decor.findViewWithTag("kanta.group." + action).getLocationInWindow(location);
          check(location[1] > lastTop, "Menu order is wrong at " + action);
          lastTop = location[1];
        }
        check(decor.findViewWithTag("kanta.group.import-pdf").performClick(), "KANTA Songbook did not open");
      });
      await(() -> visibleOnScreen("kanta.screen.pdf-songbooks"), "Menu Songbook destination is missing");
      runOnMainSync(() -> activity.getOnBackPressedDispatcher().onBackPressed());
      await(() -> visibleOnScreen("kanta.group.back"), "Songbook did not return to Menu");
      runOnMainSync(() -> activity.getWindow().getDecorView().findViewWithTag("kanta.group.back").performClick());
      await(() -> visibleOnScreen("kanta.home.search"), "Back from Menu did not return to Stage");
      capture("home_dashboard_restored.png");
      awaitStageSearch("Stage Search");
      check(state(() -> session.songs.isEmpty()), "Clean install unexpectedly populated with starter songs");
      runOnMainSync(() -> session.importSongs(java.util.Collections.singletonList(FixtureProvider.file("song"))));
      await(() -> !session.importing && session.songs.size() == 1, "Stage search fixture did not import");
      stageSearchReservationChecks();
      runOnMainSync(this::openStageQueueShortcut);
      await(
          () -> activity.getWindow().getDecorView().findViewWithTag("kanta.screen.queue") != null,
          "Home Queue did not open the reference Queue destination");
      capture("queue_screen.png", "kanta.screen.queue");
      runOnMainSync(() -> {
        View back = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.queue.back");
        check(back != null, "Queue back control missing");
        checkIconBack(back, "Queue");
        back.performClick();
      });
      await(() -> visibleOnScreen("kanta.home.search"), "Queue did not return to Stage");
      runOnMainSync(this::openStageQueueShortcut);
      await(() -> visibleOnScreen("kanta.screen.queue"), "Queue did not reopen");
      runOnMainSync(() -> {
        View add = activity.getWindow().getDecorView().findViewWithTag("kanta.queue.add-songs");
        check(add != null && add.performClick(), "Queue Add songs did not respond");
      });
      awaitStageSearch("Queue Add songs");
      runOnMainSync(() -> openMenuGroup("settings"));
      await(this::hasSettingsWithoutDuplicateControls, "Key & Tempo belongs on Stage, not in Settings");
      runOnMainSync(() -> {
        View decor = activity.getWindow().getDecorView();
        for (String moved : new String[]{"backup", "app-update", "legal", "help"})
          check(decor.findViewWithTag("kanta.group." + moved) != null,
              "Settings does not host the Support row: " + moved);
      });
      runOnMainSync(this::openMenuVoiceScoring);
      await(
          () -> activity.getWindow().getDecorView().findViewWithTag("kanta.screen.mic-voice") != null,
          "Voice & Scoring did not open for the music volume check");
      runOnMainSync(() -> {
        View decor = activity.getWindow().getDecorView();
        SeekBar slider = decor.findViewWithTag("kanta.settings.music-volume");
        float initial = session.volume;
        Bundle progress = new Bundle();
        progress.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,
            initial > .4f ? 25f : 75f);
        check(slider.performAccessibilityAction(
            android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),
            progress), "Music volume slider did not accept user progress");
        float expected = slider.getProgress() / 100f;
        check(session.volume == expected
                && new ph.kanta.reborn.settings.SettingsStore(activity).getMasterVolume() == expected,
            "The Music volume slider did not persist its new value");
        session.publish();
        check(decor.findViewWithTag("kanta.settings.music-volume") == slider,
            "Session publication replaced the Music volume slider");
        session.setVolume(initial);
        session.publish();
      });
      runOnMainSync(() -> activity.getOnBackPressedDispatcher().onBackPressed());
      await(
          () -> activity.getWindow().getDecorView().findViewWithTag("kanta.group.settings") != null,
          "Music volume check did not return to the settings group");
      runOnMainSync(() -> {
        View decor = activity.getWindow().getDecorView();
        decor.findViewWithTag("kanta.nav.Home").performClick();
        decor.findViewWithTag("kanta.nav.More").performClick();
      });
      await(this::hasSettingsWithoutDuplicateControls, "Menu dock did not resume Settings");
      runOnMainSync(() -> openMenuGroup("stage-visual"));
      await(
          () -> activity.getWindow().getDecorView().findViewWithTag("kanta.screen.visuals") != null,
          "Menu did not open Stage & Visual");
      capture("visuals_screen.png", "kanta.screen.visuals");
      runOnMainSync(() -> {
        View decor = activity.getWindow().getDecorView();
        check(decor.findViewWithTag("kanta.visuals.shuffle.photos") != null
                && decor.findViewWithTag("kanta.visuals.shuffle.videos") == null,
            "Background photos did not have separate controls");
        check(decor.findViewWithTag("kanta.visuals.tab.lyrics") == null
                && decor.findViewWithTag("kanta.visuals.tab.interlude") == null,
            "Lyrics and Interlude belong on Intermission, not Stage & Visual");
        decor.findViewWithTag("kanta.visuals.tab.videos").performClick();
        check(decor.findViewWithTag("kanta.visuals.shuffle.videos") != null
                && decor.findViewWithTag("kanta.visuals.shuffle.photos") == null,
            "Background videos did not have separate controls");
        decor.findViewWithTag("kanta.visuals.tab.background").performClick();
      });
      android.graphics.Bitmap previousScene = session.scenic;
      runOnMainSync(() -> {
        View scene = activity.getWindow().getDecorView().findViewWithTag("kanta.visuals.background.stage-lights");
        check(scene != null, "Generated Stage Lights artwork thumbnail missing");
        scene.performClick();
      });
      await(
          () -> "stageLights".equals(session.scenicName) && session.scenic != null
              && session.scenic != previousScene,
          "Generated Stage Lights artwork did not reach the live SessionController stage");
      await(() -> {
        Object background = field(persistentPrimaryStage, "backgroundStage");
        Object photo = field(background, "photoView");
        if (!(photo instanceof ImageView)) return false;
        android.graphics.drawable.Drawable drawable = ((ImageView) photo).getDrawable();
        return drawable instanceof android.graphics.drawable.BitmapDrawable
            && ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap() == session.scenic;
      }, "Background selection did not update the visible primary stage bitmap in place");
      assertPersistentStage("kanta.screen.visuals");
      runOnMainSync(() -> activity.getOnBackPressedDispatcher().onBackPressed());
      await(() -> activity.getWindow().getDecorView().findViewWithTag("kanta.group.stage-visual") != null,
          "Stage & Visual did not return to Menu");
      assertPersistentStage("kanta.group.stage-visual");
      runOnMainSync(this::openMenuVoiceScoring);
      await(
          () -> activity.getWindow().getDecorView().findViewWithTag("kanta.screen.mic-voice") != null,
          "Menu did not open Voice & Scoring");
      check(state(this::hasSharedMicVoiceControls),
          "Voice & Scoring did not expose the shared controls");
      capture("mic_voice_screen.png", "kanta.screen.mic-voice");
      runOnMainSync(() -> {
        View room = activity.getWindow().getDecorView().findViewWithTag("kanta.monitor.mode.room");
        check(room != null, "Room monitor effect control missing");
        room.performClick();
      });
      await(() -> session.monitor.mode() == ph.kanta.reborn.core.VocalMonitorEffects.Mode.ROOM,
          "Mic & Voice effect selection did not reach the existing monitor processor");
      runOnMainSync(() -> {
        session.monitor.mode(ph.kanta.reborn.core.VocalMonitorEffects.Mode.DRY);
        session.publish();
      });
      runOnMainSync(() -> {
        View back = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.mic-voice.back");
        if (back != null) {
          checkIconBack(back, "Mic & Voice");
          back.performClick();
        }
        else {
          View top = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.mic-voice");
          check(top != null, "Mic & Voice destination disappeared unexpectedly");
          activity.getOnBackPressedDispatcher().onBackPressed();
        }
      });
      await(() -> visibleOnScreen("kanta.group.back"), "Voice & Scoring did not return to Menu");
      runOnMainSync(() -> activity.getWindow().getDecorView().findViewWithTag("kanta.nav.Home").performClick());
      runOnMainSync(() -> openStageControlsForCheck(activity.getWindow().getDecorView(), "kanta.stage.controls"));
      await(this::stageControlsBelowStage, "Embedded Stage Controls must sit below the Stage, never above it");
      runOnMainSync(() -> {
        View decor = activity.getWindow().getDecorView();
        verifyStageControlsContent(decor, "kanta.stage.controls");
        collapseStageControls(decor, "kanta.stage.controls");
      });
      runOnMainSync(() -> {
        View open = activity.getWindow().getDecorView().findViewWithTag("kanta.stage.fullscreen");
        check(open != null && open.performClick(), "Stage fullscreen button did not open");
      });
      Dialog fullscreenDialog = (Dialog) field(activity, "fullscreen");
      await(
          () -> fullscreenDialog != null && fullscreenDialog.isShowing()
              && fullscreenDialog.getWindow().getDecorView().findViewWithTag("kanta.fullscreen.stage") != null,
          "Fullscreen StageSurface did not open");
      capture("fullscreen_stage.png");
      runOnMainSync(() -> {
        // The shipped fullscreen Stage carries no Stage Controls panel (fullscreenControls is
        // never built): the fullscreen bar holds transport actions, and key/tempo stay on the
        // docked Stage's Stage Controls. Assert the panel is absent rather than missing it.
        check(fullscreenDialog.getWindow().getDecorView().findViewWithTag("kanta.fullscreen.controls") == null,
            "Fullscreen Stage must not expose a Stage Controls panel");
      });
      runOnMainSync(() -> {
        View decor = fullscreenDialog.getWindow().getDecorView();
        checkStageTextButton(decor.findViewWithTag("kanta.fullscreen.restart"), R.string.stage_restart);
        checkStageTextButton(decor.findViewWithTag("kanta.fullscreen.play-pause"), R.string.stage_play);
        checkStageTextButton(decor.findViewWithTag("kanta.fullscreen.next"), R.string.stage_next);
        checkStageTextButton(decor.findViewWithTag("kanta.fullscreen.close"), R.string.stage_controls_close);
        ViewGroup transport = (ViewGroup) decor.findViewWithTag("kanta.fullscreen.play-pause").getParent();
        int transportActions = 0;
        for (int i = 0; i < transport.getChildCount(); i++) {
          if (transport.getChildAt(i) instanceof Button) {
            transportActions++;
          }
        }
        check(transportActions == 3, "Fullscreen has redundant transport actions");
        check(decor.findViewWithTag("kanta.fullscreen.time") instanceof TextView
                && decor.findViewWithTag("kanta.fullscreen.progress") == null,
            "Fullscreen should show elapsed time without a progress bar");
        check(decor.findViewWithTag("kanta.fullscreen.key.increment") == null
                && decor.findViewWithTag("kanta.fullscreen.tempo.increment") == null,
            "Pitch and tempo controls must remain in Controls");
        TextView docked = activity.getWindow().getDecorView().findViewWithTag("kanta.stage.adjustments");
        TextView expanded = decor.findViewWithTag("kanta.fullscreen.adjustments");
        int pitch = session.effectivePitch();
        String expected = activity.getString(R.string.stage_adjustment_readout,
            pitch > 0 ? "+" + pitch : Integer.toString(pitch), session.effectiveTempo());
        check(docked != null && expanded != null && expanded.isShown()
                && expected.contentEquals(docked.getText()) && expected.contentEquals(expanded.getText()),
            "Docked and fullscreen Stages must show the effective key and tempo even at rest");
      });
      runOnMainSync(() -> {
        View close = fullscreenDialog.getWindow().getDecorView().findViewWithTag("kanta.fullscreen.close");
        check(close != null, "Fullscreen close control missing");
        close.performClick();
      });
      await(() -> !fullscreenDialog.isShowing() && field(activity, "fullscreenStage") == null,
          "Fullscreen stage did not dismiss cleanly");
      runOnMainSync(() -> {
        View open = activity.getWindow().getDecorView().findViewWithTag("kanta.stage.fullscreen");
        check(open != null && open.performClick(), "Fullscreen stage could not reopen");
        Dialog reopened = (Dialog) field(activity, "fullscreen");
        check(reopened != null && reopened.isShowing() && reopened != fullscreenDialog,
            "Fullscreen reused its dismissed surface");
        reopened.dismiss();
      });
      await(() -> field(activity, "fullscreenStage") == null, "Reopened fullscreen did not release its surface");
      await(() -> visibleOnScreen("kanta.stage.primary"), "Stage did not return after closing fullscreen");
      await(() -> visibleOnScreen("kanta.stage.transport") && visibleOnScreen("kanta.home.search"),
          "Playback transport and search must share the Stage");
      assertPersistentStage("kanta.stage.transport");
      capture("now_playing_screen.png", "kanta.stage.transport");
      runOnMainSync(() -> {
        View more = activity.getWindow().getDecorView().findViewWithTag("kanta.nav.More");
        check(more != null, "Home More destination missing after Now Playing");
        more.performClick();
      });

      check(
          state(
              () -> !session.scoringEnabled && session.score == null && session.lastScore == null),
          "Fresh session enabled scoring or restored a completion");
      check(
          state(
              () -> !session.monitor.correctionEnabled() && !session.monitor.correctionAvailable()),
          "Fresh session enabled pitch correction or exposed an unverified output");
      capture("fixture_loaded.png");
      check(
          state(() -> contains(activity.getWindow().getDecorView(), "KANTA")),
          "Native activity did not render");
      capture("ui_rendered.png");
      legacyQueueRestoreChecks();
      companionChecks();
      browserCompanionChecks();
      Song song = session.songs.get(0);
      LibraryRepository isolated =
          new LibraryRepository(getTargetContext().createDeviceProtectedStorageContext());
      isolated.load();
      java.util.List<android.net.Uri> files =
          DocumentTreeScanner.scan(
              getTargetContext().getContentResolver(),
              android.provider.DocumentsContract.buildTreeDocumentUri(
                  FixtureProvider.AUTHORITY, "root"),
              () -> false);
      check(files.size() == 4, "SAF nested traversal or cycle bound");
      boolean cancelled = false;
      try {
        isolated.importBatch(
            java.util.Arrays.asList(FixtureProvider.file("song"), FixtureProvider.file("bank")),
            () -> isolated.songs().size() > 0);
      } catch (java.io.InterruptedIOException expected) {
        cancelled = true;
      }
      check(cancelled && isolated.songs().isEmpty(), "Cancelled batch published staged songs");
      LibraryRepository afterCancel =
          new LibraryRepository(getTargetContext().createDeviceProtectedStorageContext());
      afterCancel.load();
      check(
          afterCancel.songs().isEmpty(),
          "Cancelled batch left imported metadata or missing media in durable catalog");
      LibraryRepository.ImportBatchResult importResult =
          isolated.importBatch(files, () -> false);
      check(
          importResult.imported == 3
              && importResult.failed == 1
              && isolated.songs().size() == 2,
          "SAF partial import or SoundFont selection");
      check(
          !isolated.soundFontName().equals("KANTA original tone"),
          "SoundFont override not retained");
      check(
          isolated.soundFonts().size() == 4
              && isolated.soundFonts().stream().anyMatch(value -> value.fileName.equals("kanta-tone.sf2"))
              && isolated.soundFonts().stream().anyMatch(value -> value.fileName.equals(isolated.soundFont().getName()))
              && isolated.soundFonts().stream().anyMatch(value -> value.fileName.equals("kanta_reborn_karaokelive_gm_v3.sf2"))
              && isolated.soundFonts().stream().anyMatch(value -> value.fileName.equals("kanta_reborn_livestage_gm_v2.sf2")),
          "Imported and bundled SoundFonts are not discoverable");
      String importedBank = isolated.soundFont().getName();
      isolated.selectSoundFont("kanta-tone.sf2");
      check(
          isolated.soundFont().getName().equals("kanta-tone.sf2")
              && isolated.soundFontName().equals("KANTA original tone"),
          "Bundled SoundFont could not be selected");
      isolated.selectSoundFont(importedBank);
      LibraryRepository reloadedBanks =
          new LibraryRepository(getTargetContext().createDeviceProtectedStorageContext());
      reloadedBanks.load();
      check(
          reloadedBanks.soundFont().getName().equals(importedBank)
              && reloadedBanks.soundFonts().size() == 4,
          "Active SoundFont selection or inventory did not survive reload");
      check(
          isolated.songs().stream().anyMatch(value -> !value.starter && value.fileName.endsWith(".mid")),
          "Imported MIDI/KAR was not added to the playable song catalog");
      try (InputStream original =
          getTargetContext().getContentResolver().openInputStream(FixtureProvider.file("song"))) {
        Song copy =
            isolated.songs().stream()
                .filter(value -> value.title.equals("Original KAR"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing fixture song"));
        check(
            java.util.Arrays.equals(
                readFixture(original),
                java.nio.file.Files.readAllBytes(new File(isolated.media, copy.fileName).toPath())),
            "Imported source bytes changed");
      }
      runOnMainSync(() -> session.importSongs(files));
      await(
          () -> !session.importing && session.songs.size() == 2 && session.soundFonts.size() == 4,
          "SessionController did not publish imported songs and SoundFonts");
      runOnMainSync(() -> openMenuGroup("intermission"));
      await(() -> activity.getWindow().getDecorView().findViewWithTag("kanta.legacy.intermission.back") != null,
          "Intermission did not expose its Back control");
      runOnMainSync(() -> checkIconBack(
          activity.getWindow().getDecorView().findViewWithTag("kanta.legacy.intermission.back"),
          "Intermission"));
      runOnMainSync(() -> {
        check(activity.getWindow().getDecorView().findViewWithTag("kanta.party-pad.trigger.0") == null,
            "Intermission still hosts Party Pads");
        check(activity.getWindow().getDecorView().findViewWithTag("kanta.screen.intermission-editors") != null,
            "Intermission does not host the lyrics and interlude editors");
        check(activity.getWindow().getDecorView().findViewWithTag("kanta.visuals.tab.lyrics") != null
                && activity.getWindow().getDecorView().findViewWithTag("kanta.visuals.tab.interlude") != null,
            "Intermission editor tabs missing");
        openMenuGroup("party-pads");
        checkPartyPadsPanel();
      });
      runOnMainSync(() -> openMenuGroup("my-songs"));
      await(
          () -> contains(activity.getWindow().getDecorView(), "2 imported songs")
              && contains(activity.getWindow().getDecorView(), "ACTIVE")
              && contains(activity.getWindow().getDecorView(), "Use"),
          "Presets did not show imported songs and SoundFont actions");
      runOnMainSync(() -> {
        View back = activity.getWindow().getDecorView().findViewWithTag("kanta.legacy.presets.back");
        checkIconBack(back, "Presets");
        check(!contains(activity.getWindow().getDecorView(), activity.getString(R.string.library_notices)),
            "Library notices belong in Menu, not Presets");
        android.widget.Button add = activity.getWindow().getDecorView().findViewWithTag("kanta.library.add-folder");
        check(add != null && add.isEnabled() && add.getCompoundDrawablesRelative()[0] != null,
            "Presets folder CTA is missing or disabled");
      });
      capture("my-set-imported-library.png");
      runOnMainSync(
          () -> check(
              clickText(activity.getWindow().getDecorView(), "Use"),
              "SoundFont Use action was not visible/clickable"));
      await(
          () -> session.soundFontName.equals("KANTA original tone"),
          "SoundFont selector did not activate the bundled bank");
      runOnMainSync(
          () -> check(
              clickText(activity.getWindow().getDecorView(), "Use"),
              "Imported SoundFont Use action was not visible/clickable"));
      await(
          () -> !session.soundFontName.equals("KANTA original tone"),
          "SoundFont selector did not reactivate the imported bank");
      capture("my-set-active-soundfont.png");
      runOnMainSync(() -> activity.getOnBackPressedDispatcher().onBackPressed());
      await(() -> activity.getWindow().getDecorView().findViewWithTag("kanta.group.settings") != null,
          "More group did not expose Settings");
      runOnMainSync(() -> activity.getWindow().getDecorView().findViewWithTag("kanta.group.settings").performClick());

      AtomicBoolean progressSeen = new AtomicBoolean();
      AtomicBoolean progressOnMain = new AtomicBoolean(true);
      AtomicBoolean importCompleted = new AtomicBoolean();
      AtomicBoolean completionOnMain = new AtomicBoolean();
      AtomicBoolean importSucceeded = new AtomicBoolean();
      runOnMainSync(
          () -> {
            session.setImportProgressListener(new LibraryRepository.ImportProgressListener() {
              @Override
              public void onProgress(long total, long copied, String status) {
                progressSeen.set(true);
                if (Looper.myLooper() != Looper.getMainLooper()) progressOnMain.set(false);
              }

              @Override
              public void onComplete(boolean success, String message) {
                importSucceeded.set(success);
                completionOnMain.set(Looper.myLooper() == Looper.getMainLooper());
                importCompleted.set(true);
              }
            });
            session.importSongs(
                java.util.Collections.singletonList(FixtureProvider.file("audio")));
          });
      await(
          () -> !session.importing && !session.libraryImporting
              && importCompleted.get() && session.songs.size() == 2,
          "Controller audio import or progress completion failed");
      check(
          progressSeen.get() && progressOnMain.get()
              && completionOnMain.get() && importSucceeded.get(),
          "Library import progress/completion callbacks did not run on the main Looper");
      check(
          field(field(session, "repository"), "progressListener") == null,
          "Completed import retained its Activity-scoped progress listener");
      AtomicBoolean folderCompleted = new AtomicBoolean();
      AtomicBoolean folderCompletionOnMain = new AtomicBoolean();
      AtomicBoolean folderSucceeded = new AtomicBoolean(true);
      runOnMainSync(
          () -> {
            session.setImportProgressListener(new LibraryRepository.ImportProgressListener() {
              @Override
              public void onProgress(long total, long copied, String status) {}

              @Override
              public void onComplete(boolean success, String message) {
                folderSucceeded.set(success);
                folderCompletionOnMain.set(Looper.myLooper() == Looper.getMainLooper());
                folderCompleted.set(true);
              }
            });
            session.importFolder(
                android.net.Uri.parse("content://missing.kanta.reborn/tree/root"));
          });
      await(
          () -> !session.importing && !session.libraryImporting && folderCompleted.get(),
          "Folder scan failure did not finish the import progress lifecycle");
      check(
          !folderSucceeded.get() && folderCompletionOnMain.get(),
          "Folder scan failure did not report completion on the main Looper");
      check(
          field(field(session, "repository"), "progressListener") == null,
          "Failed folder scan retained its Activity-scoped progress listener");
      Song loopSong =
          session.songs.stream()
              .filter(SessionController::isAudio)
              .findFirst()
              .orElseThrow(() -> new AssertionError("Missing fixture song"));
      StageMediaLibraryChecks.run(getTargetContext());
      StageMediaStore visualStore =
          new StageMediaStore(getTargetContext().createDeviceProtectedStorageContext());
      visualStore.load();
      boolean cancelledPhoto = false;
      try {
        visualStore.importMedia(FixtureProvider.file("photo"), false, () -> true);
      } catch (java.io.InterruptedIOException expected) {
        cancelledPhoto = true;
      }
      check(
          cancelledPhoto && visualStore.selection().kind.equals("scenic"),
          "Cancelled photo import changed selection");
      StageMediaStore.Selection photo =
          visualStore.importMedia(FixtureProvider.file("photo"), false, () -> false);
      StageMediaStore reloadedVisuals =
          new StageMediaStore(getTargetContext().createDeviceProtectedStorageContext());
      check(reloadedVisuals.load().kind.equals("photo"), "Visual selection did not persist");
      try (InputStream original =
          getTargetContext().getContentResolver().openInputStream(FixtureProvider.file("photo"))) {
        check(
            java.util.Arrays.equals(
                readFixture(original), java.nio.file.Files.readAllBytes(photo.photo.toPath())),
            "Photo source bytes changed");
      }
      android.graphics.Bitmap decoded = StageMediaStore.displayPhoto(photo.photo);
      check(decoded.getWidth() == 80 && decoded.getHeight() == 60, "Photo display decode failed");
      decoded.recycle();
      boolean rejectedPhoto = false;
      try {
        visualStore.importMedia(FixtureProvider.file("bad"), false, () -> false);
      } catch (IOException expected) {
        rejectedPhoto = true;
      }
      check(
          rejectedPhoto && visualStore.selection().photo.equals(photo.photo),
          "Invalid photo replaced the saved image");
      StageMediaStore.Selection savedVideo =
          visualStore.importMedia(FixtureProvider.file("video"), true, () -> false);
      try (InputStream original =
          getTargetContext().getContentResolver().openInputStream(FixtureProvider.file("video"))) {
        check(
            java.util.Arrays.equals(
                readFixture(original), java.nio.file.Files.readAllBytes(savedVideo.video.toPath())),
            "Video source bytes changed");
      }
      check(
          new StageMediaStore(getTargetContext().createDeviceProtectedStorageContext())
              .load()
              .kind
              .equals("video"),
          "Video selection did not persist");
      boolean rejectedVideo = false;
      try {
        visualStore.importMedia(FixtureProvider.file("bad"), true, () -> false);
      } catch (IOException expected) {
        rejectedVideo = true;
      }
      check(
          rejectedVideo && visualStore.selection().video.equals(savedVideo.video),
          "Invalid video replaced the saved clip");
      check(
          visualStore.select("scenic").kind.equals("scenic")
              && visualStore.select("photo").photo.equals(photo.photo),
          "Switching visual kinds discarded saved media");
      await(() -> session.stageReady, "Visual storage did not become ready");
      capture("stage_ready.png");
      runOnMainSync(() -> session.importStage(FixtureProvider.file("photo"), false));
      await(
          () ->
              !session.importing
                  && session.stageKind.equals("photo")
                  && session.scenic != null
                  && session.scenic.getWidth() == 80,
          "Controller photo import failed");
      AtomicReference<View> stableStage = new AtomicReference<>();
      AtomicReference<StageSurface> cameraStageRef = new AtomicReference<>();
      runOnMainSync(
          () ->
              stableStage.set(
                  activity.getWindow().getDecorView().findViewWithTag("kanta.stage.primary")));
      runOnMainSync(session::publish);
      check(
          state(
              () ->
                  stableStage.get()
                      == activity
                          .getWindow()
                          .getDecorView()
                          .findViewWithTag("kanta.stage.primary")),
          "Controller update detached the stage subtree");
      runOnMainSync(() -> activity.getWindow().getDecorView().findViewWithTag("kanta.nav.Home").performClick());
      await(() -> visibleOnScreen("kanta.stage.primary"), "Stage layout never became visible");
      workspaceBackNavigationChecks();
      check(
          state(
              () -> {
                for (String name : new String[] {"Home", "More"}) {
                  View tab =
                      activity.getWindow().getDecorView().findViewWithTag("kanta.nav." + name);
                  android.graphics.Rect visible = new android.graphics.Rect();
                  if (tab == null
                      || !tab.getGlobalVisibleRect(visible)
                      || visible.width() != tab.getWidth()
                      || visible.height() != tab.getHeight()) return false;
                }
                return true;
              }),
          "Navigation tab is clipped on the phone display");
      runOnMainSync(() -> session.importStage(FixtureProvider.file("video"), true));
      await(
          () -> !session.importing && session.stageKind.equals("video"),
          "Controller video import failed");
      // The primary UI is StagePresenterImpl; StageSurface is the dedicated fullscreen renderer.
      View primaryStage = stableStage.get();
      runOnMainSync(activity::fullscreen);
      await(
          () -> field(activity, "fullscreenStage") instanceof StageSurface,
          "Fullscreen stage surface was not created");
      stableStage.set((View) field(activity, "fullscreenStage"));
      await(
          () -> ((StageSurface) stableStage.get()).isVideoVisible(),
          "Muted video did not render under lyrics");
      runOnMainSync(() -> ((StageSurface) stableStage.get()).setVisualActive(false));
      check(
          state(() -> !((StageSurface) stableStage.get()).isVideoVisible()),
          "Inactive stage retained video rendering");
      runOnMainSync(() -> ((StageSurface) stableStage.get()).setVisualActive(true));
      await(
          () -> ((StageSurface) stableStage.get()).isVideoVisible(),
          "Video did not resume after reactivation");
      runOnMainSync(activity::closeFullscreen);
      stableStage.set(primaryStage);
      await(() -> visibleOnScreen("kanta.stage.primary") && visibleOnScreen("kanta.stage.transport"),
          "Fullscreen close did not restore the primary Stage and its transport");
      runOnMainSync(() -> session.scene("bayanDawn"));
      check(state(() -> session.stageVideo == null), "Scenic selection retained video playback");
      check(
          state(() -> !session.cameraPreviewEnabled && !activity.cameraStreaming()),
          "Camera was enabled without opt-in");
      check(
          getTargetContext().checkSelfPermission(android.Manifest.permission.CAMERA)
              != android.content.pm.PackageManager.PERMISSION_GRANTED,
          "Fresh installation unexpectedly had camera permission");
      shell("pm grant ph.kanta.reborn android.permission.CAMERA");
      runOnMainSync(activity::toggleCamera);
      await(
          () ->
              activity.cameraStreaming()
                  && ((ph.kanta.reborn.stage.StagePresenterImpl) primaryStage).isCameraVisible(),
          "Opt-in front camera never rendered on the primary stage");

      // Exercise the first in-place configuration change while the primary camera is active.
      // A fresh Activity has not received a prior onConfigurationChanged callback yet.
      AtomicReference<Integer> originalOrientationRef = new AtomicReference<>();
      runOnMainSync(
          () -> originalOrientationRef.set(
              activity.getResources().getConfiguration().orientation));
      int originalOrientation = originalOrientationRef.get();
      int alternateOrientation =
          originalOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
              ? android.content.res.Configuration.ORIENTATION_PORTRAIT
              : android.content.res.Configuration.ORIENTATION_LANDSCAPE;
      runOnMainSync(
          () -> {
            android.content.res.Configuration changed =
                new android.content.res.Configuration(activity.getResources().getConfiguration());
            changed.orientation = alternateOrientation;
            activity.onConfigurationChanged(changed);
          });
      View rotatedPrimaryStage = (View) field(activity, "stagePresenter");
      check(
          rotatedPrimaryStage != primaryStage,
          "First orientation change did not rebuild the primary stage");
      AtomicReference<Integer> rotatedMainOrientationRef = new AtomicReference<>();
      runOnMainSync(
          () ->
              rotatedMainOrientationRef.set(
                  ((android.widget.LinearLayout)
                          ((android.widget.LinearLayout) field(activity, "root")).getChildAt(1))
                      .getOrientation()));
      int rotatedMainOrientation = rotatedMainOrientationRef.get();
      check(
          rotatedMainOrientation
              == (alternateOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                  ? android.widget.LinearLayout.HORIZONTAL
                  : android.widget.LinearLayout.VERTICAL),
          "First orientation change did not use the incoming layout orientation");
      await(
          () ->
              activity.cameraStreaming()
                  && ((ph.kanta.reborn.stage.StagePresenterImpl) rotatedPrimaryStage)
                      .isCameraVisible(),
          "Primary camera preview was not rebound after orientation change");
      check(
          !((ph.kanta.reborn.stage.StagePresenterImpl) primaryStage).isCameraVisible(),
          "Detached primary stage retained the camera preview");

      runOnMainSync(
          () -> {
            android.content.res.Configuration restored =
                new android.content.res.Configuration(activity.getResources().getConfiguration());
            restored.orientation = originalOrientation;
            activity.onConfigurationChanged(restored);
          });
      View primaryStageAfterRotation = (View) field(activity, "stagePresenter");
      check(
          primaryStageAfterRotation != rotatedPrimaryStage,
          "Restoring the original orientation did not rebuild the primary stage");
      AtomicReference<Integer> restoredMainOrientationRef = new AtomicReference<>();
      runOnMainSync(
          () ->
              restoredMainOrientationRef.set(
                  ((android.widget.LinearLayout)
                          ((android.widget.LinearLayout) field(activity, "root")).getChildAt(1))
                      .getOrientation()));
      int restoredMainOrientation = restoredMainOrientationRef.get();
      check(
          restoredMainOrientation
              == (originalOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                  ? android.widget.LinearLayout.HORIZONTAL
                  : android.widget.LinearLayout.VERTICAL),
          "Restored orientation did not restore the corresponding layout");
      await(
          () ->
              activity.cameraStreaming()
                  && ((ph.kanta.reborn.stage.StagePresenterImpl) primaryStageAfterRotation)
                      .isCameraVisible(),
          "Camera preview did not return to the rebuilt primary stage");
      check(
          !((ph.kanta.reborn.stage.StagePresenterImpl) rotatedPrimaryStage).isCameraVisible(),
          "Superseded primary stage retained the camera preview");
      stableStage.set(primaryStageAfterRotation);

      // A non-primary StageSurface (fullscreen or external display) remains the camera
      // owner when the primary Activity changes orientation.
      StageSurface nonPrimaryStage = new StageSurface(activity, session);
      runOnMainSync(() -> cameraStage().moveTo(nonPrimaryStage));
      check(state(() -> cameraStage().ownsSurface(nonPrimaryStage)),
          "Camera did not attach to the non-primary stage fixture");
      runOnMainSync(() -> {
        android.content.res.Configuration changed =
            new android.content.res.Configuration(activity.getResources().getConfiguration());
        changed.orientation = alternateOrientation;
        activity.onConfigurationChanged(changed);
      });
      check(state(() -> cameraStage().ownsSurface(nonPrimaryStage)),
          "Primary rotation stole the camera preview from a non-primary stage");
      runOnMainSync(() -> {
        android.content.res.Configuration restored =
            new android.content.res.Configuration(activity.getResources().getConfiguration());
        restored.orientation = originalOrientation;
        activity.onConfigurationChanged(restored);
      });
      check(state(() -> cameraStage().ownsSurface(nonPrimaryStage)),
          "Restoring primary orientation stole the non-primary camera preview");
      AtomicReference<ph.kanta.reborn.stage.StagePresenterImpl> currentPrimary =
          new AtomicReference<>();
      runOnMainSync(() -> {
        currentPrimary.set((ph.kanta.reborn.stage.StagePresenterImpl) field(activity, "stagePresenter"));
        cameraStage().moveTo(currentPrimary.get());
      });
      await(() -> activity.cameraStreaming()
              && cameraStage().ownsSurface(currentPrimary.get())
              && currentPrimary.get().isCameraVisible(),
          "Camera did not return to the current primary stage after non-primary ownership");
      check(!nonPrimaryStage.isCameraVisible(),
          "Non-primary stage retained the camera preview after ownership moved away");
      nonPrimaryStage.setVisualActive(false);
      stableStage.set(currentPrimary.get());

      runOnMainSync(activity::fullscreen);
      await(
          () -> field(activity, "fullscreenStage") instanceof StageSurface,
          "Fullscreen camera stage was not created");
      cameraStageRef.set((StageSurface) field(activity, "fullscreenStage"));
      stableStage.set(cameraStageRef.get());
      await(
          () -> activity.cameraStreaming() && cameraStageRef.get().isCameraVisible(),
          "Front camera did not render on the fullscreen stage");
      runOnMainSync(activity::closeFullscreen);
      stableStage.set(currentPrimary.get());
      // The synthetic orientation round-trips above rebuilt the primary stage and returned
      // lastOrientation to its launch value, so assertPersistentStage's orientation-change
      // refresh will not fire. Re-baseline the persistent stage to the current primary now;
      // navigation checks from here on must compare against this rebuilt view.
      runOnMainSync(() -> {
        persistentPrimaryStage = currentPrimary.get();
        persistentStageOrientation = (Integer) field(activity, "lastOrientation");
      });
      await(
          () ->
              activity.cameraStreaming()
                  && ((ph.kanta.reborn.stage.StagePresenterImpl) currentPrimary.get())
                      .isCameraVisible(),
          "Front camera did not return to the primary stage");
      await(
          () -> !cameraStageRef.get().isCameraVisible(),
          "Fullscreen stage retained its camera preview after ownership moved away");
      runOnMainSync(activity::toggleCamera);
      await(
          () ->
              !session.cameraPreviewEnabled
                  && !activity.cameraStreaming()
                  && !((ph.kanta.reborn.stage.StagePresenterImpl) currentPrimary.get())
                      .isCameraVisible(),
          "Disabling the camera did not release the primary preview");
      runOnMainSync(activity::toggleCamera);
      await(
          () ->
              activity.cameraStreaming()
                  && ((ph.kanta.reborn.stage.StagePresenterImpl) currentPrimary.get())
                      .isCameraVisible(),
          "Camera did not reopen on the current primary stage after explicit opt-in");
      capture("camera_optin.png");
      shell("input keyevent KEYCODE_HOME");
      await(() -> !session.foreground, "Home did not background KANTA");
      check(
          state(() -> !session.cameraPreviewEnabled && !activity.cameraStreaming()),
          "Backgrounding KANTA retained camera preview");
      shell("am start -n ph.kanta.reborn/.MainActivity --activity-reorder-to-front");
      await(() -> session.foreground, "KANTA did not return to the foreground");
      check(
          state(() -> !session.cameraPreviewEnabled && !activity.cameraStreaming()),
          "Foregrounding KANTA re-enabled camera without opt-in");

      runOnMainSync(() -> openMenuGroup("settings"));
      check(state(this::hasSettingsWithoutDuplicateControls),
          "Settings still embeds voice, key or tempo controls");
      runOnMainSync(this::openMenuVoiceScoring);
      await(this::hasSharedMicVoiceControls, "Menu did not open Voice & Scoring");
      capture("voice_settings.png", "kanta.screen.mic-voice");
      runOnMainSync(() -> {
        View decor = activity.getWindow().getDecorView();
        View correction = decor.findViewWithTag("kanta.pitchcorrection.toggle");
        check(!correction.isEnabled() && contains(correction, "Turn on pitch correction"),
            "Correction was enabled before verified monitoring");
        View lastScore = decor.findViewWithTag("kanta.score.last");
        check(lastScore.getVisibility() == View.GONE, "A new session displayed a fake last score");
        View back = decor.findViewWithTag("kanta.screen.mic-voice.back");
        checkIconBack(back, "Mic & Voice");
        check(back.performClick(), "Mic & Voice back button did not respond");
      });
      await(() -> visibleOnScreen("kanta.group.back"), "Voice & Scoring header Back did not return to Menu");
      runOnMainSync(this::openMenuVoiceScoring);
      await(this::hasSharedMicVoiceControls, "Voice & Scoring did not reopen from Menu");
      check(
          state(
              () ->
                  !session.microphone.enabled()
                      && !session.microphone.capturing()
                      && session.microphone.reading() == null),
          "Microphone enabled without opt-in");
      check(
          getTargetContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
              != android.content.pm.PackageManager.PERMISSION_GRANTED,
          "Fresh install unexpectedly had microphone permission");
      runOnMainSync(session::startMicrophone);
      check(state(() -> !session.microphone.enabled()), "Capture bypassed microphone permission");
      check(
          state(
              () ->
                  !session.monitor.enabled()
                      && !session.monitor.audible()
                      && session.monitor.resourcesReleased()),
          "Monitoring enabled without explicit opt-in");
      check(
          state(
              () ->
                  !session.monitor.correction(true)
                      && !session.monitor.enabled()
                      && !session.microphone.enabled()),
          "Correction selection activated output or microphone without monitoring");
      android.media.AudioManager audio =
          (android.media.AudioManager) getTargetContext().getSystemService(Context.AUDIO_SERVICE);
      android.media.AudioDeviceInfo speaker = null;
      for (android.media.AudioDeviceInfo device :
          audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS))
        if (device.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
          speaker = device;
      check(
          speaker != null && !MicrophoneMonitor.supported(speaker),
          "Built-in speaker was an accepted monitor route");
      final android.media.AudioDeviceInfo rejectedSpeaker = speaker;
      check(
          state(() -> session.monitor.outputs().isEmpty()),
          "Clean emulator unexpectedly exposes wired/USB hardware");
      runOnMainSync(() -> clickMicVoiceControl("kanta.monitor.toggle"));
      check(state(() -> !session.microphone.enabled() && !session.monitor.enabled()
              && contains(activity.getWindow().getDecorView().findViewWithTag("kanta.monitor.status"),
                  "Connect wired or USB headphones")),
          "Choose output with mic off did not show headset help without starting capture");
      shell("pm grant ph.kanta.reborn android.permission.RECORD_AUDIO");
      runOnMainSync(() -> clickMicVoiceControl("kanta.mic.toggle"));
      await(
          () -> session.microphone.capturing() && session.microphone.reading() != null,
          "Opt-in AudioRecord did not produce input frames: " + session.message);
      await(() -> {
        View decor = activity.getWindow().getDecorView();
        return contains(decor.findViewWithTag("kanta.mic.status"), "Microphone input active")
            && decor.findViewWithTag("kanta.monitor.toggle").isEnabled();
      }, "The shared microphone screen did not follow live capture");
      runOnMainSync(() -> session.startMonitor(rejectedSpeaker));
      check(
          state(
              () ->
                  !session.monitor.enabled()
                      && !session.monitor.audible()
                      && session.microphone.capturing()),
          "Speaker rejection stopped input or produced monitor output");
      check(
          state(
              () ->
                  !session.monitor.correction(true)
                      && !session.monitor.correctionEnabled()
                      && session.microphone.capturing()),
          "Correction bypassed route rejection or stopped dry capture");
      runOnMainSync(() -> clickMicVoiceControl("kanta.monitor.toggle"));
      check(
          state(() -> !session.monitor.enabled() && session.message.startsWith("Connect wired")),
          "Monitoring with no supported hardware did not explain rejection");
      runOnMainSync(
          () -> {
            session.monitor.mode(VocalMonitorEffects.Mode.HALL);
            session.monitor.gain(.9f);
          });
      check(
          state(() -> session.monitor.gain() == .35f && !session.monitor.enabled()),
          "Effect/volume setting enabled output or bypassed gain bound");
      runOnMainSync(() -> session.monitor.gain(Float.NaN));
      check(state(() -> session.monitor.gain() == .35f), "Nonfinite monitor gain accepted");
      runOnMainSync(() -> session.monitor.gain(-1));
      check(
          state(() -> session.monitor.gain() == 0 && !session.monitor.audible()),
          "Negative monitor gain accepted");
      check(
          state(() -> session.microphone.reading().pitch == null),
          "Silent emulator produced a vocal pitch");
      runOnMainSync(() -> clickMicVoiceControl("kanta.mic.toggle"));
      await(() -> session.microphone.resourcesReleased(), "AudioRecord did not release after stop");
      check(
          state(() -> session.monitor.resourcesReleased() && !session.monitor.audible()),
          "Stopping microphone retained monitor output");
      check(
          state(
              () -> !session.monitor.correctionEnabled() && !session.monitor.correctionAvailable()),
          "Stopping input retained correction activation");
      check(
          state(() -> !session.microphone.enabled() && session.microphone.reading() == null),
          "Explicit microphone stop retained measurements");
      runOnMainSync(activity::toggleMicrophone);
      await(
          () -> session.microphone.capturing(), "Microphone did not reopen after explicit opt-in");
      shell("input keyevent KEYCODE_HOME");
      await(() -> !session.foreground, "Home did not background microphone host");
      await(
          () -> session.microphone.resourcesReleased(),
          "Background retained AudioRecord resources");
      check(
          state(
              () ->
                  !session.microphone.enabled()
                      && !session.microphone.capturing()
                      && session.microphone.reading() == null),
          "Background retained capture or dry readings");
      shell("am start -n ph.kanta.reborn/.MainActivity --activity-reorder-to-front");
      await(() -> session.foreground, "Microphone host did not foreground");
      check(
          state(() -> !session.microphone.enabled()), "Returning automatically enabled microphone");
      check(
          state(() -> !session.monitor.enabled() && session.monitor.resourcesReleased()),
          "Foregrounding restored monitoring without opt-in");
      check(
          state(() -> !session.monitor.correctionEnabled() && !session.monitor.correction(true)),
          "Returning restored pitch correction without verified monitoring");
      runOnMainSync(
          () -> {
            session.hostVisible(false);
            session.startMicrophone();
            activity.onRequestPermissionsResult(
                17, new String[] {android.Manifest.permission.RECORD_AUDIO}, new int[] {0});
          });
      check(state(() -> !session.microphone.enabled()), "Late grant/background bypassed opt-in");
      runOnMainSync(() -> session.hostVisible(true));
      runOnMainSync(
          () -> {
            activity.toggleMicrophone();
            activity.toggleMicrophone();
            activity.toggleMicrophone();
          });
      await(() -> session.microphone.capturing(), "Rapid microphone replacement failed");
      runOnMainSync(activity::toggleMicrophone);
      check(
          state(() -> session.queue.snapshot().isEmpty() && session.current == null),
          "Microphone changed karaoke ownership");
      runOnMainSync(() -> activity.getOnBackPressedDispatcher().onBackPressed());
      await(() -> visibleOnScreen("kanta.group.back"), "Android Back did not return to Menu");

      runOnMainSync(
          () -> {
            session.reserve(song, "First");
            session.reserve(song, "Second");
          });
      check(
          state(
              () ->
                  session.queue.snapshot().size() == 2
                      && !session
                          .queue
                          .snapshot()
                          .get(0)
                          .id
                          .equals(session.queue.snapshot().get(1).id)),
          "Distinct reservations");
      runOnMainSync(
          () -> {
            session.next();
            session.pause();
          });
      SystemClock.sleep(500);
      check(
          state(() -> !session.playing && !session.loading && session.queue.snapshot().size() == 2),
          "Cancelled preparation consumed a reservation");
      await(
          () -> {
            for (int slot = 0; slot < 8; slot++) if (!session.party.ready(slot)) return false;
            return true;
          },
          "Generated party pads did not load");
      runOnMainSync(
          () -> {
            for (int slot = 0; slot < 8; slot++) session.triggerPad(slot);
          });
      check(
          state(() -> session.party.activeVoices() <= 4 && session.queue.snapshot().size() == 2),
          "Party audio changed karaoke reservations or exceeded voice bound");
      runOnMainSync(session::stopParty);
      check(
          state(() -> session.party.activeVoices() == 0), "Party stop-all retained active voices");
      runOnMainSync(() -> {
        View decor = activity.getWindow().getDecorView();
        decor.findViewWithTag("kanta.nav.Home").performClick();
        android.widget.EditText entry = decor.findViewWithTag("kanta.home.search");
        entry.setSelection(0, entry.length());
        check(decor.findViewWithTag("kanta.keyboard.delete").performClick()
            && entry.length() == 0, "Inline Delete did not clear the selected query");
        check(decor.findViewWithTag("kanta.stage.song-action.play") == null,
            "Stage still has a redundant standalone Play");
        check(decor.findViewWithTag("kanta.stage.song-action.pause-resume").performClick(),
            "Stage Play/Pause did not accept a tap with empty search");
      });
      await(
          () -> session.playing && session.current != null,
          "Stage PLAY did not start the queued MIDI song: " + session.message);
      check(
          state(() -> session.queue.snapshot().size() == 1 && session.singer.equals("First")),
          "Start acknowledgement violated FIFO");
      await(() -> session.position() >= 300, "AudioTrack audible clock did not advance");
      runOnMainSync(() -> {
        View toggle = activity.getWindow().getDecorView().findViewWithTag("kanta.stage.song-action.pause-resume");
        checkStageTextButton(toggle, R.string.stage_pause);
        check(toggle.isEnabled() && activity.getString(R.string.stage_pause_playback)
            .contentEquals(toggle.getContentDescription()), "Playing Stage lacks its Pause action");
        Song playing = session.current;
        java.util.UUID next = session.queue.peek().id;
        check(toggle.performClick() && !session.playing && session.current == playing
            && next.equals(session.queue.peek().id), "Stage Pause changed the song or queue");
        checkStageTextButton(toggle, R.string.stage_play);
        check(activity.getString(R.string.stage_resume_playback).contentEquals(toggle.getContentDescription())
            && toggle.performClick(), "Paused Stage lacks its Resume action");
      });
      await(() -> session.playing, "Stage Resume did not resume playback");
      persistentStagePlaybackChecks();
      runOnMainSync(() -> {
        openMenuVoiceScoring();
        clickMicVoiceControl("kanta.score.toggle");
      });
      await(session::scoreReady, "Scoring target did not prepare alongside real MIDI playback");
      check(
          state(() -> !session.microphone.enabled() && !session.monitor.enabled()),
          "Scoring opt-in implicitly enabled microphone or monitored output");
      AtomicReference<MelodyScoreSession> priorScore = new AtomicReference<>();
      runOnMainSync(() -> priorScore.set(session.score));
      runOnMainSync(() -> {
        SeekBar timing = activity.getWindow().getDecorView().findViewWithTag("kanta.score.timing");
        check(timing != null, "Score timing slider missing from Mic & Voice");
        timing.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        check(session.scoreLatency == 10, "Score timing slider did not update calibration");
      });
      await(
          () -> session.scoreReady() && session.score != priorScore.get(),
          "Latency change retained the old scoring accumulator");
      check(
          state(() -> session.score.estimate().matchedSlots == 0),
          "Latency change retained observations");
      runOnMainSync(() -> session.scoreLatency(Integer.MAX_VALUE));
      check(state(() -> session.scoreLatency == 1500
              && contains(activity.getWindow().getDecorView(), "Score timing offset: 1500 ms")),
          "Positive calibration bound did not reach the shared screen");
      runOnMainSync(() -> session.scoreLatency(Integer.MIN_VALUE));
      check(state(() -> session.scoreLatency == -500), "Negative calibration bound failed");
      runOnMainSync(() -> session.scoreLatency(0));
      await(session::scoreReady, "Latest calibration request did not retire stale preparation");

      runOnMainSync(activity::toggleCamera);
      await(activity::cameraStreaming, "Camera did not coexist with native MIDI playback");
      long cameraPosition = position();
      await(
          () -> session.playing && session.position() > cameraPosition + 200,
          "Camera preview interrupted the audible karaoke clock");
      runOnMainSync(activity::toggleMicrophone);
      await(() -> session.microphone.capturing(), "Camera/MIDI/microphone concurrency failed");
      long microphonePosition = position();
      await(
          () -> session.playing && session.position() > microphonePosition + 200,
          "Microphone capture interrupted karaoke time");
      runOnMainSync(activity::toggleMicrophone);
      capture("runtime-camera.png");
      runOnMainSync(activity::toggleCamera);

      runOnMainSync(() -> priorScore.set(session.score));
      runOnMainSync(() -> session.controls(.7f, 1.5f, 3));
      await(
          () -> session.scoreReady() && session.score != priorScore.get(),
          "Key/tempo scoring reset or audible control acknowledgement failed");
      runOnMainSync(() -> priorScore.set(session.score));
      runOnMainSync(() -> session.setVolume(.6f));
      check(state(() -> session.score == priorScore.get()), "Volume-only change cleared scoring");
      runOnMainSync(() -> session.setVolume(.7f));
      long before = position();
      await(() -> session.position() > before + 300, "Controlled playback stopped advancing");
      Bitmap capture = getUiAutomation().takeScreenshot();
      if (capture != null) {
        try (FileOutputStream out =
            new FileOutputStream(new File(getTargetContext().getFilesDir(), "runtime-stage.png"))) {
          capture.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        capture.recycle();
      }
      runOnMainSync(session::pause);
      SystemClock.sleep(150);
      long paused = position();
      SystemClock.sleep(250);
      check(position() - paused < 50, "Paused audible clock advanced");
      runOnMainSync(session::toggle);
      await(() -> session.playing && session.position() > paused + 100, "Resume failed");
      runOnMainSync(session::stop);
      check(
          state(
              () ->
                  session.lastScore == null
                      && session.score == null
                      && session.playbackIdentity == null),
          "Manual stop fabricated a completion score");
      runOnMainSync(() -> session.remove(session.queue.peek().id));
      runOnMainSync(
          () -> {
            session.controls(.7f, 2, 0);
            session.startMicrophone();
            session.reserve(song, "Score A");
            session.reserve(song, "Score B");
            session.next();
          });
      await(
          () ->
              session.playing
                  && session.singer.equals("Score A")
                  && session.scoreReady()
                  && session.microphone.capturing(),
          "First natural-completion scoring playback did not start");
      AtomicReference<java.util.UUID> scoreA = new AtomicReference<>(),
          scoreB = new AtomicReference<>();
      runOnMainSync(() -> scoreA.set(session.playbackIdentity));
      await(
          () ->
              session.lastScore != null && session.lastScore.playbackIdentity.equals(scoreA.get()),
          "First natural ending did not publish its completion identity");
      check(
          state(
              () ->
                  !session.lastScore.estimate.reportable
                      && session.lastScore.estimate.matchedSlots == 0),
          "Silent dry input produced a reportable song score");
      await(
          () -> session.playing && session.singer.equals("Score B") && session.scoreReady(),
          "Scoring completion did not advance the existing FIFO");
      runOnMainSync(() -> scoreB.set(session.playbackIdentity));
      check(!scoreA.get().equals(scoreB.get()), "Repeated song reused scoring playback identity");
      await(
          () ->
              session.lastScore != null
                  && session.lastScore.playbackIdentity.equals(scoreB.get())
                  && session.current == null
                  && !session.playing,
          "Second natural completion did not retain its own final result");
      check(
          state(() -> !session.lastScore.estimate.reportable && session.queue.snapshot().isEmpty()),
          "Insufficient second input became a score or changed FIFO ownership");
      check(state(() -> {
        View screen = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.mic-voice");
        View score = screen == null ? null : screen.findViewWithTag("kanta.score.last");
        return score != null && score.getVisibility() == View.VISIBLE
            && contains(score, "Score B") && contains(score, "Not enough stable voice input for a score");
      }), "Shared Mic & Voice did not show the actual last-song result for silence");
      runOnMainSync(
          () -> {
            session.stopMicrophone();
            session.setScoring(false);
            session.chooseIntermission(loopSong);
            if (session.loopEnabled) session.toggleIntermission();
            session.toggleIntermission();
          });
      await(() -> session.party.loopPlaying(), "Idle intermission did not start");
      runOnMainSync(() -> session.assignPad(6, loopSong));
      await(() -> !session.assigningPad, "Custom pad decode never acknowledged");
      check(
          state(() -> session.customPads[6].equals(loopSong.id)),
          "Custom pad preference did not commit");
      runOnMainSync(() -> session.resetPad(6));
      check(state(() -> session.customPads[6].isEmpty()), "Custom pad reset failed");
      int applause = ph.kanta.reborn.core.PartySounds.APPLAUSE;
      runOnMainSync(() -> {
        session.stopParty();
        session.setScoring(false);
        session.assignPad(applause, loopSong);
      });
      await(() -> !session.assigningPad && session.party.customReady(applause),
          "Assigned applause did not decode");
      runOnMainSync(() -> session.playSearchResult(loopSong, "Applause test"));
      await(() -> session.current == null && !session.loading && session.party.activeVoices() == 1,
          "Natural unscored completion did not trigger the assigned applause exactly once");
      check(state(() -> session.queue.peek() == null
              && session.lastScore.playbackIdentity.equals(scoreB.get())),
          "Applause changed queue ownership or the previous score");
      runOnMainSync(() -> {
        session.stopParty();
        session.resetPad(applause);
      });
      // Missing private data must leave the reservation recoverable.
      Song missing =
          new Song("missing-runtime-fixture", "Missing fixture", "", "999", "missing.mid", false);
      runOnMainSync(
          () -> {
            // Reserve directly in the authoritative queue: SessionController.reserve correctly
            // rejects songs outside the imported catalog, while this check targets recovery of
            // an already-persisted reservation whose media file is now missing.
            session.queue.reserve(java.util.UUID.randomUUID(), missing, "Retry");
            session.next();
          });
      await(() -> !session.loading, "Failed media preparation never completed");
      check(
          state(() -> session.lastScore.playbackIdentity.equals(scoreB.get())),
          "Failed preparation overwrote a completed score");
      check(!state(() -> session.party.loopPlaying()), "Intermission overlapped a reservation");
      check(
          state(
              () ->
                  !session.playing
                      && session.queue.peek() != null
                      && session.queue.peek().song.id.equals(missing.id)),
          "Failed preparation consumed reservation");
      runOnMainSync(
          () -> {
            session.remove(session.queue.peek().id);
            session.stopParty();
          });
      capture("final_ui.png");
      activity.finish();
      result.putString(
          "stream",
          "KANTA runtime: "
              + checks
              + " checks passed (native MIDI, FIFO, cancellation, controls, pause/resume, failed"
              + " prepare, UI)\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable failure) {
      result.putString("stream", android.util.Log.getStackTraceString(failure));
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  /**
   * Proves the empty stage is a karaoke standby surface running live background video, not a
   * static wallpaper: the 00000 code prompt is on screen, the presence overlay is the visible
   * child above the video, and its frame counter keeps advancing while the BGV renders.
   */
  private void stageStandbyChecks() {
    runOnMainSync(() -> {
      View stage = persistentPrimaryStage;
      check(stage != null, "Standby check needs the persistent stage");
      View presence = stage.findViewWithTag("kanta.stage.presence");
      check(presence != null, "Standby presence overlay missing from the karaoke stage");
      check(presence.getVisibility() == View.VISIBLE,
          "Standby presence was not shown while nothing is playing");
      View code = stage.findViewWithTag("kanta.stage.presence.code");
      check(code instanceof android.widget.TextView, "Standby code prompt is not readable text");
      check("00000".equals(((android.widget.TextView) code).getText().toString()),
          "Standby stage does not show the 00000 karaoke code prompt: "
              + (((android.widget.TextView) code).getText()));
    });
    try {
      StageMediaStore.Selection restored = new StageMediaStore(activity).load();
      check("video".equals(restored.kind) && restored.video != null && restored.video.isFile(),
          "Bundled standby video did not survive media-store reload");
    } catch (Exception failure) {
      throw new AssertionError("Could not reload standby media selection", failure);
    }
    // The standby layer must sit above the background renderer and below functional overlays.
    runOnMainSync(() -> {
      View stage = persistentPrimaryStage;
      Object presenterBackground = field(stage, "backgroundStage");
      View background = (View) presenterBackground;
      View presence = stage.findViewWithTag("kanta.stage.presence");
      check(stage instanceof android.view.ViewGroup, "Persistent stage is not a view container");
      android.view.ViewGroup group = (android.view.ViewGroup) stage;
      // Above the video layer, but not strictly adjacent: the camera preview is inserted at
      // index 1 when it exists, which is a legitimate sibling underneath the standby layer.
      check(group.indexOfChild(presence) > group.indexOfChild(background),
          "Standby presence is not drawn above the background video layer");
    });
    runOnMainSync(() -> {
      View stage = persistentPrimaryStage;
      Object background = field(stage, "backgroundStage");
      Object source = field(background, "currentSource");
      check("BGV".equals(String.valueOf(source)),
          "Standby stage background is not the bundled BGV video: " + source);
      Object texture = field(background, "videoView");
      check(texture instanceof android.view.TextureView,
          "Standby BGV is not rendering through a video texture");
      check(((android.view.TextureView) texture).isAvailable(),
          "Standby BGV video texture is not producing frames");
    });
    // Evidence of live video: the counter only advances while the BGV texture is producing frames.
    final long[] first = new long[1];
    runOnMainSync(() -> first[0] = standbyLiveFrame());
    check(first[0] >= 0, "Standby stage did not expose a live video frame counter");
    await(() -> standbyLiveFrame() > first[0] + 5,
        "Karaoke standby stage showed a frozen background instead of live BGV video");
    check(state(() -> session.current == null && !session.playing),
        "Standby stage was only asserted while nothing is playing");
  }

  /** Reads the standby overlay's own monotonic video-frame counter on the main thread. */
  private long standbyLiveFrame() {
    View stage = persistentPrimaryStage;
    View presence = stage == null ? null : stage.findViewWithTag("kanta.stage.presence");
    if (presence == null) return -1L;
    return ((ph.kanta.reborn.stage.StagePresenceOverlay) presence).liveFrame();
  }

  private void persistentStagePlaybackChecks() {
    AtomicReference<Object> backend = new AtomicReference<>();
    AtomicReference<java.util.UUID> identity = new AtomicReference<>();
    AtomicReference<java.util.List<ReservationQueue.Entry>> reservations = new AtomicReference<>();
    runOnMainSync(() -> {
      backend.set(session.player);
      identity.set(session.playbackIdentity);
      reservations.set(session.queue.snapshot());
    });
    long before = position();
    for (String destination : new String[] {"Home", "More"}) {
      runOnMainSync(() -> {
        View button = activity.getWindow().getDecorView().findViewWithTag("kanta.nav." + destination);
        check(button != null && button.performClick(), "Persistent navigation missing: " + destination);
      });
      assertPersistentStage(null);
      check(state(() -> session.playing && session.player == backend.get()
              && identity.get().equals(session.playbackIdentity)),
          "Menu navigation replaced or interrupted the playback owner: " + destination);
    }
    await(() -> session.position() > before + 200, "Menu navigation stalled the audible clock");
    check(state(() -> {
      java.util.List<ReservationQueue.Entry> after = session.queue.snapshot();
      if (after.size() != reservations.get().size()) return false;
      for (int i = 0; i < after.size(); i++)
        if (!after.get(i).id.equals(reservations.get().get(i).id)) return false;
      return true;
    }), "Menu navigation changed authoritative reservations");
    runOnMainSync(() -> openMenuGroup("settings"));
    await(this::hasSettingsWithoutDuplicateControls,
        "Persistent-stage playback check did not restore Settings");
  }

  private boolean hasSettingsWithoutDuplicateControls() {
    // Music volume lives on the Voice & Scoring page (moved out of the removed
    // Sound & Mix section), so Settings is only checked for controls that
    // belong elsewhere.
    View decor = activity.getWindow().getDecorView();
    return decor.findViewWithTag("kanta.voice.settings") == null
        && decor.findViewWithTag("kanta.settings.intermission") == null
        && decor.findViewWithTag("kanta.group.background") == null
        && decor.findViewWithTag("kanta.screen.mic-voice") == null
        && decor.findViewWithTag("kanta.pitchcorrection.toggle") == null
        && !contains(decor, "Key −") && !contains(decor, "Slower");
  }

  private void openMenuVoiceScoring() {
    openMenuGroup("voice-scoring");
  }

  private boolean hasSharedMicVoiceControls() {
    View screen = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.mic-voice");
    return screen instanceof ph.kanta.reborn.ui.KantaMicVoiceScreen
        && screen.findViewWithTag("kanta.mic.toggle") != null
        && screen.findViewWithTag("kanta.monitor.toggle") != null
        && screen.findViewWithTag("kanta.monitor.gain") instanceof SeekBar
        && screen.findViewWithTag("kanta.pitchcorrection.toggle") != null
        && screen.findViewWithTag("kanta.score.toggle") != null
        && screen.findViewWithTag("kanta.score.timing") instanceof SeekBar
        && screen.findViewWithTag("kanta.score.last") != null;
  }

  private void clickMicVoiceControl(String tag) {
    View control = activity.getWindow().getDecorView().findViewWithTag(tag);
    check(control != null && control.isEnabled() && control.performClick(),
        "Mic & Voice control did not respond: " + tag);
  }

  private void awaitStageSearch(String route) {
    await(() -> {
      View decor = activity.getWindow().getDecorView();
      View input = decor.findViewWithTag("kanta.home.search");
      return "Home".equals(field(activity, "tab")) && input instanceof EditText
          && input.hasFocus() && visibleOnScreen("kanta.home.search")
          && field(activity, "lowerUtilityAccessory") == null
          && ((KantaWorkspace) field(activity, "workspace")).activeAccessory() == null
          && decor.findViewWithTag("kanta.screen.search") == null
          && decor.findViewWithTag("kanta.utility.search-input") == null;
    }, route + " did not focus the Stage search input directly");
  }

  private View stageMatch(String description) {
    View list = activity.getWindow().getDecorView().findViewWithTag("kanta.stage.search-results");
    if (list == null) return null;
    java.util.ArrayList<View> matches = new java.util.ArrayList<>();
    list.findViewsWithText(matches, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
    if (matches.size() != 1) return null;
    View match = matches.get(0);
    return match.isShown() && match.isClickable() && match.getHeight() > 0 ? match : null;
  }

  private void stageSearchReservationChecks() throws IOException {
    AtomicReference<Song> fixture = new AtomicReference<>();
    runOnMainSync(() -> {
      for (Song candidate : session.songs) {
        if (!candidate.starter && "Original KAR".equals(candidate.title)) {
          fixture.set(candidate);
          break;
        }
      }
      check(fixture.get() != null, "Missing imported Stage search fixture");
    });
    Song song = fixture.get();
    int before = session.queue.snapshot().size();
    View decor = activity.getWindow().getDecorView();
    AtomicReference<EditText> entry = new AtomicReference<>();
    runOnMainSync(() -> {
      entry.set(decor.findViewWithTag("kanta.home.search"));
      entry.get().setText("");
      View keyboard = decor.findViewWithTag("kanta.home.keyboard");
      for (char digit : song.code.toCharArray()) {
        View key = keyboard == null ? null : keyboard.findViewWithTag("kanta.keyboard.digit." + digit);
        check(key != null && key.performClick(), "Stage numeric key did not respond");
      }
      check(song.code.contentEquals(entry.get().getText()), "Stage keys did not enter the exact song code");
      View extraDigit = keyboard.findViewWithTag("kanta.keyboard.digit.7");
      check(extraDigit != null && extraDigit.performClick()
          && song.code.contentEquals(entry.get().getText()), "Stage accepted a seventh digit");
      entry.get().setText("1234567 Long Song Title");
      check("1234567 Long Song Title".contentEquals(entry.get().getText()),
          "Stage truncated a mixed title search");
      entry.get().setText(song.code);
    });
    String description = "Reserve " + song.title + ", song " + song.code;
    await(() -> stageMatch(description) != null,
        "Typing on Stage did not show a reservable match before RES");
    runOnMainSync(() -> {
      check(session.queue.snapshot().size() == before, "Typing on Stage reserved a song automatically");
      check(decor.findViewWithTag("kanta.stage.search-preview") == null,
          "Stage duplicated its Home match list in a preview overlay");
    });
    await(() -> {
      View match = stageMatch(description);
      return match != null && match.getGlobalVisibleRect(new android.graphics.Rect());
    }, "Stage match was not visible before capture");
    // Keep the CI artifact name, but capture the results on Stage, not a dedicated screen.
    capture("search_screen.png", "kanta.stage.search-results");
    ph.kanta.reborn.ui.HomeScreen home = (ph.kanta.reborn.ui.HomeScreen) field(activity, "homeScreen");
    long[] generation = new long[1];
    runOnMainSync(() -> {
      // Start a fresh lookup so capture latency cannot affect the timeout check.
      entry.get().setText("");
      entry.get().setText(song.code);
      generation[0] = home.activeSongLookupGeneration();
      check(generation[0] != 0, "Stage lookup did not start a timeout window");
      session.publish();
      check(home.activeSongLookupGeneration() == generation[0],
          "A session publication restarted the Stage lookup");
    });
    await(() -> decor.<View>findViewWithTag("kanta.stage.search-dock").getVisibility() == View.GONE,
        "Stage search results did not time out");
    runOnMainSync(() -> {
      check(song.code.contentEquals(entry.get().getText()), "Stage timeout cleared the query");
      session.publish();
      home.showMatches(generation[0], java.util.Collections.singletonList(song), ignored -> {}, ignored -> {});
      check(decor.<View>findViewWithTag("kanta.stage.search-dock").getVisibility() == View.GONE
          && decor.<ViewGroup>findViewWithTag("kanta.stage.search-results").getChildCount() == 0,
          "A late Stage result or session publication reopened the expired list");
      int selection = entry.get().getSelectionStart();
      check(entry.get().performClick(), "Stage search editor did not accept a tap");
      check(song.code.contentEquals(entry.get().getText())
          && entry.get().getSelectionStart() == selection,
          "Reopening Stage search replaced the query or cursor");
      check(home.activeSongLookupGeneration() != 0
          && home.activeSongLookupGeneration() != generation[0], "Explicit search did not open a new window");
      home.showMatches(generation[0], java.util.Collections.emptyList(), ignored -> {}, ignored -> {});
      check(contains(decor.findViewWithTag("kanta.stage.search-results"),
          activity.getString(R.string.stage_search_preview_loading)),
          "An older same-query result replaced the new Stage lookup");
      View reserve = decor.findViewWithTag("kanta.stage.song-action.submit");
      check(reserve != null && reserve.performClick(), "Stage RES did not submit the exact code");
    });
    await(() -> {
      java.util.List<ReservationQueue.Entry> entries = session.queue.snapshot();
      return entries.size() == before + 1 && song.id.equals(entries.get(before).song.id);
    }, "Stage match did not append the expected song to the reservation queue");
    runOnMainSync(() -> {
      check(decor.findViewWithTag("kanta.home.search") == entry.get(),
          "Reserving a Stage match replaced the Stage input");
      session.remove(session.queue.snapshot().get(before).id);
      entry.get().setText("");
    });
  }

  private void workspaceBackNavigationChecks() {
    KantaWorkspace workspace = (KantaWorkspace) field(activity, "workspace");
    runOnMainSync(() -> {
      try {
        java.lang.reflect.Method open = MainActivity.class.getDeclaredMethod("presentAccessory", String.class);
        open.setAccessible(true);
        open.invoke(activity, KantaWorkspace.ACCESSORY_QUEUE);
      } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    });
    await(() -> KantaWorkspace.ACCESSORY_QUEUE.equals(field(activity, "lowerUtilityAccessory"))
            && contains((View) field(activity, "body"), "Up Next"),
        "Workspace Queue did not open the lower utility panel");
    check(state(() -> workspace.activeAccessory() == null),
        "Workspace Queue layered an overlay over the persistent stage");
    runOnMainSync(() -> activity.getOnBackPressedDispatcher().onBackPressed());
    await(() -> field(activity, "lowerUtilityAccessory") == null && visibleOnScreen("kanta.stage.primary"),
        "Predictive back did not dismiss the lower utility panel and restore Stage");
    assertPersistentStage(null);
    check(
        state(
            () -> {
              View stage =
                  activity.getWindow().getDecorView().findViewWithTag("kanta.stage.primary");
              return stage != null && stage.isAttachedToWindow()
                  && field(activity, "lowerUtilityAccessory") == null;
            }),
        "Back dismissal detached the persistent stage or retained the lower utility");
  }

  private CameraStage cameraStage() {
    return (CameraStage) field(activity, "camera");
  }

  private Object field(Object target, String name) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return field.get(target);
    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
  }

  private void legacyQueueRestoreChecks() throws Exception {
    Context target = getTargetContext();
    File scratch = java.nio.file.Files.createTempDirectory(target.getCacheDir().toPath(),
        "legacy-queue-").toFile();
    try {
      Context isolated = new ContextWrapper(target) {
        @Override public File getFilesDir() { return scratch; }
      };
      File media = new File(scratch, "media");
      if (!media.mkdir()) throw new IOException("Could not create fixture media folder");
      java.nio.file.Files.write(new File(media, "local.mp3").toPath(), new byte[] {1});
      java.util.UUID first = java.util.UUID.randomUUID(), second = java.util.UUID.randomUUID();
      org.json.JSONObject local = new org.json.JSONObject().put("id", "local-1")
          .put("title", "Local song").put("file", "local.mp3");
      org.json.JSONArray rows = new org.json.JSONArray()
          .put(new org.json.JSONObject().put("source", "youtube").put("id", "old-video-id"))
          .put(new org.json.JSONObject().put("id", first.toString()).put("song", "local-1")
              .put("singer", "First singer"))
          .put(new org.json.JSONObject().put("song", "local-1").put("youtube", "video-id"))
          .put(new org.json.JSONObject().put("song", "missing-online-song"))
          .put(new org.json.JSONObject().put("id", second.toString()).put("song", "local-1")
              .put("source", "local").put("singer", "Second singer"));
      org.json.JSONObject starter = new org.json.JSONObject().put("id", "starter:fixture")
          .put("title", "Retired starter").put("file", "fixture.mid").put("starter", true);
      java.nio.file.Files.write(new File(media, "fixture.mid").toPath(), new byte[] {1});
      rows.put(new org.json.JSONObject().put("id", java.util.UUID.randomUUID().toString())
          .put("song", "starter:fixture"));
      org.json.JSONObject fixture = new org.json.JSONObject().put("version", 1)
          .put("songs", new org.json.JSONArray().put(local).put(starter)).put("queue", rows);
      File stateFile = new File(scratch, "library-v1.json");
      java.nio.file.Files.write(stateFile.toPath(),
          fixture.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      LibraryRepository repository = new LibraryRepository(isolated);
      repository.load();
      ReservationQueue queue = new ReservationQueue();
      queue.restore(repository.queue());
      check(queue.snapshot().size() == 2 && queue.peek().id.equals(first)
              && "First singer".equals(queue.peek().singer),
          "Old online rows blocked library loading or changed the local FIFO head");
      queue.remove(first);
      check(queue.peek().id.equals(second) && "Second singer".equals(queue.peek().singer),
          "Restoration lost an intentional local repeat");
      LibraryRepository reopened = new LibraryRepository(isolated);
      reopened.load();
      check(reopened.queue().size() == 2 && reopened.queue().get(0).id.equals(first)
              && reopened.queue().get(1).id.equals(second),
          "Local reservations did not survive the migrated library write");
    } finally {
      File[] files = new File(scratch, "media").listFiles();
      if (files != null) for (File file : files) file.delete();
      new File(scratch, "media").delete();
      files = scratch.listFiles();
      if (files != null) for (File file : files) file.delete();
      scratch.delete();
    }
  }

  private void companionChecks() throws Exception {
    check(!state(() -> session.companion.enabled()), "Companion enabled without opt-in");
    java.util.List<String> addresses = CompanionControl.addresses();
    check(!addresses.isEmpty(), "Emulator has no private network interface");
    String address = addresses.get(0);
    runOnMainSync(() -> session.companion.startHost(address));
    await(() -> !session.companion.pairing.isEmpty(), "Companion host did not bind");
    AtomicReference<String> code = new AtomicReference<>();
    runOnMainSync(() -> code.set(session.companion.pairing));
    try (java.net.Socket socket = new java.net.Socket(address, CompanionControl.PORT)) {
      socket.setSoTimeout(5000);
      try (CompanionProtocol.Channel channel = CompanionProtocol.handshake(
          socket.getInputStream(), socket.getOutputStream(), code.get(), false)) {
        await(() -> session.companion.pairing.isEmpty(), "Connected host retained pairing code");
        CompanionProtocol.Request search = new CompanionProtocol.Request(
            java.util.UUID.randomUUID(), "search", "Original KAR", "");
        org.json.JSONObject found = companionRequest(channel, search);
        check(found.getJSONArray("songs").length() == 1, "Encrypted host search failed");
        check(!found.toString().contains("fileName"), "Companion exposed private media path");
        String songId = found.getJSONArray("songs").getJSONObject(0).getString("id");
        CompanionProtocol.Request reserve = new CompanionProtocol.Request(java.util.UUID.randomUUID(), "reserve", songId, "Companion test");
        org.json.JSONObject reserved = companionRequest(channel, reserve);
        check(reserved.getJSONArray("queue").length() == 1, "Companion did not reserve in authoritative FIFO");
        org.json.JSONObject replayed = companionRequest(channel, reserve);
        check(replayed.toString().equals(reserved.toString()), "Duplicate command did not return its original reply");
        check(state(() -> session.queue.snapshot().size() == 1), "Duplicate companion command reserved twice");
        companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "reserve", songId, "Another turn"));
        check(state(() -> session.queue.snapshot().size() == 2), "Intentional repeated song was suppressed");
        String first = reserved.getJSONArray("queue").getJSONObject(0).getString("id");
        org.json.JSONObject remaining = companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "cancel", first, ""));
        check(remaining.getJSONArray("queue").length() == 1, "Companion cancellation affected another turn");
        companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "cancel", remaining.getJSONArray("queue").getJSONObject(0).getString("id"), ""));
        check(state(() -> session.queue.snapshot().isEmpty()), "Companion cancellation did not reach FIFO");
        companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "reserve", songId, "Remote playback"));
        check(state(() -> session.queue.snapshot().size() == 1 && !session.playing),
            "Remote reservation did not remain queued until Play");
        companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "play", "", ""));
        await(() -> session.playing && !session.loading, "Remote local playback did not start");
        check(state(() -> session.current != null && session.current.id.equals(songId) && session.queue.snapshot().isEmpty()),
            "Remote start did not consume the local reservation");
        companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "pause", "", ""));
        await(() -> !session.playing, "Remote pause did not settle");
        companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "play", "", ""));
        await(() -> session.playing, "Remote local resume did not start");
        runOnMainSync(session::stop);
        check(!state(() -> session.microphone.enabled() || session.cameraPreviewEnabled || session.scoringEnabled), "Companion activated a sensor or scoring");
        runOnMainSync(() -> session.hostVisible(false));
        check(!state(() -> session.companion.enabled()), "Backgrounding did not revoke companion");
        boolean rejected = false;
        try { companionRequest(channel, search); } catch (IOException expected) { rejected = true; }
        check(rejected, "Revoked companion socket remained usable");
        runOnMainSync(() -> session.hostVisible(true));
        check(!state(() -> session.companion.enabled()), "Foregrounding restored companion automatically");
      }
    }
    runOnMainSync(() -> session.companion.startHost("0.0.0.0"));
    check(!state(() -> session.companion.enabled()), "Wildcard companion listener accepted");
  }

  private void browserCompanionChecks() throws Exception {
    check(!state(() -> session.browserCompanion.active()), "Browser hosting enabled without opt-in");
    String address=CompanionControl.addresses().get(0);
    runOnMainSync(() -> session.browserCompanion.start(address));
    await(() -> !session.browserCompanion.code.isEmpty(), "HTTPS host did not start");
    AtomicReference<String> origin=new AtomicReference<>(),code=new AtomicReference<>(),pin=new AtomicReference<>();
    runOnMainSync(() -> {origin.set(session.browserCompanion.address);code.set(session.browserCompanion.code);pin.set(session.browserCompanion.fingerprint);});
    javax.net.ssl.SSLContext tls=javax.net.ssl.SSLContext.getInstance("TLS");
    tls.init(null,new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager(){
      public java.security.cert.X509Certificate[] getAcceptedIssuers(){return new java.security.cert.X509Certificate[0];}
      public void checkClientTrusted(java.security.cert.X509Certificate[] c,String a)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException();}
      public void checkServerTrusted(java.security.cert.X509Certificate[] chain,String auth)throws java.security.cert.CertificateException{
        try{chain[0].checkValidity();StringBuilder actual=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()))actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
          if(!actual.toString().equals(pin.get()))throw new java.security.cert.CertificateException("Wrong host pin");
        }catch(java.security.GeneralSecurityException e){throw new java.security.cert.CertificateException(e);}
      }
    }},new java.security.SecureRandom());
    String token=browserHttp(tls,origin.get(),"/pair","",code.get().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    check(token.matches("[a-f0-9]{64}"),"HTTPS host did not issue a scoped token");
    CompanionProtocol.Request search=new CompanionProtocol.Request(
        java.util.UUID.randomUUID(), "search", "Original KAR", "");
    org.json.JSONObject found=new org.json.JSONObject(browserHttp(tls,origin.get(),"/command",token,search.encode()));
    check(found.getInt("version")==1&&found.getString("source").equals("local"),"Missing browser capability contract");
    String song=found.getJSONArray("songs").getJSONObject(0).getString("id");
    CompanionProtocol.Request reserve=new CompanionProtocol.Request(java.util.UUID.randomUUID(),"reserve",song,"Browser guest");
    String first=browserHttp(tls,origin.get(),"/command",token,reserve.encode());
    check(first.equals(browserHttp(tls,origin.get(),"/command",token,reserve.encode())),"HTTPS replay response changed");
    check(state(() -> session.queue.snapshot().size()==1),"HTTPS retry reserved twice");
    browserHttp(tls,origin.get(),"/command",token,new CompanionProtocol.Request(java.util.UUID.randomUUID(),"reserve",song,"Second turn").encode());
    check(state(() -> session.queue.snapshot().size()==2),"HTTPS intentional duplicate was suppressed");
    String reservation=new org.json.JSONObject(first).getJSONArray("queue").getJSONObject(0).getString("id");
    browserHttp(tls,origin.get(),"/command",token,new CompanionProtocol.Request(java.util.UUID.randomUUID(),"cancel",reservation,"").encode());
    check(state(() -> session.queue.snapshot().size()==1),"HTTPS cancellation changed the wrong queue");
    browserHttp(tls,origin.get(),"/command",token,new CompanionProtocol.Request(java.util.UUID.randomUUID(),"play","","").encode());
    await(() -> session.playing&&!session.loading,"HTTPS Play did not reach native audio");
    check(state(() -> session.queue.snapshot().isEmpty()),"HTTPS Play did not consume authoritative FIFO");
    browserHttp(tls,origin.get(),"/command",token,new CompanionProtocol.Request(java.util.UUID.randomUUID(),"pause","","").encode());
    check(!state(() -> session.playing),"HTTPS Pause did not reach native audio");
    byte[] stop=new CompanionProtocol.Request(java.util.UUID.randomUUID(),"next","","").encode();
    System.arraycopy("stop".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,stop,18,4);
    browserHttp(tls,origin.get(),"/command",token,stop);
    check(state(() -> session.current==null),"HTTPS Stop did not reach native audio");
    check(!state(() -> session.microphone.enabled()||session.cameraPreviewEnabled),"HTTPS companion activated a sensor");
    runOnMainSync(() -> session.hostVisible(false));
    check(!state(() -> session.browserCompanion.active()),"Background did not revoke HTTPS host");
    boolean rejected=false;try{browserHttp(tls,origin.get(),"/command",token,search.encode());}catch(Exception expected){rejected=true;}
    check(rejected,"Revoked HTTPS guest retained access");
    runOnMainSync(() -> session.hostVisible(true));
    check(!state(() -> session.browserCompanion.active()),"Returning restarted HTTPS hosting");
  }

  private String browserHttp(javax.net.ssl.SSLContext tls,String origin,String path,String token,byte[] body)throws Exception {
    javax.net.ssl.HttpsURLConnection connection=(javax.net.ssl.HttpsURLConnection)new java.net.URL(origin+path).openConnection();
    try{
      connection.setSSLSocketFactory(tls.getSocketFactory());connection.setConnectTimeout(4000);connection.setReadTimeout(4000);
      connection.setRequestMethod("POST");connection.setRequestProperty("Origin",origin);connection.setRequestProperty("Content-Type","application/octet-stream");
      if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
      connection.setDoOutput(true);connection.setFixedLengthStreamingMode(body.length);
      try(java.io.OutputStream output=connection.getOutputStream()){output.write(body);}
      check(connection.getResponseCode()==200,"HTTPS browser request rejected");
      try(java.io.InputStream input=connection.getInputStream();java.io.ByteArrayOutputStream result=new java.io.ByteArrayOutputStream()){
        byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){result.write(buffer,0,count);if(result.size()>16384)throw new IOException("Oversized HTTPS response");}
        return result.toString("UTF-8");
      }
    }finally{connection.disconnect();}
  }

  private org.json.JSONObject companionRequest(CompanionProtocol.Channel channel, CompanionProtocol.Request request) throws Exception {
    channel.send(request.encode());
    org.json.JSONObject response = new org.json.JSONObject(new String(channel.receive(), java.nio.charset.StandardCharsets.UTF_8));
    check(request.id.toString().equals(response.getString("request")), "Companion response identity mismatch");
    return response;
  }

  private boolean visibleOnScreen(String tag) {
    View view = activity.getWindow().getDecorView().findViewWithTag(tag);
    if (view == null || !view.isShown() || view.getWindowToken() == null) return false;
    android.graphics.Rect bounds = new android.graphics.Rect();
    return view.getGlobalVisibleRect(bounds) && bounds.width() > 0 && bounds.height() > 0;
  }

  /** Navigation must project the same mounted, visible presenter above every utility panel. */
  private void openStageQueueShortcut() {
    View decor = activity.getWindow().getDecorView();
    decor.findViewWithTag("kanta.nav.Home").performClick();
    View anchor = decor.findViewWithTag("kanta.stage.controls");
    check(anchor != null && anchor.performClick(), "Stage Controls did not open");
    View panel = decor.findViewWithTag("kanta.stage.controls.panel");
    check(panel != null && panel.getVisibility() == View.VISIBLE, "Inline Stage Controls panel missing");
    android.widget.Button action = panel.findViewWithTag("kanta.stage.control.queue");
    checkStageTextButton(action, R.string.stage_reserve_list);
    check(action.performClick(), "Stage Controls action failed");
    check(panel.getVisibility() != View.VISIBLE, "Stage Controls stayed above the reserve list");
  }

  /** The inline panel shows every pad and each remaining control without a second surface. */
  private void openStageControlsForCheck(View decor, String controlsTag) {
    View anchor = decor.findViewWithTag(controlsTag);
    checkStageTextButton(anchor, R.string.stage_controls);
    check(anchor.performClick(), "Stage Controls did not open");
    View panel = decor.findViewWithTag(controlsTag + ".panel");
    check(panel != null && panel.getVisibility() == View.VISIBLE, "Inline Stage Controls panel missing");
  }

  private void verifyStageControlsContent(View decor, String controlsTag) {
    View panel = decor.findViewWithTag(controlsTag + ".panel");
    if ("kanta.stage.controls".equals(controlsTag)) {
      // The portrait panel must not cover the Stage surface (no layout pass needed).
      View stage = decor.findViewWithTag("kanta.stage.primary");
      check(!(panel.getParent() instanceof android.widget.FrameLayout)
              || panel.getParent() != stage,
          "Embedded Stage Controls must not cover the Stage surface");
    }
    // The app tags panel children from the bare prefix (kanta.stage / kanta.fullscreen):
    // pads are kanta.stage.pad.N and controls kanta.stage.control.X, while the panel itself
    // is kanta.stage.controls.panel. Derive the child prefix from controlsTag accordingly.
    String prefix = controlsTag.replaceFirst("\\.controls$", "") + ".";
    for (int slot = 0; slot < 8; slot++) {
      android.view.View pad = panel.findViewWithTag(prefix + "pad." + slot);
      if (!(pad instanceof android.widget.Button)) {
        android.util.Log.e("KANTA_RUNTIME", "pad " + slot + " missing; panel tag=" + panel.getTag()
            + " vis=" + panel.getVisibility() + " children=" + ((android.view.ViewGroup) panel).getChildCount()
            + " tree=" + dumpTagTree(panel));
        check(false, "Stage Controls did not expose pad " + slot);
      }
    }
    for (String control : new String[]{"mic", "monitor", "scoring", "stop-party"}) {
      check(panel.findViewWithTag(prefix + "control." + control) != null,
          "Stage Controls is missing the labeled " + control + " control");
    }
    android.graphics.drawable.GradientDrawable background =
        (android.graphics.drawable.GradientDrawable) panel.getBackground();
    check(background != null && android.graphics.Color.alpha(background.getColor().getDefaultColor()) < 255,
        "Stage Controls panel is not translucent");
  }

  private static String dumpTagTree(android.view.View v) {
    StringBuilder o = new StringBuilder();
    dumpInto(v, o, 0);
    return o.length() == 0 ? "(empty)" : o.toString();
  }

  private static void dumpInto(android.view.View v, StringBuilder o, int d) {
    if (o.length() > 1600) return;
    o.append("\n").append("  ".repeat(d)).append(v.getClass().getSimpleName())
        .append(" tag=").append(v.getTag()).append(" vis=").append(v.getVisibility());
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup g = (android.view.ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) dumpInto(g.getChildAt(i), o, d + 1);
    }
  }

  private void collapseStageControls(View decor, String controlsTag) {
    View anchor = decor.findViewWithTag(controlsTag);
    View panel = decor.findViewWithTag(controlsTag + ".panel");
    check(anchor.performClick(), "Stage Controls did not collapse");
    check(panel.getVisibility() != View.VISIBLE, "Stage Controls panel stayed open");
  }

  /**
   * Pollable geometry: the portrait panel is a sibling below the Stage pane (never layered over
   * the Stage surface and never inside the keyboard scroll). Polled via await because the
   * bounds are only valid after the layout pass that follows opening the panel.
   */
  private boolean stageControlsBelowStage() {
    View decor = activity.getWindow().getDecorView();
    View panel = decor.findViewWithTag("kanta.stage.controls.panel");
    View stage = decor.findViewWithTag("kanta.stage.primary");
    if (panel == null || stage == null || panel.getVisibility() != View.VISIBLE) return false;
    android.graphics.Rect panelBounds = new android.graphics.Rect();
    android.graphics.Rect stageBounds = new android.graphics.Rect();
    return panel.getGlobalVisibleRect(panelBounds)
        && stage.getGlobalVisibleRect(stageBounds)
        && panelBounds.top >= stageBounds.bottom;
  }

  private void checkPartyPadsPanel() {
    View panel = activity.getWindow().getDecorView().findViewWithTag("kanta.party-pads");
    check(panel != null && panel.getVisibility() == View.VISIBLE, "Party Pads page is not showing");
    check(panel.findViewWithTag("kanta.party-pad.trigger.0") instanceof android.widget.Button,
        "Party Pads did not expose the existing sound actions");
    for (int slot = 0; slot < 8; slot++) {
      check(panel.findViewWithTag("kanta.party-pad.assign." + slot) != null
          && panel.findViewWithTag("kanta.party-pad.reset." + slot) != null,
          "Party Pads page is missing clip management for pad " + slot);
    }
  }

  private void openMenuGroup(String action) {
    View menu = activity.getWindow().getDecorView().findViewWithTag("kanta.nav.More");
    check(menu != null && menu.performClick(), "Menu tab could not be opened");
    View decor = activity.getWindow().getDecorView();
    for (int hop = 0; hop < 5 && decor.findViewWithTag("kanta.group.settings") == null; hop++) {
      activity.getOnBackPressedDispatcher().onBackPressed();
      if (decor.findViewWithTag("kanta.home.search") != null) menu.performClick();
    }
    View item = decor.findViewWithTag("kanta.group." + action);
    check(item != null && item.performClick(), "Menu group could not be opened: " + action);
  }

  private void assertPersistentStage(String lowerPanelTag) {
    await(() -> {
      // await() evaluates predicates through state(), which already runs on the main thread.
      // Do not nest Instrumentation.runOnMainSync here: Android rejects that call from main.
      int orientation = (Integer) field(activity, "lastOrientation");
      if (orientation != persistentStageOrientation) {
        // A real configuration change intentionally replaces StagePresenterImpl.
        // Refresh the identity baseline only when the Activity's orientation changes;
        // navigation within one orientation must still preserve the same stage view.
        persistentPrimaryStage = activity.getWindow().getDecorView()
            .findViewWithTag("kanta.stage.primary");
        persistentStageOrientation = orientation;
      }
      View primary = activity.getWindow().getDecorView().findViewWithTag("kanta.stage.primary");
      if (primary == null || primary != persistentPrimaryStage || !primary.isAttachedToWindow()) return false;
      String page = (String) field(activity, "tab");
      boolean stageVisible = field(activity, "lowerUtilityAccessory") == null
          && "Home".equals(page);
      if (visibleOnScreen("kanta.stage.primary") != stageVisible) return false;
      if (lowerPanelTag == null) return true;
      View panel = activity.getWindow().getDecorView().findViewWithTag(lowerPanelTag);
      // Long group menus can place a tile below the current ScrollView viewport;
      // attachment and geometry still prove it belongs to the lower utility region.
      if (panel == null || !panel.isAttachedToWindow() || panel.getHeight() <= 0) return false;
      if (!stageVisible) return true;
      // The search-results dock is positioned as a persistent overlay on the Stage,
      // so it intentionally overlaps the Stage rather than sitting below it.
      if ("kanta.stage.search-results".equals(lowerPanelTag)) {
        return panel.isAttachedToWindow();
      }
      int[] stageLocation = new int[2];
      int[] panelLocation = new int[2];
      primary.getLocationInWindow(stageLocation);
      panel.getLocationInWindow(panelLocation);
      if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        return panelLocation[0] >= stageLocation[0] + primary.getWidth();
      }
      return panelLocation[1] >= stageLocation[1] + primary.getHeight();
    }, "Navigation hid, replaced or overlapped the persistent stage: " + lowerPanelTag);
  }

  private void capture(String name, String requiredVisibleTag) throws IOException {
    await(() -> visibleOnScreen(requiredVisibleTag),
        "Destination was present in the view tree but not visible before capture: " + requiredVisibleTag);
    assertPersistentStage(requiredVisibleTag);
    capture(name);
  }

  private void capture(String name) throws IOException {
    try {
      getUiAutomation().waitForIdle(250, 5000);
    } catch (java.util.concurrent.TimeoutException continuousRendering) {
      // A live video/fullscreen stage intentionally never becomes globally idle. The target
      // visibility and persistent-stage assertions already ran above; capture its next frame.
      android.util.Log.w("KANTA_RUNTIME", "Capturing animated surface without global idle: " + name);
    }
    // Let the compositor commit the destination frame after synchronous view-tree changes.
    SystemClock.sleep(350);
    Bitmap image = getUiAutomation().takeScreenshot();
    if (image == null) throw new IOException("Missing runtime screenshot");
    try (FileOutputStream output =
        new FileOutputStream(new File(getTargetContext().getFilesDir(), name))) {
      if (!image.compress(Bitmap.CompressFormat.PNG, 100, output))
        throw new IOException("Cannot save runtime screenshot");
    } finally {
      image.recycle();
    }
  }



  private void shell(String command) throws IOException {
    try (InputStream result =
        new android.os.ParcelFileDescriptor.AutoCloseInputStream(
            getUiAutomation().executeShellCommand(command))) {
      readFixture(result);
    }
  }

  private byte[] readFixture(InputStream input) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] block = new byte[4096];
    int count;
    while ((count = input.read(block)) != -1) {
      if (bytes.size() + count > 16 * 1024 * 1024) throw new IOException("Fixture too large");
      bytes.write(block, 0, count);
    }
    return bytes.toByteArray();
  }

  private long position() {
    AtomicReference<Long> value = new AtomicReference<>(0L);
    runOnMainSync(() -> value.set(session.position()));
    return value.get();
  }
}
