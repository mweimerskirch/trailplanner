package app.trailplanner.export;

import app.trailplanner.routes.StoredRoute;
import app.trailplanner.routing.TrackPoint;
import java.util.List;

/** Everything a format may need. Climbing in metres, from the router or the import source. */
public record ExportedRoute(String name, List<TrackPoint> track, double ascent, double descent) {

  public ExportedRoute {
    track = List.copyOf(track);
  }

  /** A saved route with its stored track. */
  public static ExportedRoute of(StoredRoute route) {
    return new ExportedRoute(route.name(), route.track(), route.ascent(), route.descent());
  }
}
