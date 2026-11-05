package app.trailplan.routes;

import app.trailplan.db.Timestamps;
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
 * is treated as if it did not exist. Trashed routes keep their row with {@code deleted_at} set;
 * archived routes have {@code archived_at} set.
 */
@Repository
public class RouteRepository {

  private static final TypeReference<List<Waypoint>> WAYPOINTS = new TypeReference<>() {};
  private static final TypeReference<List<TrackPoint>> TRACK = new TypeReference<>() {};
  private static final TypeReference<RoutingPreferences> PREFERENCES = new TypeReference<>() {};
  private static final TypeReference<OriginalTrack> ORIGINAL = new TypeReference<>() {};

  private static final String SUMMARY_COLUMNS =
      """
      id, name, distance_m, ascent_m, updated_at, source, deleted_at, archived_at,
      waypoints_json IS NOT NULL AS editable
      """;

  /** How an import (or a route found by its import id) stands in the library. */
  public record ExternalMatch(long id, String name, boolean trashed) {}

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public RouteRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** Routes in the library (not archived, not in the trash), most recently changed first. */
  public List<RouteSummary> findActiveSummaries(long userId) {
    return summaries(userId, "deleted_at IS NULL AND archived_at IS NULL", "updated_at DESC");
  }

  /** Archived routes (not in the trash), most recently archived first. */
  public List<RouteSummary> findArchivedSummaries(long userId) {
    return summaries(userId, "deleted_at IS NULL AND archived_at IS NOT NULL", "archived_at DESC");
  }

  /** Routes in the trash, most recently deleted first. */
  public List<RouteSummary> findTrashedSummaries(long userId) {
    return summaries(userId, "deleted_at IS NOT NULL", "deleted_at DESC");
  }

  private List<RouteSummary> summaries(long userId, String condition, String order) {
    return jdbc.sql(
            "SELECT "
                + SUMMARY_COLUMNS
                + " FROM route WHERE rider_id = :user AND "
                + condition
                + " ORDER BY "
                + order)
        .param("user", userId)
        .query(RouteRepository::mapSummary)
        .list();
  }

  public Optional<StoredRoute> findById(long userId, long id) {
    return jdbc.sql("SELECT * FROM route WHERE id = :id AND rider_id = :user")
        .param("id", id)
        .param("user", userId)
        .query(this::map)
        .optional();
  }

  /** The rider's route imported from this source, wherever it is (library, archive or trash). */
  public Optional<ExternalMatch> findByExternalId(long userId, String externalId) {
    return jdbc.sql(
            """
            SELECT id, name, deleted_at IS NOT NULL AS trashed FROM route
            WHERE rider_id = :user AND external_id = :eid
            """)
        .param("user", userId)
        .param("eid", externalId)
        .query((rs, row) ->
            new ExternalMatch(rs.getLong("id"), rs.getString("name"), rs.getBoolean("trashed")))
        .optional();
  }

  /** External ids of the rider's routes that are not in the trash, starting with this prefix. */
  public List<String> findActiveExternalIds(long userId, String prefix) {
    return jdbc.sql(
            """
            SELECT external_id FROM route
            WHERE rider_id = :user AND deleted_at IS NULL AND external_id LIKE :prefix || '%'
            """)
        .param("user", userId)
        .param("prefix", prefix)
        .query(String.class)
        .list();
  }

