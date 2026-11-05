package app.trailplan.routes;

import app.trailplan.routing.PlannedRoute;
import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrackPoint;
import app.trailplan.routing.Waypoint;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Routes in SQLite. Every method is scoped to one rider ({@code userId}): a route of another rider
 * is treated as if it did not exist. Trashed routes keep their row with {@code deleted_at} set.
 */
@Repository
public class RouteRepository {

  private static final TypeReference<List<Waypoint>> WAYPOINTS = new TypeReference<>() {};
  private static final TypeReference<List<TrackPoint>> TRACK = new TypeReference<>() {};
  private static final TypeReference<RoutingPreferences> PREFERENCES = new TypeReference<>() {};

  private static final String SUMMARY_COLUMNS =
      """
      id, name, distance_m, ascent_m, updated_at, source, deleted_at,
      waypoints_json <> '[]' AS editable
      """;

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public RouteRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** Routes in the library, most recently changed first. */
  public List<RouteSummary> findActiveSummaries(long userId) {
    return jdbc.sql(
            "SELECT "
                + SUMMARY_COLUMNS
                + " FROM route WHERE user_id = :user AND deleted_at IS NULL ORDER BY updated_at DESC")
        .param("user", userId)
        .query(RouteRepository::mapSummary)
        .list();
  }

  /** Routes in the trash, most recently deleted first. */
  public List<RouteSummary> findTrashedSummaries(long userId) {
    return jdbc.sql(
            "SELECT "
                + SUMMARY_COLUMNS
                + " FROM route WHERE user_id = :user AND deleted_at IS NOT NULL"
                + " ORDER BY deleted_at DESC")
        .param("user", userId)
        .query(RouteRepository::mapSummary)
        .list();
  }

  public Optional<StoredRoute> findById(long userId, long id) {
    return jdbc.sql("SELECT * FROM route WHERE id = :id AND user_id = :user")
        .param("id", id)
        .param("user", userId)
        .query(this::map)
        .optional();
  }

  /** The id of a route in the library (not the trash) imported from this source, if any. */
  public Optional<Long> findActiveIdByExternalId(long userId, String externalId) {
    return jdbc.sql(
            """
            SELECT id FROM route
            WHERE user_id = :user AND external_id = :eid AND deleted_at IS NULL LIMIT 1
            """)
        .param("user", userId)
        .param("eid", externalId)
        .query(Long.class)
        .optional();
  }

  /** External ids of all routes in the library (not the trash) that start with this prefix. */
  public List<String> findActiveExternalIds(long userId, String prefix) {
    return jdbc.sql(
            """
            SELECT external_id FROM route
            WHERE user_id = :user AND deleted_at IS NULL AND external_id LIKE :prefix || '%'
            """)
        .param("user", userId)
        .param("prefix", prefix)
        .query(String.class)
        .list();
  }

  public long insert(long userId, RouteDraft draft, PlannedRoute planned, Instant now) {
    return insert(
        userId,
        draft.name(),
        write(draft.waypoints()),
        write(draft.preferences()),
        write(planned.points()),
        planned.distance(),
        planned.ascent(),
        planned.descent(),
        RouteSource.PLANNER,
        null,
        now);
  }

  /** Stores an imported track as-is; it gets waypoints only when the rider converts it. */
  public long insertImported(long userId, ImportedRoute route, Instant now) {
    return insert(
        userId,
        route.name(),
        write(List.of()),
        write(RoutingPreferences.defaults()),
        write(route.track()),
        route.distance(),
        route.ascent(),
        route.descent(),
        route.source(),
        route.externalId(),
        now);
  }

