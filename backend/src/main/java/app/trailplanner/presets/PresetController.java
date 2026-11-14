package app.trailplanner.presets;

import app.trailplanner.routing.RoutingPreferences;
import app.trailplanner.users.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/presets")
public class PresetController {

  public record PresetRequest(
      @NotBlank @Size(max = 60) String name, @NotNull RoutingPreferences preferences) {}

  private final PresetService presets;

  public PresetController(PresetService presets) {
    this.presets = presets;
  }

  @GetMapping
  public List<Preset> list(CurrentUser user, Locale locale) {
    return presets.list(user, locale);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Preset create(CurrentUser user, @Valid @RequestBody PresetRequest request, Locale locale) {
    return presets.create(user, request.name(), request.preferences(), locale);
  }

  @PutMapping("/{id}")
  public Preset update(
      CurrentUser user,
      @PathVariable long id,
      @Valid @RequestBody PresetRequest request,
      Locale locale) {
    return presets.update(user, id, request.name(), request.preferences(), locale);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(CurrentUser user, @PathVariable long id) {
    presets.delete(user, id);
  }
}
