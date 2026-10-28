package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import ph.kanta.reborn.core.MidiLyrics;
import ph.kanta.reborn.core.RenderedPlaybackClock;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders scrolling timed lyrics with active/inactive/upcoming states.
 *
 * <p>Synced to {@link RenderedPlaybackClock}. Uses StaticLayout for efficient text layout.
 * Designed per {@link replica/design/components.md} LyricStage spec.
 */
public final class LyricStage extends View {

  private static final int MAX_LINES_VISIBLE = 7;
  private static final int LINE_SPACING_DP = 16;

  private final TextPaint activePaint;
  private final TextPaint inactivePaint;
  private final TextPaint upcomingPaint;
  private final Paint highlightPaint;
  private final int lineSpacingPx;
  private final int paddingHorizontalPx;
  private final int paddingVerticalPx;

  private MidiLyrics.Cue[] cues = new MidiLyrics.Cue[0];
  private RenderedPlaybackClock clock;
  private int currentLineIndex = -1;
  private List<StaticLayout> layouts = new ArrayList<>();
  private List<Rect> lineBounds = new ArrayList<>();
  private int measuredWidth = 0;
  private int measuredHeight = 0;

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

    // Active lyric line (currently singing)
    activePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    activePaint.setTextSize(24 * density); // 24sp per design tokens
    activePaint.setColor(0xFFFF3D8B); // karaoke_lyric_active
    activePaint.setFakeBoldText(true);
    activePaint.setTextAlign(Paint.Align.CENTER);

    // Inactive sung lines
    inactivePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    inactivePaint.setTextSize(20 * density); // 20sp
    inactivePaint.setColor(0xFFFFFFFF); // karaoke_lyric_inactive
    inactivePaint.setTextAlign(Paint.Align.CENTER);

    // Upcoming lines
    upcomingPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    upcomingPaint.setTextSize(16 * density); // 16sp
    upcomingPaint.setColor(0xFFBBBBBB); // karaoke_lyric_upcoming
    upcomingPaint.setTextAlign(Paint.Align.CENTER);

    // Highlight background for active line
    highlightPaint = new Paint();
    highlightPaint.setColor(0x33FF3D8B); // 20% accent
  }

  /** Sets the lyrics cues and clock. */
  public void setLyrics(MidiLyrics.Cue[] cues, RenderedPlaybackClock clock) {
    this.cues = cues != null ? cues : new MidiLyrics.Cue[0];
    this.clock = clock;
    rebuildLayouts();
    invalidate();
  }

  private void rebuildLayouts() {
    layouts.clear();
    lineBounds.clear();
    if (cues.length == 0 || measuredWidth <= 0) return;

    int availableWidth = measuredWidth - 2 * paddingHorizontalPx;
    for (MidiLyrics.Cue cue : cues) {
      TextPaint paint = getPaintForLine(-1); // default to inactive for measurement
      StaticLayout layout = StaticLayout.Builder.obtain(cue.text, 0, cue.text.length(), paint, availableWidth)
          .setAlignment(Layout.Alignment.ALIGN_CENTER)
          .setLineSpacing(lineSpacingPx, 1.0f)
          .setIncludePad(false)
          .build();
      layouts.add(layout);
    }
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

    // Update current line index from clock
    if (clock != null) {
      long pos = clock.positionUs();
      currentLineIndex = findCurrentLine(pos);
    }

    // Calculate visible range (center current line)
    int centerY = measuredHeight / 2;
    int startLine = Math.max(0, currentLineIndex - MAX_LINES_VISIBLE / 2);
    int endLine = Math.min(layouts.size(), startLine + MAX_LINES_VISIBLE);

    // Draw lines
    int y = paddingVerticalPx;
    for (int i = 0; i < layouts.size(); i++) {
      StaticLayout layout = layouts.get(i);
      int lineHeight = layout.getHeight();

      if (i >= startLine && i < endLine) {
        // Visible line
        int x = measuredWidth / 2;
        TextPaint paint = getPaintForLine(i);

        if (i == currentLineIndex) {
          // Draw highlight background
          Rect bounds = new Rect(
              paddingHorizontalPx,
              y - lineSpacingPx / 2,
              measuredWidth - paddingHorizontalPx,
              y + lineHeight + lineSpacingPx / 2);
          canvas.drawRect(bounds, highlightPaint);
        }

        canvas.save();
        canvas.translate(x - layout.getWidth() / 2, y);
        layout.draw(canvas);
        canvas.restore();
      }
      y += lineHeight + lineSpacingPx;
    }
  }

  private void drawPlaceholder(Canvas canvas) {
    String text = "No lyrics loaded";
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setTextSize(16 * getResources().getDisplayMetrics().density);
    paint.setColor(0xFF999999);
    paint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(text, measuredWidth / 2f, measuredHeight / 2f, paint);
  }

  private int findCurrentLine(long positionUs) {
    for (int i = 0; i < cues.length; i++) {
      long start = cues[i].startUs;
      long end = (i + 1 < cues.length) ? cues[i + 1].startUs : Long.MAX_VALUE;
      if (positionUs >= start && positionUs < end) {
        return i;
      }
    }
    return -1;
  }
}
