package app.trailplan.routes;

/** Where a route came from. Imported routes start as a fixed track without waypoints. */
public enum RouteSource {
  PLANNER,
  GPX,
  FIT,
  KOMOOT
}
