package app.trailplanner.routes;

import app.trailplanner.web.ApiException;
import org.springframework.http.HttpStatus;

public class RouteNotFoundException extends ApiException {

  private static final long serialVersionUID = 1L;

  public RouteNotFoundException(long id) {
    super(HttpStatus.NOT_FOUND, "Route not found", "Route " + id + " does not exist.");
  }
}
