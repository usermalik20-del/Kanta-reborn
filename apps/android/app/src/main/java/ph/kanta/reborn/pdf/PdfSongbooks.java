package ph.kanta.reborn.pdf;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import ph.kanta.reborn.R;
import ph.kanta.reborn.ui.KantaTheme;
import ph.kanta.reborn.ui.MenuSurface;

/** Activity-owned PDF destination. Provider reads and native rendering stay off the UI thread. */
public final class PdfSongbooks {
  private static final String STATE_FILE = "pdfSongbooks.file";
  private static final String STATE_PAGE = "pdfSongbooks.page";
  private static final String STATE_ZOOM = "pdfSongbooks.zoom";
  private static final String STATE_IMPORT = "pdfSongbooks.importing";
  private static final float ZOOM_STEP = 1.25f;
  private final Activity activity;
  private final int requestCode;
  private final BiConsumer<String, Throwable> diagnostics;
  private final PdfSongbookStore store;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private LinearLayout screen;
  private TextView heading;
  private java.util.function.Consumer<LinearLayout> exportSection;
  private LinearLayout content;
  private String selected;
  private int pageIndex;
  private float zoom = PdfPageBounds.FIT_WIDTH;
  private int importMessage;
  private boolean importing;
  private boolean closed;
  private int readGeneration;
  private Future<?> readTask;

  public PdfSongbooks(Activity activity, Bundle saved, int requestCode,
      BiConsumer<String, Throwable> diagnostics) {
    this.activity = activity;
    this.requestCode = requestCode;
    this.diagnostics = diagnostics;
    store = new PdfSongbookStore(new File(activity.getFilesDir(), "pdf-songbooks"));
    if (saved != null) {
      selected = saved.getString(STATE_FILE);
      pageIndex = saved.getInt(STATE_PAGE);
      zoom = PdfPageBounds.savedZoom(saved.getFloat(STATE_ZOOM, PdfPageBounds.FIT_WIDTH));
      if (saved.getBoolean(STATE_IMPORT)) importMessage = R.string.pdf_songbooks_import_interrupted;
    }
  }

  public void saveState(Bundle state) {
    state.putString(STATE_FILE, selected);
    state.putInt(STATE_PAGE, pageIndex);
    state.putFloat(STATE_ZOOM, zoom);
    state.putBoolean(STATE_IMPORT, importing);
  }

  public boolean isMountedIn(LinearLayout parent) {
    return screen != null && screen.getParent() == parent;
  }

  public void mount(LinearLayout parent, Runnable back, java.util.function.Consumer<LinearLayout> exports) {
    exportSection = exports;
    screen = column();
    screen.setTag("kanta.screen.pdf-songbooks");
    LinearLayout bar = new LinearLayout(activity);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPaddingRelative(dp(14), dp(8), dp(14), dp(8));
    bar.addView(MenuSurface.backButton(activity, activity.getString(R.string.pdf_songbooks_back),
        "kanta.pdf-songbooks.back", back), new LinearLayout.LayoutParams(dp(48), dp(48)));
    heading = MenuSurface.heading(activity, activity.getString(R.string.pdf_songbooks_title), 20, 0);
    heading.setSingleLine(true);
    heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
    titleParams.setMarginStart(dp(12));
    bar.addView(heading, titleParams);
    screen.addView(bar);
    content = column();
    screen.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    // The host ScrollView fills its viewport. Use that measured height, without screen-size guesses,
    // while still allowing a long library (or an exceptionally short window) to scroll.
    parent.addView(screen, new LinearLayout.LayoutParams(-1, 0, 1));
    // View-only reads are reissued on reattach or configuration change. There is no open renderer
    // to transfer between views, and a picker result never needs to take a persistent URI grant.
    refresh();
  }

  public void unmount() {
    cancelRead();
    screen = null;
    heading = null;
    exportSection = null;
    content = null;
  }

  public void close() {
    closed = true;
    unmount();
    worker.shutdownNow();
  }

  /** Back from a page goes to its library; Back from the library returns to the originating page. */
  public boolean backToLibrary() {
    if (screen == null || selected == null) return false;
    selected = null;
    pageIndex = 0;
    scrollTop();
    refresh();
    return true;
  }

  private void pick() {
    if (importing) return;
    try {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
          .addCategory(Intent.CATEGORY_OPENABLE)
          .setType("application/pdf")
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      activity.startActivityForResult(intent, requestCode);
    } catch (ActivityNotFoundException | SecurityException unavailable) {
      diagnostics.accept("Open PDF picker", unavailable);
      importMessage = R.string.pdf_songbooks_picker_unavailable;
      refresh();
    }
  }

