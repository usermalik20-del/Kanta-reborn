package ph.kanta.reborn;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.text.*;
import android.view.*;
import java.util.*;
import ph.kanta.reborn.core.*;

/** Renders controller state without owning transport or timing. */
final class StageView extends View {
  private final SessionController session;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private String lastDescription = "";
  private final Rect source = new Rect();
  private final RectF target = new RectF();
  private final TextPaint lyricPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint wavePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private StaticLayout currentLayout, nextLayout, highlightLayout;
  private String cachedCurrent = "", cachedNext = "";
  private int cachedWidth = -1;
  private float cachedSize;
  private boolean externalMedia;
  private boolean fullscreenMode;
  private final Bitmap portraitArtwork;
  private final Bitmap landscapeArtwork;
  private final float[] stars = new float[192];

  StageView(Context context, SessionController session) {
    super(context);
    this.session = session;
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
    lyricPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    lyricPaint.setShadowLayer(dp(2), 0, dp(2), Color.BLACK);
    wavePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    wavePaint.setShadowLayer(dp(3), 0, 0, 0x6600F0FF);
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
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    int index = MidiLyrics.activeIndex(session.lyrics, session.position());
    MidiLyrics.Cue cue =
        index >= 0 && index < session.lyrics.size() ? session.lyrics.get(index) : null;
    float font = Math.min(dp(48), Math.max(dp(20), w / 18)) * session.lyricScale;
    String current =
        session.current == null
            ? "Your music. Your stage."
            : cue == null
                ? (session.lyrics.isEmpty() ? session.current.title : "Get ready to sing\u2026")
                : cue.line;
    String next =
        index + 1 < session.lyrics.size() ? session.lyrics.get(index + 1).line : "";
    int width = Math.max(1, (int) w - dp(32));
    if (!current.equals(cachedCurrent)
        || !next.equals(cachedNext)
        || width != cachedWidth
        || font != cachedSize) {
      cachedCurrent = current;
      cachedNext = next;
      cachedWidth = width;
      cachedSize = font;
      currentLayout = layout(current, width, font, Color.WHITE);
      highlightLayout = cue == null ? null : layout(cue.line, width, font, 0xFFF6CE77);
      nextLayout = layout(next, width, font * .7f, 0xFFB9D8E8);
    }
    float currentLyricY = h * (fullscreenMode ? (w >= h ? .24f : .32f) : .46f);
    float nextLyricY = h * (fullscreenMode ? (w >= h ? .41f : .49f) : .70f);
    drawLayout(canvas, currentLayout, w / 2, currentLyricY);
    if (cue != null && highlightLayout != null) {
      drawProgressiveHighlight(
          canvas,
          currentLayout,
          highlightLayout,
          w / 2,
          currentLyricY,
          LyricWordAnimation.highlightedCharacters(cue, session.position()));
      drawPitchWave(canvas, currentLayout, w / 2, currentLyricY, cue, session.position());
    }
    if (!next.isEmpty()) drawLayout(canvas, nextLayout, w / 2, nextLyricY);
    if (session.current == null)
      line(
          canvas,
          "Reserve your next song in CONTROL",
          w / 2,
          h * (fullscreenMode ? .48f : .68f),
          w - dp(40),
          font * 0.6f,
          0xFFB9D8E8);
    paint.setTextSize(dp(11));
    paint.setTextAlign(Paint.Align.RIGHT);
    paint.setColor(0xBFF6CE77);
    canvas.drawText("KANTA", w - dp(14), h - dp(14), paint);
  }

