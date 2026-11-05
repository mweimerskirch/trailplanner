package app.trailplan.routes;

import app.trailplan.export.FileNames;
import app.trailplan.export.GpxWriter;
import app.trailplan.routing.PlannedRoute;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RouteController {

  private static final MediaType GPX = MediaType.parseMediaType("application/gpx+xml");

  private final RouteService routes;
  private final GpxWriter gpx;

  public RouteController(RouteService routes, GpxWriter gpx) {
    this.routes = routes;
    this.gpx = gpx;
  }

  @GetMapping("/routes")
  public List<RouteSummary> list() {
    return routes.list();
  }

  @GetMapping("/routes/{id}")
  public RouteDetails get(@PathVariable long id) {
    return routes.get(id).details();
  }

  @PostMapping("/routes")
  public ResponseEntity<RouteDetails> create(@Valid @RequestBody RouteDraft draft) {
    RouteDetails created = routes.create(draft).details();
    return ResponseEntity.created(URI.create("/api/routes/" + created.id())).body(created);
  }

  @PutMapping("/routes/{id}")
  public RouteDetails update(@PathVariable long id, @Valid @RequestBody RouteDraft draft) {
    return routes.update(id, draft).details();
  }

  @DeleteMapping("/routes/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable long id) {
    routes.delete(id);
  }

  @GetMapping("/routes/{id}/gpx")
  public ResponseEntity<String> savedGpx(@PathVariable long id) {
    StoredRoute route = routes.get(id);
    return gpxResponse(route.name(), gpx.write(route.name(), route.track()));
  }

  /** GPX for the route currently in the planner, saved or not. */
  @PostMapping("/export/gpx")
  public ResponseEntity<String> draftGpx(@Valid @RequestBody RouteDraft draft) {
    PlannedRoute planned = routes.plan(draft);
    return gpxResponse(draft.name(), gpx.write(draft.name(), planned.points()));
  }

  private static ResponseEntity<String> gpxResponse(String name, String body) {
    return ResponseEntity.ok()
        .contentType(GPX)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(FileNames.forRoute(name, "gpx"))
                .build()
                .toString())
        .body(body);
  }
}
