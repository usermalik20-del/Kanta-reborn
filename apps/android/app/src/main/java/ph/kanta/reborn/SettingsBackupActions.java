package ph.kanta.reborn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.Lifecycle;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.kanta.reborn.core.PlaybackSettingsBackup;

/** User-initiated document operations. KANTA never gets account access or keeps a URI grant. */
final class SettingsBackupActions {
  private final ComponentActivity host;
  private final SessionController session;
  private final ActivityResultLauncher<Intent> savePicker, restorePicker;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private AlertDialog dialog;
  private boolean busy, closed;

  SettingsBackupActions(ComponentActivity host, SessionController session) {
    this.host = host;
    this.session = session;
    savePicker = host.getActivityResultRegistry().register("kanta.settings.backup.save", host,
        new ActivityResultContracts.StartActivityForResult(), this::saveResult);
    restorePicker = host.getActivityResultRegistry().register("kanta.settings.backup.restore", host,
        new ActivityResultContracts.StartActivityForResult(), this::restoreResult);
  }

  void show() {
    if (!visible() || busy || (dialog != null && dialog.isShowing())) return;
    LinearLayout body = MenuDialogStyle.body(host);
    LinearLayout card = MenuDialogStyle.card(body, null);
    card.addView(MenuDialogStyle.label(host, host.getString(R.string.settings_backup_details)));
    MenuDialogStyle.addControl(card, MenuDialogStyle.button(host,
        host.getString(R.string.settings_backup_save), R.drawable.kanta_submenu_upload, true, () -> selectBackupAction(true)));
    MenuDialogStyle.addControl(card, MenuDialogStyle.button(host,
        host.getString(R.string.settings_backup_restore), R.drawable.kanta_submenu_download, false, () -> selectBackupAction(false)));
    dialog = MenuDialogStyle.dialog(host, R.string.settings_backup_title, R.drawable.kanta_menu_cloud_upload, body)
        .setNegativeButton(android.R.string.cancel, null)
        .show();
    MenuDialogStyle.styleShownDialog(dialog);
  }

  private void selectBackupAction(boolean saving) {
    if (dialog != null) dialog.dismiss();
    pick(saving);
  }

  private void pick(boolean saving) {
    if (busy || !visible()) return;
    if (!session.canRestorePlaybackSettings()) {
      notice(R.string.settings_backup_stop_song);
      return;
    }
    Intent picker = new Intent(saving ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        // Providers can report different MIME types for the same file. Validate contents on read.
        .setType(saving ? "application/octet-stream" : "*/*");
    if (saving) picker.putExtra(Intent.EXTRA_TITLE, PlaybackSettingsBackup.FILE_NAME);
    try {
      busy = true;
      (saving ? savePicker : restorePicker).launch(picker);
    } catch (ActivityNotFoundException | SecurityException unavailable) {
      busy = false;
      notice(R.string.settings_backup_no_picker);
    }
  }

  private Uri selectedDocument(ActivityResult result, int error) {
    if (closed || result.getResultCode() != Activity.RESULT_OK) {
      busy = false;
      return null;
    }
    Intent data = result.getData();
    Uri uri = data == null ? null : data.getData();
    if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
      busy = false;
      notice(error);
      return null;
    }
    busy = true;
    return uri;
  }

  private void saveResult(ActivityResult result) {
    Uri uri = selectedDocument(result, R.string.settings_backup_save_failed);
    if (uri == null) return;
    byte[] bytes;
    try {
      bytes = session.snapshotPlaybackSettings().encode();
    } catch (IllegalArgumentException invalid) {
      busy = false;
      notice(R.string.settings_backup_save_failed);
      return;
    }
    ContentResolver resolver = host.getContentResolver();
    worker.execute(() -> {
      int status = R.string.settings_backup_saved;
      try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
        if (out == null) throw new IOException();
        out.write(bytes);
      } catch (IOException | RuntimeException unavailable) {
        status = R.string.settings_backup_save_failed;
      }
      int message = status;
      host.runOnUiThread(() -> {
        busy = false;
        notice(message);
      });
    });
  }

  private void restoreResult(ActivityResult result) {
    Uri uri = selectedDocument(result, R.string.settings_backup_read_failed);
    if (uri == null) return;
    ContentResolver resolver = host.getContentResolver();
    worker.execute(() -> {
      try {
        PlaybackSettingsBackup backup;
        try (InputStream in = resolver.openInputStream(uri)) {
          if (in == null) throw new IOException();
          backup = PlaybackSettingsBackup.read(in);
        }
        host.runOnUiThread(() -> confirmRestore(backup));
      } catch (IOException | RuntimeException unavailable) {
        int error = unavailable instanceof PlaybackSettingsBackup.InvalidBackup
            ? R.string.settings_backup_invalid : R.string.settings_backup_read_failed;
        host.runOnUiThread(() -> {
          busy = false;
          notice(error);
        });
      }
    });
  }

  private void confirmRestore(PlaybackSettingsBackup backup) {
    busy = false;
    if (!visible()) return;
    if (!session.canRestorePlaybackSettings()) {
      notice(R.string.settings_backup_stop_song);
      return;
    }
    LinearLayout body = MenuDialogStyle.body(host);
    LinearLayout card = MenuDialogStyle.card(body, null);
    card.addView(MenuDialogStyle.label(host, host.getString(R.string.settings_backup_confirm_details)));
    dialog = MenuDialogStyle.dialog(host, R.string.settings_backup_confirm_title, R.drawable.kanta_submenu_download, body)
        .setPositiveButton(R.string.settings_backup_restore, (d, which) -> {
          if (!visible()) return;
          notice(session.restorePlaybackSettings(backup)
              ? R.string.settings_backup_restored : R.string.settings_backup_stop_song);
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
    MenuDialogStyle.styleShownDialog(dialog);
  }

  private boolean visible() {
    return !closed && !host.isDestroyed() && !host.isFinishing()
        && host.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
  }

  private void notice(@androidx.annotation.StringRes int message) {
    if (visible()) Toast.makeText(host, message, Toast.LENGTH_LONG).show();
  }

  void close() {
    closed = true;
    if (dialog != null) dialog.dismiss();
    worker.shutdownNow();
  }
}
