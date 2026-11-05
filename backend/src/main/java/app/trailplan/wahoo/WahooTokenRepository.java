package app.trailplan.wahoo;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Trailplan is single-user, so there is at most one Wahoo connection (row id 1). */
@Repository
public class WahooTokenRepository {

  private final JdbcClient jdbc;

  public WahooTokenRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<WahooToken> find() {
    return jdbc.sql("SELECT access_token, refresh_token, expires_at FROM wahoo_token WHERE id = 1")
        .query((rs, row) ->
            new WahooToken(
                rs.getString("access_token"),
                rs.getString("refresh_token"),
                Instant.parse(rs.getString("expires_at"))))
        .optional();
  }

  public void save(WahooToken token) {
    jdbc.sql(
            """
            INSERT INTO wahoo_token (id, access_token, refresh_token, expires_at)
            VALUES (1, :access, :refresh, :expires)
            ON CONFLICT (id) DO UPDATE SET access_token = excluded.access_token,
              refresh_token = excluded.refresh_token, expires_at = excluded.expires_at
            """)
        .param("access", token.accessToken())
        .param("refresh", token.refreshToken())
        .param("expires", token.expiresAt().toString())
        .update();
  }

  public void delete() {
    jdbc.sql("DELETE FROM wahoo_token WHERE id = 1").update();
  }
}
