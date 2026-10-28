package ph.kanta.reborn.stage;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ph.kanta.reborn.core.ReservationQueue;
import ph.kanta.reborn.core.Song;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only projection of the ReservationQueue.
 *
 * <p>Per {@link replica/design/components.md} QueueOverlay spec.
 * Shows position, title, reserver. Current playing highlighted.
 */
public final class QueueOverlay extends FrameLayout {

  private static final int ANIM_DURATION_MS = 250;
  private static final int SHEET_HEIGHT_PERCENT = 60;

  private final RecyclerView queueList;
  private final TextView emptyView;
  private final QueueAdapter adapter;

  private ReservationQueue queue;
  private QueueListener listener;
  private boolean isVisible = false;

  public QueueOverlay(Context context) {
    this(context, null);
  }

  public QueueOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);
    setClipChildren(false);
    setClipToPadding(false);

    float density = context.getResources().getDisplayMetrics().density;

    LinearLayout container = new LinearLayout(context);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setBackgroundColor(0xFF1B1030); // surface
    container.setPadding((int)(20 * density), (int)(20 * density), (int)(20 * density), (int)(20 * density));
    LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    params.gravity = android.view.Gravity.BOTTOM;
    addView(container, params);

    // Title
    TextView titleView = new TextView(context);
    titleView.setText("Up Next");
    titleView.setTextSize(19);
    titleView.setTextColor(0xFFFFFFFF);
    titleView.setTypeface(null, android.graphics.Typeface.BOLD);
    titleView.setPadding(0, 0, 0, (int)(16 * density));
    container.addView(titleView);

    // Queue list
    queueList = new RecyclerView(context);
    queueList.setLayoutManager(new LinearLayoutManager(context));
    adapter = new QueueAdapter();
    queueList.setAdapter(adapter);
    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0);
    listParams.weight = 1;
    container.addView(queueList, listParams);

    // Empty view
    emptyView = new TextView(context);
    emptyView.setText("Queue is empty");
    emptyView.setTextColor(0xFF999999);
    emptyView.setGravity(android.view.Gravity.CENTER);
    emptyView.setVisibility(GONE);
    LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0);
    emptyParams.weight = 1;
    container.addView(emptyView, emptyParams);
  }

  /** Sets the queue. */
  public void setQueue(ReservationQueue queue) {
    this.queue = queue;
    update();
  }

  /** Sets the listener. */
  public void setListener(QueueListener listener) {
    this.listener = listener;
  }

  /** Shows the overlay. */
  public void show() {
    if (isVisible) return;
    isVisible = true;
    setTranslationY(getHeight());
    setVisibility(VISIBLE);
    animate().translationY(0).setDuration(ANIM_DURATION_MS).start();
  }

  /** Hides the overlay. */
  public void hide() {
    if (!isVisible) return;
    isVisible = false;
    animate().translationY(getHeight()).setDuration(ANIM_DURATION_MS)
        .withEndAction(() -> setVisibility(GONE))
        .start();
  }

  /** Updates the queue display. */
  public void update() {
    if (queue == null) return;
    List<ReservationQueue.Entry> entries = queue.snapshot();
    adapter.setEntries(entries);
    if (entries.isEmpty()) {
      emptyView.setVisibility(VISIBLE);
      queueList.setVisibility(GONE);
    } else {
      emptyView.setVisibility(GONE);
      queueList.setVisibility(VISIBLE);
    }
  }

  private final class QueueAdapter extends RecyclerView.Adapter<QueueViewHolder> {
    private List<ReservationQueue.Entry> entries = new ArrayList<>();

    void setEntries(List<ReservationQueue.Entry> entries) {
      this.entries = entries != null ? entries : new ArrayList<>();
      notifyDataSetChanged();
    }

    @Override
    public QueueViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
      View view = new QueueItemView(getContext());
      return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(QueueViewHolder holder, int position) {
      holder.bind(entries.get(position), position == 0);
    }

    @Override
    public int getItemCount() {
      return entries.size();
    }
  }

  private final class QueueViewHolder extends RecyclerView.ViewHolder {
    private final QueueItemView itemView;

    QueueViewHolder(View view) {
      super(view);
      this.itemView = (QueueItemView) view;
    }

    void bind(ReservationQueue.Entry entry, boolean isCurrent) {
      itemView.bind(entry, isCurrent, (v) -> {
        if (listener != null) listener.onRemove(entry.id);
      });
    }
  }

  private static final class QueueItemView extends LinearLayout {
    private final TextView positionBadge;
    private final TextView titleView;
    private final TextView reserverView;
    private final TextView removeButton;

    QueueItemView(Context context) {
      super(context);
      setOrientation(HORIZONTAL);
      setPadding((int)(16 * getResources().getDisplayMetrics().density),
          (int)(12 * getResources().getDisplayMetrics().density),
          (int)(16 * getResources().getDisplayMetrics().density),
          (int)(12 * getResources().getDisplayMetrics().density));
      setGravity(android.view.Gravity.CENTER_VERTICAL);
      setMinimumHeight((int)(56 * getResources().getDisplayMetrics().density));

      float density = getResources().getDisplayMetrics().density;

      // Position badge
      positionBadge = new TextView(context);
      positionBadge.setBackgroundResource(ph.kanta.reborn.R.drawable.karaoke_badge_bg);
      positionBadge.setTextColor(0xFFFFFFFF);
      positionBadge.setTextSize(12);
      positionBadge.setTypeface(null, android.graphics.Typeface.BOLD);
      positionBadge.setGravity(android.view.Gravity.CENTER);
      positionBadge.setPadding((int)(8 * density), (int)(4 * density), (int)(8 * density), (int)(4 * density));
      LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      badgeParams.rightMargin = (int)(16 * density);
      addView(positionBadge, badgeParams);

      // Title and reserver
      LinearLayout info = new LinearLayout(context);
      info.setOrientation(VERTICAL);
      info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

      titleView = new TextView(context);
      titleView.setTextSize(15);
      titleView.setTextColor(0xFFFFFFFF);
      titleView.setTypeface(null, android.graphics.Typeface.BOLD);

      reserverView = new TextView(context);
      reserverView.setTextSize(13);
      reserverView.setTextColor(0xFFCCCCCC);

      info.addView(titleView);
      info.addView(reserverView);
      addView(info);

      // Remove button
      removeButton = new TextView(context);
      removeButton.setText("✕");
      removeButton.setTextSize(18);
      removeButton.setTextColor(0xFFFF5A6E); // destructive
      removeButton.setGravity(android.view.Gravity.CENTER);
      removeButton.setPadding((int)(12 * density), 0, 0, 0);
      removeButton.setOnClickListener(v -> {});
      addView(removeButton);
    }

    void bind(ReservationQueue.Entry entry, boolean isCurrent, OnClickListener removeListener) {
      positionBadge.setText(String.valueOf(getAdapterPosition() + 1));
      titleView.setText(entry.song.title);
      reserverView.setText(entry.singer.isEmpty() ? "You" : entry.singer);

      if (isCurrent) {
        setBackgroundColor(0xFF2A1A47); // elevated
        // Add accent left bar would need custom drawable
      } else {
        setBackgroundColor(0x00000000); // transparent
      }

      // Show remove only for own reservations (simplified: always show)
      removeButton.setVisibility(VISIBLE);
      removeButton.setOnClickListener(removeListener);
    }
  }

  /** Queue listener interface. */
  public interface QueueListener {
    void onRemove(UUID reservationId);
  }
}
