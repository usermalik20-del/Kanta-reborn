package ph.kanta.reborn.core;

import java.util.*;

/** Authoritative FIFO; request replay and intentional repeated songs are distinct. */
public final class ReservationQueue {
  public static final class Entry {
    public final UUID id;
    public final Song song;
    public final String singer;

    public Entry(UUID id, Song song, String singer) {
      this.id = id;
      this.song = song;
      this.singer = singer == null ? "" : singer;
    }
  }

  private final ArrayList<Entry> items = new ArrayList<>();
  private final LinkedHashMap<UUID, Entry> requests = new LinkedHashMap<>();

  public synchronized Entry reserve(UUID requestId, Song song, String singer) {
    Objects.requireNonNull(requestId, "requestId");
    if (requests.containsKey(requestId)) return requests.get(requestId);
    if (items.size() >= 1000) throw new IllegalStateException("Up Next is full");
    Entry e =
        new Entry(
            UUID.randomUUID(),
            Objects.requireNonNull(song),
            singer == null ? "" : singer.substring(0, Math.min(singer.length(), 80)).trim());
    items.add(e);
    requests.put(requestId, e);
    if (requests.size() > 4096) requests.remove(requests.keySet().iterator().next());
    return e;
  }

  public synchronized List<Entry> snapshot() {
    return Collections.unmodifiableList(new ArrayList<>(items));
  }

  public synchronized Entry peek() {
    return items.isEmpty() ? null : items.get(0);
  }

  public synchronized boolean remove(UUID id) {
    return items.removeIf(e -> e.id.equals(id));
  }

  public synchronized void move(UUID id, int destination) {
    if (destination < 0 || destination >= items.size()) return;
    for (int i = 0; i < items.size(); i++)
      if (items.get(i).id.equals(id)) {
        Entry e = items.remove(i);
        items.add(destination, e);
        return;
      }
  }

  public synchronized void clear() {
    items.clear();
  }

  public synchronized void restore(List<Entry> saved) {
    Objects.requireNonNull(saved, "saved");
    if (saved.size() > 1000) throw new IllegalArgumentException("Queue too large");
    HashSet<UUID> ids = new HashSet<>();
    for (Entry e : saved) {
      if (e == null || e.id == null || e.song == null)
        throw new IllegalArgumentException("Invalid reservation");
      if (!ids.add(e.id)) throw new IllegalArgumentException("Duplicate reservation");
    }
    items.clear();
    items.addAll(saved);
    requests.clear();
  }
}
