package app.trailplan.routes;

import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrackConverter;
import app.trailplan.users.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

  public record ConversionRequest(@NotNull RoutingPreferences preferences) {}

  private final RouteService routes;

  public RouteController(RouteService routes) {
    this.routes = routes;
  }

  @GetMapping("/routes")
  public List<RouteSummary> list(CurrentUser user) {
    return routes.list(user);
  }

  @GetMapping("/routes/archive")
  public List<RouteSummary> archive(CurrentUser user) {
    return routes.archive(user);
  }

  /** Archives a route: out of the main list, but kept (unlike the trash, never emptied). */
  @PostMapping("/routes/{id}/archive")
  public RouteDetails archive(CurrentUser user, @PathVariable long id) {
    return routes.setArchived(user, id, true).details();
  }

  @DeleteMapping("/routes/{id}/archive")
  public RouteDetails unarchive(CurrentUser user, @PathVariable long id) {
    return routes.setArchived(user, id, false).details();
  }

  @GetMapping("/routes/trash")
  public List<RouteSummary> trash(CurrentUser user) {
    return routes.trash(user);
  }

  /** Deletes every route in the trash for good. */
  @DeleteMapping("/routes/trash")
  public EmptyTrashResult emptyTrash(CurrentUser user) {
    return new EmptyTrashResult(routes.emptyTrash(user));
  }

  @GetMapping("/routes/{id}")
  public RouteDetails get(CurrentUser user, @PathVariable long id) {
    return routes.get(user, id).details();
  }

  @PostMapping("/routes")
  public ResponseEntity<RouteDetails> create(
      CurrentUser user, @Valid @RequestBody RouteDraft draft) {
    RouteDetails created = routes.create(user, draft).details();
    return ResponseEntity.created(URI.create("/api/routes/" + created.id())).body(created);
  }

  @PutMapping("/routes/{id}")
  public RouteDetails update(
      CurrentUser user, @PathVariable long id, @Valid @RequestBody RouteDraft draft) {
    return routes.update(user, id, draft).details();
  }

  /** Renames without recalculating, so it also works for imported routes. */
  @PatchMapping("/routes/{id}")
  public RouteDetails rename(
      CurrentUser user, @PathVariable long id, @Valid @RequestBody RenameRequest request) {
    return routes.rename(user, id, request.name()).details();
  }

  /** Waypoints for editing an imported route; nothing is saved. */
  @PostMapping("/routes/{id}/conversion")
  public TrackConverter.Result convert(
      CurrentUser user, @PathVariable long id, @Valid @RequestBody ConversionRequest request) {
    return routes.convert(user, id, request.preferences());
  }

  /** Puts an imported route's original track back (it becomes view-only again). */
  @PostMapping("/routes/{id}/revert")
  public RouteDetails revert(CurrentUser user, @PathVariable long id) {
    return routes.revertToOriginal(user, id).details();
  }

  @PostMapping("/routes/{id}/duplicate")
  public ResponseEntity<RouteDetails> duplicate(CurrentUser user, @PathVariable long id) {
    RouteDetails copy = routes.duplicate(user, id).details();
    return ResponseEntity.created(URI.create("/api/routes/" + copy.id())).body(copy);
  }

  /** Moves the route to the trash; it can be restored until the trash is emptied. */
  @DeleteMapping("/routes/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(CurrentUser user, @PathVariable long id) {
    routes.moveToTrash(user, id);
  }

  @PostMapping("/routes/{id}/restore")
  public RouteDetails restore(CurrentUser user, @PathVariable long id) {
    return routes.restore(user, id).details();
  }

  @DeleteMapping("/routes/{id}/permanent")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePermanently(CurrentUser user, @PathVariable long id) {
    routes.deletePermanently(user, id);
  }
}
