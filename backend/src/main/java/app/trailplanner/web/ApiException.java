package app.trailplanner.web;

import org.springframework.http.HttpStatus;

/**
 * An error with a message that is safe to show to the rider as-is. Answered as an RFC 9457 problem
 * with this status, title, detail (the message) and, if set, a machine-readable {@code reason} the
 * UI can react to. Errors from other exceptions are internal (500).
 */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;
  private final String title;
  private final String reason;

  public ApiException(HttpStatus status, String title, String message) {
    this(status, title, null, message, null);
  }

  public ApiException(
      HttpStatus status, String title, String reason, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.title = title;
    this.reason = reason;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  /** Machine-readable reason, e.g. NO_USER, or null. */
  public String reason() {
    return reason;
  }
}
