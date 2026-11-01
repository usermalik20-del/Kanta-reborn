package ph.kanta.reborn;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.io.*;
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
    long deadline = SystemClock.uptimeMillis() + 15000;
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

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      Intent launch =
          new Intent(getTargetContext(), MainActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity = (MainActivity) startActivitySync(launch);
      session = ((KantaApplication) activity.getApplication()).session;
      await(() -> session.ready, "Library did not open");
      await(() -> session.stageReady, "Stage media store did not become ready");
      capture("library_ready.png");
      check(
          state(() -> contains(activity.getWindow().getDecorView(), "Music brings people together.")),
          "Reference-led Home dashboard was not mounted at launch");
      capture("home_dashboard.png");
      runOnMainSync(() -> {
        View search = activity.getWindow().getDecorView().findViewWithTag("kanta.home.feature.onSearch");
        check(search != null, "Home dashboard Search feature missing");
        search.performClick();
      });
      await(
          () -> activity.getWindow().getDecorView().findViewWithTag("kanta.screen.search") != null,
          "Home Search did not open the reference Search destination");
      capture("search_screen.png");
      runOnMainSync(() -> {
        View back = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.search.back");
        check(back != null, "Search back control missing");
        back.performClick();
      });
      await(
          () -> contains(activity.getWindow().getDecorView(), "Music brings people together."),
          "Search did not return to Home");
      capture("home_dashboard_restored.png");
      runOnMainSync(() -> {
        View queue = activity.getWindow().getDecorView().findViewWithTag("kanta.home.feature.onQueue");
        check(queue != null, "Home Queue feature missing");
        queue.performClick();
      });
      await(
          () -> activity.getWindow().getDecorView().findViewWithTag("kanta.screen.queue") != null,
          "Home Queue did not open the reference Queue destination");
      capture("queue_screen.png");
      runOnMainSync(() -> {
        View back = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.queue.back");
        check(back != null, "Queue back control missing");
        back.performClick();
      });
      await(
          () -> contains(activity.getWindow().getDecorView(), "Music brings people together."),
          "Queue did not return to Home");
      runOnMainSync(() -> {
        View pitch = activity.getWindow().getDecorView().findViewWithTag("kanta.home.feature.onPitchTempo");
        check(pitch != null, "Home Pitch & Tempo feature missing");
        pitch.performClick();
      });
      await(
          () -> activity.getWindow().getDecorView().findViewWithTag("kanta.screen.pitch-tempo") != null,
          "Home Pitch & Tempo did not open its reference destination");
      capture("pitch_tempo_screen.png");
      runOnMainSync(() -> {
        View increment = activity.getWindow().getDecorView().findViewWithTag("kanta.pitch.key.increment");
        check(increment != null, "Pitch increment control missing");
        increment.performClick();
      });
      await(() -> session.pitch == 1, "Pitch screen did not update the active session key");
      runOnMainSync(() -> {
        View reset = activity.getWindow().getDecorView().findViewWithTag("kanta.pitch.reset");
        check(reset != null, "Pitch reset control missing");
        reset.performClick();
      });
      await(() -> session.pitch == 0 && Math.abs(session.tempo - 1.0f) < 0.001f,
          "Pitch reset did not restore the original key and tempo");
      runOnMainSync(() -> {
        View back = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.pitch-tempo.back");
        check(back != null, "Pitch & Tempo back control missing");
        back.performClick();
      });
      await(
          () -> contains(activity.getWindow().getDecorView(), "Music brings people together."),
          "Pitch & Tempo did not return to Home");
      runOnMainSync(() -> {
        View visuals = activity.getWindow().getDecorView().findViewWithTag("kanta.home.feature.onVisuals");
        check(visuals != null, "Home Visuals feature missing");
        visuals.performClick();
      });
      await(
          () -> activity.getWindow().getDecorView().findViewWithTag("kanta.screen.visuals") != null,
          "Home Visuals did not open the reference destination");
      capture("visuals_screen.png");
      runOnMainSync(() -> {
        View scene = activity.getWindow().getDecorView().findViewWithTag("kanta.visuals.background.bamboo-mist");
        check(scene != null, "Existing scenic artwork thumbnail missing");
        scene.performClick();
      });
      await(() -> "bambooMist".equals(session.scenicName), "Visual selection did not reach SessionController");
      runOnMainSync(() -> activity.getOnBackPressedDispatcher().onBackPressed());
      await(
          () -> contains(activity.getWindow().getDecorView(), "Music brings people together."),
          "Visuals did not return to Home");
      runOnMainSync(() -> {
        View mic = activity.getWindow().getDecorView().findViewWithTag("kanta.home.feature.onMicVoice");
        check(mic != null, "Home Mic & Voice feature missing");
        mic.performClick();
      });
      await(
          () -> activity.getWindow().getDecorView().findViewWithTag("kanta.screen.mic-voice") != null,
          "Home Mic & Voice did not open the reference destination");
      capture("mic_voice_screen.png");
      runOnMainSync(() -> {
        View room = activity.getWindow().getDecorView().findViewWithTag("kanta.monitor.mode.room");
        check(room != null, "Room monitor effect control missing");
        room.performClick();
      });
      await(() -> session.monitor.mode() == ph.kanta.reborn.core.VocalMonitorEffects.Mode.ROOM,
          "Mic & Voice effect selection did not reach the existing monitor processor");
      session.monitor.mode(ph.kanta.reborn.core.VocalMonitorEffects.Mode.DRY);
      session.publish();
      runOnMainSync(() -> {
        View back = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.mic-voice.back");
        if (back != null) back.performClick();
        else {
          View top = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.mic-voice");
          check(top != null, "Mic & Voice destination disappeared unexpectedly");
          activity.getOnBackPressedDispatcher().onBackPressed();
        }
      });
      await(
          () -> contains(activity.getWindow().getDecorView(), "Music brings people together."),
          "Mic & Voice did not return to Home");
      runOnMainSync(() -> {
        View more = activity.getWindow().getDecorView().findViewWithTag("kanta.home.nav.More");
        check(more != null, "Home dashboard More destination missing");
        more.performClick();
      });
      runOnMainSync(() -> {
        View open = activity.getWindow().getDecorView().findViewWithTag("kanta.fullscreen.open");
        check(open != null, "Stage fullscreen entry missing");
        open.performClick();
      });
      Dialog fullscreenDialog = (Dialog) field(activity, "fullscreen");
      await(
          () -> fullscreenDialog != null && fullscreenDialog.isShowing()
              && fullscreenDialog.getWindow().getDecorView().findViewWithTag("kanta.fullscreen.stage") != null,
          "Fullscreen StageSurface did not open");
      capture("fullscreen_stage.png");
      runOnMainSync(() -> {
        View keyUp = fullscreenDialog.getWindow().getDecorView().findViewWithTag("kanta.fullscreen.key.increment");
        check(keyUp != null, "Fullscreen key increment control missing");
        keyUp.performClick();
      });
      await(() -> session.pitch == 1, "Fullscreen key control did not update SessionController");
      runOnMainSync(() -> {
        View keyDown = fullscreenDialog.getWindow().getDecorView().findViewWithTag("kanta.fullscreen.key.decrement");
        check(keyDown != null, "Fullscreen key decrement control missing");
        keyDown.performClick();
      });
      await(() -> session.pitch == 0, "Fullscreen key control did not restore original key");
      runOnMainSync(() -> {
        View close = fullscreenDialog.getWindow().getDecorView().findViewWithTag("kanta.fullscreen.close");
        check(close != null, "Fullscreen close control missing");
        close.performClick();
      });
      await(() -> !fullscreenDialog.isShowing(), "Fullscreen stage did not dismiss cleanly");
      View primaryStage = activity.getWindow().getDecorView().findViewWithTag("kanta.stage.primary");
      check(primaryStage != null, "Persistent stage missing before Now Playing");
      runOnMainSync(primaryStage::performClick);
      await(() -> activity.getWindow().getDecorView().findViewWithTag("kanta.screen.now-playing") != null,
          "Tapping the persistent stage did not open Now Playing");
      check(state(() -> contains(activity.getWindow().getDecorView(), "Nothing playing")),
          "Now Playing did not display the truthful empty playback state");
      capture("now_playing_screen.png");
      runOnMainSync(() -> {
        View back = activity.getWindow().getDecorView().findViewWithTag("kanta.screen.now-playing.back");
        check(back != null, "Now Playing back control missing");
        back.performClick();
      });
      await(() -> contains(activity.getWindow().getDecorView(), "Music brings people together."),
          "Now Playing did not return to Home");
      runOnMainSync(() -> {
        View more = activity.getWindow().getDecorView().findViewWithTag("kanta.home.nav.More");
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
      check(state(() -> session.songs.size() == 3), "Clean install starter catalog");
      capture("fixture_loaded.png");
      check(
          state(() -> contains(activity.getWindow().getDecorView(), "KANTA")),
          "Native activity did not render");
      capture("ui_rendered.png");
      youtubeLifecycleChecks();
      capture("youtube_loaded.png");
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
            () -> isolated.songs().size() > 3);
      } catch (java.io.InterruptedIOException expected) {
        cancelled = true;
      }
      check(cancelled && isolated.songs().size() == 3, "Cancelled batch published staged songs");
      LibraryRepository afterCancel =
          new LibraryRepository(getTargetContext().createDeviceProtectedStorageContext());
      afterCancel.load();
      check(
          afterCancel.songs().size() == 3
              && afterCancel.songs().stream().allMatch(
                  value -> value.starter && new File(afterCancel.media, value.fileName).isFile()),
          "Cancelled batch left imported metadata or missing media in durable catalog");
      int[] imported = isolated.importBatch(files, () -> false);
      check(
          imported[0] == 3 && imported[1] == 1 && isolated.songs().size() == 5,
          "SAF partial import or SoundFont selection");
      check(
          !isolated.soundFontName().equals("KANTA original tone"),
          "SoundFont override not retained");
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
      runOnMainSync(
          () ->
              session.importSongs(
                  java.util.Collections.singletonList(FixtureProvider.file("audio"))));
      await(
          () -> !session.importing && session.songs.size() == 4, "Controller audio import failed");
      Song loopSong =
          session.songs.stream()
              .filter(SessionController::isAudio)
              .findFirst()
              .orElseThrow(() -> new AssertionError("Missing fixture song"));
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
      await(() -> stableStage.get().getWidth() > 0, "Stage layout never became visible after leaving Home");
      workspaceBackNavigationChecks();
      check(
          state(
              () -> {
                for (String name : new String[] {"My Set", "Loop", "Scenic", "Realm", "Control"}) {
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
      stableStage.set(primaryStage);
      await(
          () ->
              activity.cameraStreaming()
                  && ((ph.kanta.reborn.stage.StagePresenterImpl) primaryStage).isCameraVisible(),
          "Front camera did not return to the primary stage");
      await(
          () -> !cameraStageRef.get().isCameraVisible(),
          "Fullscreen stage retained its camera preview after ownership moved away");
      runOnMainSync(activity::toggleCamera);
      await(
          () ->
              !session.cameraPreviewEnabled
                  && !activity.cameraStreaming()
                  && !((ph.kanta.reborn.stage.StagePresenterImpl) primaryStage).isCameraVisible(),
          "Disabling the camera did not release the primary preview");
      runOnMainSync(activity::toggleCamera);
      await(activity::cameraStreaming, "Camera did not reopen after explicit opt-in");
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

      runOnMainSync(
          () ->
              activity
                  .getWindow()
                  .getDecorView()
                  .findViewWithTag("kanta.voice.settings")
                  .performClick());
      check(
          state(
              () ->
                  contains(
                          activity.getWindow().getDecorView(),
                          "Wired / USB / Bluetooth monitoring")
                      && contains(activity.getWindow().getDecorView(), "Turn on microphone")),
          "Voice settings did not expand from Control");
      capture("voice_settings.png");
      check(
          state(
              () -> {
                View correction =
                    activity
                        .getWindow()
                        .getDecorView()
                        .findViewWithTag("kanta.pitchcorrection.toggle");
                return correction != null
                    && !correction.isEnabled()
                    && contains(activity.getWindow().getDecorView(), "Turn on pitch correction");
              }),
          "Correction control was missing or enabled before verified monitoring");
      runOnMainSync(
          () ->
              activity
                  .getWindow()
                  .getDecorView()
                  .findViewWithTag("kanta.voice.settings")
                  .performClick());
      check(
          state(
              () ->
                  !contains(
                      activity.getWindow().getDecorView(),
                      "Wired / USB / Bluetooth monitoring")),
          "Collapsed voice settings still occupied Control");
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
      shell("pm grant ph.kanta.reborn android.permission.RECORD_AUDIO");
      runOnMainSync(() -> {
        YouTubeEmbeddedPlayer online = openNativePreviewFixture(
            YouTubeVideoReference.parse("https://youtu.be/abcdefghijk"));
        activity.toggleMicrophone();
        check(online.released() && !session.onlineAudioActive(),
            "Microphone focus acquisition retained online audio ownership");
      });
      await(
          () -> session.microphone.capturing() && session.microphone.reading() != null,
          "Opt-in AudioRecord did not produce input frames: " + session.message);
      runOnMainSync(() -> {
        activity.showYouTubeVideo(YouTubeVideoReference.parse("https://youtu.be/abcdefghijk"));
        check(field(activity, "youtubePlayer") == null && !session.onlineAudioActive()
                && session.microphone.capturing(),
            "Online opening interrupted active microphone input");
      });
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
      runOnMainSync(activity::toggleMonitoring);
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
      runOnMainSync(activity::toggleMicrophone);
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
      youtubeAudioCoordinationChecks();
      runOnMainSync(session::next);
      await(
          () -> session.playing && session.current != null,
          "Native MIDI player did not start: " + session.message);
      check(
          state(() -> session.queue.snapshot().size() == 1 && session.singer.equals("First")),
          "Start acknowledgement violated FIFO");
      await(() -> session.position() >= 300, "AudioTrack audible clock did not advance");
      runOnMainSync(() -> session.setScoring(true));
      await(session::scoreReady, "Scoring target did not prepare alongside real MIDI playback");
      check(
          state(() -> !session.microphone.enabled() && !session.monitor.enabled()),
          "Scoring opt-in implicitly enabled microphone or monitored output");
      AtomicReference<MelodyScoreSession> priorScore = new AtomicReference<>();
      runOnMainSync(() -> priorScore.set(session.score));
      runOnMainSync(() -> session.scoreLatency(100));
      await(
          () -> session.scoreReady() && session.score != priorScore.get(),
          "Latency change retained the old scoring accumulator");
      check(
          state(() -> session.score.estimate().matchedSlots == 0),
          "Latency change retained observations");
      runOnMainSync(() -> session.scoreLatency(Integer.MAX_VALUE));
      check(state(() -> session.scoreLatency == 1500), "Positive calibration bound failed");
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
      check(
          state(
              () ->
                  contains(
                      activity.getWindow().getDecorView(),
                      "Not enough stable voice input for a score")),
          "Last-song UI displayed a fabricated score for silence");
      runOnMainSync(
          () -> {
            session.stopMicrophone();
            session.setScoring(false);
            session.chooseIntermission(loopSong);
            session.toggleIntermission();
          });
      await(() -> session.party.loopPlaying(), "Idle intermission did not start");
      runOnMainSync(() -> {
        YouTubeEmbeddedPlayer online = openNativePreviewFixture(
            YouTubeVideoReference.parse("https://youtu.be/abcdefghijk"));
        check(!session.party.loopPlaying() && !session.loopEnabled && session.onlineAudioActive(),
            "Online opening retained intermission audio");
        session.publish();
        check(!online.released() && !session.party.loopPlaying(),
            "Automatic publication restarted native audio during online preview");
        session.toggleIntermission();
        check(online.released() && !session.onlineAudioActive(),
            "Explicit intermission action retained online player");
      });
      await(() -> session.party.loopPlaying(), "Explicit intermission restart did not play");
      runOnMainSync(() -> session.assignPad(6, loopSong));
      await(() -> !session.assigningPad, "Custom pad decode never acknowledged");
      check(
          state(() -> session.customPads[6].equals(loopSong.id)),
          "Custom pad preference did not commit");
      runOnMainSync(() -> session.resetPad(6));
      check(state(() -> session.customPads[6].isEmpty()), "Custom pad reset failed");
      // Missing private data must leave the reservation recoverable.
      Song missing =
          new Song("missing-runtime-fixture", "Missing fixture", "", "999", "missing.mid", false);
      runOnMainSync(
          () -> {
            session.reserve(missing, "Retry");
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

  private void workspaceBackNavigationChecks() {
    KantaWorkspace workspace = (KantaWorkspace) field(activity, "workspace");
    runOnMainSync(
        () -> {
          View search =
              activity.getWindow().getDecorView().findViewWithTag("kanta.workspace.search");
          check(search != null, "Workspace Search action missing");
          search.performClick();
        });
    await(
        () -> workspace.activeAccessory() != null && !workspace.isStageOnly(),
        "Workspace Search did not open an accessory");
    runOnMainSync(() -> activity.getOnBackPressedDispatcher().onBackPressed());
    await(workspace::isStageOnly, "Predictive back did not dismiss the workspace accessory");
    check(
        state(
            () -> {
              View stage =
                  activity.getWindow().getDecorView().findViewWithTag("kanta.stage.primary");
              androidx.activity.OnBackPressedCallback callback =
                  (androidx.activity.OnBackPressedCallback) field(activity, "workspaceBackCallback");
              return stage != null && !callback.isEnabled();
            }),
        "Back dismissal detached the persistent stage or left the callback enabled");
  }

  private Object field(Object target, String name) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return field.get(target);
    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
  }

  private void youtubeLifecycleChecks() {
    // Only native lifetime is asserted; remote responses and advertisements are not fixtures.
    runOnMainSync(() -> {
      session.reserve(session.songs.get(0), "Synthetic online lifecycle A");
      session.reserve(session.songs.get(0), "Synthetic online lifecycle B");
      java.util.List<ReservationQueue.Entry> before = session.queue.snapshot();
      check(before.size() == 2 && !before.get(0).id.equals(before.get(1).id),
          "Lifecycle fixture did not create distinct local reservations");
      boolean playing = session.playing;
      Song current = session.current;
      YouTubeVideoReference a = YouTubeVideoReference.parse("https://youtu.be/abcdefghijk");
      YouTubeVideoReference b = YouTubeVideoReference.parse("https://youtu.be/12345678901");
      activity.showYouTubeVideo(a);
      YouTubeEmbeddedPlayer first = (YouTubeEmbeddedPlayer) field(activity, "youtubePlayer");
      android.webkit.WebViewClient stale = first.getWebViewClient();
      activity.showYouTubeVideo(b);
      YouTubeEmbeddedPlayer second = (YouTubeEmbeddedPlayer) field(activity, "youtubePlayer");
      check(first.released() && first.getParent() == null, "Replacement mounted before prior online player retired");
      check(second != first && !second.released(), "Replacement did not create a live native player");
      stale.onRenderProcessGone(first, null);
      check(field(activity, "youtubePlayer") == second && !second.released(), "Stale renderer callback retired replacement");
      activity.showYouTubeVideo(b);
      YouTubeEmbeddedPlayer repeated = (YouTubeEmbeddedPlayer) field(activity, "youtubePlayer");
      check(second.released() && repeated != second, "Repeated link reused a native player");
      // Exercise the real activity pause hook without depending on remote playback.
      callActivityOnPause(activity);
      check(repeated.released() && repeated.getParent() == null, "Activity pause retained online playback");
      activity.showYouTubeVideo(a);
      check(field(activity, "youtubePlayer") == null, "Late callback reopened online video while inactive");
      callActivityOnResume(activity);
      check(field(activity, "youtubePlayer") == null, "Foreground return automatically reopened online video");
      activity.showYouTubeVideo(a);
      YouTubeEmbeddedPlayer reopened = (YouTubeEmbeddedPlayer) field(activity, "youtubePlayer");
      check(reopened != repeated && !reopened.released(), "Explicit reopen failed");
      android.webkit.WebViewClient failedClient = reopened.getWebViewClient();
      failedClient.onRenderProcessGone(reopened, null);
      check(reopened.released(), "Renderer failure did not retire online player");
      failedClient.onRenderProcessGone(reopened, null);
      reopened.release();
      reopened.show(b);
      check(reopened.released(), "Released WebView accepted a late show");
      check(session.current == current && session.playing == playing, "Online lifetime changed local transport");
      java.util.List<ReservationQueue.Entry> after = session.queue.snapshot();
      check(before.size() == after.size(), "Online lifetime consumed local FIFO");
      for (int i = 0; i < before.size(); i++)
        check(before.get(i).id.equals(after.get(i).id), "Online lifetime changed local reservation identity");
      for (ReservationQueue.Entry entry : before) session.remove(entry.id);
    });
    // Drain Dialog's asynchronous dismissal notification before the rest of the suite.
    waitForIdleSync();
    check(state(() -> field(activity, "youtubePlayer") == null), "Dismissed online player remained owned");
  }

  private void youtubeAudioCoordinationChecks() {
    runOnMainSync(() -> {
      java.util.List<ReservationQueue.Entry> before = session.queue.snapshot();
      Song current = session.current;
      session.triggerPad(0);
      check(session.party.activeVoices() > 0, "Native effect fixture did not start");
      YouTubeVideoReference reference = YouTubeVideoReference.parse("https://youtu.be/abcdefghijk");
      YouTubeEmbeddedPlayer first = openNativePreviewFixture(reference);
      YouTubePreviewLifecycle lifecycle = (YouTubePreviewLifecycle) field(activity, "youtubeLifecycle");
      java.util.UUID prior = lifecycle.active().id;
      check(session.onlineAudioActive() && session.party.activeVoices() == 0,
          "Online ownership did not stop native effects");
      YouTubeEmbeddedPlayer replacement = openNativePreviewFixture(reference);
      session.releaseOnlineAudio(prior);
      check(first.released() && session.onlineAudioActive() && !replacement.released(),
          "Stale online release cleared replacement audio ownership");
      session.publish();
      check(!replacement.released(), "Idle publication revoked online ownership");
      session.triggerPad(0);
      check(replacement.released() && !session.onlineAudioActive() && session.party.activeVoices() > 0,
          "Explicit effect did not retire online player before native output");
      session.stopParty();
      YouTubeEmbeddedPlayer backgrounded = openNativePreviewFixture(reference);
      session.hostVisible(false);
      check(backgrounded.released() && !session.onlineAudioActive(),
          "Host foreground loss retained online audio ownership");
      session.hostVisible(true);
      check(!session.onlineAudioActive() && field(activity, "youtubePlayer") == null,
          "Foreground return restored online audio without opt-in");
      check(session.current == current && !session.playing, "Online audio handoff changed local transport");
      java.util.List<ReservationQueue.Entry> after = session.queue.snapshot();
      check(after.size() == before.size(), "Audio ownership consumed reservations");
      for (int i = 0; i < before.size(); i++)
        check(before.get(i).id.equals(after.get(i).id), "Audio ownership changed reservation identity");
    });
    waitForIdleSync();
  }

  private YouTubeEmbeddedPlayer openNativePreviewFixture(YouTubeVideoReference reference) {
    activity.showYouTubeVideo(reference);
    YouTubeEmbeddedPlayer player = (YouTubeEmbeddedPlayer) field(activity, "youtubePlayer");
    check(player != null, "Native online preview did not open");
    // Exercise native ownership without relying on remote responses or playable video.
    player.setWebViewClient(new android.webkit.WebViewClient());
    player.getSettings().setBlockNetworkLoads(true);
    player.stopLoading();
    player.loadData("<html><body>Native lifecycle fixture</body></html>", "text/html", "UTF-8");
    return player;
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
        CompanionProtocol.Request search = new CompanionProtocol.Request(java.util.UUID.randomUUID(), "search", "Morning", "");
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
        YouTubeVideoReference online = YouTubeVideoReference.parse("https://youtu.be/abcdefghijk");
        AtomicReference<YouTubeEmbeddedPlayer> onlinePlayer = new AtomicReference<>();
        runOnMainSync(() -> {
          onlinePlayer.set(openNativePreviewFixture(online));
        });
        companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "reserve", songId, "Remote playback"));
        check(state(() -> field(activity, "youtubePlayer") == onlinePlayer.get()),
            "Reservation alone retired online preview");
        companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "play", "", ""));
        await(() -> session.playing && !session.loading, "Remote local playback did not start");
        check(state(() -> onlinePlayer.get().released() && field(activity, "youtubePlayer") == null),
            "Remote asynchronous local start retained online preview");
        check(state(() -> session.current != null && session.current.id.equals(songId) && session.queue.snapshot().isEmpty()),
            "Online retirement changed remote local playback or FIFO");
        companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "pause", "", ""));
        await(() -> !session.playing, "Remote pause did not settle");
        runOnMainSync(() -> {
          onlinePlayer.set(openNativePreviewFixture(online));
        });
        companionRequest(channel, new CompanionProtocol.Request(java.util.UUID.randomUUID(), "play", "", ""));
        await(() -> session.playing, "Remote local resume did not start");
        check(state(() -> onlinePlayer.get().released() && field(activity, "youtubePlayer") == null),
            "Remote local resume retained online preview");
        runOnMainSync(session::stop);
        check(state(() -> field(activity, "youtubePlayer") == null),
            "Stopping local playback reopened retired online preview");
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
    CompanionProtocol.Request search=new CompanionProtocol.Request(java.util.UUID.randomUUID(),"search","Morning","");
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

  private void capture(String name) throws IOException {
    try {
      getUiAutomation().waitForIdle(250, 5000);
    } catch (java.util.concurrent.TimeoutException timeout) {
      throw new IOException("UI did not settle before screenshot: " + name, timeout);
    }
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
