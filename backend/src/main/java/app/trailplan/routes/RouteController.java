package app.trailplan.routes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

  public record RenameRequest(@NotBlank @Size(max = RouteService.MAX_NAME_LENGTH) String name) {}

  public record EmptyTrashResult(int deleted) {}

  private final RouteService routes;

  public RouteController(RouteService routes) {
    this.routes = routes;
  }

  @GetMapping("/routes")
  public List<RouteSummary> list() {
    return routes.list();
  }

  @GetMapping("/routes/trash")
  public List<RouteSummary> trash() {
    return routes.trash();
  }

  /** Deletes every route in the trash for good. */
  @DeleteMapping("/routes/trash")
  public EmptyTrashResult emptyTrash() {
    return new EmptyTrashResult(routes.emptyTrash());
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

  /** Renames without recalculating, so it also works for imported routes. */
  @PatchMapping("/routes/{id}")
  public RouteDetails rename(@PathVariable long id, @Valid @RequestBody RenameRequest request) {
    return routes.rename(id, request.name()).details();
  }

  @PostMapping("/routes/{id}/duplicate")
  public ResponseEntity<RouteDetails> duplicate(@PathVariable long id) {
    RouteDetails copy = routes.duplicate(id).details();
    return ResponseEntity.created(URI.create("/api/routes/" + copy.id())).body(copy);
  }

  /** Moves the route to the trash; it can be restored until the trash is emptied. */
  @DeleteMapping("/routes/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable long id) {
    routes.moveToTrash(id);
  }

  @PostMapping("/routes/{id}/restore")
  public RouteDetails restore(@PathVariable long id) {
    return routes.restore(id).details();
  }

  @DeleteMapping("/routes/{id}/permanent")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePermanently(@PathVariable long id) {
    routes.deletePermanently(id);
  }
}
