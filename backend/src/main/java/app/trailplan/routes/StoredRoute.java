package app.trailplan.routes;

import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrackPoint;
import app.trailplan.routing.Waypoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A saved route as stored in the database, including the current track.
 *
 * <p>Imported routes are not {@code editable} until the rider converts them; until then they have
 * no waypoints and the track is the imported one. Their {@code original} is kept for good.
 */
public record StoredRoute(
    long id,
    String name,
    List<Waypoint> waypoints,
    boolean editable,
    RoutingPreferences preferences,
    List<TrackPoint> track,
    double distance,
    double ascent,
    double descent,
    Instant createdAt,
    Instant updatedAt,
    RouteSource source,
    Optional<String> externalId,
    Optional<Instant> deletedAt,
    Optional<Instant> archivedAt,
    Optional<OriginalTrack> original) {

  public StoredRoute {
    waypoints = List.copyOf(waypoints);
    track = List.copyOf(track);
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
        editable,
        original.isPresent(),
        deletedAt.orElse(null),
        archivedAt.orElse(null));
  }
}
