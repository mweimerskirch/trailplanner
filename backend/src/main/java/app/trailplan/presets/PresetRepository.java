package app.trailplan.presets;

import app.trailplan.db.Timestamps;
import app.trailplan.routing.RoutingPreferences;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
            "SELECT id, name, preferences_json, position FROM preset WHERE rider_id = :user"
                + " ORDER BY position, id")
        .param("user", userId)
        .query((rs, row) ->
            new Preset(
                rs.getLong("id"),
                rs.getString("name"),
                read(rs.getString("preferences_json")),
                rs.getInt("position")))
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
    if (nameTaken(userId, name, null)) {
      return Optional.empty();
    }
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO preset (rider_id, name, preferences_json, position, created_at, updated_at)
            VALUES (:user, :name, :prefs,
                    (SELECT COALESCE(MAX(position), 0) + 1 FROM preset WHERE rider_id = :user),
                    :now, :now)
            """)
        .param("user", userId)
        .param("name", name)
        .param("prefs", write(prefs))
        .param("now", Timestamps.format(now))
        .update(keys);
    Number key = keys.getKey();
    return key == null ? Optional.empty() : Optional.of(key.longValue());
  }

  /** False if the preset does not exist. Check {@link #nameTaken} first. */
  public boolean update(long userId, long id, String name, RoutingPreferences prefs, Instant now) {
    return jdbc.sql(
                """
                UPDATE preset SET name = :name, preferences_json = :prefs, updated_at = :now
                WHERE id = :id AND rider_id = :user
                """)
            .param("id", id)
            .param("user", userId)
            .param("name", name)
            .param("prefs", write(prefs))
            .param("now", Timestamps.format(now))
            .update()
        == 1;
  }

  public boolean delete(long userId, long id) {
    return jdbc.sql("DELETE FROM preset WHERE id = :id AND rider_id = :user")
            .param("id", id)
            .param("user", userId)
            .update()
        == 1;
  }

  /** Riders who have not received the built-in presets yet. */
  public List<Long> ridersWithoutDefaults() {
    return jdbc.sql("SELECT id FROM rider WHERE presets_seeded = 0").query(Long.class).list();
  }

  public void markSeeded(long userId) {
    jdbc.sql("UPDATE rider SET presets_seeded = 1 WHERE id = :user").param("user", userId).update();
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
