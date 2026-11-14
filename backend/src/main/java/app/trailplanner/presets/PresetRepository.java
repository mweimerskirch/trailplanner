package app.trailplanner.presets;

import app.trailplanner.db.Timestamps;
import app.trailplanner.routing.RoutingPreferences;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Presets in SQLite, always scoped to one rider. */
@Repository
public class PresetRepository {

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public PresetRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<Preset> findAll(long userId) {
    return jdbc.sql(
            "SELECT id, name, preferences_json, position, builtin FROM preset WHERE rider_id = :user"
                + " ORDER BY position, id")
        .param("user", userId)
        .query((rs, row) ->
            new Preset(
                rs.getLong("id"),
                rs.getString("name"),
                read(rs.getString("preferences_json")),
                rs.getInt("position"),
                rs.getString("builtin")))
        .list();
  }

  public Optional<Preset> findById(long userId, long id) {
    return findAll(userId).stream().filter(p -> p.id() == id).findFirst();
  }

  /**
   * Whether the rider has a preset with this name, other than {@code exceptId} (or any if null).
   */
  public boolean nameTaken(long userId, String name, Long exceptId) {
    return jdbc.sql(
                "SELECT COUNT(*) FROM preset WHERE rider_id = :user AND name = :name"
                    + " AND (:except IS NULL OR id <> :except)")
            .param("user", userId)
            .param("name", name)
            .param("except", exceptId)
            .query(Integer.class)
            .single()
        > 0;
  }

  /** Appends a preset at the end of the list. Empty if the rider already has one with the name. */
  public Optional<Long> insert(long userId, String name, RoutingPreferences prefs, Instant now) {
    return insert(userId, name, prefs, null, now);
  }

  /** {@link #insert}, for a built-in preset ({@code builtin}: its DefaultPresets name). */
  public Optional<Long> insert(
      long userId, String name, RoutingPreferences prefs, String builtin, Instant now) {
    if (nameTaken(userId, name, null)) {
      return Optional.empty();
    }
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO preset
              (rider_id, name, preferences_json, builtin, position, created_at, updated_at)
            VALUES (:user, :name, :prefs, :builtin,
                    (SELECT COALESCE(MAX(position), 0) + 1 FROM preset WHERE rider_id = :user),
                    :now, :now)
            """)
        .param("user", userId)
        .param("name", name)
        .param("prefs", write(prefs))
        .param("builtin", builtin)
        .param("now", Timestamps.format(now))
        .update(keys);
    Number key = keys.getKey();
    return key == null ? Optional.empty() : Optional.of(key.longValue());
  }

  /**
   * False if the preset does not exist. Check {@link #nameTaken} first. {@code builtin}: as in
   * {@link Preset}; null once the rider renamed it.
   */
  public boolean update(
      long userId, long id, String name, RoutingPreferences prefs, String builtin, Instant now) {
    return jdbc.sql(
                """
                UPDATE preset
                SET name = :name, preferences_json = :prefs, builtin = :builtin, updated_at = :now
                WHERE id = :id AND rider_id = :user
                """)
            .param("id", id)
            .param("user", userId)
            .param("name", name)
            .param("prefs", write(prefs))
            .param("builtin", builtin)
            .param("now", Timestamps.format(now))
            .update()
        == 1;
  }

  /**
   * Marks the presets from before the builtin column that still have a built-in preset's name (in
   * any language) as that built-in preset.
   */
  public void markBuiltin(String builtin, Collection<String> names) {
    jdbc.sql("UPDATE preset SET builtin = :builtin WHERE builtin IS NULL AND name IN (:names)")
        .param("builtin", builtin)
        .param("names", names)
        .update();
  }

  public boolean delete(long userId, long id) {
    return jdbc.sql("DELETE FROM preset WHERE id = :id AND rider_id = :user")
            .param("id", id)
            .param("user", userId)
            .update()
        == 1;
  }

  /**
   * Riders seeded with an older version of the built-in presets than {@code current}, with the
   * version they have (0: none yet). rider.presets_seeded holds that version.
   */
  public Map<Long, Integer> seededVersions(int current) {
    Map<Long, Integer> riders = new LinkedHashMap<>();
    jdbc.sql("SELECT id, presets_seeded FROM rider WHERE presets_seeded < :current")
        .param("current", current)
        .query(
            (RowCallbackHandler) rs -> riders.put(rs.getLong("id"), rs.getInt("presets_seeded")));
    return riders;
  }

  public void markSeeded(long userId, int version) {
    jdbc.sql("UPDATE rider SET presets_seeded = :version WHERE id = :user")
        .param("user", userId)
        .param("version", version)
        .update();
  }

  private String write(RoutingPreferences prefs) {
    try {
      return json.writeValueAsString(prefs);
    } catch (JacksonException e) {
      throw new IllegalStateException("Cannot serialize preferences", e);
    }
  }

  private RoutingPreferences read(String value) {
    try {
      return json.readValue(value, RoutingPreferences.class);
    } catch (JacksonException e) {
      throw new IllegalStateException("Corrupt preferences in preset table", e);
    }
  }
}
