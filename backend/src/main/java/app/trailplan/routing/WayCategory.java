package app.trailplan.routing;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Way types the rider can weight. The same grouping is used for the BRouter profile and for the
 * way-type breakdown of a calculated route, so both always agree.
 */
public enum WayCategory {
  TRAIL(PreferenceLevel.FAVOURITE),
  ROUGH_TRACK(PreferenceLevel.PREFER),
  GRAVEL_TRACK(PreferenceLevel.PREFER),
  FOOTWAY(PreferenceLevel.NEUTRAL),
  CYCLEWAY(PreferenceLevel.NEUTRAL),
  QUIET_ROAD(PreferenceLevel.AVOID),
  MAIN_ROAD(PreferenceLevel.AVOID),
  BUSY_ROAD(PreferenceLevel.AVOID);

  private static final Set<String> QUIET =
      Set.of("residential", "unclassified", "service", "living_street", "road");
  private static final Set<String> MAIN =
      Set.of("secondary", "secondary_link", "tertiary", "tertiary_link");
  private static final Set<String> BUSY = Set.of("primary", "primary_link", "trunk", "trunk_link");

  private final PreferenceLevel defaultLevel;

  WayCategory(PreferenceLevel defaultLevel) {
    this.defaultLevel = defaultLevel;
  }

  public PreferenceLevel defaultLevel() {
    return defaultLevel;
  }

  /** Classifies a way from its OSM tags; empty for ways outside all categories (ferries etc.). */
  public static Optional<WayCategory> classify(Map<String, String> tags) {
    String highway = tags.getOrDefault("highway", "");
    return Optional.ofNullable(
        switch (highway) {
          case "path", "bridleway" -> TRAIL;
          case "track" -> {
            String grade = tags.getOrDefault("tracktype", "");
            yield grade.equals("grade1") || grade.equals("grade2") ? GRAVEL_TRACK : ROUGH_TRACK;
          }
          case "footway", "pedestrian", "steps" -> FOOTWAY;
          case "cycleway" -> CYCLEWAY;
          default -> {
            if (QUIET.contains(highway)) {
              yield QUIET_ROAD;
            }
            if (MAIN.contains(highway)) {
              yield MAIN_ROAD;
            }
            yield BUSY.contains(highway) ? BUSY_ROAD : null;
          }
        });
  }
}
