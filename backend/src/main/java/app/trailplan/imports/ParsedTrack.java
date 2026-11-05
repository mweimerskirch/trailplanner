package app.trailplan.imports;

import app.trailplan.routing.TrackPoint;
import java.util.List;

/** What a route file contains. {@code name} is null when the file has none. */
public record ParsedTrack(String name, List<TrackPoint> points) {

  public ParsedTrack {
    points = List.copyOf(points);
  }
}
