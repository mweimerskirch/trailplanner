package app.trailplan.komoot;

import app.trailplan.web.ApiException;
import org.springframework.http.HttpStatus;

/** A Komoot request failed. The message is safe to show to the rider. */
public class KomootException extends ApiException {

  private static final long serialVersionUID = 1L;

  public KomootException(HttpStatus status, String message) {
    super(status, "Komoot import failed", null, message, null);
  }

  public KomootException(HttpStatus status, String message, Throwable cause) {
    super(status, "Komoot import failed", null, message, cause);
  }
}
