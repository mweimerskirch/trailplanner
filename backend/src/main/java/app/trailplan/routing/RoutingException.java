package app.trailplan.routing;

/** A route could not be calculated. The message is safe to show to the rider. */
public class RoutingException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public enum Reason {
    POINT_NOT_ON_MAP,
    NO_ROUTE,
    ROUTER_UNAVAILABLE,
    ROUTER_ERROR
  }

  private final Reason reason;

  public RoutingException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public RoutingException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
