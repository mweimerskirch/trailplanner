package app.trailplan.routing;

import java.util.Set;

public enum Surface {
  PAVED,
  UNPAVED,
  UNKNOWN;

  private static final Set<String> PAVED_VALUES =
      Set.of(
          "asphalt",
          "concrete",
          "concrete:plates",
          "concrete:lanes",
          "paving_stones",
          "sett",
          "chipseal",
          "paved",
          "metal",
          "wood");

  public static Surface classify(String surfaceTag) {
    if (surfaceTag == null || surfaceTag.isBlank()) {
      return UNKNOWN;
    }
    return PAVED_VALUES.contains(surfaceTag) ? PAVED : UNPAVED;
  }
}
