package app.trailplan.routing;

import java.util.ArrayList;
import java.util.List;

/** All legs of a route joined into one track. */
public record PlannedRoute(
    List<TrackPoint> points,
    double distance,
    double ascent,
    double descent,
    LegBreakdown breakdown) {

  public PlannedRoute {
    points = List.copyOf(points);
  }

  public static PlannedRoute join(List<RouteLeg> legs) {
    List<TrackPoint> points = new ArrayList<>();
    double distance = 0;
    double ascent = 0;
    LegBreakdown breakdown = LegBreakdown.empty();
    for (RouteLeg leg : legs) {
      List<TrackPoint> legPoints = leg.points();
      // Each leg starts where the previous one ended; skip the duplicate junction point.
      int start = points.isEmpty() ? 0 : 1;
      points.addAll(legPoints.subList(Math.min(start, legPoints.size()), legPoints.size()));
      distance += leg.distance();
      ascent += leg.ascent();
      breakdown = breakdown.plus(leg.breakdown());
    }
    return new PlannedRoute(points, distance, ascent, TrackStats.descent(points), breakdown);
  }
}