  public void acceptResult(int result, Intent data) {
    if (closed || importing || removing || result != Activity.RESULT_OK) return;
    Uri uri = data == null ? null : data.getData();
    if (uri == null) {
      importMessage = R.string.pdf_songbooks_no_document;
      refresh();
      return;
    }
    importing = true;
    importMessage = 0;
    refresh();
    worker.execute(() -> {
      File saved = null;
      int error = 0;
      try {
        saved = store.importDocument(activity.getContentResolver(), uri);
      } catch (PdfSongbookStore.TooLarge tooLarge) {
        error = R.string.pdf_songbooks_too_large;
      } catch (PdfSongbookStore.Unreadable unreadable) {
        error = R.string.pdf_songbooks_unreadable;
      } catch (IOException | RuntimeException | OutOfMemoryError failed) {
        error = R.string.pdf_songbooks_import_failed;
      }
      File book = saved;
      int message = error;
      main.post(() -> {
        if (closed) return;
        importing = false;
        importMessage = message;
        if (book != null) selectBook(book);
        refresh();
      });
    });
  }

  private void refresh() {
    cancelRead();
    if (content == null) return;
    content.removeAllViews();
    File book = importing ? null : store.selected(selected);
    heading.setText(book == null ? activity.getString(R.string.pdf_songbooks_title) : book.getName());
    int padding = book == null && (importing || removing) ? dp(16) : 0;
    content.setPadding(padding, 0, padding, padding);
    if (importing) {
      note(content, R.string.pdf_songbooks_importing);
    } else if (removing) {
      note(content, importMessage);
    } else if (book == null) {
      selected = null;
      showLibrary();
      exportSection.accept(content);
    } else {
      showPage(book);
    }
  }

  private void showLibrary() {
    LinearLayout library = MenuSurface.section(content, activity.getString(R.string.pdf_songbooks_title),
        R.drawable.kanta_menu_books);
    note(library, R.string.pdf_songbooks_intro);
    Button add = button(R.string.pdf_songbooks_add, this::pick);
    MenuSurface.setLeadingIcon(add, R.drawable.kanta_submenu_file_plus);
    MenuSurface.actionStyle(add, true);
    library.addView(add);
    if (importMessage != 0) note(library, importMessage);
    LinearLayout list = column();
    library.addView(list, new LinearLayout.LayoutParams(-1, -2));
    note(list, R.string.pdf_songbooks_loading);
    int generation = readGeneration;
    readTask = worker.submit(() -> {
      File[] books;
      try {
        books = store.list();
      } catch (IOException | RuntimeException failed) {
        books = null;
      }
      File[] result = books;
      main.post(() -> {
        if (generation != readGeneration) return;
        list.removeAllViews();
        if (result == null) {
          note(list, R.string.pdf_songbooks_list_failed);
          list.addView(button(R.string.pdf_songbooks_retry, this::refresh));
        } else if (result.length == 0) {
          note(list, R.string.pdf_songbooks_empty);
        } else {
          for (File book : result) {
            Button open = KantaTheme.secondaryButton(activity, book.getName(), v -> {
              selectBook(book);
              refresh();
            });
            open.setAllCaps(false);
            MenuSurface.setLeadingIcon(open, R.drawable.kanta_submenu_document);
            MenuSurface.actionStyle(open, false);
            open.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(open, new LinearLayout.LayoutParams(0, -2, 1));
            Button remove = button(R.string.label_remove, () -> confirmRemoval(book));
            MenuSurface.setLeadingIcon(remove, R.drawable.kanta_submenu_remove);
            remove.setContentDescription(activity.getString(R.string.pdf_songbooks_remove_name,
                book.getName()));
            row.addView(remove, new LinearLayout.LayoutParams(-2, -2));
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
          }
        }
      });
    });
  }

  private boolean removing;

