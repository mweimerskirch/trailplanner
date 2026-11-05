package app.trailplan.users;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  /** A rider in the selection list; {@code routes} counts routes in the library (not the trash). */
  public record UserSummary(long id, String name, int routes) {}

  private final JdbcClient jdbc;

  public UserRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<UserSummary> findAll() {
    return jdbc.sql(
            """
            SELECT u.id, u.name,
              (SELECT COUNT(*) FROM route r WHERE r.user_id = u.id AND r.deleted_at IS NULL) AS routes
            FROM app_user u ORDER BY u.name COLLATE NOCASE, u.id
            """)
        .query((rs, row) ->
            new UserSummary(rs.getLong("id"), rs.getString("name"), rs.getInt("routes")))
        .list();
  }

  public Optional<CurrentUser> findById(long id) {
    return jdbc.sql("SELECT id, name FROM app_user WHERE id = :id")
        .param("id", id)
        .query((rs, row) -> new CurrentUser(rs.getLong("id"), rs.getString("name")))
        .optional();
  }

  public long insert(String name, Instant now) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql("INSERT INTO app_user (name, created_at) VALUES (:name, :now)")
        .param("name", name)
        .param("now", now.toString())
        .update(keys);
    Number key = keys.getKey();
    if (key == null) {
      throw new IllegalStateException("SQLite returned no id for the new rider");
    }
    return key.longValue();
  }

  public boolean rename(long id, String name) {
    return jdbc.sql("UPDATE app_user SET name = :name WHERE id = :id")
            .param("id", id)
            .param("name", name)
            .update()
        == 1;
  }

  /** Deletes the rider with all their routes and cloud connections (foreign keys cascade). */
  public boolean delete(long id) {
    return jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", id).update() == 1;
  }
}
