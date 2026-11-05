package app.trailplan.routing;

import java.util.List;

/** The route between two consecutive waypoints. Distances in metres. */
public record RouteLeg(
    List<TrackPoint> points, double distance, double ascent, LegBreakdown breakdown) {

  public RouteLeg {
    points = List.copyOf(points);
  }
}
