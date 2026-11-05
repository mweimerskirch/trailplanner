package app.trailplan.routes;

import java.time.Instant;

/** One entry of the route library. {@code wahooSyncedAt} is null when never sent to Wahoo. */
public record RouteSummary(
    long id,
    String name,
    double distance,
    double ascent,
    Instant updatedAt,
    Instant wahooSyncedAt) {}
