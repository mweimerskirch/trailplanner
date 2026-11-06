package app.trailplan.rides;

import java.time.Instant;

/**
 * A ride as listed. Durations in seconds, distances in metres, speeds in m/s; values the device did
 * not record are null.
 *
 * @param utcOffset local time minus UTC at the ride, in seconds; null if unknown
 * @param avgSpeed distance over moving time
 */
public record RideSummary(
    long id,
    String name,
    String sport,
    Instant startedAt,
    Integer utcOffset,
    double elapsed,
    double moving,
    double distance,
    double ascent,
    double descent,
    Double avgSpeed,
    Double maxSpeed,
    Integer avgHeartRate,
    Integer maxHeartRate,
    Integer avgPower,
    Integer calories,
    String fileName) {}
