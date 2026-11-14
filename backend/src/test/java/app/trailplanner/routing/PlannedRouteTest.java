package app.trailplanner.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlannedRouteTest {

  private static RouteLeg leg(double distance, double ascent, TrackPoint... points) {
    return new RouteLeg(
        List.of(points),
        distance,
        ascent,
        new LegBreakdown(Map.of(WayCategory.TRAIL, distance), 0, Map.of()));
  }

  @Test
  void joinsLegsWithoutDuplicatingTheJunctionPoint() {
    TrackPoint a = new TrackPoint(6.10, 49.60, 300.0);
    TrackPoint b = new TrackPoint(6.11, 49.61, 310.0);
    TrackPoint c = new TrackPoint(6.12, 49.62, 320.0);

    PlannedRoute route = PlannedRoute.join(List.of(leg(1000, 10, a, b), leg(500, 10, b, c)));

    assertThat(route.points()).containsExactly(a, b, c);
    assertThat(route.distance()).isEqualTo(1500);
    assertThat(route.ascent()).isEqualTo(20);
    assertThat(route.breakdown().wayTypes()).containsEntry(WayCategory.TRAIL, 1500.0);
  }

  @Test
  void descentIgnoresSmallElevationNoise() {
    PlannedRoute route =
        PlannedRoute.join(
            List.of(
                leg(
                    1000,
                    0,
                    new TrackPoint(6.10, 49.60, 400.0),
                    new TrackPoint(6.11, 49.60, 398.0), // 2 m dip: noise
                    new TrackPoint(6.12, 49.60, 400.0),
                    new TrackPoint(6.13, 49.60, null), // no elevation data
                    new TrackPoint(6.14, 49.60, 350.0))));

    assertThat(route.descent()).isEqualTo(50);
  }
}
