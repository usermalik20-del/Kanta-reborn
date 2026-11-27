package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import ph.kanta.reborn.core.LyricWordAnimation;
import ph.kanta.reborn.core.MidiLyrics;
import ph.kanta.reborn.core.PitchSource;
import ph.kanta.reborn.core.RenderedPlaybackClock;
import ph.kanta.reborn.font.FontStore;

import java.util.Arrays;

/** Draws the active cue and optional next cue, synced to the rendered playback clock. */
public final class LyricStage extends View {

  private static final int PITCH_SPARKS = 8;
  private static final MidiLyrics.Cue[] EMPTY_CUES = new MidiLyrics.Cue[0];

  private final TextPaint activePaint;
  private final TextPaint upcomingPaint;
  private final TextPaint echoPaint;
  private final TextPaint pitchWavePaint;
  private final Paint sparkPaint;
  private final TextPaint placeholderPaint;
  private final FontStore fontStore;
  private Typeface appliedTypeface;
  private boolean appliedBold = true;
  private final Paint titlePaint;
  private final Paint scrimPaint;
  private StageStyle style = new StageStyle(0xFFFFFFFF, 0.8f, false, true, 1, "center", true,
      false, "", 0xFFFFFFFF, "static", 0, 1f, "above", 0);
  private static final long LINE_FADE_MS = 450;
  private StageLyricLayout ghostLyrics;
  private float ghostX, ghostY, lastX, lastY;
  private long fadeStartMs = -1;
  private long countdownFadeStartMs = -1;
  private long countdownFadeDurationMs = 0;

  private MidiLyrics.Cue[] cues = EMPTY_CUES;
  private RenderedPlaybackClock clock;
  private PitchSource pitchSource;
  private int currentLineIndex = -1;
  private long playedFrames;
  private StageLyricLayout visibleLyrics;
  private float pitchEnergy = 0f;
  private int safeZonePercent;
  private int safeInset;
  private int burnShiftX, burnShiftY;

  public LyricStage(Context context) {
    this(context, null);
  }

  public LyricStage(Context context, AttributeSet attrs) {
    super(context, attrs);
    fontStore = FontStore.getInstance(context);
    setWillNotDraw(false);

    float density = context.getResources().getDisplayMetrics().density;
    activePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    activePaint.setColor(0xFFFFFFFF);
    activePaint.setFakeBoldText(true);
    activePaint.setShadowLayer(3 * density, 0, density, 0xFF000000);

    upcomingPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    upcomingPaint.setColor(0xFFE8EEF4);
    upcomingPaint.setShadowLayer(3 * density, 0, density, 0xFF000000);

    echoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    echoPaint.setColor(0x55FFFFFF); // dimmed KAR echo/backing line

    pitchWavePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    pitchWavePaint.setColor(0xCCE9FFFF);
    pitchWavePaint.setFakeBoldText(true);


    placeholderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    placeholderPaint.setTextSize(16 * density);
    placeholderPaint.setColor(0xFF999999);
    placeholderPaint.setTextAlign(Paint.Align.CENTER);
    sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    sparkPaint.setStyle(Paint.Style.FILL);
    titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    titlePaint.setTextSize(14 * density);
    titlePaint.setColor(0xCCFFFFFF);
    titlePaint.setTextAlign(Paint.Align.CENTER);
    scrimPaint = new Paint();
    scrimPaint.setColor(0x9904070C);
  }

  /** Pushes the persisted stage style; value-equal snapshots are ignored. */
  public void setStyle(StageStyle style) {
    if (style == null || this.style.sameAs(style)) return;
    this.style = style;
    visibleLyrics = null;
    invalidate();
  }

  /** True while the presence overlay owns the empty stage. */
  private boolean standby;

  /** Standby means the presence overlay owns the empty stage, so draw nothing here. */
  public void setStandby(boolean standby) {
    if (this.standby == standby) return;
    this.standby = standby;
    invalidate();
  }

  /** Sets the lyrics cues and clock. */
  public void setLyrics(MidiLyrics.Cue[] cues, RenderedPlaybackClock clock) {
    MidiLyrics.Cue[] updated = cues != null ? cues : EMPTY_CUES;
    if (this.clock == clock && Arrays.equals(this.cues, updated)) return;
    this.cues = updated;
    this.clock = clock;
    visibleLyrics = null;
    invalidate();
  }