  private void drawProgressiveHighlight(
      Canvas canvas,
      StaticLayout base,
      StaticLayout highlight,
      float x,
      float y,
      float highlightedCharacters) {
    if (base == null || highlight == null || highlightedCharacters <= 0f) return;
    float originX = x - base.getWidth() / 2f;
    float originY = y - base.getHeight() / 2f;
    for (int line = 0; line < base.getLineCount(); line++) {
      int lineStart = base.getLineStart(line);
      int lineEnd = base.getLineEnd(line);
      if (highlightedCharacters <= lineStart) continue;

      float target = Math.min(highlightedCharacters, lineEnd);
      int fullCharacters = (int) Math.floor(target);
      float partial = target - fullCharacters;

      float left = base.getPrimaryHorizontal(lineStart);
      float right;
      if (fullCharacters >= lineEnd) {
        right = base.getPrimaryHorizontal(lineEnd);
      } else {
        float charLeft = base.getPrimaryHorizontal(fullCharacters);
        float charRight =
            fullCharacters + 1 <= lineEnd
                ? base.getPrimaryHorizontal(fullCharacters + 1)
                : charLeft;
        right = charLeft + (charRight - charLeft) * partial;
      }
      if (right <= left) continue;

      canvas.save();
      canvas.clipRect(
          originX + left,
          originY + base.getLineTop(line),
          originX + right,
          originY + base.getLineBottom(line));
      canvas.translate(originX, originY);
      highlight.draw(canvas);
      canvas.restore();
    }
  }

  private void drawPitchWave(
      Canvas canvas,
      StaticLayout layout,
      float x,
      float y,
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

    float originX = x - layout.getWidth() / 2f;
    float originY = y - layout.getHeight() / 2f;
    float density = getResources().getDisplayMetrics().density;
    float timeSeconds = SystemClock.elapsedRealtime() / 1000f;
    float amplitude = 4f * density * energy;

    wavePaint.setTextSize(cachedSize);
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

  private StaticLayout layout(CharSequence text, int width, float size, int color) {
    TextPaint style = new TextPaint(lyricPaint);
    style.setTextSize(size);
    style.setColor(color);
    return StaticLayout.Builder.obtain(text, 0, text.length(), style, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setIncludePad(false)
        .setMaxLines(2)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build();
  }

  private void drawLayout(Canvas canvas, StaticLayout layout, float x, float y) {
    if (layout == null) return;
    canvas.save();
    canvas.translate(x - layout.getWidth() / 2f, y - layout.getHeight() / 2f);
    layout.draw(canvas);
    canvas.restore();
  }

  private void realm(Canvas canvas, float width, float height) {
    double travel = (SystemClock.elapsedRealtime() % 60000) / 60000.0;
    paint.setColor(0x806FE5E8);
    for (int i = 0; i < stars.length; i += 3) {
      double z = .15 + ((stars[i + 2] + travel) % 1.0) * 2;
      float x = (float) (width / 2 + (stars[i] - .5) * width / z);
      float y = (float) (height / 2 + (stars[i + 1] - .5) * height / z);
      float radius = (float) Math.min(dp(4), dp(2) / z);
      if (x >= 0 && y >= 0 && x <= width && y <= height) canvas.drawCircle(x, y, radius, paint);
    }
  }

  private void line(
      Canvas canvas, String text, float x, float y, float maxWidth, float size, int color) {
    paint.setTextSize(size);
    paint.setColor(color);
    paint.setTextAlign(Paint.Align.CENTER);
    String[] words = text.split("\\s+");
    List<String> lines = new ArrayList<>();
    String line = "";
    for (String word : words) {
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (paint.measureText(candidate) > maxWidth && !line.isEmpty()) {
        lines.add(line);
        line = word;
      } else line = candidate;
    }
    if (!line.isEmpty()) lines.add(line);
    int count = Math.min(2, lines.size());
    for (int i = 0; i < count; i++) {
      String value = lines.get(i);
      if (i == 1 && lines.size() > 2) value += "\u2026";
      while (paint.measureText(value) > maxWidth && value.length() > 1)
        value = value.substring(0, value.length() - 1);
      canvas.drawText(value, x, y + (i - (count - 1) / 2f) * size * 1.2f, paint);
    }
  }
}
