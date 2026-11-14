package app.trailplanner.routes;

import app.trailplanner.web.ApiException;
import org.springframework.http.HttpStatus;

/** The route cannot be changed in its current state. {@code reason} tells the UI which state. */
public class RouteConflictException extends ApiException {

  private static final long serialVersionUID = 1L;

  public static RouteConflictException inTrash() {
    return new RouteConflictException(
        "IN_TRASH", "This route is in the trash. Restore it first, or save it as a new route.");
  }

  public static RouteConflictException changedElsewhere() {
    return new RouteConflictException(
        "CHANGED_ELSEWHERE",
        "This route was saved elsewhere (another tab or device) since you opened it. Save it as a"
            + " new route, or reopen it to see the other version.");
  }

  public static RouteConflictException alreadyEditable() {
    return new RouteConflictException("ALREADY_EDITABLE", "This route already has waypoints.");
  }

  public static RouteConflictException noOriginal() {
    return new RouteConflictException("NO_ORIGINAL", "This route was not imported.");
  }

  private RouteConflictException(String reason, String message) {
    super(HttpStatus.CONFLICT, "Route not changed", reason, message, null);
  }
}
