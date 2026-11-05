package app.trailplan.routes;

import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrackPoint;
import app.trailplan.routing.Waypoint;
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
    Instant archivedAt) {}
