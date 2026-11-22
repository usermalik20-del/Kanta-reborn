package ph.kanta.reborn;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.text.*;
import android.view.*;
import java.util.*;
import ph.kanta.reborn.core.*;
import ph.kanta.reborn.font.FontStore;
import ph.kanta.reborn.stage.StageLyricLayout;

/** Renders controller state without owning transport or timing. */
// Programmatic-only view: callers supply runtime dependencies; not inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class StageView extends View {
  private final SessionController session;
  private final FontStore fontStore;
  private Typeface appliedTypeface;
  private final Typeface builtInTypeface = Typeface.create("sans-serif", Typeface.BOLD);
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private String lastDescription = "";
  private final Rect source = new Rect();
  private final RectF target = new RectF();
  private final TextPaint lyricPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint wavePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private StageLyricLayout visibleLyrics;
  private int cachedHeight;
  private boolean cachedEcho;
  private String cachedCurrent = "", cachedNext = "";
  private int cachedWidth = -1;
  private float cachedSize;
  private boolean externalMedia;
  private boolean fullscreenMode;
  private int contentTop, contentBottom;
  private final Bitmap portraitArtwork;
  private final Bitmap landscapeArtwork;
  private final float[] stars = new float[192];

  StageView(Context context, SessionController session) {
    super(context);
    this.session = session;
    fontStore = FontStore.getInstance(context);
    BitmapFactory.Options artworkOptions = new BitmapFactory.Options();
    artworkOptions.inSampleSize = 2;
    artworkOptions.inPreferredConfig = Bitmap.Config.RGB_565;
    portraitArtwork = BitmapFactory.decodeResource(
        getResources(), R.drawable.kanta_stage_phone, artworkOptions);
    landscapeArtwork = BitmapFactory.decodeResource(
        getResources(), R.drawable.kanta_stage_tv, artworkOptions);
    setMinimumHeight(dp(220));
    Random random = new Random(0x4b414e54);
    for (int i = 0; i < stars.length; i++) stars[i] = random.nextFloat();
    // Larger, legible lyrics with strong dark outline for contrast on any background
    lyricPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    lyricPaint.setShadowLayer(dp(3), 0, dp(3), 0xFF000000);
    // Gold timing highlight (no red/pink)
    wavePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    wavePaint.setShadowLayer(dp(3), 0, 0, 0x66FFD700);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  private int dp(int v) {
    return (int) (v * getResources().getDisplayMetrics().density);
  }

  void mediaVisible(boolean visible) {
    externalMedia = visible;
    invalidate();
  }

  void setFullscreenMode(boolean enabled) {
    fullscreenMode = enabled;
    invalidate();
  }

  void setContentInsets(int top, int bottom) {
    if (contentTop == top && contentBottom == bottom) return;
    contentTop = top;
    contentBottom = bottom;
    invalidate();
  }

  void refresh() {
    int i = MidiLyrics.activeIndex(session.lyrics, session.position());
    String description =
        ((session.current == null ? "KANTA stage" : session.current.title)
            + ". "
            + (i >= 0 ? session.lyrics.get(i).line : ""));
    if (!description.equals(lastDescription)) {
      setContentDescription(description);
      lastDescription = description;
    }
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    Typeface selected = fontStore.typeface();
    if (selected != appliedTypeface) {
      appliedTypeface = selected;
      Typeface face = selected != null ? selected : builtInTypeface;
      lyricPaint.setTypeface(face);
      wavePaint.setTypeface(face);
      cachedWidth = -1;
    }
    float w = getWidth(), h = getHeight();
    if (!externalMedia) canvas.drawColor(Color.rgb(13, 18, 42));
    Bitmap selectedScene = session.scenic;
    Bitmap scene = selectedScene != null && !selectedScene.isRecycled()
        ? selectedScene
        : (w >= h ? landscapeArtwork : portraitArtwork);
    if (!externalMedia && scene != null && !scene.isRecycled()) {
      float scale = Math.max(w / scene.getWidth(), h / scene.getHeight());
      float sw = w / scale, sh = h / scale;
      source.set(
          (int) ((scene.getWidth() - sw) / 2),
          (int) ((scene.getHeight() - sh) / 2),
          (int) ((scene.getWidth() + sw) / 2),
          (int) ((scene.getHeight() + sh) / 2));
      target.set(0, 0, w, h);
      paint.setAlpha(255);
      canvas.drawBitmap(scene, source, target, paint);
      paint.setColor(0x88000000);
      canvas.drawRect(0, 0, w, h, paint);
    }
    if (externalMedia) {
      paint.setColor(0x88000000);
      canvas.drawRect(0, 0, w, h, paint);
    }
    if (session.realm) realm(canvas, w, h);
    int lyricHeight = getHeight() - contentTop - contentBottom;
    if (lyricHeight < 1) return;
    if (!standbyMode) {
      long position = session.position();
      int index = session.current == null ? -1 : MidiLyrics.activeIndex(session.lyrics, position);
      MidiLyrics.Cue cue = index >= 0 ? session.lyrics.get(index) : null;
      String current = session.current == null ? "Your music. Your stage." : cue != null ? cue.line
          : session.lyrics.isEmpty() ? session.current.title : "Get ready to sing\u2026";
      MidiLyrics.Cue nextCue = cue != null && index + 1 < session.lyrics.size()
          ? session.lyrics.get(index + 1) : null;
      String next = session.current == null ? "Reserve your next song in CONTROL"
          : nextCue == null ? "" : nextCue.line;
      float font = StageLyricLayout.preferredSize(getResources(), getWidth(), true)
          * session.lyricScale;
      boolean echo = nextCue != null && nextCue.echo;
      int width = getWidth() - dp(36);
      int padding = Math.min(dp(18), lyricHeight / 10);
      int height = lyricHeight - 2 * padding;
      if (width > 0) {
        if (!current.equals(cachedCurrent) || !next.equals(cachedNext) || echo != cachedEcho
            || width != cachedWidth || height != cachedHeight || font != cachedSize) {
          cachedCurrent = current;
          cachedNext = next;
          cachedEcho = echo;
          cachedWidth = width;
          cachedHeight = height;
          cachedSize = font;
          lyricPaint.setTextSize(font);
          lyricPaint.setColor(Color.WHITE);
          TextPaint preview = new TextPaint(lyricPaint);
          preview.setTextSize(font * .65f);
          preview.setColor(echo ? 0x55FFFFFF : 0xFFB9D8E8);
          visibleLyrics = StageLyricLayout.fit(current, lyricPaint, next, preview, width, height,
              dp(12), dp(18));
        }
        float x = (w - width) / 2f;
        float y = contentTop + padding + Math.max(0, height - visibleLyrics.height())
            * (fullscreenMode ? .45f : .5f);
        canvas.save();
        canvas.clipRect(0, contentTop, w, h - contentBottom);
        visibleLyrics.draw(canvas, x, y, LyricWordAnimation.highlightedCharacters(cue, position),
            0xFFFFD700);
        if (cue != null) drawPitchWave(canvas, visibleLyrics.current, x, y, cue, position);
        canvas.restore();
      }
    }
    paint.setTextSize(dp(11));
    paint.setTextAlign(Paint.Align.RIGHT);
    paint.setColor(0xBFF6CE77);
    canvas.drawText("KANTA", w - dp(14), h - contentBottom - dp(8), paint);
  }

  private void drawPitchWave(
      Canvas canvas,
      StaticLayout layout,
      float originX,
      float originY,
      MidiLyrics.Cue cue,
      long positionMilliseconds) {
    if (layout == null) return;
    float energy =
        LyricWordAnimation.pitchEnergy(
            session.currentPitchHz(), session.measuredAtMs(), SystemClock.elapsedRealtime());
    if (energy <= 0.02f) return;

    int[] fragment = LyricWordAnimation.activeFragment(cue, positionMilliseconds);
    int start = fragment[0];
    int end = fragment[1];
    if (start >= end) return;

    float density = getResources().getDisplayMetrics().density;
    float timeSeconds = SystemClock.elapsedRealtime() / 1000f;
    float amplitude = 4f * density * energy;

    wavePaint.setTextSize(layout.getPaint().getTextSize());
    wavePaint.setColor(0xFFE9FFFF);

    for (int i = start; i < end && i < cue.line.length(); i++) {
      if (Character.isWhitespace(cue.line.charAt(i))) continue;
      int line = layout.getLineForOffset(i);
      float glyphX = layout.getPrimaryHorizontal(i);
      float baseline = layout.getLineBaseline(line);
      float phase = timeSeconds * 7f + i * 1.9f;
      float dy = (float) Math.sin(phase) * amplitude;
      float scale = 1f + 0.18f * energy * (float) Math.sin(phase + 0.8f);
      canvas.save();
      canvas.translate(originX + glyphX, originY + baseline + dy);
      canvas.scale(scale, scale);
      canvas.drawText(cue.line, i, i + 1, 0f, 0f, wavePaint);
      canvas.restore();
    }
  }

  private void realm(Canvas canvas, float width, float height) {
    // Realm: animated starfield that responds to the music
    double travel = (SystemClock.elapsedRealtime() % 60000) / 60000.0;
    // Use session energy to drive particle intensity
    float energy = 1f + (session.currentPitchHz() > 0 ? 0.5f : 0f);
    for (int i = 0; i < stars.length; i += 3) {
      double z = .15 + ((stars[i + 2] + travel) % 1.0) * 2;
      float x = (float) (width / 2 + (stars[i] - .5) * width / z * energy);
      float y = (float) (height / 2 + (stars[i + 1] - .5) * height / z * energy);
      float radius = (float) Math.min(dp(5), dp(3) / z);
      int alpha = (int) (180 / z);
      paint.setColor((alpha << 24) | 0x6FE5E8);
      if (x >= 0 && y >= 0 && x <= width && y <= height) canvas.drawCircle(x, y, radius, paint);
    }
  }

  private boolean standbyMode;

  /** Standby means the presence card owns the empty stage, so draw no placeholder text. */
  void setStandbyMode(boolean standby) { standbyMode = standby; }

}
