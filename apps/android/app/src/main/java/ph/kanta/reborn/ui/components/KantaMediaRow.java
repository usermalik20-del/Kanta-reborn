package ph.kanta.reborn.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Song result row with bundled cover art, metadata and a visible reservation action. */
public class KantaMediaRow extends LinearLayout {
  private final float density;

  public KantaMediaRow(Context context) { this(context, null); }

  public KantaMediaRow(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    density = context.getResources().getDisplayMetrics().density;
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setPadding(0, (int)(7 * density), 0, (int)(7 * density));
    setMinimumHeight((int)(76 * density));
  }

  public void bind(String artworkColor, String title, String artist, Runnable onSing) {
    bind(0, artworkColor, title, artist, onSing);
  }

  public void bind(int artworkResource, String title, String artist, Runnable onSing) {
    bind(artworkResource, null, title, artist, onSing);
  }

  private void bind(int artworkResource, String artworkColor, String title, String artist, Runnable onSing) {
    removeAllViews();
    ImageView artwork = new ImageView(getContext());
    artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
    GradientDrawable outline = ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        artworkColor == null ? ph.kanta.reborn.ui.KantaTheme.SURFACE_ELEVATED : parseColor(artworkColor),
        10, ph.kanta.reborn.ui.KantaTheme.BORDER, 1, density);
    artwork.setBackground(outline);
    artwork.setClipToOutline(true);
    if (artworkResource != 0) artwork.setImageResource(artworkResource);
    addView(artwork, new LayoutParams((int)(64 * density), (int)(64 * density)));

    LinearLayout metadata = new LinearLayout(getContext());
    metadata.setOrientation(VERTICAL);
    metadata.setGravity(Gravity.CENTER_VERTICAL);
    metadata.setPadding((int)(10 * density), 0, (int)(6 * density), 0);
    TextView titleView = new TextView(getContext());
    titleView.setText(title);
    titleView.setTextSize(15);
    titleView.setTypeface(null, Typeface.BOLD);
    titleView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_PRIMARY);
    titleView.setSingleLine(true);
    titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    metadata.addView(titleView);
    TextView artistView = new TextView(getContext());
    artistView.setText(artist);
    artistView.setTextSize(13);
    artistView.setTextColor(ph.kanta.reborn.ui.KantaTheme.TEXT_SECONDARY);
    artistView.setSingleLine(true);
    artistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    metadata.addView(artistView);
    addView(metadata, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

    Button sing = new Button(getContext());
    sing.setText("Sing");
    sing.setAllCaps(false);
    sing.setTextSize(13);
    sing.setTypeface(null, Typeface.BOLD);
    sing.setTextColor(ph.kanta.reborn.ui.KantaTheme.PRIMARY_CYAN);
    sing.setBackground(ph.kanta.reborn.ui.KantaTheme.backgroundDrawable(
        ph.kanta.reborn.ui.KantaTheme.SURFACE, 11,
        ph.kanta.reborn.ui.KantaTheme.PRIMARY_BLUE, 1, density));
    sing.setMinWidth((int)(58 * density));
    sing.setMinimumHeight((int)(42 * density));
    sing.setOnClickListener(v -> { if (onSing != null) onSing.run(); });
    addView(sing);
  }

  private int parseColor(String hex) {
    try {
      return android.graphics.Color.parseColor(hex);
    } catch (Exception ignored) {
      return ph.kanta.reborn.ui.KantaTheme.BORDER;
    }
  }
}
