package app.trailplanner.config;

import java.util.Locale;

/**
 * Where the map opens for someone who has not planned anything yet (setting {@code
 * trailplanner.area}, environment variable TRAILPLANNER_AREA).
 */
public record StartArea(double lat, double lng, double zoom) {

  /** Luxembourg City. */
  public static final StartArea DEFAULT = new StartArea(49.61, 6.13, 10);

  public StartArea {
    if (!(lat >= -85 && lat <= 85) || !(lng >= -180 && lng <= 180) || !(zoom >= 0 && zoom <= 18)) {
      throw new IllegalArgumentException(
          String.format(
              Locale.ROOT, "out of range: latitude %s, longitude %s, zoom %s", lat, lng, zoom));
    }
  }

  /** "latitude,longitude[,zoom]", e.g. "49.61,6.13,10"; blank for the default. */
  public static StartArea parse(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT;
    }
    String[] parts = value.split(",");
    try {
      if (parts.length == 2 || parts.length == 3) {
        return new StartArea(
            Double.parseDouble(parts[0].strip()),
            Double.parseDouble(parts[1].strip()),
            parts.length == 3 ? Double.parseDouble(parts[2].strip()) : DEFAULT.zoom());
      }
    } catch (IllegalArgumentException e) {
      throw invalid(value, e);
    }
    throw invalid(value, null);
  }

  private static IllegalArgumentException invalid(String value, Exception cause) {
    return new IllegalArgumentException(
        "TRAILPLANNER_AREA must be \"latitude,longitude\" or \"latitude,longitude,zoom\""
            + " (e.g. \"49.61,6.13,10\"), not \""
            + value
            + "\"",
        cause);
  }
}