  private void confirmRemoval(File book) {
    if (closed || importing || removing || selected != null) return;
    new android.app.AlertDialog.Builder(activity)
        .setTitle(R.string.pdf_songbooks_remove_title)
        .setMessage(activity.getString(R.string.pdf_songbooks_remove_detail, book.getName()))
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.label_remove, (dialog, which) -> removeBook(book))
        .show();
  }

  private void removeBook(File book) {
    if (closed || importing || removing || selected != null) return;
    removing = true;
    importMessage = R.string.pdf_songbooks_removing;
    refresh();
    worker.execute(() -> {
      int result = R.string.pdf_songbooks_removed;
      try {
        store.delete(book.getName());
      } catch (IOException | RuntimeException failed) {
        diagnostics.accept("Remove private PDF copy", failed);
        result = R.string.pdf_songbooks_remove_failed;
      }
      int message = result;
      main.post(() -> {
        if (closed) return;
        removing = false;
        importMessage = message;
        refresh();
      });
    });
  }

  private void showPage(File book) {
    LinearLayout page = column();
    content.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
    note(page, R.string.pdf_songbooks_opening);
    int generation = readGeneration;
    int requested = pageIndex;
    readTask = worker.submit(() -> {
      PdfSongbookStore.RenderedPage result;
      try {
        result = PdfSongbookStore.render(book, requested);
      } catch (IOException | RuntimeException | OutOfMemoryError failed) {
        result = null;
      }
      PdfSongbookStore.RenderedPage rendered = result;
      main.post(() -> {
        if (generation != readGeneration) {
          if (rendered != null) rendered.image.recycle();
          return;
        }
        page.removeAllViews();
        if (rendered == null) {
          note(page, R.string.pdf_songbooks_page_failed);
          page.addView(button(R.string.pdf_songbooks_retry, this::refresh));
          return;
        }
        pageIndex = rendered.index;
        showRenderedPage(page, rendered);
      });
    });
  }

  private void showRenderedPage(LinearLayout page, PdfSongbookStore.RenderedPage rendered) {
    String label = activity.getString(R.string.pdf_songbooks_page, pageIndex + 1, rendered.total);
    LinearLayout controls = new LinearLayout(activity);
    controls.setGravity(Gravity.CENTER_VERTICAL);
    ImageButton previous = toolbarIcon(controls, R.drawable.kanta_menu_back,
        R.string.pdf_songbooks_previous);
    previous.setEnabled(pageIndex > 0);
    previous.setOnClickListener(v -> changePage(-1, rendered.total));
    TextView count = KantaTheme.bodyText(activity,
        activity.getString(R.string.pdf_songbooks_page_short, pageIndex + 1, rendered.total), 12);
    count.setContentDescription(label);
    count.setGravity(Gravity.CENTER);
    controls.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
    ImageButton next = toolbarIcon(controls, R.drawable.kanta_submenu_next,
        R.string.pdf_songbooks_next);
    next.setEnabled(pageIndex < rendered.total - 1);
    next.setOnClickListener(v -> changePage(1, rendered.total));
    ImageButton out = toolbarIcon(controls, R.drawable.kanta_submenu_zoom_out,
        R.string.pdf_songbooks_zoom_out);
    Button fit = toolbarButton(controls, R.string.pdf_songbooks_zoom_fit,
        R.string.pdf_songbooks_zoom_fit_page);
    ImageButton in = toolbarIcon(controls, R.drawable.kanta_submenu_zoom_in,
        R.string.pdf_songbooks_zoom_in);
    PdfPageView preview = new PdfPageView(activity, rendered.image, zoom,
        view -> {
          zoom = view.zoom();
          out.setEnabled(view.canZoomOut());
          in.setEnabled(view.canZoomIn());
        },
        delta -> changePage(delta, rendered.total));
    preview.setContentDescription(label);
    // Keep some room to read even if chrome exceeds an unusually short window.
    preview.setMinimumHeight(dp(72));
    out.setOnClickListener(v -> preview.scaleBy(1f / ZOOM_STEP));
    fit.setOnClickListener(v -> preview.setZoom(PdfPageBounds.FIT_PAGE));
    in.setOnClickListener(v -> preview.scaleBy(ZOOM_STEP));
    page.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    page.addView(controls, new LinearLayout.LayoutParams(-1, -2));
  }

  private ImageButton toolbarIcon(LinearLayout controls, int icon, int description) {
    ImageButton control = MenuSurface.iconAction(activity, icon, activity.getString(description));
    controls.addView(control, new LinearLayout.LayoutParams(0, -2, 1));
    return control;
  }

  private Button toolbarButton(LinearLayout controls, int label, int description) {
    Button control = KantaTheme.secondaryButton(activity, activity.getString(label), null);
    control.setContentDescription(activity.getString(description));
    control.setAllCaps(false);
    control.setMinWidth(0);
    control.setMinimumWidth(0);
    MenuSurface.actionStyle(control, false);
    control.setPadding(0, dp(4), 0, dp(4));
    controls.addView(control, new LinearLayout.LayoutParams(0, -2, 1));
    return control;
  }

  private void selectBook(File book) {
    selected = book.getName();
    pageIndex = 0;
    zoom = PdfPageBounds.FIT_WIDTH;
    scrollTop();
  }

  private void changePage(int delta, int pageCount) {
    int next = pageIndex + delta;
    if (next < 0 || next >= pageCount) return;
    pageIndex = next;
    refresh();
  }

  private void cancelRead() {
    readGeneration++;
    if (readTask != null) readTask.cancel(true);
    readTask = null;
  }

  private void scrollTop() {
    if (screen != null && screen.getParent() instanceof LinearLayout
        && ((LinearLayout) screen.getParent()).getParent() instanceof ScrollView) {
      ((ScrollView) ((LinearLayout) screen.getParent()).getParent()).scrollTo(0, 0);
    }
  }

  private int dp(int value) {
    return Math.round(value * activity.getResources().getDisplayMetrics().density);
  }

  private LinearLayout column() {
    LinearLayout layout = new LinearLayout(activity);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  private void note(LinearLayout parent, int message) {
    TextView text = KantaTheme.bodyText(activity, activity.getString(message), 14);
    text.setPadding(0, dp(8), 0, dp(8));
    text.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
    parent.addView(text);
  }

  private Button button(int label, Runnable action) {
    Button button = KantaTheme.secondaryButton(activity, activity.getString(label), v -> action.run());
    button.setAllCaps(false);
    MenuSurface.actionStyle(button, false);
    return button;
  }
}
