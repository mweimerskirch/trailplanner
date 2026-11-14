package app.trailplanner.routing;

/** How the leg from a waypoint to the next one is calculated. */
public enum LegType {
  /** Routed with the rider's preferences. */
  ROUTED,
  /**
   * Routed, but closed and restricted ways (under construction, no access, no bikes, against a
   * one-way street) are allowed, at a cost. Used where the rider knows they can pass; the route
   * then carries warnings at those ways.
   */
  FORCED,
  /** A straight line, for stretches without any mapped way. */
  STRAIGHT
}
