package app.trailplanner.routing;

/** Extra cost factor applied to ways that do not run through forest. */
public enum WoodsPreference {
  INDIFFERENT(1.0),
  PREFER(1.5),
  STRONGLY_PREFER(2.2);

  private final double outsideForestFactor;

  WoodsPreference(double outsideForestFactor) {
    this.outsideForestFactor = outsideForestFactor;
  }

  public double outsideForestFactor() {
    return outsideForestFactor;
  }
}
