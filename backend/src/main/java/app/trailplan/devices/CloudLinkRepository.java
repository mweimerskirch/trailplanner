package app.trailplan.devices;

import app.trailplan.db.Timestamps;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Which remote route (per cloud service) belongs to which saved route. Links are deleted with their
 * route (foreign key with ON DELETE CASCADE) and are not copied when a route is duplicated.
 */
@Repository
public class CloudLinkRepository {

  private final JdbcClient jdbc;

  public CloudLinkRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<String> findRemoteId(long routeId, String service) {
    return jdbc.sql(
            "SELECT remote_id FROM route_cloud_link WHERE route_id = :route AND service = :service")
        .param("route", routeId)
        .param("service", service)
        .query(String.class)
        .optional();
  }

  public void save(long routeId, String service, String remoteId, Instant syncedAt) {
    jdbc.sql(
            """
            INSERT INTO route_cloud_link (route_id, service, remote_id, synced_at)
            VALUES (:route, :service, :remote, :at)
            ON CONFLICT (route_id, service)
              DO UPDATE SET remote_id = excluded.remote_id, synced_at = excluded.synced_at
            """)
        .param("route", routeId)
        .param("service", service)
        .param("remote", remoteId)
        .param("at", Timestamps.format(syncedAt))
        .update();
  }

  /** Forgets all of a rider's links to a service, e.g. when they disconnect the account. */
  public void deleteForRider(long userId, String service) {
    jdbc.sql(
            """
            DELETE FROM route_cloud_link WHERE service = :service
              AND route_id IN (SELECT id FROM route WHERE rider_id = :user)
            """)
        .param("user", userId)
        .param("service", service)
        .update();
  }

  public void delete(long routeId, String service) {
    jdbc.sql("DELETE FROM route_cloud_link WHERE route_id = :route AND service = :service")
        .param("route", routeId)
        .param("service", service)
        .update();
  }
}
