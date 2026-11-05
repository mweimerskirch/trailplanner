package app.trailplan.routing;

/** Hardest mtb:scale grade the route may use. Only applies where trails carry the tag. */
public enum TrailDifficulty {
  S0,
  S1,
  S2,
  S3,
  S4,
  S5,
  ANY;

  /** The highest allowed mtb:scale value, or 6 (the top of the scale) for {@link #ANY}. */
  public int maxScale() {
    return this == ANY ? 6 : ordinal();
  }
}
