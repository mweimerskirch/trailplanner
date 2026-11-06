package app.trailplan.presets;

import static app.trailplan.routing.PreferenceLevel.AVOID;
import static app.trailplan.routing.PreferenceLevel.FAVOURITE;
import static app.trailplan.routing.PreferenceLevel.NEUTRAL;
import static app.trailplan.routing.PreferenceLevel.NEVER;
import static app.trailplan.routing.PreferenceLevel.PREFER;
import static app.trailplan.routing.WayCategory.BUSY_ROAD;
import static app.trailplan.routing.WayCategory.CYCLEWAY;
import static app.trailplan.routing.WayCategory.FOOTWAY;
import static app.trailplan.routing.WayCategory.GRAVEL_TRACK;
import static app.trailplan.routing.WayCategory.MAIN_ROAD;
import static app.trailplan.routing.WayCategory.QUIET_ROAD;
import static app.trailplan.routing.WayCategory.ROUGH_TRACK;
import static app.trailplan.routing.WayCategory.TRAIL;

import app.trailplan.routing.PreferenceLevel;
import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrailDifficulty;
import app.trailplan.routing.WayCategory;
import app.trailplan.routing.WoodsPreference;
import java.util.EnumMap;
import java.util.List;

/** The presets every rider starts with. They become the rider's own (editable, deletable). */
public final class DefaultPresets {

  public record Preset(String name, RoutingPreferences preferences) {}

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
                  AVOID)));

  /**
   * Levels in the order trail, rough track, gravel track, footway, cycleway, quiet road, main road,
   * busy road.
   */
  private static RoutingPreferences prefs(
      TrailDifficulty difficulty,
      WoodsPreference woods,
      boolean avoidClimbs,
      PreferenceLevel... levels) {
    WayCategory[] order = {
      TRAIL, ROUGH_TRACK, GRAVEL_TRACK, FOOTWAY, CYCLEWAY, QUIET_ROAD, MAIN_ROAD, BUSY_ROAD
    };
    EnumMap<WayCategory, PreferenceLevel> map = new EnumMap<>(WayCategory.class);
    for (int i = 0; i < order.length; i++) {
      map.put(order[i], levels[i]);
    }
    return new RoutingPreferences(map, difficulty, woods, avoidClimbs);
  }
}
