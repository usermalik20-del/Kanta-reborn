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
  private boolean appliedBold;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dimPaint;
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
  private int cachedColor = -1, cachedUpcomingColor = -1;
  private String cachedAlign = "center";
  private boolean externalMedia;
  private boolean fullscreenMode;
  private static final long LINE_FADE_MS = 450;
  private StageLyricLayout ghostLyrics;
  private float ghostX, ghostTop, lastX, lastTop;
  private long fadeStartMs = -1;
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
    dimPaint = new Paint();
    dimPaint.setColor(0xFF000000);
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
    int i = MidiLyrics.activeIndex(session.lyrics, session.lyricPositionMs());
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
    boolean wantBold = session.lyricBold();
    Typeface selected = fontStore.typeface();
    if (selected != appliedTypeface || wantBold != appliedBold) {
      appliedTypeface = selected;
      appliedBold = wantBold;
      Typeface face = selected != null ? selected : Typeface.create("sans-serif", Typeface.NORMAL);
      if (wantBold) face = Typeface.create(face, Typeface.BOLD);
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
    int dimPercent = session.stageDimAlpha();
    if (dimPercent > 0) {
      dimPaint.setAlpha(Math.round(dimPercent * 2.55f));
      canvas.drawRect(0, 0, w, h, dimPaint);
    }
    if (session.realm) realm(canvas, w, h);
    int lyricHeight = getHeight() - contentTop - contentBottom;
    if (lyricHeight < 1) return;
    if (!standbyMode) {
      // The effective position carries the singer's sync offset so cue selection,
      // word highlighting and the pitch wave all move together.
      long position = session.lyricPositionMs();
      int index = session.current == null ? -1 : MidiLyrics.activeIndex(session.lyrics, position);
      MidiLyrics.Cue cue = index >= 0 ? session.lyrics.get(index) : null;
      boolean interlude = session.preSongInterludeActive();
      // The waiting interlude fades out across the vocal countdown so the stage
      // is clear where the first sung line lands.
      float interludeAlpha = interlude ? interludeFadeAlpha() : 1f;
      boolean interludeVisible = interlude && interludeAlpha > 0f;
      String current = session.current == null ? "Your music. Your stage." : cue != null ? cue.line
          : session.lyrics.isEmpty() ? session.current.title
          : (interludeVisible ? session.interludeText() : "");
      MidiLyrics.Cue nextCue = cue != null && index + 1 < session.lyrics.size()
          ? session.lyrics.get(index + 1) : null;
      String next = session.current == null ? "Reserve your next song in CONTROL"
          : nextCue == null ? "" : nextCue.line;
      float font = StageLyricLayout.preferredSize(getResources(), getWidth(), true)
          * session.lyricScale;
      if (interludeVisible && session.interludeTextSizeSp() > 0) {
        font = session.interludeTextSizeSp() * getResources().getDisplayMetrics().scaledDensity;
      }
      int lineColor = interludeVisible
          ? fadeColor(session.interludeTextColor(), interludeAlpha)
          : session.lyricActiveColor();
      String align = session.lyricAlignment();
      boolean showUpcoming = session.lyricShowUpcoming()
          && !"multi".equals(session.lyricLines());
      if (session.lyricAllCaps()) {
        current = oneToOneUpper(current);
        next = oneToOneUpper(next);
      }
      int upcomingColor = argb(
          (int) Math.round(session.lyricUpcomingOpacity() * 255), 0xB9, 0xD8, 0xE8);
      String upcomingLine = showUpcoming ? next : "";
      boolean echo = nextCue != null && nextCue.echo;
      int width = getWidth() - dp(36);
      int padding = Math.min(dp(18), lyricHeight / 10);
      int height = lyricHeight - 2 * padding;
      if (width > 0) {
        if (!current.equals(cachedCurrent) || !upcomingLine.equals(cachedNext)
            || echo != cachedEcho || width != cachedWidth || height != cachedHeight
            || font != cachedSize || lineColor != cachedColor
            || upcomingColor != cachedUpcomingColor || !align.equals(cachedAlign)) {
          // Fixed rows: when the sung line changes, the previous pair dissolves in
          // place while the new pair fades in at the same anchor.
          if (visibleLyrics != null && !current.equals(cachedCurrent)
              && !cachedCurrent.isEmpty()) {
            ghostLyrics = visibleLyrics;
            ghostX = lastX;
            ghostTop = lastTop;
            fadeStartMs = SystemClock.elapsedRealtime();
          }
          cachedCurrent = current;
          cachedNext = upcomingLine;
          cachedEcho = echo;
          cachedWidth = width;
          cachedHeight = height;
          cachedSize = font;
          cachedColor = lineColor;
          cachedUpcomingColor = upcomingColor;
          cachedAlign = align;
          lyricPaint.setTextSize(font);
          lyricPaint.setColor(lineColor);
          applyOutline(lyricPaint, session.lyricOutline());
          TextPaint preview = new TextPaint(lyricPaint);
          preview.setTextSize(font * .65f);
          preview.setColor(echo ? 0x55FFFFFF : upcomingColor);
          Layout.Alignment la = "left".equals(align)
              ? Layout.Alignment.ALIGN_NORMAL : Layout.Alignment.ALIGN_CENTER;
          visibleLyrics = StageLyricLayout.fit(current, lyricPaint, upcomingLine, preview,
              width, height, dp(12), dp(18), la);
        }
        float x = (w - width) / 2f;
        // The row anchor uses a fixed reference height, not the current pair's
        // measured height, so a short line never drags the rows it replaces.
        float refHeight = font * 2.4f + dp(18);
        float blockTop = contentTop + padding + Math.max(0, height - refHeight)
            * (fullscreenMode ? .45f : .5f);
        if ("bottom".equals(align)) {
          blockTop = contentTop + padding + Math.max(0, height - visibleLyrics.height());
        }
        if ("left".equals(align)) x = dp(18);
        lastX = x;
        lastTop = blockTop;
        long fadeAge = fadeStartMs >= 0 ? SystemClock.elapsedRealtime() - fadeStartMs
            : Long.MAX_VALUE;
        float fadeIn = fadeAge < LINE_FADE_MS ? fadeAge / (float) LINE_FADE_MS : 1f;
        final float drawX = x, drawTop = blockTop;
        canvas.save();
        canvas.clipRect(0, contentTop, w, h - contentBottom);
        if (interludeVisible)
          drawInterludeImage(canvas, w, h, width, lyricHeight, interludeAlpha);
        String anim = interludeVisible ? session.interludeAnimation() : "static";
        long now = SystemClock.elapsedRealtime();
        float t = now / 1000f;
        if (ghostLyrics != null) {
          if (fadeAge >= LINE_FADE_MS) {
            ghostLyrics = null;
            fadeStartMs = -1;
          } else {
            withAlpha(canvas, 1f - fadeIn,
                () -> ghostLyrics.draw(canvas, ghostX, ghostTop, 0, 0));
          }
        }
        if ("pulse".equals(anim)) {
          withAlpha(canvas, fadeIn, () -> {
            float pulse = 1f + .05f * (float) Math.sin(t * 2.4);
            float cx = drawX + width / 2f, cy = drawTop + visibleLyrics.height() / 2f;
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(pulse, pulse);
            canvas.translate(-cx, -cy);
            visibleLyrics.draw(canvas, drawX, drawTop, 0, 0);
            canvas.restore();
          });
        } else if ("marquee".equals(anim)) {
          withAlpha(canvas, fadeIn, () -> {
            float textW = visibleLyrics.current.getWidth();
            float total = width + textW;
            // Slow readable scroll: px per second scaled to the text size, with
            // seconds (not milliseconds) as the time base.
            float densityPx = getResources().getDisplayMetrics().density;
            float speed = 40f * densityPx
                * (lyricPaint.getTextSize() / (40f * densityPx));
            double offset = total <= 0 ? 0 : (now / 1000.0 * speed) % total;
            float startX = width - (float) offset;
            visibleLyrics.draw(canvas, startX, drawTop, 0, 0);
            visibleLyrics.draw(canvas, startX + total, drawTop, 0, 0);
          });
        } else {
          withAlpha(canvas, fadeIn,
              () -> visibleLyrics.draw(canvas, drawX, drawTop,
                  LyricWordAnimation.highlightedCharacters(cue, position),
                  highlightColor(lineColor)));
        }
        if (cue != null) drawPitchWave(canvas, visibleLyrics.current, x, blockTop, cue, position);
        if (interludeVisible && session.interludeShowVisualizer())
          drawVisualizerBars(canvas, w, h, t);
        if ((ghostLyrics != null || (interlude && session.countdownRemainingMs() >= 0))
            && isAttachedToWindow()) {
          postInvalidateOnAnimation();
        }
        canvas.restore();
      }
      if (session.lyricShowTitle() && session.current != null) drawSongTitle(canvas, w);
      if (session.lyricShowCountdown() && session.countdownRemainingMs() >= 0) {
        drawCountdownDots(canvas, w);
      }
    }
    boolean showWatermark = session.preSongInterludeActive()
        ? session.interludeShowWatermark() : session.lyricShowWatermark();
    if (showWatermark) {
      paint.setTextSize(dp(11));
      paint.setTextAlign(Paint.Align.RIGHT);
      paint.setColor(0xBFF6CE77);
      canvas.drawText("KANTA", w - dp(14), h - contentBottom - dp(8), paint);
    }
    // Animated interlude states and the vocal countdown need continuous frames; the
    // session clock alone is too slow for a marquee.
    boolean interludeFrames = session.preSongInterludeActive()
        && (!"static".equals(session.interludeAnimation())
            || session.interludeShowVisualizer()
            || session.countdownRemainingMs() >= 0);
    if (isAttachedToWindow()
        && (interludeFrames
            || (session.lyricShowCountdown() && session.countdownRemainingMs() >= 0))) {
      postInvalidateOnAnimation();
    }
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

  /** Interlude image: above the message, or a dimmed full-bleed background. */
  private void drawInterludeImage(Canvas canvas, float w, float h, int width, int lyricHeight,
      float alpha) {
    if (!"image".equals(session.interludeMode())) return;
    android.graphics.drawable.Drawable interlude = session.interludeImage();
    if (interlude == null) return;
    if (alpha < 1f) {
      android.graphics.Paint layer = new android.graphics.Paint();
      layer.setAlpha((int) (Math.max(0f, alpha) * 255));
      canvas.saveLayer(null, layer);
      drawInterludeImageInner(canvas, w, h, width, lyricHeight, interlude);
      canvas.restore();
      return;
    }
    drawInterludeImageInner(canvas, w, h, width, lyricHeight, interlude);
  }

  private void drawInterludeImageInner(Canvas canvas, float w, float h, int width,
      int lyricHeight, android.graphics.drawable.Drawable interlude) {
    int iw = interlude.getIntrinsicWidth();
    int ih = interlude.getIntrinsicHeight();
    if (iw <= 0 || ih <= 0) return;
    float factor = session.interludeImageScale();
    if ("background".equals(session.interludeImagePlacement())) {
      float scale = Math.max(w / iw, h / ih) * factor;
      int dw = Math.round(iw * scale), dh = Math.round(ih * scale);
      int left = Math.round((w - dw) / 2f), top = Math.round((h - dh) / 2f);
      interlude.setBounds(left, top, left + dw, top + dh);
      interlude.draw(canvas);
      paint.setColor(0x99000000);
      canvas.drawRect(0, 0, w, h, paint);
      return;
    }
    float maxW = width * .8f * factor;
    float maxH = lyricHeight * .45f * factor;
    float scale = Math.min(maxW / iw, maxH / ih);
    int dw = Math.round(iw * scale);
    int dh = Math.round(ih * scale);
    int left = Math.round((w - dw) / 2f);
    int top = Math.round(contentTop + (lyricHeight - dh) / 2f - dh * .18f);
    interlude.setBounds(left, top, left + dw, top + dh);
    interlude.draw(canvas);
  }

  private static void withAlpha(Canvas canvas, float alpha, Runnable draw) {
    float clamped = Math.max(0f, Math.min(1f, alpha));
    if (clamped >= 1f) {
      draw.run();
      return;
    }
    android.graphics.Paint layer = new android.graphics.Paint();
    layer.setAlpha((int) (clamped * 255));
    canvas.saveLayer(null, layer);
    draw.run();
    canvas.restore();
  }

  private static int fadeColor(int color, float alpha) {
    int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255) & 0xFF;
    return (color & 0x00FFFFFF) | (a << 24);
  }

  /** 1 before the countdown, 0 the moment the first sung cue can land. */
  private float interludeFadeAlpha() {
    long remaining = session.countdownRemainingMs();
    long total = session.countdownTotalMs();
    if (remaining < 0 || total <= 0) return 1f;
    return Math.max(0f, remaining / (float) total);
  }

  /** Reactive bars along the bottom of the interlude, driven by the shared pitch energy. */
  private void drawVisualizerBars(Canvas canvas, float w, float h, float t) {
    float energy = LyricWordAnimation.pitchEnergy(
        session.currentPitchHz(), session.measuredAtMs(), SystemClock.elapsedRealtime());
    int bars = 24;
    float density = getResources().getDisplayMetrics().density;
    float barW = 4f * density, gap = 3f * density;
    float totalW = bars * barW + (bars - 1) * gap;
    float x0 = (w - totalW) / 2f;
    float baseY = h - contentBottom - dp(26);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xCC76E4FF);
    for (int b = 0; b < bars; b++) {
      float wob = .5f + .5f * (float) Math.sin(t * 3.1 + b * .83);
      float amp = (.15f + .85f * wob) * Math.max(.18f, energy);
      float barH = Math.max(3f * density, amp * dp(34));
      canvas.drawRect(x0 + b * (barW + gap), baseY - barH, x0 + b * (barW + gap) + barW, baseY, paint);
    }
  }

  /** Optional song title header above the lyric block. */
  private void drawSongTitle(Canvas canvas, float w) {
    String title = ph.kanta.reborn.stage.StagePresenterImpl.songTitleHeader(session.current);
    paint.setTextSize(dp(14));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setColor(0xCCFFFFFF);
    float max = w - dp(48);
    if (paint.measureText(title) > max) {
      while (title.length() > 1 && paint.measureText(title + "\u2026") > max)
        title = title.substring(0, title.length() - 1);
      title += "\u2026";
    }
    canvas.drawText(title, w / 2f, contentTop + dp(24), paint);
  }

  /** Vocal countdown dots: one per remaining second, centered above the stage. */
  private void drawCountdownDots(Canvas canvas, float w) {
    int seconds = (int) Math.ceil(session.countdownRemainingMs() / 1000.0);
    seconds = Math.max(1, Math.min(8, seconds));
    float gapX = dp(16);
    float x0 = (w - (seconds - 1) * gapX) / 2f;
    float y0 = contentTop + (session.lyricShowTitle() ? dp(48) : dp(32));
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xE676E4FF);
    for (int d = 0; d < seconds; d++) canvas.drawCircle(x0 + d * gapX, y0, dp(5), paint);
  }

  /** Per-character upper case: preserves length so cue offsets stay valid. */
  private static String oneToOneUpper(String value) {
    if (value == null || value.isEmpty()) return value;
    char[] out = value.toCharArray();
    for (int i = 0; i < out.length; i++) out[i] = Character.toUpperCase(out[i]);
    return new String(out);
  }

  private static int argb(int alpha, int r, int g, int b) {
    return (alpha & 0xFF) << 24 | (r << 16) | (g << 8) | b;
  }

  private void applyOutline(TextPaint p, int outline) {
    switch (outline) {
      case 0: p.setShadowLayer(0, 0, 0, 0xFF000000); break;
      case 2: p.setShadowLayer(dp(5), 0, dp(5), 0xFF000000); break;
      default: p.setShadowLayer(dp(2), 0, dp(2), 0xFF000000);
    }
  }

  /** Sung-portion highlight: classic gold over the default white, else a lightened color. */
  private static int highlightColor(int lineColor) {
    if (lineColor == 0xFFFFFFFF) return 0xFFFFD700;
    int r = (lineColor >> 16) & 0xFF, g = (lineColor >> 8) & 0xFF, b = lineColor & 0xFF;
    r += (255 - r) * 45 / 100;
    g += (255 - g) * 45 / 100;
    b += (255 - b) * 45 / 100;
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  private boolean standbyMode;

  /** Standby means the presence card owns the empty stage, so draw no placeholder text. */
  void setStandbyMode(boolean standby) { standbyMode = standby; }

}
