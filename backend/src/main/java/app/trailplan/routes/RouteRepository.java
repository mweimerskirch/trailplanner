package app.trailplan.routes;

import app.trailplan.routing.GeoPoint;
import app.trailplan.routing.PlannedRoute;
import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrackPoint;
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

@Repository
public class RouteRepository {

  private static final TypeReference<List<GeoPoint>> WAYPOINTS = new TypeReference<>() {};
  private static final TypeReference<List<TrackPoint>> TRACK = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public RouteRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<RouteSummary> findAllSummaries() {
    return jdbc.sql(
            """
            SELECT id, name, distance_m, ascent_m, updated_at, wahoo_synced_at
            FROM route ORDER BY updated_at DESC
            """)
        .query((rs, row) ->
            new RouteSummary(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDouble("distance_m"),
                rs.getDouble("ascent_m"),
                Instant.parse(rs.getString("updated_at")),
                instantOrNull(rs, "wahoo_synced_at")))
        .list();
  }

  public Optional<StoredRoute> findById(long id) {
    return jdbc.sql("SELECT * FROM route WHERE id = :id")
        .param("id", id)
        .query(this::map)
        .optional();
  }

  public long insert(RouteDraft draft, PlannedRoute planned, Instant now) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO route (name, waypoints_json, preferences_json, track_json,
                               distance_m, ascent_m, descent_m, created_at, updated_at)
            VALUES (:name, :waypoints, :preferences, :track,
                    :distance, :ascent, :descent, :now, :now)
            """)
        .param("name", draft.name())
        .param("waypoints", write(draft.waypoints()))
        .param("preferences", write(draft.preferences()))
        .param("track", write(planned.points()))
        .param("distance", planned.distance())
        .param("ascent", planned.ascent())
        .param("descent", planned.descent())
        .param("now", now.toString())
        .update(keys);
    Number key = keys.getKey();
    if (key == null) {
      throw new IllegalStateException("SQLite returned no id for the new route");
    }
    return key.longValue();
  }

  public boolean update(long id, RouteDraft draft, PlannedRoute planned, Instant now) {
    return jdbc.sql(
                """
                UPDATE route SET name = :name, waypoints_json = :waypoints,
                  preferences_json = :preferences, track_json = :track, distance_m = :distance,
                  ascent_m = :ascent, descent_m = :descent, updated_at = :now
                WHERE id = :id
                """)
            .param("id", id)
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

  public boolean delete(long id) {
    return jdbc.sql("DELETE FROM route WHERE id = :id").param("id", id).update() == 1;
  }

  public void markSyncedToWahoo(long id, long wahooRouteId, Instant at) {
    jdbc.sql("UPDATE route SET wahoo_route_id = :wid, wahoo_synced_at = :at WHERE id = :id")
        .param("id", id)
        .param("wid", wahooRouteId)
        .param("at", at.toString())
        .update();
  }

  /** Called when Wahoo no longer knows the route, e.g. after it was deleted in the app. */
  public void clearWahooLink(long id) {
    jdbc.sql("UPDATE route SET wahoo_route_id = NULL, wahoo_synced_at = NULL WHERE id = :id")
        .param("id", id)
        .update();
  }

  private StoredRoute map(ResultSet rs, int row) throws SQLException {
    long wahooId = rs.getLong("wahoo_route_id");
    Optional<Long> wahooRouteId = rs.wasNull() ? Optional.empty() : Optional.of(wahooId);
    return new StoredRoute(
        rs.getLong("id"),
        rs.getString("name"),
        read(rs.getString("waypoints_json"), WAYPOINTS),
        read(rs.getString("preferences_json"), new TypeReference<RoutingPreferences>() {}),
        read(rs.getString("track_json"), TRACK),
        rs.getDouble("distance_m"),
        rs.getDouble("ascent_m"),
        rs.getDouble("descent_m"),
        Instant.parse(rs.getString("created_at")),
        Instant.parse(rs.getString("updated_at")),
        wahooRouteId,
        Optional.ofNullable(instantOrNull(rs, "wahoo_synced_at")));
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
