package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KantaMediaRow extends LinearLayout {
  private final float density;
  public KantaMediaRow(Context context) { this(context, null); }
  public KantaMediaRow(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    this.density = context.getResources().getDisplayMetrics().density;
    setOrientation(VERTICAL);
    setGravity(Gravity.START);
    setPadding(0, (int)(8*density), 0, (int)(8*density));
  }
  public void bind(String artworkColor, String title, String artist, Runnable onSing) {
    removeAllViews();
    KantaSongArtwork artwork = new KantaSongArtwork(getContext());
    artwork.setColor(parseColor(artworkColor));
    artwork.setRadiusDp(10);
    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams((int)(56*density), (int)(56*density));
    addView(artwork, ap);
    TextView titleView = new TextView(getContext());
    titleView.setText(title);
    titleView.setTextSize(15);
    titleView.setTypeface(null, Typeface.BOLD);
    titleView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    titleView.setSingleLine(true);
    titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    tp.topMargin = (int)(2*density);
    addView(titleView, tp);
    TextView artistView = new TextView(getContext());
    artistView.setText(artist);
    artistView.setTextSize(13);
    artistView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    artistView.setSingleLine(true);
    artistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    addView(artistView);
  }
  private int parseColor(String hex) {
    try { return android.graphics.Color.parseColor(hex); } catch (Exception e) {
      return ph.kanta.reborn.ui.KantaTheme.BORDER;
    }
  }
}
