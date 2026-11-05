package app.trailplan.routing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Small LRU cache for calculated legs. Dragging one point only recalculates two legs. */
public final class LegCache {

  private final Map<String, RouteLeg> entries;

  public LegCache(int maxEntries) {
    this.entries =
        new LinkedHashMap<>(256, 0.75f, true) {
          private static final long serialVersionUID = 1L;

          @Override
          protected boolean removeEldestEntry(Map.Entry<String, RouteLeg> eldest) {
            return size() > maxEntries;
          }
        };
  }

  public RouteLeg get(String key, Supplier<RouteLeg> loader) {
    synchronized (entries) {
      RouteLeg cached = entries.get(key);
      if (cached != null) {
        return cached;
      }
    }
    // Load outside the lock so slow routing calls don't block each other.
    RouteLeg leg = loader.get();
    synchronized (entries) {
      entries.put(key, leg);
    }
    return leg;
  }
}
