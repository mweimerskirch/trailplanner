package app.trailplanner.presets;

import app.trailplanner.routing.RoutingPreferences;
import app.trailplanner.users.CurrentUser;
import app.trailplanner.web.ApiException;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Riding-style presets per rider. Every rider gets the {@link DefaultPresets} once: new riders when
 * they are created, existing riders on the first start after presets were added (only the ones
 * added since they were last seeded). A built-in preset is shown in the language of the UI until
 * the rider renames it ({@link Preset#builtin}).
 */
@Service
public class PresetService implements ApplicationRunner {

  /** The languages of the app, besides English (messages_<language>.properties). */
  private static final List<Locale> TRANSLATIONS = List.of(Locale.FRENCH, Locale.GERMAN);

  private final PresetRepository presets;
  private final Clock clock;
  private final MessageSource messages;

  public PresetService(PresetRepository presets, Clock clock, MessageSource messages) {
    this.presets = presets;
    this.clock = clock;
    this.messages = messages;
  }

  /** Gives every rider the built-in presets they have not had yet. */
  @Override
  public void run(ApplicationArguments args) {
    markBuiltins();
    seedMissing();
  }

  /** Presets seeded before the builtin column: recognised by their name in any language. */
  public void markBuiltins() {
    for (DefaultPresets.Preset preset : DefaultPresets.ALL) {
      Set<String> names = new LinkedHashSet<>();
      names.add(preset.name());
      TRANSLATIONS.forEach(locale -> names.add(translate(preset.name(), locale)));
      presets.markBuiltin(preset.name(), names);
    }
  }

  public void seedMissing() {
    presets.seededVersions(DefaultPresets.VERSION).forEach(this::seed);
  }

  /** The built-in presets for a new rider, named in English. */
  public void seed(long userId) {
    seed(userId, 0, Locale.ENGLISH);
  }

  /** The built-in presets for a new rider, named in the language of the UI that created them. */
  public void seed(long userId, Locale locale) {
    seed(userId, 0, locale);
  }

  public void seed(long userId, int seededVersion) {
    seed(userId, seededVersion, Locale.ENGLISH);
  }

  /**
   * The built-in presets added after {@code seededVersion} (skipping names the rider already uses),
   * once. Names are translated by {@code preset.<name>} keys in messages_<language>.properties.
   */
  @Transactional
  public void seed(long userId, int seededVersion, Locale locale) {
    for (DefaultPresets.Preset preset : DefaultPresets.ALL) {
      if (preset.since() > seededVersion) {
        presets.insert(
            userId,
            translate(preset.name(), locale),
            preset.preferences(),
            preset.name(),
            clock.instant());
      }
    }
    presets.markSeeded(userId, DefaultPresets.VERSION);
  }

  /** "Gravel / XC" → "preset.gravel-xc". */
  public static String messageKey(String name) {
    return "preset."
        + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  /** A built-in preset's name in a language (English if there is no translation). */
  private String translate(String builtin, Locale locale) {
    return messages.getMessage(messageKey(builtin), null, builtin, locale);
  }

  private Preset shown(Preset preset, Locale locale) {
    String builtin = preset.builtin();
    return builtin == null ? preset : preset.withName(translate(builtin, locale));
  }

  /** The rider's presets, built-in ones named in {@code locale}. */
  public List<Preset> list(CurrentUser user, Locale locale) {
    return presets.findAll(user.id()).stream().map(p -> shown(p, locale)).toList();
  }

  public Preset create(CurrentUser user, String name, RoutingPreferences prefs, Locale locale) {
    if (shownNameTaken(user, name, null, locale)) {
      throw nameTaken(name);
    }
    long id =
        presets
            .insert(user.id(), name.strip(), prefs, clock.instant())
            .orElseThrow(() -> nameTaken(name));
    return get(user, id, locale);
  }

  /**
   * Saving a built-in preset under the name it is shown with keeps it built-in (and translated);
   * any other name makes it the rider's own.
   */
  public Preset update(
      CurrentUser user, long id, String name, RoutingPreferences prefs, Locale locale) {
    Preset stored = presets.findById(user.id(), id).orElseThrow(() -> notFound(id));
    String newName = name.strip();
    boolean keepsBuiltin = stored.builtin() != null && newName.equals(shown(stored, locale).name());
    if (keepsBuiltin) {
      presets.update(user.id(), id, stored.name(), prefs, stored.builtin(), clock.instant());
      return get(user, id, locale);
    }
    if (presets.nameTaken(user.id(), newName, id) || shownNameTaken(user, newName, id, locale)) {
      throw nameTaken(name);
    }
    if (!presets.update(user.id(), id, newName, prefs, null, clock.instant())) {
      throw notFound(id);
    }
    return get(user, id, locale);
  }

  /**
   * Whether another preset is shown with this name (a built-in one may be stored under another).
   */
  private boolean shownNameTaken(CurrentUser user, String name, Long exceptId, Locale locale) {
    return list(user, locale).stream()
        .anyMatch(p -> !Long.valueOf(p.id()).equals(exceptId) && p.name().equals(name.strip()));
  }

  public void delete(CurrentUser user, long id) {
    if (!presets.delete(user.id(), id)) {
      throw notFound(id);
    }
  }

  private Preset get(CurrentUser user, long id, Locale locale) {
    return shown(presets.findById(user.id(), id).orElseThrow(() -> notFound(id)), locale);
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
