package app.trailplan.routing;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Small LRU cache for calculated legs: dragging one point only recalculates two legs. Entries
 * expire, so updated map data (weekly BRouter tiles) is used without restarting the backend.
 */
public final class LegCache {

  static final Duration DEFAULT_MAX_AGE = Duration.ofHours(6);

  private record Cached(RouteLeg leg, long createdNanos) {}

  private final Map<String, Cached> entries;
  private final long maxAgeNanos;
  private final LongSupplier clock;

  public LegCache(int maxEntries) {
    this(maxEntries, DEFAULT_MAX_AGE, System::nanoTime);
  }

  LegCache(int maxEntries, Duration maxAge, LongSupplier nanoClock) {
    this.maxAgeNanos = maxAge.toNanos();
    this.clock = nanoClock;
    this.entries =
        new LinkedHashMap<>(256, 0.75f, true) {
          private static final long serialVersionUID = 1L;

          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > maxEntries;
          }
        };
  }

  public RouteLeg get(String key, Supplier<RouteLeg> loader) {
    synchronized (entries) {
      Cached cached = entries.get(key);
      if (cached != null && clock.getAsLong() - cached.createdNanos() < maxAgeNanos) {
        return cached.leg();
      }
    }
    // Load outside the lock so slow routing calls don't block each other.
    RouteLeg leg = loader.get();
    synchronized (entries) {
      entries.put(key, new Cached(leg, clock.getAsLong()));
    }
    return leg;
  }
}
