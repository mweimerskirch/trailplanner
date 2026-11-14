package app.trailplanner.routes;

import app.trailplanner.routing.RoutingPreferences;
import app.trailplanner.routing.TrackPoint;
import app.trailplanner.routing.Waypoint;
import java.time.Instant;
import java.util.List;

/**
 * A saved route for reopening in the planner. Editable routes are recalculated from their
 * waypoints; for imported routes that are not editable yet, {@code track} is what the planner
 * shows. {@code hasOriginal} is true for imports, whose original track can be restored.
 */
public record RouteDetails(
    long id,
    String name,
    List<Waypoint> waypoints,
    RoutingPreferences preferences,
    List<TrackPoint> track,
    double distance,
    double ascent,
    double descent,
    Instant createdAt,
    Instant updatedAt,
    RouteSource source,
    boolean editable,
    boolean hasOriginal,
    Instant deletedAt,
    Instant archivedAt,
    boolean favorite) {}
