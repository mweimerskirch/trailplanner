package app.trailplanner.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The converter against fake routers on a synthetic 10 km track going east from Luxembourg City (a
 * point every 10 m).
 */
class TrackConverterTest {

  private static final double DEG_PER_M = 0.01 / 721; // longitude at 49.61° N
  private final ExecutorService executor = Executors.newFixedThreadPool(4);

  @AfterEach
  void stop() {
    executor.shutdownNow();
  }

  private static List<TrackPoint> eastward(double lengthM) {
    List<TrackPoint> track = new ArrayList<>();
    for (double d = 0; d <= lengthM; d += 10) {
      track.add(new TrackPoint(6.13 + d * DEG_PER_M, 49.61, 300.0));
    }
    return track;
  }

  /** A router that follows the track exactly (a straight line between the two points). */
  private static RouteLeg follow(GeoPoint from, GeoPoint to) {
    return new RouteLeg(
        List.of(
            new TrackPoint(from.lng(), from.lat(), null), new TrackPoint(to.lng(), to.lat(), null)),
        0,
        0,
        LegBreakdown.empty());
  }

  /** Detours 500 m north on every leg that crosses the stretch between 4.0 and 4.2 km. */
  private static RouteLeg detourAroundGap(GeoPoint from, GeoPoint to) {
    double gapStart = 6.13 + 4000 * DEG_PER_M;
    double gapEnd = 6.13 + 4200 * DEG_PER_M;
    if (from.lng() < gapEnd && to.lng() > gapStart) {
      double mid = (from.lng() + to.lng()) / 2;
      return new RouteLeg(
          List.of(
              new TrackPoint(from.lng(), from.lat(), null),
              new TrackPoint(mid, from.lat() + 0.0045, null),
              new TrackPoint(to.lng(), to.lat(), null)),
          0,
          0,
          LegBreakdown.empty());
    }
    return follow(from, to);
  }

  /** The forced router passes the gap, with a warning, as a router allowing closed ways would. */
  private static RouteLeg forcedThroughGap(GeoPoint from, GeoPoint to) {
    RouteLeg leg = follow(from, to);
    double gapStart = 6.13 + 4000 * DEG_PER_M;
    double gapEnd = 6.13 + 4200 * DEG_PER_M;
    List<RouteWarning> warnings =
        from.lng() < gapEnd && to.lng() > gapStart
            ? List.of(new RouteWarning(gapStart, 49.61, "Under construction"))
            : List.of();
    return new RouteLeg(leg.points(), 0, 0, LegBreakdown.empty(), warnings);
  }

  private TrackConverter converter(
      BiFunction<GeoPoint, GeoPoint, RouteLeg> router,
      BiFunction<GeoPoint, GeoPoint, RouteLeg> forced) {
    return new TrackConverter(router, forced, executor, 200);
  }

  @Test
  void aTrackTheRouterFollowsNeedsOnlyAFewWaypoints() {
    TrackConverter.Result result =
        converter(TrackConverterTest::follow, TrackConverterTest::follow).convert(eastward(10_000));

    assertThat(result.waypoints()).hasSize(2); // merged into a single leg
    assertThat(result.waypoints()).allMatch(w -> w.leg() == LegType.ROUTED);
    assertThat(result.forcedLegs()).isZero();
  }

  @Test
  void aClosedWayBecomesAForcedLegInsteadOfADetour() {
    TrackConverter.Result result =
        converter(TrackConverterTest::detourAroundGap, TrackConverterTest::forcedThroughGap)
            .convert(eastward(10_000));

    assertThat(result.forcedLegs()).isPositive();
    assertThat(result.straightLegs()).isZero();
    // Only the leg over the gap is forced; it starts before the gap and ends after it.
    List<Waypoint> wps = result.waypoints();
    for (int i = 0; i < wps.size() - 1; i++) {
      boolean crossesGap =
          wps.get(i).lng() < 6.13 + 4200 * DEG_PER_M
              && wps.get(i + 1).lng() > 6.13 + 4000 * DEG_PER_M;
      assertThat(wps.get(i).leg()).isEqualTo(crossesGap ? LegType.FORCED : LegType.ROUTED);
    }
    assertThat(wps.size()).isLessThan(10);
  }

  @Test
  void aStretchWithoutAnyWayBecomesStraightLines() {
    TrackConverter.Result result =
        converter(TrackConverterTest::detourAroundGap, TrackConverterTest::detourAroundGap)
            .convert(eastward(10_000));

    assertThat(result.straightLegs()).isPositive();
    for (int i = 0; i < result.waypoints().size() - 1; i++) {
      Waypoint w = result.waypoints().get(i);
      if (w.leg() == LegType.STRAIGHT) {
        // Straight legs stay in (or right next to) the gap; everything else is routed.
        assertThat(w.lng()).isBetween(6.13 + 3900 * DEG_PER_M, 6.13 + 4200 * DEG_PER_M);
      }
    }
    assertThat(result.waypoints().getFirst().lng()).isEqualTo(6.13);
  }

  @Test
  void routerErrorsAreTreatedLikeLegsThatStray() {
    AtomicInteger calls = new AtomicInteger();
    BiFunction<GeoPoint, GeoPoint, RouteLeg> failing =
        (a, b) -> {
          calls.incrementAndGet();
          throw new RoutingException(RoutingException.Reason.POINT_NOT_ON_MAP, "not mapped");
        };

    TrackConverter.Result result = converter(failing, failing).convert(eastward(500));

    assertThat(result.waypoints().getFirst().leg()).isEqualTo(LegType.STRAIGHT);
    assertThat(calls).hasPositiveValue();
  }
}
