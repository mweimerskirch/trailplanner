package app.trailplanner.routing;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class RoutingService {

  /** BRouter runs with 4 threads (brouter/Dockerfile); more parallel requests would only queue. */
  private static final int PARALLEL_LEGS = 4;

  private final ProfileStore profiles;
  private final BRouterClient client;
  private final LegCache cache;
  private final ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_LEGS);

  public RoutingService(ProfileStore profiles, BRouterClient client, LegCache cache) {
    this.profiles = profiles;
    this.client = client;
    this.cache = cache;
  }

  public RouteLeg leg(GeoPoint from, GeoPoint to, RoutingPreferences preferences) {
    return leg(from, to, preferences, LegType.ROUTED);
  }

  /** One leg of the given type; straight legs need no router. */
  public RouteLeg leg(GeoPoint from, GeoPoint to, RoutingPreferences preferences, LegType type) {
    if (type == LegType.STRAIGHT) {
      return straightLeg(from, to);
    }
    String profileId = profiles.profileIdFor(preferences, type == LegType.FORCED);
    String key = profileId + "|" + from.key() + "|" + to.key();
    return cache.get(key, () -> client.route(from, to, profileId, preferences.activity()));
  }

  /** A leg drawn as a straight line: no router involved, way type and elevation unknown. */
  public static RouteLeg straightLeg(GeoPoint from, GeoPoint to) {
    TrackPoint a = new TrackPoint(from.lng(), from.lat(), null);
    TrackPoint b = new TrackPoint(to.lng(), to.lat(), null);
    double distance = a.distanceTo(b);
    return new RouteLeg(
        List.of(a, b),
        distance,
        0,
        new LegBreakdown(Map.of(), distance, Map.of(Surface.UNKNOWN, distance)));
  }

  /**
   * Calculates the whole route. Legs already calculated while planning come from the cache; the
   * others are routed in parallel, so saving a route with many waypoints stays fast.
   */
  public PlannedRoute plan(List<Waypoint> waypoints, RoutingPreferences preferences) {
    if (waypoints.size() < 2) {
      throw new IllegalArgumentException("A route needs at least two points.");
    }
    List<CompletableFuture<RouteLeg>> legs = new ArrayList<>(waypoints.size() - 1);
    for (int i = 0; i < waypoints.size() - 1; i++) {
      Waypoint from = waypoints.get(i);
      Waypoint to = waypoints.get(i + 1);
      legs.add(
          from.leg() == LegType.STRAIGHT
              ? CompletableFuture.completedFuture(straightLeg(from.point(), to.point()))
              : CompletableFuture.supplyAsync(
                  () -> leg(from.point(), to.point(), preferences, from.leg()), executor));
    }
    try {
      return PlannedRoute.join(legs.stream().map(CompletableFuture::join).toList());
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException cause) {
        throw cause; // e.g. RoutingException with a message for the rider
      }
      throw e;
    }
  }

  /**
   * Waypoints that make the router reproduce a finished track (see {@link TrackConverter}), routed
   * with the rider's preferences.
   */
  public TrackConverter.Result convertTrack(
      List<TrackPoint> track, RoutingPreferences preferences, int maxWaypoints) {
    return new TrackConverter(
            (from, to) -> leg(from, to, preferences, LegType.ROUTED),
            (from, to) -> leg(from, to, preferences, LegType.FORCED),
            executor,
            maxWaypoints)
        .convert(track);
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }
}
