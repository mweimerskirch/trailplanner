package app.trailplan.routes;

import app.trailplan.routing.PlannedRoute;
import app.trailplan.routing.RoutingService;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RouteService {

  private final RouteRepository repository;
  private final RoutingService routing;
  private final Clock clock;

  public RouteService(RouteRepository repository, RoutingService routing, Clock clock) {
    this.repository = repository;
    this.routing = routing;
    this.clock = clock;
  }

  public List<RouteSummary> list() {
    return repository.findAllSummaries();
  }

  public StoredRoute get(long id) {
    return repository.findById(id).orElseThrow(() -> new RouteNotFoundException(id));
  }

  public StoredRoute create(RouteDraft draft) {
    PlannedRoute planned = plan(draft);
    long id = repository.insert(draft, planned, clock.instant());
    return get(id);
  }

  public StoredRoute update(long id, RouteDraft draft) {
    PlannedRoute planned = plan(draft);
    if (!repository.update(id, draft, planned, clock.instant())) {
      throw new RouteNotFoundException(id);
    }
    return get(id);
  }

  public void delete(long id) {
    if (!repository.delete(id)) {
      throw new RouteNotFoundException(id);
    }
  }

  /** Recalculates the track server-side so stored tracks always match waypoints and profile. */
  public PlannedRoute plan(RouteDraft draft) {
    return routing.plan(draft.waypoints(), draft.preferences());
  }
}
