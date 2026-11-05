package app.trailplan.wahoo;

import org.springframework.http.HttpStatus;

public class WahooException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;

  public WahooException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public WahooException(HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