  /** Supplies the single microphone owner's pitch estimate for visual energy only. */
  public void setPitchSource(PitchSource pitchSource) {
    this.pitchSource = pitchSource;
  }

  /**
   * Supplies the monotonically increasing count of rendered output frames.
   * The playback owner must call this from the same output clock used to append
   * spans to RenderedPlaybackClock; wall-clock time is not a playback source.
   */
  public void setPlayedFrames(long playedFrames) {
    if (playedFrames < 0) throw new IllegalArgumentException("Played frames cannot be negative");
    if (this.playedFrames == playedFrames) return;
    this.playedFrames = playedFrames;
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    visibleLyrics = null;
  }

  /** Standby message shown while the reserved song waits for its first cue. */
  private String interludeText;
  /** Optional image above the standby message; null for text-only interludes. */
  private Bitmap interludeImage;
  private final android.graphics.RectF interludeRect = new android.graphics.RectF();
  private boolean interludeDirty;

  public void setInterludeText(String text) {
    if (java.util.Objects.equals(interludeText, text)) return;
    interludeText = text;
    interludeDirty = true;
    invalidate();
  }

  public void setInterludeImage(Bitmap image) {
    if (interludeImage == image) return;
    interludeImage = image;
    interludeDirty = true;
    invalidate();
  }

  /** Countdown window for the interlude fade; (-1, 0) resets to fully visible. */
  public void setSafeZonePercent(int percent) { safeZonePercent = Math.max(0, Math.min(10, percent)); }
  public void setBurnShift(int dxPx, int dyPx) { burnShiftX = dxPx; burnShiftY = dyPx; }

