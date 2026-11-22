package ph.kanta.reborn.stage;

import android.content.Context;
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

  private static final int HIGHLIGHT_COLOR = 0xFFF6CE77;
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

  private MidiLyrics.Cue[] cues = EMPTY_CUES;
  private RenderedPlaybackClock clock;
  private PitchSource pitchSource;
  private int currentLineIndex = -1;
  private long playedFrames;
  private StageLyricLayout visibleLyrics;
  private float pitchEnergy = 0f;

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

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (standby) return;
    if (cues.length == 0) {
      canvas.drawText("No timed lyrics for this song", getWidth() / 2f, getHeight() / 2f,
          placeholderPaint);
      return;
    }
    Typeface selected = fontStore.typeface();
    if (selected != appliedTypeface) {
      appliedTypeface = selected;
      activePaint.setTypeface(selected);
      upcomingPaint.setTypeface(selected);
      echoPaint.setTypeface(selected);
      pitchWavePaint.setTypeface(selected);
      visibleLyrics = null;
    }
    float density = getResources().getDisplayMetrics().density;
    int width = getWidth() - 2 * (int) (20 * density);
    int padding = Math.min((int) (12 * density), getHeight() / 12);
    int height = getHeight() - 2 * padding;
    if (width < 1 || height < 1) return;
    long position = clock != null ? clock.position(playedFrames) : 0L;
    int index = findCurrentLine(position);
    MidiLyrics.Cue cue = cues[Math.max(0, index)];
    if (visibleLyrics == null || index != currentLineIndex) {
      currentLineIndex = index;
      MidiLyrics.Cue next = Math.max(0, index) + 1 < cues.length
          ? cues[Math.max(0, index) + 1] : null;
      float size = StageLyricLayout.preferredSize(getResources(), getWidth(), false);
      activePaint.setTextSize(size);
      TextPaint preview = next != null && next.echo ? echoPaint : upcomingPaint;
      preview.setTextSize(size * .65f);
      visibleLyrics = StageLyricLayout.fit(cue.line, activePaint, next == null ? "" : next.line,
          preview, width, height, (int) (8 * density), 16 * density);
    }
    float x = (getWidth() - width) / 2f;
    float y = padding + Math.max(0, height - visibleLyrics.height()) / 2f;
    visibleLyrics.draw(canvas, x, y,
        index < 0 ? 0f : LyricWordAnimation.highlightedCharacters(cue, position), HIGHLIGHT_COLOR);
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
