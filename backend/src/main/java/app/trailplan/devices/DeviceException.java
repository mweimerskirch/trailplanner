package app.trailplan.devices;

import org.springframework.http.HttpStatus;

/** A device or cloud request failed. The message is safe to show to the rider. */
public class DeviceException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;

  public DeviceException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public DeviceException(HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
