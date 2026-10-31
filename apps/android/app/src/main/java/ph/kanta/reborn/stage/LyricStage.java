package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import ph.kanta.reborn.core.LyricWordAnimation;
import ph.kanta.reborn.core.MidiLyrics;
import ph.kanta.reborn.core.PitchSource;
import ph.kanta.reborn.core.RenderedPlaybackClock;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders scrolling timed lyrics with active/inactive/upcoming states.
 *
 * <p>Synced to {@link RenderedPlaybackClock}. The scrolling window is unchanged; the active line
 * now fills smoothly across each source-timed lyric fragment instead of switching a whole word at
 * once, and the fragment being sung reacts to the live pitch estimate through a small per-letter
 * wave. Pitch is a visual energy signal only: it never changes cue timing, the playback clock or
 * scoring, and the stage still renders with the microphone off.
 */
public final class LyricStage extends View {

  private static final int MAX_LINES_VISIBLE = 7;
  private static final int LINE_SPACING_DP = 16;
  private static final float ACTIVE_SIZE_SP = 24f;
  private static final float INACTIVE_SIZE_SP = 20f;
  private static final float UPCOMING_SIZE_SP = 16f;
  private static final float PILL_PAD_DP = 12f;
  private static final int PITCH_SPARKS = 8;

  private final TextPaint activePaint;
  private final TextPaint highlightPaint;
  private final TextPaint inactivePaint;
  private final TextPaint upcomingPaint;
  private final TextPaint pitchWavePaint;
  private final Paint pillPaint;
  private final Paint sparkPaint;
  private final int lineSpacingPx;
  private final int paddingHorizontalPx;
  private final int paddingVerticalPx;

  private MidiLyrics.Cue[] cues = new MidiLyrics.Cue[0];
  private RenderedPlaybackClock clock;
  private PitchSource pitchSource;
  private int currentLineIndex = -1;
  private long playedFrames;
  private List<StaticLayout> layouts = new ArrayList<>();
  private StaticLayout highlightLayout;
  private int highlightLayoutLine = -1;
  private int measuredWidth = 0;
  private int measuredHeight = 0;
  private float pitchEnergy = 0f;

  public LyricStage(Context context) {
    this(context, null);
  }

  public LyricStage(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);

    float density = context.getResources().getDisplayMetrics().density;
    lineSpacingPx = (int) (LINE_SPACING_DP * density);
    paddingHorizontalPx = (int) (24 * density);
    paddingVerticalPx = (int) (32 * density);

    activePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    activePaint.setTextSize(ACTIVE_SIZE_SP * density);
    activePaint.setColor(0xFFFF3D8B); // karaoke_lyric_active
    activePaint.setFakeBoldText(true);

    highlightPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    highlightPaint.setTextSize(ACTIVE_SIZE_SP * density);
    highlightPaint.setColor(0xFFF6CE77); // existing stage gold for sung characters
    highlightPaint.setFakeBoldText(true);

    inactivePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    inactivePaint.setTextSize(INACTIVE_SIZE_SP * density);
    inactivePaint.setColor(0xFFFFFFFF); // karaoke_lyric_inactive

    upcomingPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    upcomingPaint.setTextSize(UPCOMING_SIZE_SP * density);
    upcomingPaint.setColor(0xFFBBBBBB); // karaoke_lyric_upcoming

    pitchWavePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    pitchWavePaint.setTextSize(ACTIVE_SIZE_SP * density);
    pitchWavePaint.setColor(0xCCE9FFFF);
    pitchWavePaint.setFakeBoldText(true);

    pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    pillPaint.setColor(0x33FF3D8B); // 20% accent

    sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    sparkPaint.setStyle(Paint.Style.FILL);
  }

  /** Sets the lyrics cues and clock. */
  public void setLyrics(MidiLyrics.Cue[] cues, RenderedPlaybackClock clock) {
    this.cues = cues != null ? cues : new MidiLyrics.Cue[0];
    this.clock = clock;
    this.currentLineIndex = -1;
    rebuildLayouts();
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

  private void rebuildLayouts() {
    layouts.clear();
    highlightLayout = null;
    highlightLayoutLine = -1;
    if (cues.length == 0 || measuredWidth <= 0) return;

    int availableWidth = Math.max(1, measuredWidth - 2 * paddingHorizontalPx);
    for (int i = 0; i < cues.length; i++) {
      layouts.add(buildLayout(cues[i].line, getPaintForLine(i), availableWidth));
    }
    if (currentLineIndex >= 0 && currentLineIndex < cues.length) {
      highlightLayout = buildLayout(cues[currentLineIndex].line, highlightPaint, availableWidth);
      highlightLayoutLine = currentLineIndex;
    }
  }

  private StaticLayout buildLayout(String text, TextPaint paint, int width) {
    return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(lineSpacingPx, 1.0f)
        .setIncludePad(false)
        .build();
  }

  private TextPaint getPaintForLine(int lineIndex) {
    if (lineIndex == currentLineIndex) return activePaint;
    if (lineIndex < currentLineIndex) return inactivePaint;
    return upcomingPaint;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    measuredWidth = w;
    measuredHeight = h;
    rebuildLayouts();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (cues.length == 0 || layouts.isEmpty()) {
      drawPlaceholder(canvas);
      return;
    }

    long positionMilliseconds = clock != null ? clock.position(playedFrames) : 0L;
    pitchEnergy = currentPitchEnergy();

    int nextLineIndex = findCurrentLine(positionMilliseconds);
    if (nextLineIndex != currentLineIndex) {
      currentLineIndex = nextLineIndex;
      rebuildLayouts();
    }

    int startLine =
        currentLineIndex >= 0 ? Math.max(0, currentLineIndex - MAX_LINES_VISIBLE / 2) : 0;
    int endLine = Math.min(layouts.size(), startLine + MAX_LINES_VISIBLE);
    int totalHeight = 0;
    for (int i = startLine; i < endLine; i++) {
      totalHeight += layouts.get(i).getHeight();
      if (i + 1 < endLine) totalHeight += lineSpacingPx;
    }

    int y = Math.max(paddingVerticalPx, (measuredHeight - totalHeight) / 2);
    int x = measuredWidth / 2;
    for (int i = startLine; i < endLine; i++) {
      StaticLayout layout = layouts.get(i);
      if (i == currentLineIndex) {
        drawActiveLine(canvas, layout, x, y, cues[i], positionMilliseconds);
      } else {
        canvas.save();
        canvas.translate(x - layout.getWidth() / 2f, y);
        layout.draw(canvas);
        canvas.restore();
      }
      y += layout.getHeight() + lineSpacingPx;
    }
  }

  private void drawActiveLine(
      Canvas canvas,
      StaticLayout layout,
      float x,
      float y,
      MidiLyrics.Cue cue,
      long positionMilliseconds) {
    float density = getResources().getDisplayMetrics().density;
    float originX = x - layout.getWidth() / 2f;
    float originY = y;
    float pad = PILL_PAD_DP * density;

    RectF pill =
        new RectF(
            Math.max(paddingHorizontalPx, originX - pad),
            originY - pad / 3f,
            Math.min(measuredWidth - paddingHorizontalPx, originX + layout.getWidth() + pad),
            originY + layout.getHeight() + pad / 3f);
    canvas.drawRoundRect(pill, pad, pad, pillPaint);

    canvas.save();
    canvas.translate(originX, originY);
    layout.draw(canvas);
    canvas.restore();

    float highlighted = LyricWordAnimation.highlightedCharacters(cue, positionMilliseconds);
    if (highlighted > 0f
        && highlightLayout != null
        && highlightLayoutLine == currentLineIndex
        && highlightLayout.getLineCount() > 0) {
      drawProgressiveHighlight(
          canvas, layout, highlightLayout, originX, originY, highlighted);
    }

    if (pitchEnergy > 0.02f) {
      drawPitchWave(canvas, layout, originX, originY, cue, positionMilliseconds, density);
      drawPitchSparks(canvas, layout, originX, originY, density);
    }
  }

  private void drawProgressiveHighlight(
      Canvas canvas,
      StaticLayout base,
      StaticLayout highlight,
      float originX,
      float originY,
      float highlightedCharacters) {
    int lineCount = base.getLineCount();
    for (int line = 0; line < lineCount; line++) {
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

  private void drawPlaceholder(Canvas canvas) {
    String text = "No lyrics loaded";
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setTextSize(16 * getResources().getDisplayMetrics().density);
    paint.setColor(0xFF999999);
    paint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(text, measuredWidth / 2f, measuredHeight / 2f, paint);
  }

  private int findCurrentLine(long positionMilliseconds) {
    for (int i = 0; i < cues.length; i++) {
      long start = cues[i].milliseconds;
      long end = (i + 1 < cues.length) ? cues[i + 1].milliseconds : Long.MAX_VALUE;
      if (positionMilliseconds >= start && positionMilliseconds < end) {
        return i;
      }
    }
    return -1;
  }
}
