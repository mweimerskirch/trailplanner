package app.trailplan.wahoo;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** One Wahoo connection per rider. Deleted with the rider (foreign key cascade). */
@Repository
public class WahooTokenRepository {

  private final JdbcClient jdbc;

  public WahooTokenRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<WahooToken> find(long userId) {
    return jdbc.sql(
            "SELECT access_token, refresh_token, expires_at FROM wahoo_token WHERE user_id = :user")
        .param("user", userId)
        .query((rs, row) ->
            new WahooToken(
                rs.getString("access_token"),
                rs.getString("refresh_token"),
                Instant.parse(rs.getString("expires_at"))))
        .optional();
  }

  public void save(long userId, WahooToken token) {
    jdbc.sql(
            """
            INSERT INTO wahoo_token (user_id, access_token, refresh_token, expires_at)
            VALUES (:user, :access, :refresh, :expires)
            ON CONFLICT (user_id) DO UPDATE SET access_token = excluded.access_token,
              refresh_token = excluded.refresh_token, expires_at = excluded.expires_at
            """)
        .param("user", userId)
        .param("access", token.accessToken())
        .param("refresh", token.refreshToken())
        .param("expires", token.expiresAt().toString())
        .update();
  }

  public void delete(long userId) {
    jdbc.sql("DELETE FROM wahoo_token WHERE user_id = :user").param("user", userId).update();
  }
}
