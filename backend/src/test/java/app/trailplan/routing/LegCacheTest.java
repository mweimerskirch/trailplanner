package app.trailplan.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LegCacheTest {

  private static final RouteLeg LEG =
      new RouteLeg(
          List.of(new TrackPoint(6.1, 49.6, null), new TrackPoint(6.2, 49.7, null)),
          100,
          0,
          LegBreakdown.empty());

  @Test
  void loadsOnceAndEvictsTheLeastRecentlyUsedEntry() {
    LegCache cache = new LegCache(2);
    AtomicInteger loads = new AtomicInteger();

    cache.get("a", () -> count(loads));
    cache.get("b", () -> count(loads));
    cache.get("a", () -> count(loads)); // hit; "b" is now the eldest
    cache.get("c", () -> count(loads)); // evicts "b"
    cache.get("a", () -> count(loads)); // still cached
    cache.get("b", () -> count(loads)); // reloaded

    assertThat(loads).hasValue(4);
  }

  @Test
  void failuresAreNotCached() {
    LegCache cache = new LegCache(10);

    assertThatThrownBy(() ->
            cache.get(
                "a",
                () -> {
                  throw new RoutingException(RoutingException.Reason.NO_ROUTE, "no route");
                }))
        .isInstanceOf(RoutingException.class);

    assertThat(cache.get("a", () -> LEG)).isSameAs(LEG);
  }

  private static RouteLeg count(AtomicInteger loads) {
    loads.incrementAndGet();
    return LEG;
  }
}
