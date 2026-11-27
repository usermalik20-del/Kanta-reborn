package ph.kanta.reborn.stage;

/**
 * Snapshot of the stage style the editor persists. Renderers receive it on
 * every session publish so the embedded Stage, the fullscreen/PiP surfaces
 * and the editor preview all read the same state without owning it.
 */
public final class StageStyle {
  public final int activeColor;
  public final float upcomingAlpha;
  public final boolean allCaps;
  public final boolean bold;
  public final int outline;
  public final String alignment;
  public final boolean showUpcoming;
  public final boolean showTitle;
  public final String title;
  public final int interludeColor;
  public final String animation;
  public final int interludeSizeSp;
  public final float imageScale;
  public final String imagePlacement;
  /** Effective lyric sync offset in milliseconds for the timed cues. */
  public final int syncOffsetMs;
  /** Cyan halo behind the interlude message instead of the plain outline. */
  public final boolean interludeGlow;
  /** Drop shadow radius behind the interlude message in dp (0 = none). */
  public final int interludeShadowDp;
  /** Marquee speed: 0 slow, 1 normal, 2 fast. */
  public final int marqueeSpeed;
  /** Marquee scroll direction: rtl (classic) or ltr. */
  public final String marqueeDir;

  public StageStyle(int activeColor, float upcomingAlpha, boolean allCaps, boolean bold,
      int outline, String alignment, boolean showUpcoming, boolean showTitle, String title,
      int interludeColor, String animation, int interludeSizeSp, float imageScale,
      String imagePlacement, int syncOffsetMs, boolean interludeGlow,
      int interludeShadowDp, int marqueeSpeed, String marqueeDir) {
    this.activeColor = activeColor;
    this.upcomingAlpha = upcomingAlpha;
    this.allCaps = allCaps;
    this.bold = bold;
    this.outline = outline;
    this.alignment = alignment;
    this.showUpcoming = showUpcoming;
    this.showTitle = showTitle;
    this.title = title;
    this.interludeColor = interludeColor;
    this.animation = animation;
    this.interludeSizeSp = interludeSizeSp;
    this.imageScale = imageScale;
    this.imagePlacement = imagePlacement;
    this.syncOffsetMs = syncOffsetMs;
    this.interludeGlow = interludeGlow;
    this.interludeShadowDp = interludeShadowDp;
    this.marqueeSpeed = marqueeSpeed;
    this.marqueeDir = marqueeDir;
  }

  /** Value equality so renderers can skip repaints when nothing changed. */
  public boolean sameAs(StageStyle other) {
    return other != null
        && activeColor == other.activeColor
        && upcomingAlpha == other.upcomingAlpha
        && allCaps == other.allCaps
        && bold == other.bold
        && outline == other.outline
        && alignment.equals(other.alignment)
        && showUpcoming == other.showUpcoming
        && showTitle == other.showTitle
        && (title == null ? other.title == null : title.equals(other.title))
        && interludeColor == other.interludeColor
        && animation.equals(other.animation)
        && interludeSizeSp == other.interludeSizeSp
        && imageScale == other.imageScale
        && imagePlacement.equals(other.imagePlacement)
        && syncOffsetMs == other.syncOffsetMs
        && interludeGlow == other.interludeGlow
        && interludeShadowDp == other.interludeShadowDp
        && marqueeSpeed == other.marqueeSpeed
        && marqueeDir.equals(other.marqueeDir);
  }
}
