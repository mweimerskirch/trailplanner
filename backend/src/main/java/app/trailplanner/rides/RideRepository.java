package app.trailplanner.rides;

import app.trailplanner.db.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Rides in SQLite, every method scoped to one rider. Deleted rides keep their row (without the
 * file) so that importing from a device does not bring them back.
 */
@Repository
public class RideRepository {

  private static final String SUMMARY_COLUMNS =
      """
      id, name, sport, started_at, utc_offset_s, elapsed_s, moving_s, distance_m, ascent_m,
      descent_m, max_speed, avg_heart_rate, max_heart_rate, avg_power, calories, file_name
      """;

  /** A stored ride found by its file's checksum. */
  public record FileMatch(long id, String name, boolean deleted) {}

  /** A ride's file as imported. */
  public record StoredFile(String fileName, byte[] data, String sha256) {}

  private final JdbcClient jdbc;

  public RideRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** The rider's rides (not deleted), newest first. */
  public List<RideSummary> findSummaries(long userId) {
    return jdbc.sql(
            "SELECT "
                + SUMMARY_COLUMNS
                + " FROM ride WHERE rider_id = :user AND deleted_at IS NULL"
                + " ORDER BY started_at DESC")
        .param("user", userId)
        .query(RideRepository::mapSummary)
        .list();
  }

  public Optional<RideSummary> findSummary(long userId, long id) {
    return jdbc.sql(
            "SELECT "
                + SUMMARY_COLUMNS
                + " FROM ride WHERE rider_id = :user AND id = :id AND deleted_at IS NULL")
        .param("user", userId)
        .param("id", id)
        .query(RideRepository::mapSummary)
        .optional();
  }

  public Optional<StoredFile> findFile(long userId, long id) {
    return jdbc.sql(
            """
            SELECT file_name, fit, file_sha256 FROM ride
            WHERE rider_id = :user AND id = :id AND deleted_at IS NULL
            """)
        .param("user", userId)
        .param("id", id)
        .query((rs, row) ->
            new StoredFile(
                rs.getString("file_name"), rs.getBytes("fit"), rs.getString("file_sha256")))
        .optional();
  }

  public Optional<FileMatch> findBySha256(long userId, String sha256) {
    return jdbc.sql(
            """
            SELECT id, name, deleted_at IS NOT NULL AS deleted FROM ride
            WHERE rider_id = :user AND file_sha256 = :sha
            """)
        .param("user", userId)
        .param("sha", sha256)
        .query((rs, row) ->
            new FileMatch(rs.getLong("id"), rs.getString("name"), rs.getBoolean("deleted")))
        .optional();
  }

  /** "name/size" of every file the rider imported, deleted rides included. */
  public Set<String> findFileKeys(long userId) {
    return new HashSet<>(
        jdbc.sql("SELECT file_name, file_size FROM ride WHERE rider_id = :user")
            .param("user", userId)
            .query((rs, row) -> fileKey(rs.getString("file_name"), rs.getLong("file_size")))
            .list());
  }

  public static String fileKey(String fileName, long size) {
    return fileName + "/" + size;
  }

  public long insert(
      long userId,
      String name,
      RecordedRide ride,
      String fileName,
      byte[] data,
      String sha256,
      Instant now) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO ride (rider_id, name, sport, started_at, utc_offset_s, elapsed_s, moving_s,
                              distance_m, ascent_m, descent_m, max_speed, avg_heart_rate,
                              max_heart_rate, avg_power, calories, fit, file_name, file_size,
                              file_sha256, imported_at)
            VALUES (:user, :name, :sport, :started, :offset, :elapsed, :moving,
                    :distance, :ascent, :descent, :maxSpeed, :avgHr,
                    :maxHr, :avgPower, :calories, :fit, :fileName, :fileSize,
                    :sha, :now)
            """)
        .param("user", userId)
        .param("name", name)
        .param("sport", ride.sport())
        .param("started", Timestamps.format(ride.startedAt()))
        .param("offset", ride.utcOffset())
        .param("elapsed", ride.elapsed())
        .param("moving", ride.moving())
        .param("distance", ride.distance())
        .param("ascent", ride.ascent())
        .param("descent", ride.descent())
        .param("maxSpeed", ride.maxSpeed())
        .param("avgHr", ride.avgHeartRate())
        .param("maxHr", ride.maxHeartRate())
        .param("avgPower", ride.avgPower())
        .param("calories", ride.calories())
        .param("fit", data)
        .param("fileName", fileName)
        .param("fileSize", data.length)
        .param("sha", sha256)
        .param("now", Timestamps.format(now))
        .update(keys);
    Number key = keys.getKey();
    if (key == null) {
      throw new IllegalStateException("SQLite returned no id for the new ride");
    }
    return key.longValue();
  }

  public boolean rename(long userId, long id, String name) {
    return jdbc.sql(
                "UPDATE ride SET name = :name"
                    + " WHERE rider_id = :user AND id = :id AND deleted_at IS NULL")
            .param("name", name)
            .param("user", userId)
            .param("id", id)
            .update()
        == 1;
  }

  /** Deletes the ride's file and hides it; the row stays so imports skip the file. */
  public boolean delete(long userId, long id, Instant now) {
    return jdbc.sql(
                """
                UPDATE ride SET deleted_at = :now, fit = NULL
                WHERE rider_id = :user AND id = :id AND deleted_at IS NULL
                """)
            .param("now", Timestamps.format(now))
            .param("user", userId)
            .param("id", id)
            .update()
        == 1;
  }

  /** Brings a deleted ride back, with its file uploaded again. */
  public void restore(long userId, long id, byte[] data) {
    jdbc.sql("UPDATE ride SET deleted_at = NULL, fit = :fit WHERE rider_id = :user AND id = :id")
        .param("fit", data)
        .param("user", userId)
        .param("id", id)
        .update();
  }

  private static RideSummary mapSummary(ResultSet rs, int row) throws SQLException {
    double distance = rs.getDouble("distance_m");
    double moving = rs.getDouble("moving_s");
    return new RideSummary(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("sport"),
        Timestamps.parse(rs.getString("started_at")),
        integer(rs, "utc_offset_s"),
        rs.getDouble("elapsed_s"),
        moving,
        distance,
        rs.getDouble("ascent_m"),
        rs.getDouble("descent_m"),
        moving > 0 ? distance / moving : null,
        decimal(rs, "max_speed"),
        integer(rs, "avg_heart_rate"),
        integer(rs, "max_heart_rate"),
        integer(rs, "avg_power"),
        integer(rs, "calories"),
        rs.getString("file_name"));
  }

  /** A nullable INTEGER column. */
  private static Integer integer(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  /** A nullable REAL column. */
  private static Double decimal(ResultSet rs, String column) throws SQLException {
    double value = rs.getDouble(column);
    return rs.wasNull() ? null : value;
  }
}
