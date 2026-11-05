package app.trailplan.routing;

import java.util.Map;
import java.util.Set;

/**
 * A closed or restricted way on a forced leg, at the end of the stretch that has it. The reason is
 * shown to the rider as-is.
 */
public record RouteWarning(double lng, double lat, String reason) {

  private static final Set<String> BIKES_ALLOWED =
      Set.of("yes", "permissive", "designated", "destination");
  private static final Set<String> ONEWAY = Set.of("yes", "true", "1");

  /**
   * Why a way with these OSM tags is normally avoided, or null if it is not restricted. Mirrors the
   * blocking rules in {@link BRouterProfileGenerator}.
   */
  public static String reasonFor(Map<String, String> tags) {
    if ("construction".equals(tags.get("highway"))) {
      return "Under construction (closed in OpenStreetMap)";
    }
    boolean bikesAllowed = BIKES_ALLOWED.contains(tags.getOrDefault("bicycle", ""));
    if (!bikesAllowed) {
      String bicycle = tags.getOrDefault("bicycle", "");
      if (bicycle.equals("no")) {
        return "No bikes allowed";
      }
      if (bicycle.equals("use_sidepath")) {
        return "Bikes must use the cycle path alongside";
      }
      String access = tags.getOrDefault("access", "");
      if (access.equals("no")) {
        return "No access";
      }
      if (access.equals("private")) {
        return "Private way";
      }
    }
    if ("yes".equals(tags.get("reversedirection"))
        && ONEWAY.contains(tags.getOrDefault("oneway", ""))
        && !"no".equals(tags.get("oneway:bicycle"))) {
      return "Against a one-way street";
    }
    return null;
  }
}
