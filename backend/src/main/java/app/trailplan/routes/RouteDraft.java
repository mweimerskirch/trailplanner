package app.trailplan.routes;

import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.Waypoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * What the planner sends to save or export a route. The track is always recalculated.
 *
 * @param expectedUpdatedAt when saving changes: the route's {@code updatedAt} as the planner last
 *     saw it; the save is refused if the route was saved elsewhere since. Optional.
 */
public record RouteDraft(
    @NotBlank @Size(max = 120) String name,
    @NotNull @Size(min = 2, max = 250) List<@Valid @NotNull Waypoint> waypoints,
    @NotNull RoutingPreferences preferences,
    Instant expectedUpdatedAt) {

  public RouteDraft {
    name = name == null ? null : name.strip();
    waypoints = waypoints == null ? null : List.copyOf(waypoints);
  }

  public RouteDraft(String name, List<Waypoint> waypoints, RoutingPreferences preferences) {
    this(name, waypoints, preferences, null);
  }
}
