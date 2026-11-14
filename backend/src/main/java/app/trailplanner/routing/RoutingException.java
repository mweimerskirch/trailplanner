package app.trailplanner.routing;

import app.trailplanner.web.ApiException;
import org.springframework.http.HttpStatus;

/** A route could not be calculated. The message is safe to show to the rider. */
public class RoutingException extends ApiException {

  private static final long serialVersionUID = 1L;

  public enum Reason {
    POINT_NOT_ON_MAP(HttpStatus.UNPROCESSABLE_ENTITY),
    NO_ROUTE(HttpStatus.UNPROCESSABLE_ENTITY),
    ROUTER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    ROUTER_ERROR(HttpStatus.BAD_GATEWAY);

    private final HttpStatus status;

    Reason(HttpStatus status) {
      this.status = status;
    }
  }

  private final Reason reason;

  public RoutingException(Reason reason, String message) {
    this(reason, message, null);
  }

  public RoutingException(Reason reason, String message, Throwable cause) {
    super(reason.status, "Route not calculated", reason.name(), message, cause);
    this.reason = reason;
  }

  public Reason routingReason() {
    return reason;
  }
}
