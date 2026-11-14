package app.trailplanner.users;

import app.trailplanner.web.ApiException;
import org.springframework.http.HttpStatus;

/** The request needs a rider, but none is chosen (or the chosen one was deleted). */
public class NoUserSelectedException extends ApiException {

  private static final long serialVersionUID = 1L;

  public NoUserSelectedException() {
    super(HttpStatus.UNAUTHORIZED, "No rider chosen", "NO_USER", "Choose a rider first.", null);
  }
}
