package app.trailplan.routing;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoutingService {

  private final ProfileStore profiles;
  private final BRouterClient client;
  private final LegCache cache;

  public RoutingService(ProfileStore profiles, BRouterClient client, LegCache cache) {
    this.profiles = profiles;
    this.client = client;
    this.cache = cache;
  }

  public RouteLeg leg(GeoPoint from, GeoPoint to, RoutingPreferences preferences) {
    String profileId = profiles.profileIdFor(preferences);
    String key = profileId + "|" + from.key() + "|" + to.key();
    return cache.get(key, () -> client.route(from, to, profileId));
  }

  /** Calculates the whole route; legs already calculated while planning come from the cache. */
  public PlannedRoute plan(List<GeoPoint> waypoints, RoutingPreferences preferences) {
    if (waypoints.size() < 2) {
      throw new IllegalArgumentException("A route needs at least two points.");
    }
    List<RouteLeg> legs = new ArrayList<>(waypoints.size() - 1);
    for (int i = 0; i < waypoints.size() - 1; i++) {
      legs.add(leg(waypoints.get(i), waypoints.get(i + 1), preferences));
    }
    return PlannedRoute.join(legs);
  }
}
