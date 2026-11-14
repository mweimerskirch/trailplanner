package app.trailplanner.routes;

import app.trailplanner.routing.TrackPoint;
import java.util.List;

/** A finished track from a file or from Komoot, before it is stored. Distances in metres. */
public record ImportedRoute(
    String name,
    RouteSource source,
    String externalId,
    List<TrackPoint> track,
    double distance,
    double ascent,
    double descent) {

  public ImportedRoute {
    track = List.copyOf(track);
  }
}
