package app.trailplan.routes;

import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrackPoint;
import app.trailplan.routing.Waypoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A saved route as stored in the database, including the calculated track.
 *
 * <p>Imported routes have no waypoints until the rider converts them for editing; until then the
 * stored track is the original, imported one.
 */
public record StoredRoute(
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
    Optional<String> externalId,
    Optional<Instant> deletedAt) {

  public StoredRoute {
    waypoints = List.copyOf(waypoints);
    track = List.copyOf(track);
  }

  /** False for imported routes that have not been converted to waypoints yet. */
  public boolean editable() {
    return !waypoints.isEmpty();
  }

  public RouteDetails details() {
    return new RouteDetails(
        id,
        name,
        waypoints,
        preferences,
        track,
        distance,
        ascent,
        descent,
        createdAt,
        updatedAt,
        source,
        editable(),
        deletedAt.orElse(null));
  }
}
