package app.trailplanner.presets;

import app.trailplanner.routing.RoutingPreferences;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A rider's named riding style. {@code builtin} is the {@link DefaultPresets} name of a built-in
 * preset the rider has not renamed (null otherwise); its name is shown in the UI language.
 */
public record Preset(
    long id,
    String name,
    RoutingPreferences preferences,
    int position,
    @JsonIgnore String builtin) {

  public Preset withName(String translated) {
    return new Preset(id, translated, preferences, position, builtin);
  }
}
