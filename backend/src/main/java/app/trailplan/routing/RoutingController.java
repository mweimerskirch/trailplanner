package app.trailplan.routing;

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

  public record LegRequest(
      @NotNull GeoPoint from, @NotNull GeoPoint to, @NotNull RoutingPreferences preferences) {}

  private final RoutingService routing;

  public RoutingController(RoutingService routing) {
    this.routing = routing;
  }

  @PostMapping("/leg")
  public RouteLeg leg(@Valid @RequestBody LegRequest request) {
    return routing.leg(request.from(), request.to(), request.preferences());
  }

  @GetMapping("/preferences/defaults")
  public RoutingPreferences defaults() {
    return RoutingPreferences.defaults();
  }
}
