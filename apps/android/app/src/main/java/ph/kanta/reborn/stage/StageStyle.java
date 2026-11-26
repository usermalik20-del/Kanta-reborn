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

  public StageStyle(int activeColor, float upcomingAlpha, boolean allCaps, boolean bold,
      int outline, String alignment, boolean showUpcoming, boolean showTitle, String title,
      int interludeColor, String animation, int interludeSizeSp, float imageScale,
      String imagePlacement) {
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
        && imagePlacement.equals(other.imagePlacement);
  }
}
