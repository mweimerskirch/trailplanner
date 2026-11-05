package app.trailplan.routes;

import app.trailplan.routing.PlannedRoute;
import app.trailplan.routing.RoutingService;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RouteService {

  static final int MAX_NAME_LENGTH = 120;
  private static final String COPY_SUFFIX = " (copy)";

  private final RouteRepository repository;
  private final RoutingService routing;
  private final Clock clock;

  public RouteService(RouteRepository repository, RoutingService routing, Clock clock) {
    this.repository = repository;
    this.routing = routing;
    this.clock = clock;
  }

  public List<RouteSummary> list() {
    return repository.findActiveSummaries();
  }

  public List<RouteSummary> trash() {
    return repository.findTrashedSummaries();
  }

  public StoredRoute get(long id) {
    return repository.findById(id).orElseThrow(() -> new RouteNotFoundException(id));
  }

  public StoredRoute create(RouteDraft draft) {
    PlannedRoute planned = plan(draft);
    long id = repository.insert(draft, planned, clock.instant());
    return get(id);
  }

  /** Saves new waypoints; for an imported route this is the conversion to an editable one. */
  public StoredRoute update(long id, RouteDraft draft) {
    PlannedRoute planned = plan(draft);
    if (!repository.update(id, draft, planned, clock.instant())) {
      throw new RouteNotFoundException(id);
    }
    return get(id);
  }

  public StoredRoute rename(long id, String name) {
    if (!repository.rename(id, name.strip(), clock.instant())) {
      throw new RouteNotFoundException(id);
    }
    return get(id);
  }

  public StoredRoute duplicate(long id) {
    String name = get(id).name();
    int keep = Math.min(name.length(), MAX_NAME_LENGTH - COPY_SUFFIX.length());
    long copy =
        repository
            .duplicate(id, name.substring(0, keep) + COPY_SUFFIX, clock.instant())
            .orElseThrow(() -> new RouteNotFoundException(id));
    return get(copy);
  }

  /** Moves the route to the trash. Deleting a route that is already in the trash is a no-op. */
  public void moveToTrash(long id) {
    if (!repository.moveToTrash(id, clock.instant())) {
      get(id); // throws if the route does not exist at all
    }
  }

  public StoredRoute restore(long id) {
    if (!repository.restore(id)) {
      get(id); // throws if the route does not exist at all
    }
    return get(id);
  }

  public void deletePermanently(long id) {
    if (!repository.deletePermanently(id)) {
      throw new RouteNotFoundException(id);
    }
  }

  public int emptyTrash() {
    return repository.emptyTrash();
  }

  /** Recalculates the track server-side so stored tracks always match waypoints and profile. */
  public PlannedRoute plan(RouteDraft draft) {
    return routing.plan(draft.waypoints(), draft.preferences());
  }
}
