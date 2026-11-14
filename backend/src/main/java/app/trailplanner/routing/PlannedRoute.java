package app.trailplanner.routing;

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
      // Each leg starts where the previous one ended; skip the duplicate junction point, but keep
      // its elevation if the previous leg has none there (straight legs have no elevation).
      int start = points.isEmpty() ? 0 : 1;
      if (start == 1 && !legPoints.isEmpty()) {
        TrackPoint last = points.get(points.size() - 1);
        TrackPoint junction = legPoints.get(0);
        if (last.ele() == null && junction.ele() != null) {
          points.set(points.size() - 1, new TrackPoint(last.lng(), last.lat(), junction.ele()));
        }
      }
      points.addAll(legPoints.subList(Math.min(start, legPoints.size()), legPoints.size()));
      distance += leg.distance();
      ascent += leg.ascent();
      breakdown = breakdown.plus(leg.breakdown());
    }
    return new PlannedRoute(points, distance, ascent, descent(points, ascent), breakdown);
  }

  /**
   * Descent consistent with the router's filtered ascent: what goes up and does not end higher
   * comes down again. Falls back to our own estimate when the ends have no elevation.
   */
  private static double descent(List<TrackPoint> points, double ascent) {
    Double first = points.isEmpty() ? null : points.getFirst().ele();
    Double last = points.isEmpty() ? null : points.getLast().ele();
    if (first == null || last == null) {
      return TrackStats.descent(points);
    }
    return Math.max(0, ascent - (last - first));
  }
}
