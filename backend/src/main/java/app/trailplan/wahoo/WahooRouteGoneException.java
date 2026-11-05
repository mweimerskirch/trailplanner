package app.trailplan.wahoo;

import org.springframework.http.HttpStatus;

/** The Wahoo route we linked to no longer exists (deleted in the Wahoo app). */
public class WahooRouteGoneException extends WahooException {

  private static final long serialVersionUID = 1L;

  public WahooRouteGoneException(Throwable cause) {
    super(HttpStatus.NOT_FOUND, "The route no longer exists in your Wahoo account.", cause);
  }
}
