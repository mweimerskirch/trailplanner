package app.trailplanner.routing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {

  /** {@code type} defaults to a normally routed leg. */
  public record LegRequest(
      @NotNull GeoPoint from,
      @NotNull GeoPoint to,
      @NotNull RoutingPreferences preferences,
      LegType type) {}

  private final RoutingService routing;

  public RoutingController(RoutingService routing) {
    this.routing = routing;
  }

  @PostMapping("/leg")
  public RouteLeg leg(@Valid @RequestBody LegRequest request) {
    LegType type = request.type() == null ? LegType.ROUTED : request.type();
    return routing.leg(request.from(), request.to(), request.preferences(), type);
  }

  @GetMapping("/preferences/defaults")
  public RoutingPreferences defaults() {
    return RoutingPreferences.defaults();
  }
}
