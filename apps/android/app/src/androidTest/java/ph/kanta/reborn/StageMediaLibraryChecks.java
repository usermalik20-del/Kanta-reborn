package ph.kanta.reborn;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Isolated v1 upgrade, multi-copy, cancel, thumbnail and removal checks for RuntimeChecks. */
final class StageMediaLibraryChecks {
  private StageMediaLibraryChecks() {}

  static void run(Context app) throws Exception {
    File sandbox = Files.createTempDirectory(app.getCacheDir().toPath(), "stage-library-test").toFile();
    Context context = new ContextWrapper(app) {
      @Override public File getFilesDir() { return sandbox; }
    };
    try {
      StageMediaStore store = new StageMediaStore(context);
      File root = store.getRoot();
      File selection = new File(root, "selection.json");
      String photo = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.photo";
      String retained = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.video";
      String bundled = BundledBgvCatalog.defaultClip().id + ".video";
      String orphan = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc.photo";
      byte[] marker = {7, 3, 2};
      for (String name : Arrays.asList(photo, retained, bundled, orphan)) {
        Files.write(new File(root, name).toPath(), marker);
      }
      byte[] v1 = new JSONObject().put("version", 1).put("kind", "video")
          .put("photo", photo).put("video", bundled).put("importedVideo", retained)
          .toString().getBytes(StandardCharsets.UTF_8);
      // An interrupted legacy write can leave only the backup file.
      Files.write(new File(selection + ".bak").toPath(), v1);
      StageMediaStore.Selection migrated = store.load();
      check("video".equals(migrated.kind) && bundled.equals(migrated.video.getName())
          && photo.equals(migrated.photo.getName()) && retained.equals(migrated.importedVideo.getName()),
          "v1 selected and retained visuals changed");
      check(migrated.imports.size() == 2 && migrated.imports.get(0).displayName.isEmpty(),
          "v1 should retain its two named references only, with unknown display names");
      check(store.hasSavedSelection(), "Legacy backup recovery did not restore a saved selection");

      File sourceA = new File(sandbox, "My first photo.png");
      File sourceB = new File(sandbox, "Blue night.png");
      writePhoto(sourceA, android.graphics.Color.CYAN);
      writePhoto(sourceB, android.graphics.Color.MAGENTA);
      byte[] original = Files.readAllBytes(sourceA.toPath());
      Uri a = Uri.fromFile(sourceA), b = Uri.fromFile(sourceB);
      StageMediaStore.ImportResult result = store.importBatch(Arrays.asList(a,
          FixtureProvider.file("bad"), b, a), false, () -> false);
      check(result.added == 2 && result.duplicates == 1 && result.skipped == 1 && !result.cancelled,
          "Mixed batch did not keep both valid files, handle duplicate, or skip invalid file");
      check(bundled.equals(store.selection().video.getName()) && "video".equals(store.selection().kind),
          "Batch import changed the selected background instead of waiting for Use");
      AtomicBoolean stopped = new AtomicBoolean(true);
      StageMediaStore.ImportResult cancelled = store.importBatch(Collections.singletonList(b), false, stopped::get);
      check(cancelled.cancelled && cancelled.added == 0 && cancelled.duplicates == 0,
          "Cancelled batch altered the existing inventory");

      store = new StageMediaStore(context);
      StageMediaStore.Selection after = store.load();
      check(after.imports.size() == 4 && retained.equals(after.importedVideo.getName()),
          "All imported rows and the prior v1 background did not survive reopen");
      StageMediaStore.Entry first = named(after, sourceA.getName());
      StageMediaStore.Entry second = named(after, sourceB.getName());
      check(Arrays.equals(original, Files.readAllBytes(first.file.toPath())), "Copy changed photo bytes");
      Bitmap thumbnail = StageMediaStore.thumbnail(first);
      try {
        check(thumbnail != null && Math.max(thumbnail.getWidth(), thumbnail.getHeight()) <= 160,
            "Photo thumbnail exceeded its bound");
      } finally {
        if (thumbnail != null) thumbnail.recycle();
      }
      StageMediaStore.ImportResult videos = store.importBatch(
          Collections.singletonList(FixtureProvider.file("video")), true, () -> false);
      check(videos.added == 1, "Content-provider video was not imported");
      StageMediaStore.Entry video = named(store.selection(), "Original video.mp4");
      check(video.video, "Content-provider display name or type was lost");
      store.selectImport(video.file.getName(), true);
      check(store.removeImport(retained, true), "Inactive video could not be removed beside an active video");
      store.selectImport(first.file.getName(), false);
      boolean protectedActive = false;
      try {
        store.removeImport(first.file.getName(), false);
      } catch (IOException expected) {
        protectedActive = true;
      }
      check(protectedActive && first.file.isFile(), "Removing the exact active visual was allowed");
      check(store.removeImport(second.file.getName(), false) && !second.file.isFile(),
          "Inactive same-kind copy could not be removed independently");
      after = new StageMediaStore(context).load();
      check("photo".equals(after.kind) && first.file.equals(after.photo) && sourceB.isFile()
          && Arrays.equals(original, Files.readAllBytes(sourceA.toPath())),
          "Use did not survive reopen, or removal touched the original files");

      // An unknown schema must not be silently replaced by a later import.
      byte[] unknown = "{\"version\":99}".getBytes(StandardCharsets.UTF_8);
      Files.write(selection.toPath(), unknown);
      StageMediaStore unavailable = new StageMediaStore(context);
      try {
        unavailable.load();
        throw new AssertionError("Unrecognized visual settings loaded");
      } catch (IOException expected) { /* Unknown version. */ }
      try {
        unavailable.importBatch(Collections.singletonList(a), false, () -> false);
        throw new AssertionError("Import overwrote an unknown settings version");
      } catch (IOException expected) { /* Read failure fences writes. */ }
      check(Arrays.equals(unknown, Files.readAllBytes(selection.toPath())),
          "Unknown visual settings were overwritten");
    } finally {
      try (java.util.stream.Stream<java.nio.file.Path> tree = Files.walk(sandbox.toPath())) {
        for (java.nio.file.Path path : (Iterable<java.nio.file.Path>)
            tree.sorted(java.util.Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
      }
    }
  }

  private static StageMediaStore.Entry named(StageMediaStore.Selection selection, String name) {
    for (StageMediaStore.Entry entry : selection.imports) {
      if (name.equals(entry.displayName)) return entry;
    }
    throw new AssertionError("Missing saved visual name " + name);
  }

  private static void writePhoto(File file, int color) throws IOException {
    Bitmap image = Bitmap.createBitmap(400, 320, Bitmap.Config.ARGB_8888);
    try (FileOutputStream out = new FileOutputStream(file)) {
      image.eraseColor(color);
      check(image.compress(Bitmap.CompressFormat.PNG, 100, out), "Failed to write fixture photo");
    } finally { image.recycle(); }
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
