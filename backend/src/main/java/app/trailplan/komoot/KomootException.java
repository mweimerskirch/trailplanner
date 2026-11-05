package app.trailplan.komoot;

import org.springframework.http.HttpStatus;

/** A Komoot request failed. The message is safe to show to the rider. */
public class KomootException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;

  public KomootException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public KomootException(HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