  public void setCountdownFade(long startMs, long durationMs) {
    countdownFadeStartMs = startMs;
    countdownFadeDurationMs = durationMs;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (standby) return;
    if (cues.length == 0) {
      // The current song's title and singer still own the header line while the
      // stage has no timed lyrics to render.
      if (style.showTitle && !style.title.isEmpty()) {
        float d = getResources().getDisplayMetrics().density;
        String header = style.title;
        float max = getWidth() - 40 * d;
        if (titlePaint.measureText(header) > max) {
          while (!header.isEmpty() && titlePaint.measureText(header + "\u2026") > max)
            header = header.substring(0, header.length() - 1);
          header += "\u2026";
        }
        canvas.drawText(header, getWidth() / 2f + burnShiftX, 18 * d + burnShiftY, titlePaint);
      }
      canvas.drawText("No timed lyrics for this song", getWidth() / 2f, getHeight() / 2f,
          placeholderPaint);
      return;
    }
    Typeface selected = fontStore.typeface();
    if (selected != appliedTypeface || style.bold != appliedBold) {
      appliedTypeface = selected;
      appliedBold = style.bold;
      Typeface base = selected != null
          ? selected : Typeface.create("sans-serif", Typeface.NORMAL);
      Typeface face = style.bold ? Typeface.create(base, Typeface.BOLD) : base;
      activePaint.setTypeface(face);
      upcomingPaint.setTypeface(face);
      echoPaint.setTypeface(face);
      pitchWavePaint.setTypeface(face);
      visibleLyrics = null;
    }
    float density = getResources().getDisplayMetrics().density;
    long now = SystemClock.elapsedRealtime();
    int index = findCurrentLine(clock != null
        ? clock.position(playedFrames) + style.syncOffsetMs : 0L);
    boolean interlude = index < 0 && interludeText != null && !interludeText.isEmpty();
    // The waiting interlude fades out across the vocal countdown (the presenter
    // pushes the countdown window), clearing the stage for the first sung line.
    float interludeAlpha = 1f;
    if (interlude && countdownFadeDurationMs > 0 && countdownFadeStartMs >= 0) {
      long remaining = countdownFadeStartMs + countdownFadeDurationMs - now;
      interludeAlpha = Math.max(0f, Math.min(1f, remaining / (float) countdownFadeDurationMs));
    }
    if (interlude && interludeAlpha <= 0f) return;
    safeInset = (int) (getWidth() * safeZonePercent / 100f);
    int width = getWidth() - 2 * (int) (20 * density) - 2 * safeInset;
    int padding = Math.min((int) (12 * density), getHeight() / 12);
    int height = getHeight() - 2 * padding - 2 * safeInset;
    if (width < 1 || height < 1) return;
    long position = clock != null ? clock.position(playedFrames) + style.syncOffsetMs : 0L;
    MidiLyrics.Cue cue = cues[Math.max(0, index)];
    // Before the first cue the reserved song's standby interlude owns the primary line.
    String primary = interlude
        ? interludeText : (style.allCaps ? oneToOneUpper(cue.line) : cue.line);
    int activeColor = interlude ? fadeColor(style.interludeColor, interludeAlpha)
        : style.activeColor;
    activePaint.setColor(activeColor);
    applyOutline(activePaint, style.outline, density);
    upcomingPaint.setColor(argb(Math.round(style.upcomingAlpha * 255), 0xB9, 0xD8, 0xE8));
    applyOutline(upcomingPaint, style.outline, density);
    if (style.showTitle && !style.title.isEmpty()) {
      String header = style.title;
      float max = width;
      if (titlePaint.measureText(header) > max) {
        while (!header.isEmpty() && titlePaint.measureText(header + "\u2026") > max)
          header = header.substring(0, header.length() - 1);
        header += "\u2026";
      }
      canvas.drawText(header, getWidth() / 2f + burnShiftX, 18 * density + burnShiftY, titlePaint);
    }
    if (visibleLyrics == null || index != currentLineIndex || interludeDirty) {
      // Fixed rows: the previous sung pair dissolves in place as the new pair
      // fades in at the same anchor.
      if (visibleLyrics != null && index != currentLineIndex) {
        ghostLyrics = visibleLyrics;
        ghostX = lastX;
        ghostY = lastY;
        fadeStartMs = now;
      }
      interludeDirty = false;
      currentLineIndex = index;
      MidiLyrics.Cue next = style.showUpcoming
          && Math.max(0, index) + 1 < cues.length
          ? cues[Math.max(0, index) + 1] : null;
      float size = interlude && style.interludeSizeSp > 0
          ? style.interludeSizeSp * getResources().getDisplayMetrics().scaledDensity
          : StageLyricLayout.preferredSize(getResources(), getWidth(), false);
      activePaint.setTextSize(size);
      TextPaint preview = next != null && next.echo ? echoPaint : upcomingPaint;
      preview.setTextSize(size * .65f);
      String nextLine = next == null ? ""
          : (style.allCaps ? oneToOneUpper(next.line) : next.line);
      android.text.Layout.Alignment align = "left".equals(style.alignment)
          ? android.text.Layout.Alignment.ALIGN_NORMAL : android.text.Layout.Alignment.ALIGN_CENTER;
      visibleLyrics = StageLyricLayout.fit(primary, activePaint, nextLine, preview, width, height,
          (int) (8 * density), 16 * density, align);
    }
    float x = "left".equals(style.alignment) ? 20 * density : (getWidth() - width) / 2f;
    // The row anchor uses a fixed reference height, not the current pair's
    // measured height, so a short line never drags the rows it replaces.
    float refHeight = activePaint.getTextSize() * 2.4f + 16 * density;
    float y = "bottom".equals(style.alignment)
        ? padding + Math.max(0, height - visibleLyrics.height())
        : padding + Math.max(0, height - refHeight) / 2f;
    lastX = x;
    lastY = y;
    final float drawX = x, drawY = y;
    long fadeAge = fadeStartMs >= 0 ? now - fadeStartMs : Long.MAX_VALUE;
    float fadeIn = fadeAge < LINE_FADE_MS ? fadeAge / (float) LINE_FADE_MS : 1f;
    if (interlude && interludeImage != null && !interludeImage.isRecycled()) {
      withAlpha(canvas, interludeAlpha, () -> {
        if ("background".equals(style.imagePlacement)) {
          float s = Math.max((float) getWidth() / interludeImage.getWidth(),
              (float) getHeight() / interludeImage.getHeight()) * style.imageScale;
          int dw = Math.max(1, Math.round(interludeImage.getWidth() * s));
          int dh = Math.max(1, Math.round(interludeImage.getHeight() * s));
          interludeRect.set((getWidth() - dw) / 2f, (getHeight() - dh) / 2f,
              (getWidth() + dw) / 2f, (getHeight() + dh) / 2f);
          canvas.drawBitmap(interludeImage, null, interludeRect, null);
          canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        } else {
          float scale = Math.min((width * .8f * style.imageScale) / interludeImage.getWidth(),
              (height * .45f * style.imageScale) / interludeImage.getHeight());
          int dw = Math.max(1, Math.round(interludeImage.getWidth() * scale));
          int dh = Math.max(1, Math.round(interludeImage.getHeight() * scale));
          int left = Math.round((getWidth() - dw) / 2f);
          int top = Math.round(padding + (height - dh) / 2f - dh * .18f);
          interludeRect.set(left, top, left + dw, top + dh);
          canvas.drawBitmap(interludeImage, null, interludeRect, null);
        }
      });
    }
    if (ghostLyrics != null) {
      if (fadeAge >= LINE_FADE_MS) {
        ghostLyrics = null;
        fadeStartMs = -1;
      } else {
        withAlpha(canvas, 1f - fadeIn,
            () -> ghostLyrics.draw(canvas, ghostX, ghostY, 0f, 0));
      }
    }
    // Interlude motion: pulse breathes around the block center; the marquee scrolls
    // at a text-size-proportional speed so large text is not rushed off screen.
    String animation = interlude ? style.animation : "static";
    if ("pulse".equals(animation)) {
      withAlpha(canvas, fadeIn, () -> {
        float amplitude = 1f + 0.04f * (float) Math.sin(now / 260.0);
        float cx = drawX + visibleLyrics.current.getWidth() / 2f;
        float cy = drawY + visibleLyrics.height() / 2f;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(amplitude, amplitude);
        canvas.translate(-cx, -cy);
        visibleLyrics.draw(canvas, drawX, drawY, 0f, highlightColor(activeColor));
        canvas.restore();
      });
    } else if ("marquee".equals(animation)) {
      withAlpha(canvas, fadeIn, () -> {
        float textWidth = visibleLyrics.current.getWidth() + 16 * density;
        float speed = 30f * density * (activePaint.getTextSize() / (40f * density));
        float span = Math.max(1f, textWidth + width);
        float offset = textWidth
            - (float) (((now / 1000.0) * speed) % span);
        canvas.save();
        canvas.clipRect(20 * density + safeInset, padding + safeInset,
            getWidth() - 20 * density - safeInset, height + padding - safeInset);
        visibleLyrics.draw(canvas, drawX + offset, drawY, 0f, highlightColor(activeColor));
        canvas.restore();
      });
    } else {
      withAlpha(canvas, fadeIn,
          () -> visibleLyrics.draw(canvas, drawX, drawY,
              index < 0 ? 0f : LyricWordAnimation.highlightedCharacters(cue, position),
              highlightColor(activeColor)));
    }
    if (isAttachedToWindow() && (ghostLyrics != null
        || (interlude && (countdownFadeDurationMs > 0 || !"static".equals(animation))))) {
      postInvalidateOnAnimation();
    }
    pitchEnergy = currentPitchEnergy();
    if (index >= 0 && pitchEnergy > .02f) {
      StaticLayout layout = visibleLyrics.current;
      pitchWavePaint.setTextSize(layout.getPaint().getTextSize());
      drawPitchWave(canvas, layout, x, y, cue, position, density);
      drawPitchSparks(canvas, layout, x, y, density);
    }
  }

