package app.trailplanner.routing;

import java.util.Locale;

public record GeoPoint(double lat, double lng) {

  public GeoPoint {
    if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
      throw new IllegalArgumentException("Latitude out of range: " + lat);
    }
    if (!Double.isFinite(lng) || lng < -180 || lng > 180) {
      throw new IllegalArgumentException("Longitude out of range: " + lng);
    }
  }

  /** Stable key with ~10 cm precision, used for caching. */
  public String key() {
    return String.format(Locale.ROOT, "%.6f,%.6f", lat, lng);
  }

  /** "lon,lat" as BRouter expects it. */
  public String toLonLat() {
    return String.format(Locale.ROOT, "%.6f,%.6f", lng, lat);
  }
}
