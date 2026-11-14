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
  private static final Set<String> HORSES_ALLOWED =
      Set.of("yes", "permissive", "designated", "official", "destination");
  private static final Set<String> NOT_FOR_HORSES = Set.of("footway", "pedestrian", "cycleway");
  private static final Set<String> ONEWAY = Set.of("yes", "true", "1");

  /**
   * Why a way with these OSM tags is normally avoided, or null if it is not restricted. Mirrors the
   * blocking rules in {@link BRouterProfileGenerator}.
   */
  public static String reasonFor(Map<String, String> tags) {
    return reasonFor(tags, Activity.MTB);
  }

  /** As {@link #reasonFor(Map)}, with the access rules of the activity (bike or horse). */
  public static String reasonFor(Map<String, String> tags, Activity activity) {
    if ("construction".equals(tags.get("highway"))) {
      return "Under construction (closed in OpenStreetMap)";
    }
    return activity == Activity.HORSE ? horseReason(tags) : bikeReason(tags);
  }

  private static String horseReason(Map<String, String> tags) {
    String horse = tags.getOrDefault("horse", "");
    if (!HORSES_ALLOWED.contains(horse)) {
      if (horse.equals("no")) {
        return "No horses allowed";
      }
      if (NOT_FOR_HORSES.contains(tags.getOrDefault("highway", ""))) {
        return "Footway or cycleway, not open to horses";
      }
      String reason = accessReason(tags);
      if (reason != null || horse.equals("private")) {
        return reason == null ? "Private way" : reason;
      }
    }
    if ("yes".equals(tags.get("reversedirection"))
        && ONEWAY.contains(tags.getOrDefault("oneway", ""))) {
      return "Against a one-way street";
    }
    return null;
  }

  private static String bikeReason(Map<String, String> tags) {
    boolean bikesAllowed = BIKES_ALLOWED.contains(tags.getOrDefault("bicycle", ""));
    if (!bikesAllowed) {
      String bicycle = tags.getOrDefault("bicycle", "");
      if (bicycle.equals("no")) {
        return "No bikes allowed";
      }
      if (bicycle.equals("use_sidepath")) {
        return "Bikes must use the cycle path alongside";
      }
      String reason = accessReason(tags);
      if (reason != null) {
        return reason;
      }
    }
    if ("yes".equals(tags.get("reversedirection"))
        && ONEWAY.contains(tags.getOrDefault("oneway", ""))
        && !"no".equals(tags.get("oneway:bicycle"))) {
      return "Against a one-way street";
    }
    return null;
  }

  private static String accessReason(Map<String, String> tags) {
    return switch (tags.getOrDefault("access", "")) {
      case "no" -> "No access";
      case "private" -> "Private way";
      default -> null;
    };
  }
}
