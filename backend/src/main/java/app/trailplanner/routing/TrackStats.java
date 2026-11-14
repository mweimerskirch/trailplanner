package app.trailplanner.routing;

import java.util.List;

/** Distance and climbing of a track. Elevation sums use a 3 m hysteresis to ignore noise. */
public final class TrackStats {

  private static final double HYSTERESIS_M = 3;

  private TrackStats() {}

  /** Length along the track in metres. */
  public static double distance(List<TrackPoint> points) {
    double total = 0;
    for (int i = 1; i < points.size(); i++) {
      total += points.get(i - 1).distanceTo(points.get(i));
    }
    return total;
  }

  public static double ascent(List<TrackPoint> points) {
    return climb(points, 1);
  }

  public static double descent(List<TrackPoint> points) {
    return climb(points, -1);
  }

  /**
   * Sums changes in one direction (1 = up, -1 = down). The reference follows the track while it
   * moves against that direction, and a change only counts once it reaches the hysteresis.
   */
  private static double climb(List<TrackPoint> points, int direction) {
    double total = 0;
    Double reference = null;
    for (TrackPoint p : points) {
      Double ele = p.ele();
      if (ele == null) {
        continue;
      }
      double change = reference == null ? 0 : (ele - reference) * direction;
      if (reference == null || change < 0) {
        reference = ele;
      } else if (change >= HYSTERESIS_M) {
        total += change;
        reference = ele;
      }
    }
    return total;
  }
}
