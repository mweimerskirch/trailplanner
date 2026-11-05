package app.trailplan.routing;

import java.util.List;

/** The route between two consecutive waypoints. Distances in metres. */
public record RouteLeg(
    List<TrackPoint> points,
    double distance,
    double ascent,
    LegBreakdown breakdown,
    List<RouteWarning> warnings) {

  public RouteLeg {
    points = List.copyOf(points);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public RouteLeg(List<TrackPoint> points, double distance, double ascent, LegBreakdown breakdown) {
    this(points, distance, ascent, breakdown, List.of());
  }
}
