package app.trailplanner.presets;

import static app.trailplanner.routing.PreferenceLevel.AVOID;
import static app.trailplanner.routing.PreferenceLevel.FAVOURITE;
import static app.trailplanner.routing.PreferenceLevel.NEUTRAL;
import static app.trailplanner.routing.PreferenceLevel.NEVER;
import static app.trailplanner.routing.PreferenceLevel.PREFER;
import static app.trailplanner.routing.WayCategory.BUSY_ROAD;
import static app.trailplanner.routing.WayCategory.CYCLEWAY;
import static app.trailplanner.routing.WayCategory.FOOTWAY;
import static app.trailplanner.routing.WayCategory.GRAVEL_TRACK;
import static app.trailplanner.routing.WayCategory.MAIN_ROAD;
import static app.trailplanner.routing.WayCategory.QUIET_ROAD;
import static app.trailplanner.routing.WayCategory.ROUGH_TRACK;
import static app.trailplanner.routing.WayCategory.TRAIL;

import app.trailplanner.routing.Activity;
import app.trailplanner.routing.PreferenceLevel;
import app.trailplanner.routing.RoutingPreferences;
import app.trailplanner.routing.TrailDifficulty;
import app.trailplanner.routing.WayCategory;
import app.trailplanner.routing.WoodsPreference;
import java.util.EnumMap;
import java.util.List;

/**
 * The presets every rider starts with, for each activity. They become the rider's own (editable,
 * deletable).
 */
public final class DefaultPresets {

  /**
   * A built-in preset. {@code since} is the {@link #VERSION} that added it: riders seeded with an
   * older version get it on the next start (rider.presets_seeded holds the version they have).
   */
  public record Preset(String name, RoutingPreferences preferences, int since) {

    Preset(String name, RoutingPreferences preferences) {
      this(name, preferences, 1);
    }
  }

  /** 1: the mountain-bike presets; 2: road bike and horseback presets. */
  public static final int VERSION = 2;

  private DefaultPresets() {}

  public static final List<Preset> ALL =
      List.of(
          new Preset(
              "Flowy trails",
              prefs(
                  TrailDifficulty.S2,
                  WoodsPreference.PREFER,
                  false,
                  FAVOURITE,
                  PREFER,
                  PREFER,
                  NEUTRAL,
                  NEUTRAL,
                  AVOID,
                  AVOID,
                  AVOID)),
          new Preset(
              "Technical",
              prefs(
                  TrailDifficulty.S4,
                  WoodsPreference.PREFER,
                  false,
                  FAVOURITE,
                  PREFER,
                  NEUTRAL,
                  NEUTRAL,
                  AVOID,
                  AVOID,
                  AVOID,
                  NEVER)),
          new Preset(
              "Gravel / XC",
              prefs(
                  TrailDifficulty.S1,
                  WoodsPreference.INDIFFERENT,
                  false,
                  NEUTRAL,
                  FAVOURITE,
                  FAVOURITE,
                  NEUTRAL,
                  PREFER,
                  NEUTRAL,
                  AVOID,
                  AVOID)),
          new Preset(
              "Easy / family",
              prefs(
                  TrailDifficulty.S0,
                  WoodsPreference.INDIFFERENT,
                  true,
                  NEUTRAL,
                  NEUTRAL,
                  PREFER,
                  NEUTRAL,
                  FAVOURITE,
                  PREFER,
                  AVOID,
                  NEVER)),
          new Preset(
              "Fast connection",
              prefs(
                  TrailDifficulty.S1,
                  WoodsPreference.INDIFFERENT,
                  false,
                  AVOID,
                  AVOID,
                  NEUTRAL,
                  AVOID,
                  FAVOURITE,
                  PREFER,
                  NEUTRAL,
                  AVOID)),
          new Preset(
              "Quiet roads",
              prefs(
                  Activity.ROAD,
                  false,
                  NEVER,
                  NEVER,
                  AVOID,
                  AVOID,
                  PREFER,
                  FAVOURITE,
                  NEUTRAL,
                  AVOID),
              2),
          new Preset(
              "Fast roads",
              prefs(
                  Activity.ROAD,
                  false,
                  NEVER,
                  NEVER,
                  NEVER,
                  NEVER,
                  NEUTRAL,
                  PREFER,
                  FAVOURITE,
                  NEUTRAL),
              2),
          new Preset(
              "Bike paths, relaxed",
              prefs(
                  Activity.ROAD,
                  true,
                  AVOID,
                  NEVER,
                  NEUTRAL,
                  NEUTRAL,
                  FAVOURITE,
                  PREFER,
                  AVOID,
                  NEVER),
              2),
          new Preset(
              "Bridleways & forest",
              prefs(
                  Activity.HORSE,
                  WoodsPreference.PREFER,
                  false,
                  FAVOURITE,
                  FAVOURITE,
                  PREFER,
                  NEUTRAL,
                  AVOID,
                  NEUTRAL,
                  AVOID,
                  NEVER),
              2),
          new Preset(
              "Gentle hack",
              prefs(
                  Activity.HORSE,
                  WoodsPreference.INDIFFERENT,
                  true,
                  PREFER,
                  PREFER,
                  FAVOURITE,
                  NEUTRAL,
                  AVOID,
                  PREFER,
                  AVOID,
                  NEVER),
              2),
          new Preset(
              "Direct",
              prefs(
                  Activity.HORSE,
                  WoodsPreference.INDIFFERENT,
                  false,
                  PREFER,
                  PREFER,
                  PREFER,
                  NEUTRAL,
                  NEUTRAL,
                  PREFER,
                  NEUTRAL,
                  AVOID),
              2));

  /**
   * Levels in the order trail, rough track, gravel track, footway, cycleway, quiet road, main road,
   * busy road.
   */
  private static RoutingPreferences prefs(
      TrailDifficulty difficulty,
      WoodsPreference woods,
      boolean avoidClimbs,
      PreferenceLevel... levels) {
    return new RoutingPreferences(levels(levels), difficulty, woods, avoidClimbs, Activity.MTB);
  }

  /** Road bike: no trail difficulty or woods (they do not apply). Levels as above. */
  private static RoutingPreferences prefs(
      Activity activity, boolean avoidClimbs, PreferenceLevel... levels) {
    return prefs(activity, WoodsPreference.INDIFFERENT, avoidClimbs, levels);
  }

  /** Other activities: the trail difficulty does not apply. Levels as above. */
  private static RoutingPreferences prefs(
      Activity activity, WoodsPreference woods, boolean avoidClimbs, PreferenceLevel... levels) {
    return new RoutingPreferences(
        levels(levels), TrailDifficulty.ANY, woods, avoidClimbs, activity);
  }

  private static EnumMap<WayCategory, PreferenceLevel> levels(PreferenceLevel... levels) {
    WayCategory[] order = {
      TRAIL, ROUGH_TRACK, GRAVEL_TRACK, FOOTWAY, CYCLEWAY, QUIET_ROAD, MAIN_ROAD, BUSY_ROAD
    };
    EnumMap<WayCategory, PreferenceLevel> map = new EnumMap<>(WayCategory.class);
    for (int i = 0; i < order.length; i++) {
      map.put(order[i], levels[i]);
    }
    return map;
  }
}
