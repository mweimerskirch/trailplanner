package app.trailplan.presets;

import app.trailplan.routing.RoutingPreferences;
import app.trailplan.users.CurrentUser;
import app.trailplan.web.ApiException;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Riding-style presets per rider. Every rider gets the {@link DefaultPresets} once: new riders when
 * they are created, existing riders on the first start after presets were added.
 */
@Service
public class PresetService implements ApplicationRunner {

  private final PresetRepository presets;
  private final Clock clock;

  public PresetService(PresetRepository presets, Clock clock) {
    this.presets = presets;
    this.clock = clock;
  }

  /** Gives every rider who has not had them yet the built-in presets. */
  @Override
  public void run(ApplicationArguments args) {
    seedMissing();
  }

  public void seedMissing() {
    presets.ridersWithoutDefaults().forEach(this::seed);
  }

  /** The built-in presets for one rider (skipping names they already use), once. */
  @Transactional
  public void seed(long userId) {
    for (DefaultPresets.Preset preset : DefaultPresets.ALL) {
      presets.insert(userId, preset.name(), preset.preferences(), clock.instant());
    }
    presets.markSeeded(userId);
  }

  public List<Preset> list(CurrentUser user) {
    return presets.findAll(user.id());
  }

  public Preset create(CurrentUser user, String name, RoutingPreferences prefs) {
    long id =
        presets
            .insert(user.id(), name.strip(), prefs, clock.instant())
            .orElseThrow(() -> nameTaken(name));
    return get(user, id);
  }

  public Preset update(CurrentUser user, long id, String name, RoutingPreferences prefs) {
    if (presets.nameTaken(user.id(), name.strip(), id)) {
      throw nameTaken(name);
    }
    if (!presets.update(user.id(), id, name.strip(), prefs, clock.instant())) {
      throw notFound(id);
    }
    return get(user, id);
  }

  public void delete(CurrentUser user, long id) {
    if (!presets.delete(user.id(), id)) {
      throw notFound(id);
    }
  }

  private Preset get(CurrentUser user, long id) {
    return presets.findById(user.id(), id).orElseThrow(() -> notFound(id));
  }

  private static ApiException notFound(long id) {
    return new ApiException(
        HttpStatus.NOT_FOUND, "Preset not found", "Preset " + id + " does not exist.");
  }

  private static ApiException nameTaken(String name) {
    return new ApiException(
        HttpStatus.CONFLICT,
        "Preset not saved",
        "NAME_TAKEN",
        "You already have a preset called “" + name.strip() + "”.",
        null);
  }
}
