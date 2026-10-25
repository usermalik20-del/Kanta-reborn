package ph.kanta.reborn.core;

import java.util.UUID;

/** Presentation identities, independent of the local transport and FIFO. Main-thread owned. */
public final class YouTubePreviewLifecycle {
  public static final class Session {
    public final UUID id = UUID.randomUUID();
    public final YouTubeVideoReference reference;
    private Session(YouTubeVideoReference reference) { this.reference = reference; }
  }
  private Session active;
  public Session active() { return active; }
  public Session open(YouTubeVideoReference reference) {
    if (reference == null) throw new IllegalArgumentException("Missing video reference");
    active = new Session(reference);
    return active;
  }
  public boolean close(UUID id) {
    if (active == null || !active.id.equals(id)) return false;
    active = null;
    return true;
  }
  public void close() { active = null; }
}
