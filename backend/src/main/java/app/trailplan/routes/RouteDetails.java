package app.trailplan.routes;

import app.trailplan.routing.GeoPoint;
import app.trailplan.routing.RoutingPreferences;
import java.time.Instant;
import java.util.List;

/** A saved route for reopening in the planner. The track is recalculated from the waypoints. */
public record RouteDetails(
    long id,
    String name,
    List<GeoPoint> waypoints,
    RoutingPreferences preferences,
    double distance,
    double ascent,
    Instant createdAt,
    Instant updatedAt,
    Instant wahooSyncedAt) {}
