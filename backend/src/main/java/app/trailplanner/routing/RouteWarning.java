package app.trailplanner.routing;

import java.util.Map;
import java.util.Set;

/**
 * A closed or restricted way on a forced leg, at the end of the stretch that has it. The UI
 * translates the {@code code} (a {@link Reason} name); {@code reason} is the English text, shown
 * when the code is null or unknown to it.
 */
public record RouteWarning(double lng, double lat, String reason, String code) {

  /** Why a way is restricted; the UI has a translation for each name. */
  public enum Reason {
    UNDER_CONSTRUCTION("Under construction (closed in OpenStreetMap)"),
    NO_HORSES("No horses allowed"),
    NOT_FOR_HORSES("Footway or cycleway, not open to horses"),
    NO_BIKES("No bikes allowed"),
    USE_SIDEPATH("Bikes must use the cycle path alongside"),
    NO_ACCESS("No access"),
    PRIVATE("Private way"),
    ONEWAY("Against a one-way street");

    private final String text;

    Reason(String text) {
      this.text = text;
    }

    public String text() {
      return text;
    }
  }

  public RouteWarning(double lng, double lat, Reason reason) {
    this(lng, lat, reason.text(), reason.name());
  }

  /** A warning without a code (the reason is shown as it is). */
  public RouteWarning(double lng, double lat, String reason) {
    this(lng, lat, reason, null);
  }

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
    Reason reason = restriction(tags, activity);
    return reason == null ? null : reason.text();
  }

  /** As {@link #reasonFor(Map, Activity)}, as a {@link Reason}. */
  public static Reason restriction(Map<String, String> tags, Activity activity) {
    if ("construction".equals(tags.get("highway"))) {
      return Reason.UNDER_CONSTRUCTION;
    }
    return activity == Activity.HORSE ? horseReason(tags) : bikeReason(tags);
  }

  private static Reason horseReason(Map<String, String> tags) {
    String horse = tags.getOrDefault("horse", "");
    if (!HORSES_ALLOWED.contains(horse)) {
      if (horse.equals("no")) {
        return Reason.NO_HORSES;
      }
      if (NOT_FOR_HORSES.contains(tags.getOrDefault("highway", ""))) {
        return Reason.NOT_FOR_HORSES;
      }
      Reason reason = accessReason(tags);
      if (reason != null || horse.equals("private")) {
        return reason == null ? Reason.PRIVATE : reason;
      }
    }
    if ("yes".equals(tags.get("reversedirection"))
        && ONEWAY.contains(tags.getOrDefault("oneway", ""))) {
      return Reason.ONEWAY;
    }
    return null;
  }

  private static Reason bikeReason(Map<String, String> tags) {
    boolean bikesAllowed = BIKES_ALLOWED.contains(tags.getOrDefault("bicycle", ""));
    if (!bikesAllowed) {
      String bicycle = tags.getOrDefault("bicycle", "");
      if (bicycle.equals("no")) {
        return Reason.NO_BIKES;
      }
      if (bicycle.equals("use_sidepath")) {
        return Reason.USE_SIDEPATH;
      }
      Reason reason = accessReason(tags);
      if (reason != null) {
        return reason;
      }
    }
    if ("yes".equals(tags.get("reversedirection"))
        && ONEWAY.contains(tags.getOrDefault("oneway", ""))
        && !"no".equals(tags.get("oneway:bicycle"))) {
      return Reason.ONEWAY;
    }
    return null;
  }

  private static Reason accessReason(Map<String, String> tags) {
    return switch (tags.getOrDefault("access", "")) {
      case "no" -> Reason.NO_ACCESS;
      case "private" -> Reason.PRIVATE;
      default -> null;
    };
  }
}
