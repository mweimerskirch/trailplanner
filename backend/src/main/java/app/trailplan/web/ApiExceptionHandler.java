package app.trailplan.web;

import app.trailplan.devices.DeviceException;
import app.trailplan.komoot.KomootException;
import app.trailplan.routes.RouteNotFoundException;
import app.trailplan.routing.RoutingException;
import app.trailplan.users.NoUserSelectedException;
import app.trailplan.wahoo.WahooException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns domain errors into RFC 9457 problem responses with messages the UI can show as-is. */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler
  ProblemDetail routing(RoutingException e) {
    HttpStatus status =
        switch (e.reason()) {
          case POINT_NOT_ON_MAP, NO_ROUTE -> HttpStatus.UNPROCESSABLE_ENTITY;
          case ROUTER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
          case ROUTER_ERROR -> HttpStatus.BAD_GATEWAY;
        };
    if (status.is5xxServerError()) {
      log.warn("Routing failed: {}", e.getMessage(), e.getCause());
    }
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
    problem.setTitle("Route not calculated");
    problem.setProperty("reason", e.reason().name());
    return problem;
  }

  @ExceptionHandler
  ProblemDetail notFound(RouteNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler
  ProblemDetail badRequest(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler
  ProblemDetail wahoo(WahooException e) {
    log.warn("Wahoo request failed: {}", e.getMessage(), e.getCause());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    problem.setTitle("Wahoo sync failed");
    return problem;
  }

  @ExceptionHandler
  ProblemDetail komoot(KomootException e) {
    if (e.status().is5xxServerError()) {
      log.warn("Komoot request failed: {}", e.getMessage(), e.getCause());
    }
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    problem.setTitle("Komoot import failed");
    return problem;
  }

  @ExceptionHandler
  ProblemDetail device(DeviceException e) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    problem.setTitle("Sending failed");
    return problem;
  }

  /** The UI reacts to reason NO_USER by showing the rider selection. */
  @ExceptionHandler
  ProblemDetail noUser(NoUserSelectedException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    problem.setTitle("No rider chosen");
    problem.setProperty("reason", "NO_USER");
    return problem;
  }
}
