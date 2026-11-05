package app.trailplan.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns {@link ApiException}s into RFC 9457 problem responses the UI can show as-is. */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler
  ProblemDetail api(ApiException e) {
    if (e.status().is5xxServerError()) {
      log.warn("{}: {}", e.title(), e.getMessage(), e.getCause());
    }
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    problem.setTitle(e.title());
    if (e.reason() != null) {
      problem.setProperty("reason", e.reason());
    }
    return problem;
  }
}