  public long insert(long userId, RouteDraft draft, PlannedRoute planned, Instant now) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO route (rider_id, name, source, waypoints_json, preferences_json,
                               track_json, distance_m, ascent_m, descent_m, created_at, updated_at)
            VALUES (:user, :name, 'PLANNER', :waypoints, :preferences,
                    :track, :distance, :ascent, :descent, :now, :now)
            """)
        .param("user", userId)
        .param("name", draft.name())
        .param("waypoints", write(draft.waypoints()))
        .param("preferences", write(draft.preferences()))
        .param("track", write(planned.points()))
        .param("distance", planned.distance())
        .param("ascent", planned.ascent())
        .param("descent", planned.descent())
        .param("now", Timestamps.format(now))
        .update(keys);
    return generatedId(keys);
  }

  /**
   * Stores an imported track as-is, without waypoints (not editable yet). The track is also kept as
   * the original, which edits never change.
   */
  public long insertImported(long userId, ImportedRoute route, Instant now) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO route (rider_id, name, source, external_id, preferences_json, track_json,
                               distance_m, ascent_m, descent_m, original_json,
                               created_at, updated_at)
            VALUES (:user, :name, :source, :externalId, :preferences, :track,
                    :distance, :ascent, :descent, :original, :now, :now)
            """)
        .param("user", userId)
        .param("name", route.name())
        .param("source", route.source().name())
        .param("externalId", route.externalId())
        .param("preferences", write(RoutingPreferences.defaults()))
        .param("track", write(route.track()))
        .param("distance", route.distance())
        .param("ascent", route.ascent())
        .param("descent", route.descent())
        .param(
            "original",
            write(
                new OriginalTrack(
                    route.track(), route.distance(), route.ascent(), route.descent())))
        .param("now", Timestamps.format(now))
        .update(keys);
    return generatedId(keys);
  }

  /**
   * Saves new waypoints and the track calculated from them. Only routes in the library or archive
   * (not the trash). With {@code expectedUpdatedAt}, only if nobody saved the route since.
   */
  public boolean update(
      long userId,
      long id,
      RouteDraft draft,
      PlannedRoute planned,
      Instant now,
      Instant expectedUpdatedAt) {
    return jdbc.sql(
                """
                UPDATE route SET name = :name, waypoints_json = :waypoints,
                  preferences_json = :preferences, track_json = :track, distance_m = :distance,
                  ascent_m = :ascent, descent_m = :descent, updated_at = :now
                WHERE id = :id AND rider_id = :user AND deleted_at IS NULL
                  AND (:expected IS NULL OR updated_at = :expected)
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
            .param("now", Timestamps.format(now))
            .param("expected", Timestamps.format(expectedUpdatedAt))
            .update()
        == 1;
  }

  /** Puts the original imported track back and drops the waypoints (view mode again). */
  public boolean revertToOriginal(long userId, long id, OriginalTrack original, Instant now) {
    return jdbc.sql(
                """
                UPDATE route SET waypoints_json = NULL, track_json = :track, distance_m = :distance,
                  ascent_m = :ascent, descent_m = :descent, updated_at = :now
                WHERE id = :id AND rider_id = :user AND deleted_at IS NULL
                """)
            .param("id", id)
            .param("user", userId)
            .param("track", write(original.track()))
            .param("distance", original.distance())
            .param("ascent", original.ascent())
            .param("descent", original.descent())
            .param("now", Timestamps.format(now))
            .update()
        == 1;
  }

  public boolean rename(long userId, long id, String name, Instant now) {
    return jdbc.sql(
                """
                UPDATE route SET name = :name, updated_at = :now
                WHERE id = :id AND rider_id = :user AND deleted_at IS NULL
                """)
            .param("id", id)
            .param("user", userId)
            .param("name", name)
            .param("now", Timestamps.format(now))
            .update()
        == 1;
  }

  /**
   * Copies a route under a new name, including the original of an import. The copy has no cloud
   * links (they live in their own table) and no import id, so re-sending or re-importing never
   * confuses it with the original.
   *
   * @return the id of the copy, or empty if the original does not exist
   */
  public Optional<Long> duplicate(long userId, long id, String name, Instant now) {
    KeyHolder keys = new GeneratedKeyHolder();
    int rows =
        jdbc.sql(
                """
                INSERT INTO route (rider_id, name, source, waypoints_json, preferences_json,
                                   track_json, distance_m, ascent_m, descent_m, original_json,
                                   created_at, updated_at)
                SELECT rider_id, :name, source, waypoints_json, preferences_json,
                       track_json, distance_m, ascent_m, descent_m, original_json, :now, :now
                FROM route WHERE id = :id AND rider_id = :user
                """)
            .param("id", id)
            .param("user", userId)
            .param("name", name)
            .param("now", Timestamps.format(now))
            .update(keys);
    return rows == 1 ? Optional.of(generatedId(keys)) : Optional.empty();
  }

  /** Archives or unarchives a route. False if it does not exist. */
  public boolean setArchived(long userId, long id, Instant archivedAt) {
    return jdbc.sql("UPDATE route SET archived_at = :at WHERE id = :id AND rider_id = :user")
            .param("id", id)
            .param("user", userId)
            .param("at", Timestamps.format(archivedAt))
            .update()
        == 1;
  }

  /** Moves a route to the trash. False if it does not exist or is already there. */
  public boolean moveToTrash(long userId, long id, Instant now) {
    return jdbc.sql(
                """
                UPDATE route SET deleted_at = :now
                WHERE id = :id AND rider_id = :user AND deleted_at IS NULL
                """)
            .param("id", id)
            .param("user", userId)
            .param("now", Timestamps.format(now))
            .update()
        == 1;
  }

  /** Takes a route out of the trash. False if it does not exist or is not in the trash. */
  public boolean restore(long userId, long id) {
    return jdbc.sql(
                """
                UPDATE route SET deleted_at = NULL
                WHERE id = :id AND rider_id = :user AND deleted_at IS NOT NULL
                """)
            .param("id", id)
            .param("user", userId)
            .update()
        == 1;
  }

  public boolean deletePermanently(long userId, long id) {
    return jdbc.sql("DELETE FROM route WHERE id = :id AND rider_id = :user")
            .param("id", id)
            .param("user", userId)
            .update()
        == 1;
  }

  /** Deletes every route in the trash for good and returns how many there were. */
  public int emptyTrash(long userId) {
    return jdbc.sql("DELETE FROM route WHERE rider_id = :user AND deleted_at IS NOT NULL")
        .param("user", userId)
        .update();
  }

  private static RouteSummary mapSummary(ResultSet rs, int row) throws SQLException {
    return new RouteSummary(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getDouble("distance_m"),
        rs.getDouble("ascent_m"),
        Timestamps.parse(rs.getString("updated_at")),
        RouteSource.valueOf(rs.getString("source")),
        rs.getBoolean("editable"),
        Timestamps.parse(rs.getString("deleted_at")),
        Timestamps.parse(rs.getString("archived_at")));
  }

  private StoredRoute map(ResultSet rs, int row) throws SQLException {
    String waypoints = rs.getString("waypoints_json");
    String original = rs.getString("original_json");
    return new StoredRoute(
        rs.getLong("id"),
        rs.getString("name"),
        waypoints == null ? List.of() : read(waypoints, WAYPOINTS),
        waypoints != null,
        read(rs.getString("preferences_json"), PREFERENCES),
        read(rs.getString("track_json"), TRACK),
        rs.getDouble("distance_m"),
        rs.getDouble("ascent_m"),
        rs.getDouble("descent_m"),
        Timestamps.parse(rs.getString("created_at")),
        Timestamps.parse(rs.getString("updated_at")),
        RouteSource.valueOf(rs.getString("source")),
        Optional.ofNullable(rs.getString("external_id")),
        Optional.ofNullable(Timestamps.parse(rs.getString("deleted_at"))),
        Optional.ofNullable(Timestamps.parse(rs.getString("archived_at"))),
        original == null ? Optional.empty() : Optional.of(read(original, ORIGINAL)));
  }

  private static long generatedId(KeyHolder keys) {
    Number key = keys.getKey();
    if (key == null) {
      throw new IllegalStateException("SQLite returned no id for the new route");
    }
    return key.longValue();
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
