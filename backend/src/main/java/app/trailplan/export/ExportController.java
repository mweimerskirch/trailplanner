package app.trailplan.export;

import app.trailplan.routes.RouteDraft;
import app.trailplan.routes.RouteService;
import app.trailplan.routes.StoredRoute;
import app.trailplan.routing.PlannedRoute;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

  /** A saved route, with its stored track (for imported routes: the original track). */
  @GetMapping("/routes/{id}/export/{format}")
  public ResponseEntity<byte[]> saved(@PathVariable long id, @PathVariable String format) {
    StoredRoute route = routes.get(id);
    return file(
        formats.get(format),
        new ExportedRoute(route.name(), route.track(), route.ascent(), route.descent()));
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
