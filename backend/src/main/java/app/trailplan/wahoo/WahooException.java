package app.trailplan.wahoo;

import app.trailplan.web.ApiException;
import org.springframework.http.HttpStatus;

/** A Wahoo request failed. The message is safe to show to the rider. */
public class WahooException extends ApiException {

  private static final long serialVersionUID = 1L;

  public WahooException(HttpStatus status, String message) {
    super(status, "Wahoo sync failed", null, message, null);
  }

  public WahooException(HttpStatus status, String message, Throwable cause) {
    super(status, "Wahoo sync failed", null, message, cause);
  }
}
