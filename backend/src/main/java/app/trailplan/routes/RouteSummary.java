package app.trailplan.routes;

import java.time.Instant;

/**
 * One entry of the route library. {@code deletedAt} is null unless the route is in the trash,
 * {@code archivedAt} null unless it is archived, and {@code editable} is false for imported routes
 * that have not been converted to waypoints yet.
 */
public record RouteSummary(
    long id,
    String name,
    double distance,
    double ascent,
    Instant updatedAt,
    RouteSource source,
    boolean editable,
    Instant deletedAt,
    Instant archivedAt) {}
