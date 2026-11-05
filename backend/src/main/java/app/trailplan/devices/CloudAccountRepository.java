package app.trailplan.devices;

import app.trailplan.db.Timestamps;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A rider's OAuth tokens per cloud service. One generic table for all services, so a new cloud
 * integration needs no schema change. Deleted with the rider (foreign key cascade).
 */
@Repository
public class CloudAccountRepository {

  public record Tokens(String accessToken, String refreshToken, Instant expiresAt) {}

  private final JdbcClient jdbc;

  public CloudAccountRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Tokens> find(long userId, String service) {
    return jdbc.sql(
            """
            SELECT access_token, refresh_token, expires_at FROM cloud_account
            WHERE rider_id = :user AND service = :service
            """)
        .param("user", userId)
        .param("service", service)
        .query((rs, row) ->
            new Tokens(
                rs.getString("access_token"),
                rs.getString("refresh_token"),
                Timestamps.parse(rs.getString("expires_at"))))
        .optional();
  }

  public void save(long userId, String service, Tokens tokens) {
    jdbc.sql(
            """
            INSERT INTO cloud_account (rider_id, service, access_token, refresh_token, expires_at)
            VALUES (:user, :service, :access, :refresh, :expires)
            ON CONFLICT (rider_id, service) DO UPDATE SET access_token = excluded.access_token,
              refresh_token = excluded.refresh_token, expires_at = excluded.expires_at
            """)
        .param("user", userId)
        .param("service", service)
        .param("access", tokens.accessToken())
        .param("refresh", tokens.refreshToken())
        .param("expires", Timestamps.format(tokens.expiresAt()))
        .update();
  }

  public void delete(long userId, String service) {
    jdbc.sql("DELETE FROM cloud_account WHERE rider_id = :user AND service = :service")
        .param("user", userId)
        .param("service", service)
        .update();
  }
}
