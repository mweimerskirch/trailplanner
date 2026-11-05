package app.trailplan.routing;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One point of a calculated track. Serialized as a compact {@code [lng, lat, ele]} array, the same
 * order GeoJSON uses. Elevation is null where the elevation model has no data.
 */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder({"lng", "lat", "ele"})
public record TrackPoint(double lng, double lat, Double ele) {

  private static final double EARTH_RADIUS_M = 6_371_000;

  public double distanceTo(TrackPoint other) {
    double dLat = Math.toRadians(other.lat - lat);
    double dLng = Math.toRadians(other.lng - lng);
    double a =
        Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat))
                * Math.cos(Math.toRadians(other.lat))
                * Math.pow(Math.sin(dLng / 2), 2);
    return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
  }
}
