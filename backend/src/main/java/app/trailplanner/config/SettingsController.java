package app.trailplanner.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Server settings the UI needs, available without choosing a rider. */
@RestController
public class SettingsController {

  public record Settings(StartArea area) {}

  private final Settings settings;

  public SettingsController(TrailPlannerProperties props) {
    // Parsed here, so a wrong TRAILPLANNER_AREA stops the server at startup with a clear message.
    this.settings = new Settings(StartArea.parse(props.area()));
  }

  @GetMapping("/api/settings")
  public Settings settings() {
    return settings;
  }
}