  private long insert(
      long userId,
      String name,
      String waypoints,
      String preferences,
      String track,
      double distance,
      double ascent,
      double descent,
      RouteSource source,
      String externalId,
      Instant now) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO route (name, waypoints_json, preferences_json, track_json,
                               distance_m, ascent_m, descent_m, created_at, updated_at,
                               source, external_id, user_id)
            VALUES (:name, :waypoints, :preferences, :track,
                    :distance, :ascent, :descent, :now, :now, :source, :externalId, :user)
            """)
        .param("user", userId)
        .param("name", name)
        .param("waypoints", waypoints)
        .param("preferences", preferences)
        .param("track", track)
        .param("distance", distance)
        .param("ascent", ascent)
        .param("descent", descent)
        .param("now", now.toString())
        .param("source", source.name())
        .param("externalId", externalId)
        .update(keys);
    return generatedId(keys);
  }

  public boolean update(long userId, long id, RouteDraft draft, PlannedRoute planned, Instant now) {
    return jdbc.sql(
                """
                UPDATE route SET name = :name, waypoints_json = :waypoints,
                  preferences_json = :preferences, track_json = :track, distance_m = :distance,
                  ascent_m = :ascent, descent_m = :descent, updated_at = :now
                WHERE id = :id AND user_id = :user
                """)
            .param("id", id)
            .param("user", userId)
            .param("name", draft.name())
            .param("waypoints", write(draft.waypoints()))
            .param("preferences", write(draft.preferences()))
            .param("track", write(planned.points()))
            .param("distance", planned.distance())
            .param("ascent", planned.ascent())
            .param("descent", planned.descent())
            .param("now", now.toString())
            .update()
        == 1;
  }

  public boolean rename(long userId, long id, String name, Instant now) {
    return jdbc.sql(
                "UPDATE route SET name = :name, updated_at = :now WHERE id = :id AND user_id = :user")
            .param("id", id)
            .param("user", userId)
            .param("name", name)
            .param("now", now.toString())
            .update()
        == 1;
  }

  /**
   * Copies a route under a new name. The copy has no cloud links (they live in their own table) and
   * no import id, so re-sending or re-importing never confuses it with the original.
   *
   * @return the id of the copy, or empty if the original does not exist
   */
  public Optional<Long> duplicate(long userId, long id, String name, Instant now) {
    KeyHolder keys = new GeneratedKeyHolder();
    int rows =
        jdbc.sql(
                """
                INSERT INTO route (name, waypoints_json, preferences_json, track_json,
                                   distance_m, ascent_m, descent_m, created_at, updated_at,
                                   source, external_id, user_id)
                SELECT :name, waypoints_json, preferences_json, track_json,
                       distance_m, ascent_m, descent_m, :now, :now, source, NULL, user_id
                FROM route WHERE id = :id AND user_id = :user
                """)
            .param("id", id)
            .param("user", userId)
            .param("name", name)
            .param("now", now.toString())
            .update(keys);
    return rows == 1 ? Optional.of(generatedId(keys)) : Optional.empty();
  }

  /** Moves a route to the trash. False if it does not exist or is already there. */
  public boolean moveToTrash(long userId, long id, Instant now) {
    return jdbc.sql(
                """
                UPDATE route SET deleted_at = :now
                WHERE id = :id AND user_id = :user AND deleted_at IS NULL
                """)
            .param("id", id)
            .param("user", userId)
            .param("now", now.toString())
            .update()
        == 1;
  }

  /** Takes a route out of the trash. False if it does not exist or is not in the trash. */
  public boolean restore(long userId, long id) {
    return jdbc.sql(
                """
                UPDATE route SET deleted_at = NULL
                WHERE id = :id AND user_id = :user AND deleted_at IS NOT NULL
                """)
            .param("id", id)
            .param("user", userId)
            .update()
        == 1;
  }

  public boolean deletePermanently(long userId, long id) {
    return jdbc.sql("DELETE FROM route WHERE id = :id AND user_id = :user")
            .param("id", id)
            .param("user", userId)
            .update()
        == 1;
  }

  /** Deletes every route in the trash for good and returns how many there were. */
  public int emptyTrash(long userId) {
    return jdbc.sql("DELETE FROM route WHERE user_id = :user AND deleted_at IS NOT NULL")
        .param("user", userId)
        .update();
  }

  private static RouteSummary mapSummary(ResultSet rs, int row) throws SQLException {
    return new RouteSummary(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getDouble("distance_m"),
        rs.getDouble("ascent_m"),
        Instant.parse(rs.getString("updated_at")),
        RouteSource.valueOf(rs.getString("source")),
        rs.getBoolean("editable"),
        instantOrNull(rs, "deleted_at"));
  }

  private StoredRoute map(ResultSet rs, int row) throws SQLException {
    return new StoredRoute(
        rs.getLong("id"),
        rs.getString("name"),
        read(rs.getString("waypoints_json"), WAYPOINTS),
        read(rs.getString("preferences_json"), PREFERENCES),
        read(rs.getString("track_json"), TRACK),
        rs.getDouble("distance_m"),
        rs.getDouble("ascent_m"),
        rs.getDouble("descent_m"),
        Instant.parse(rs.getString("created_at")),
        Instant.parse(rs.getString("updated_at")),
        RouteSource.valueOf(rs.getString("source")),
        Optional.ofNullable(rs.getString("external_id")),
        Optional.ofNullable(instantOrNull(rs, "deleted_at")));
  }

  private static long generatedId(KeyHolder keys) {
    Number key = keys.getKey();
    if (key == null) {
      throw new IllegalStateException("SQLite returned no id for the new route");
    }
    return key.longValue();
  }

  private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
    String value = rs.getString(column);
    return value == null ? null : Instant.parse(value);
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("Cannot serialize " + value.getClass().getSimpleName(), e);
    }
  }

  private <T> T read(String value, TypeReference<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JacksonException e) {
      throw new IllegalStateException("Corrupt JSON column in route table", e);
    }
  }
}
