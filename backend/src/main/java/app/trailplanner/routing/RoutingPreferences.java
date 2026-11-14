package app.trailplanner.routing;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Everything the rider can tune. Missing values fall back to defaults, so older saved routes and
 * partial requests stay valid when new categories are added.
 *
 * <p>{@code avoidClimbs} is a {@code Boolean} so that a missing value can default to false: Jackson
 * 3 rejects a missing primitive by default ({@code FAIL_ON_NULL_FOR_PRIMITIVES}). It is never null
 * after construction. A missing {@code activity} means {@link Activity#MTB}.
 */
public record RoutingPreferences(
    Map<WayCategory, PreferenceLevel> levels,
    TrailDifficulty maxDifficulty,
    WoodsPreference woods,
    Boolean avoidClimbs,
    Activity activity) {

  public RoutingPreferences {
    EnumMap<WayCategory, PreferenceLevel> complete = new EnumMap<>(WayCategory.class);
    for (WayCategory category : WayCategory.values()) {
      PreferenceLevel level = levels == null ? null : levels.get(category);
      complete.put(category, level == null ? category.defaultLevel() : level);
    }
    levels = Collections.unmodifiableMap(complete);
    maxDifficulty = maxDifficulty == null ? TrailDifficulty.S3 : maxDifficulty;
    woods = woods == null ? WoodsPreference.PREFER : woods;
    avoidClimbs = Boolean.TRUE.equals(avoidClimbs);
    activity = activity == null ? Activity.MTB : activity;
  }

  /** Mountain-bike preferences (saved routes and presets from before activities existed). */
  public RoutingPreferences(
      Map<WayCategory, PreferenceLevel> levels,
      TrailDifficulty maxDifficulty,
      WoodsPreference woods,
      Boolean avoidClimbs) {
    this(levels, maxDifficulty, woods, avoidClimbs, null);
  }

  public static RoutingPreferences defaults() {
    return new RoutingPreferences(Map.of(), null, null, false);
  }

  public PreferenceLevel level(WayCategory category) {
    return levels.get(category);
  }
}
