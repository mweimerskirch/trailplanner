package app.trailplan.export;

import app.trailplan.routing.TrackPoint;
import java.util.List;

/** Everything a format may need. Climbing in metres, from the router or the import source. */
public record ExportedRoute(String name, List<TrackPoint> track, double ascent, double descent) {

  public ExportedRoute {
    track = List.copyOf(track);
  }
}
