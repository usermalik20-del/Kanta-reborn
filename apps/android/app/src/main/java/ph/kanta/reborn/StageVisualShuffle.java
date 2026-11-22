package ph.kanta.reborn;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Main-thread state for one prepared stage background, never for a renderer or manual choice. */
final class StageVisualShuffle<T> {
  private long generation;
  private boolean requested;
  private T next;

  boolean needsPreparation() {
    return !requested;
  }

  long beginPreparation() {
    requested = true;
    return ++generation;
  }

  boolean isCurrent(long request) {
    return requested && request == generation;
  }

  void prepared(long request, T result) {
    if (isCurrent(request)) next = result;
  }

  void invalidate() {
    generation++;
    requested = false;
    next = null;
  }

  T takeAtSongBoundary() {
    T result = next;
    if (result != null) invalidate();
    return result;
  }

  static <E> List<E> alternatives(List<E> options, E current, Random random) {
    List<E> alternatives = new ArrayList<>(options);
    alternatives.removeIf(candidate -> candidate.equals(current));
    java.util.Collections.shuffle(alternatives, random);
    return alternatives;
  }
}
