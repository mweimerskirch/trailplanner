package app.trailplanner.export;

import app.trailplanner.routes.OriginalTrack;
import app.trailplanner.routes.RouteConflictException;
import app.trailplanner.routes.RouteDraft;
import app.trailplanner.routes.RouteService;
import app.trailplanner.routes.StoredRoute;
import app.trailplanner.routing.PlannedRoute;
import app.trailplanner.users.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Downloads in any registered {@link RouteFormat}. */
@RestController
@RequestMapping("/api")
public class ExportController {

  private final RouteService routes;
  private final RouteFormats formats;

  public ExportController(RouteService routes, RouteFormats formats) {
    this.routes = routes;
    this.formats = formats;
  }

  /**
   * A saved route with its stored track; with {@code original=true}, an imported route's original
   * track as imported, even after it was edited.
   */
  @GetMapping("/routes/{id}/export/{format}")
  public ResponseEntity<byte[]> saved(
      CurrentUser user,
      @PathVariable long id,
      @PathVariable String format,
      @RequestParam(defaultValue = "false") boolean original) {
    StoredRoute route = routes.get(user, id);
    if (original) {
      OriginalTrack o = route.original().orElseThrow(RouteConflictException::noOriginal);
      return file(
          formats.get(format), new ExportedRoute(route.name(), o.track(), o.ascent(), o.descent()));
    }
    return file(formats.get(format), ExportedRoute.of(route));
  }

  /** The route currently in the planner, saved or not; the track is recalculated. */
  @PostMapping("/export/{format}")
  public ResponseEntity<byte[]> draft(
      @PathVariable String format, @Valid @RequestBody RouteDraft draft) {
    RouteFormat target = formats.get(format);
    PlannedRoute planned = routes.plan(draft);
    return file(
        target,
        new ExportedRoute(draft.name(), planned.points(), planned.ascent(), planned.descent()));
  }

  private static ResponseEntity<byte[]> file(RouteFormat format, ExportedRoute route) {
    return ResponseEntity.ok()
        .contentType(format.mediaType())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(FileNames.forRoute(route.name(), format.extension()))
                .build()
                .toString())
        .body(format.write(route));
  }
}