  private void drawPitchWave(
      Canvas canvas,
      StaticLayout layout,
      float originX,
      float originY,
      MidiLyrics.Cue cue,
      long positionMilliseconds,
      float density) {
    int[] fragment = LyricWordAnimation.activeFragment(cue, positionMilliseconds);
    int start = fragment[0];
    int end = fragment[1];
    if (start >= end || cue.line.isEmpty()) return;

    long now = SystemClock.elapsedRealtime();
    float timeSeconds = now / 1000f;
    float amplitude = 3.5f * density * pitchEnergy;

    for (int index = start; index < end && index < cue.line.length(); index++) {
      if (Character.isWhitespace(cue.line.charAt(index))) continue;
      int line = layout.getLineForOffset(index);
      float glyphX = layout.getPrimaryHorizontal(index);
      float baseline = layout.getLineBaseline(line);
      float phase = timeSeconds * 7f + index * 1.9f;
      float dy = (float) Math.sin(phase) * amplitude;
      float scale = 1f + 0.18f * pitchEnergy * (float) Math.sin(phase + 0.8f);
      float pivotX = originX + glyphX;
      float pivotY = originY + baseline + dy;

      canvas.save();
      canvas.translate(pivotX, pivotY);
      canvas.scale(scale, scale);
      canvas.drawText(cue.line, index, index + 1, 0f, 0f, pitchWavePaint);
      canvas.restore();
    }
  }

