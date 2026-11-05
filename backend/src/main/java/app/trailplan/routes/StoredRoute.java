package app.trailplan.routes;

import app.trailplan.routing.GeoPoint;
import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrackPoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** A saved route as stored in the database, including the calculated track. */
public record StoredRoute(
    long id,
    String name,
    List<GeoPoint> waypoints,
    RoutingPreferences preferences,
    List<TrackPoint> track,
    double distance,
    double ascent,
    double descent,
    Instant createdAt,
    Instant updatedAt,
    Optional<Long> wahooRouteId,
    Optional<Instant> wahooSyncedAt) {

  public StoredRoute {
    waypoints = List.copyOf(waypoints);
    track = List.copyOf(track);
  }

  public RouteSummary summary() {
    return new RouteSummary(id, name, distance, ascent, updatedAt, wahooSyncedAt.orElse(null));
  }

  public RouteDetails details() {
    return new RouteDetails(
        id,
        name,
        waypoints,
        preferences,
        distance,
        ascent,
        createdAt,
        updatedAt,
        wahooSyncedAt.orElse(null));
  }
}
