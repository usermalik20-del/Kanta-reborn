package ph.kanta.reborn.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ph.kanta.reborn.ui.components.KantaIconButton;

public class KantaQueueScreen extends LinearLayout {
  public interface Listener {
    void onBack();
    void onClear();
    void onAddSongs();
    void onRemove(int position);
    void onItemClicked(int position);
  }
  private final Context context;
  private final float density;
  private final Listener listener;
  private LinearLayout itemsContainer;
  private TextView headerCount;
  public KantaQueueScreen(Context context, Listener listener) {
    super(context);
    this.context = context;
    this.density = context.getResources().getDisplayMetrics().density;
    this.listener = listener;
    setOrientation(VERTICAL);
    setBackgroundColor(ph.kanta.reborn.ui.KantaTheme.BG_MAIN);
    build();
  }
  private void build() {
    LinearLayout top = new LinearLayout(context);
    top.setOrientation(HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(8*density));
    KantaIconButton back = new KantaIconButton(context);
    back.setText("\u2190"); back.setTextSize(18); back.setTag("kanta.screen.queue.back");
    back.setOnClickListener(v -> { if (listener != null) listener.onBack(); });
    headerCount = new TextView(context);
    headerCount.setText("Queue (0)");
    headerCount.setTextSize(18);
    headerCount.setTypeface(null, Typeface.BOLD);
    headerCount.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    cp.weight = 1;
    headerCount.setLayoutParams(cp);
    Button clear = new Button(context);
    clear.setText("Clear");
    clear.setTextSize(12);
    clear.setTextColor(ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN);
    clear.setTypeface(null, Typeface.BOLD);
    clear.setBackground(null);
    clear.setPadding(0, 0, 0, 0);
    clear.setOnClickListener(v -> { if (listener != null) listener.onClear(); });
    top.addView(back); top.addView(headerCount); top.addView(clear);
    addView(top);
    itemsContainer = new LinearLayout(context);
    itemsContainer.setOrientation(VERTICAL);
    itemsContainer.setPadding((int)(16*density), 0, (int)(16*density), 0);
    LinearLayout.LayoutParams icParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0);
    icParams.weight = 1;
    addView(itemsContainer, icParams);
  }
  public void setCount(int count) {
    if (headerCount != null) headerCount.setText("Queue (" + count + ")");
  }
  public void setItems(String[][] items) {
    itemsContainer.removeAllViews();
    if (items == null || items.length == 0) {
      TextView empty = new TextView(context);
      empty.setText("Queue is empty. Reserve a song to begin.");
      empty.setTextSize(15);
      empty.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
      empty.setGravity(Gravity.CENTER);
      empty.setPadding(0, (int)(40*density), 0, 0);
      itemsContainer.addView(empty);
      return;
    }
    for (int i = 0; i < items.length; i++) {
      String[] item = items[i];
      String position = item[0];
      String title = item[1];
      String artist = item[2];
      boolean isPlaying = item.length > 3 && Boolean.parseBoolean(item[3]);
      String artworkColor = item.length > 4 ? item[4] : "#203459";
      LinearLayout row = new LinearLayout(context);
      row.setOrientation(HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding((int)(12*density),(int)(8*density),(int)(12*density),(int)(8*density));
      row.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
          isPlaying ? ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN : ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED,
          12, ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
      row.setClickable(true);
      final int itemIdx = i;
      row.setOnClickListener(v -> {
        if (listener != null) listener.onItemClicked(itemIdx);
      });
      TextView pos = new TextView(context);
      pos.setText(position);
      pos.setTextSize(13);
      pos.setTypeface(null, Typeface.BOLD);
      pos.setTextColor(isPlaying ? ph.kanta.reborn.ui.KantaTheme.BG_MAIN : ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
      pos.setPadding((int)(8*density), 0, (int)(8*density), 0);
      row.addView(pos);
      LinearLayout info = new LinearLayout(context);
      ImageView artwork = new ImageView(context);
      artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
      int artworkResource = 0;
      if (item.length > 5) {
        try { artworkResource = Integer.parseInt(item[5]); } catch (NumberFormatException ignored) {}
      }
      if (artworkResource != 0) artwork.setImageResource(artworkResource);
      artwork.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
          ph.kanta.reborn.ui.KantaTheme.SURFACE, 9, ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
      artwork.setClipToOutline(true);
      artwork.setContentDescription("Artwork for " + title);
      LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams((int)(54*density), (int)(54*density));
      artParams.leftMargin = (int)(8*density);
      row.addView(artwork, artParams);
      info.setOrientation(VERTICAL);
      TextView titleView = new TextView(context);
      titleView.setText(title);
      titleView.setTextSize(15);
      titleView.setTypeface(null, Typeface.BOLD);
      titleView.setTextColor(isPlaying ? ph.kanta.reborn.ui.KantaTheme.BG_MAIN : ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
      TextView artistView = new TextView(context);
      artistView.setText(artist);
      artistView.setTextSize(13);
      artistView.setTextColor(isPlaying ? ph.kanta.reborn.ui.KantaTheme.BG_MAIN : ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
      info.addView(titleView); info.addView(artistView);
      LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
      infoParams.leftMargin = (int)(12*density);
      row.addView(info, infoParams);
      Button overflow = new Button(context);
      overflow.setText("\u22EE");
      overflow.setTextSize(16);
      overflow.setTextColor(isPlaying ? ph.kanta.reborn.ui.KantaTheme.BG_MAIN : ph.kanta.reborn.ui.KantaTheme.TEXT_MUTED);
      overflow.setBackground(null);
      overflow.setPadding(0, 0, 0, 0);
      final int idx = i;
      overflow.setOnClickListener(v -> {
        if (listener != null) listener.onRemove(idx);
      });
      row.addView(overflow);
      itemsContainer.addView(row);
    }
  }
  public void showAddSongsCTA() {
    Button add = new Button(context);
    add.setText("+ Add Songs");
    add.setTextSize(16);
    add.setTypeface(null, Typeface.BOLD);
    add.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    add.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED, 20,
        ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density));
    add.setPadding(0, (int)(18*density), 0, (int)(18*density));
    add.setOnClickListener(v -> { if (listener != null) listener.onAddSongs(); });
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    params.leftMargin = (int)(32*density); params.rightMargin = (int)(32*density);
    params.topMargin = (int)(16*density); params.bottomMargin = (int)(24*density);
    addView(add, params);
  }
}