  private void drawPitchSparks(
      Canvas canvas, StaticLayout layout, float originX, float originY, float density) {
    long now = SystemClock.elapsedRealtime();
    float centerX = originX + layout.getWidth() / 2f;
    float centerY = originY + layout.getHeight() / 2f;
    float orbitX = layout.getWidth() / 2f + 14f * density;
    float orbitY = Math.max(10f * density, layout.getHeight() / 2f + 6f * density);
    int alpha = (int) (170f * pitchEnergy);
    sparkPaint.setARGB(alpha, 0, 240, 255);
    for (int i = 0; i < PITCH_SPARKS; i++) {
      double angle = (now / 620.0) + i * (Math.PI * 2 / PITCH_SPARKS);
      float sx = centerX + (float) Math.cos(angle) * orbitX;
      float sy = centerY + (float) Math.sin(angle) * orbitY;
      float radius = 1.6f * density + 2.4f * density * pitchEnergy;
      canvas.drawCircle(sx, sy, radius, sparkPaint);
    }
  }

  private float currentPitchEnergy() {
    if (pitchSource == null) return 0f;
    return LyricWordAnimation.pitchEnergy(
        pitchSource.currentPitchHz(), pitchSource.measuredAtMs(), SystemClock.elapsedRealtime());
  }

  private static String oneToOneUpper(String input) {
    StringBuilder out = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++)
      out.append(Character.toUpperCase(input.charAt(i)));
    return out.toString();
  }

  private static int argb(int alpha, int red, int green, int blue) {
    return (alpha << 24) | (red << 16) | (green << 8) | blue;
  }

  private static void withAlpha(Canvas canvas, float alpha, Runnable draw) {
    float clamped = Math.max(0f, Math.min(1f, alpha));
    if (clamped >= 1f) {
      draw.run();
      return;
    }
    Paint layer = new Paint();
    layer.setAlpha((int) (clamped * 255));
    canvas.saveLayer(null, layer);
    draw.run();
    canvas.restore();
  }

  private static int fadeColor(int color, float alpha) {
    int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255) & 0xFF;
    return (color & 0x00FFFFFF) | (a << 24);
  }

  private static void applyOutline(Paint paint, int level, float density) {
    switch (level) {
      case 0:
        paint.setShadowLayer(0, 0, 0, 0);
        break;
      case 2:
        paint.setShadowLayer(5 * density, 0, 5 * density, 0xFF000000);
        break;
      default:
        paint.setShadowLayer(2 * density, 0, 2 * density, 0xFF000000);
    }
  }

  /** Classic gold highlight when the active color is white, a lightened tint otherwise. */
  private static int highlightColor(int activeColor) {
    int alpha = (activeColor >>> 24) & 0xFF;
    int red = (activeColor >> 16) & 0xFF;
    int green = (activeColor >> 8) & 0xFF;
    int blue = activeColor & 0xFF;
    if (red == 255 && green == 255 && blue == 255) return (alpha << 24) | 0xF6CE77;
    int weight = 45;
    return (alpha << 24)
        | (Math.min(255, red + (255 - red) * weight / 100) << 16)
        | (Math.min(255, green + (255 - green) * weight / 100) << 8)
        | Math.min(255, blue + (255 - blue) * weight / 100);
  }

  private int findCurrentLine(long positionMilliseconds) {
    int low = 0, high = cues.length;
    while (low < high) {
      int middle = (low + high) >>> 1;
      if (cues[middle].milliseconds <= positionMilliseconds) low = middle + 1;
      else high = middle;
    }
    return low - 1;
  }
}
