package app.trailplan.routes;

import app.trailplan.routing.PlannedRoute;
import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.RoutingService;
import app.trailplan.routing.TrackConverter;
import app.trailplan.users.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RouteService {

  static final int MAX_NAME_LENGTH = 120;

  /** RouteDraft accepts 250 waypoints; leave room for the rider's own edits. */
  static final int MAX_CONVERTED_WAYPOINTS = 200;

  private static final String COPY_SUFFIX = " (copy)";

  private final RouteRepository repository;
  private final RoutingService routing;
  private final Clock clock;

  public RouteService(RouteRepository repository, RoutingService routing, Clock clock) {
    this.repository = repository;
    this.routing = routing;
    this.clock = clock;
  }

  public List<RouteSummary> list(CurrentUser user) {
    return repository.findActiveSummaries(user.id());
  }

  public List<RouteSummary> archive(CurrentUser user) {
    return repository.findArchivedSummaries(user.id());
  }

  /** Archives the route (or takes it out of the archive); it is never deleted by this. */
  public StoredRoute setArchived(CurrentUser user, long id, boolean archived) {
    if (!repository.setArchived(user.id(), id, archived ? clock.instant() : null)) {
      throw new RouteNotFoundException(id);
    }
    return get(user, id);
  }

  public List<RouteSummary> trash(CurrentUser user) {
    return repository.findTrashedSummaries(user.id());
  }

  /** The rider's route; routes of other riders are reported as not found. */
  public StoredRoute get(CurrentUser user, long id) {
    return repository.findById(user.id(), id).orElseThrow(() -> new RouteNotFoundException(id));
  }

  public StoredRoute create(CurrentUser user, RouteDraft draft) {
    PlannedRoute planned = plan(draft);
    long id = repository.insert(user.id(), draft, planned, clock.instant());
    return get(user, id);
  }

  /** Saves new waypoints; for an imported route this is the conversion to an editable one. */
  public StoredRoute update(CurrentUser user, long id, RouteDraft draft) {
    requireChangeable(user, id, draft.expectedUpdatedAt()); // before spending time on routing
    PlannedRoute planned = plan(draft);
    if (!repository.update(
        user.id(), id, draft, planned, clock.instant(), draft.expectedUpdatedAt())) {
      requireChangeable(user, id, draft.expectedUpdatedAt()); // changed meanwhile: say why
      throw new RouteNotFoundException(id);
    }
    return get(user, id);
  }

  public StoredRoute rename(CurrentUser user, long id, String name) {
    requireChangeable(user, id, null);
    if (!repository.rename(user.id(), id, name.strip(), clock.instant())) {
      throw new RouteNotFoundException(id);
    }
    return get(user, id);
  }

  /**
   * Puts an imported route's original track back: the route is shown as imported again, without
   * waypoints. Its edited version is replaced (undo in the planner or "Save as new" keep it).
   */
  public StoredRoute revertToOriginal(CurrentUser user, long id) {
    StoredRoute route = requireChangeable(user, id, null);
    OriginalTrack original = route.original().orElseThrow(RouteConflictException::noOriginal);
    repository.revertToOriginal(user.id(), id, original, clock.instant());
    return get(user, id);
  }

  /** The route, if it may be changed: it exists, is not in the trash, and is as expected. */
  private StoredRoute requireChangeable(CurrentUser user, long id, Instant expectedUpdatedAt) {
    StoredRoute route = get(user, id);
    if (route.deletedAt().isPresent()) {
      throw RouteConflictException.inTrash();
    }
    if (expectedUpdatedAt != null && !expectedUpdatedAt.equals(route.updatedAt())) {
      throw RouteConflictException.changedElsewhere();
    }
    return route;
  }

  public StoredRoute duplicate(CurrentUser user, long id) {
    String name = get(user, id).name();
    int keep = Math.min(name.length(), MAX_NAME_LENGTH - COPY_SUFFIX.length());
    long copy =
        repository
            .duplicate(user.id(), id, name.substring(0, keep) + COPY_SUFFIX, clock.instant())
            .orElseThrow(() -> new RouteNotFoundException(id));
    return get(user, copy);
  }

  /** Moves the route to the trash. Deleting a route that is already in the trash is a no-op. */
  public void moveToTrash(CurrentUser user, long id) {
    if (!repository.moveToTrash(user.id(), id, clock.instant())) {
      get(user, id); // throws if the route does not exist at all
    }
  }

  public StoredRoute restore(CurrentUser user, long id) {
    if (!repository.restore(user.id(), id)) {
      get(user, id); // throws if the route does not exist at all
    }
    return get(user, id);
  }

  public void deletePermanently(CurrentUser user, long id) {
    if (!repository.deletePermanently(user.id(), id)) {
      throw new RouteNotFoundException(id);
    }
  }

  public int emptyTrash(CurrentUser user) {
    return repository.emptyTrash(user.id());
  }

  /**
   * Waypoints for editing an imported route: as few as possible, checked against the router so the
   * route follows the original. Nothing is saved; the planner shows the result until the rider
   * saves it.
   */
  public TrackConverter.Result convert(CurrentUser user, long id, RoutingPreferences preferences) {
    StoredRoute route = get(user, id);
    if (route.editable()) {
      throw RouteConflictException.alreadyEditable();
    }
    return routing.convertTrack(route.track(), preferences, MAX_CONVERTED_WAYPOINTS);
  }

  /** Recalculates the track server-side so stored tracks always match waypoints and profile. */
  public PlannedRoute plan(RouteDraft draft) {
    return routing.plan(draft.waypoints(), draft.preferences());
  }
}
