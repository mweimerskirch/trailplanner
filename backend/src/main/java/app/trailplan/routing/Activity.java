package app.trailplan.routing;

/**
 * What the route is planned for. It decides which ways are allowed (bike or horse access) and how
 * surfaces count; the rider's way-type levels apply on top.
 */
public enum Activity {
  /** Mountain bike: trails welcome, limited by the mtb:scale difficulty. */
  MTB,
  /** Road bike: paved ways only; rough surfaces and graded trails are blocked. */
  ROAD,
  /** Horseback: follows horse access, avoids hard surfaces, steps and impassable barriers. */
  HORSE
}
