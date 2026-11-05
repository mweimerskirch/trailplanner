package app.trailplan.routes;

public class RouteNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RouteNotFoundException(long id) {
    super("Route " + id + " does not exist.");
  }
}
