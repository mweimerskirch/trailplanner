package app.trailplanner.routing;

/** How much the rider wants a way type. The cost factor is what BRouter multiplies distance by. */
public enum PreferenceLevel {
  NEVER(10_000),
  AVOID(6),
  NEUTRAL(2.5),
  PREFER(1.5),
  FAVOURITE(1);

  private final double costFactor;

  PreferenceLevel(double costFactor) {
    this.costFactor = costFactor;
  }

  public double costFactor() {
    return costFactor;
  }
}
