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
 * they are created, existing riders on the first start after presets were added (only the ones
 * added since they were last seeded).
 */
@Service
public class PresetService implements ApplicationRunner {

  private final PresetRepository presets;
  private final Clock clock;

  public PresetService(PresetRepository presets, Clock clock) {
    this.presets = presets;
    this.clock = clock;
  }

  /** Gives every rider the built-in presets they have not had yet. */
  @Override
  public void run(ApplicationArguments args) {
    seedMissing();
  }

  public void seedMissing() {
    presets.seededVersions(DefaultPresets.VERSION).forEach(this::seed);
  }

  /** The built-in presets for a new rider. */
  public void seed(long userId) {
    seed(userId, 0);
  }

  /**
   * The built-in presets added after {@code seededVersion} (skipping names the rider already uses),
   * once.
   */
  @Transactional
  public void seed(long userId, int seededVersion) {
    for (DefaultPresets.Preset preset : DefaultPresets.ALL) {
      if (preset.since() > seededVersion) {
        presets.insert(userId, preset.name(), preset.preferences(), clock.instant());
      }
    }
    presets.markSeeded(userId, DefaultPresets.VERSION);
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
