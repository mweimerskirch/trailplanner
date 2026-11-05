package app.trailplan.routing;

/**
 * A point the rider placed. {@code leg} says how the leg from this waypoint to the next one is
 * calculated (ignored on the last waypoint). It defaults to {@link LegType#ROUTED} when missing, so
 * waypoints stored before leg types existed stay valid.
 */
public record Waypoint(double lat, double lng, LegType leg) {

  public Waypoint {
    new GeoPoint(lat, lng); // validates the coordinates
    leg = leg == null ? LegType.ROUTED : leg;
  }

  public static Waypoint of(double lat, double lng) {
    return new Waypoint(lat, lng, LegType.ROUTED);
  }

  public GeoPoint point() {
    return new GeoPoint(lat, lng);
  }
}
