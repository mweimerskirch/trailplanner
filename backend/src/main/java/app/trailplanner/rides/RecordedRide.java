package app.trailplanner.rides;

import app.trailplanner.routing.TrackPoint;
import java.time.Instant;
import java.util.List;

/**
 * What a recorded activity file says about the ride. Durations in seconds, distances in metres,
 * speeds in m/s. Values the device did not record are null.
 *
 * @param utcOffset the device's local time minus UTC at the end of the ride, in seconds
 * @param sport e.g. {@code cycling}, {@code running}; null if not recorded
 */
public record RecordedRide(
    Instant startedAt,
    Integer utcOffset,
    String sport,
    double elapsed,
    double moving,
    double distance,
    double ascent,
    double descent,
    Double maxSpeed,
    Integer avgHeartRate,
    Integer maxHeartRate,
    Integer avgPower,
    Integer calories,
    List<TrackPoint> track) {

  public RecordedRide {
    track = List.copyOf(track);
  }
}
