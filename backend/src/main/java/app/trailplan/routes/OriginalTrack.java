package app.trailplan.routes;

import app.trailplan.routing.TrackPoint;
import java.util.List;

/** An imported route's track and statistics as imported; edits never change it. */
public record OriginalTrack(
    List<TrackPoint> track, double distance, double ascent, double descent) {

  public OriginalTrack {
    track = List.copyOf(track);
  }
}
