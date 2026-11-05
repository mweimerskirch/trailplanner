package app.trailplan.db;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Brings the SQLite schema up to date at startup. The schema version is stored in SQLite's {@code
 * PRAGMA user_version}; each entry of {@link #MIGRATIONS} runs once, in its own transaction.
 *
 * <p>Only ever append migrations; never change one that has been released, because existing
 * databases have already run it. Migration 1 is the original schema and uses {@code IF NOT EXISTS},
 * so databases created before migrations existed (version 0) pick up where they are.
 */
@Component
public class DatabaseMigrations implements InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(DatabaseMigrations.class);

  static final List<List<String>> MIGRATIONS =
      List.of(
          // 1: original schema
          List.of(
              """
              CREATE TABLE IF NOT EXISTS route (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                name             TEXT    NOT NULL,
                waypoints_json   TEXT    NOT NULL,
                preferences_json TEXT    NOT NULL,
                track_json       TEXT    NOT NULL,
                distance_m       REAL    NOT NULL,
                ascent_m         REAL    NOT NULL,
                descent_m        REAL    NOT NULL DEFAULT 0,
                created_at       TEXT    NOT NULL,
                updated_at       TEXT    NOT NULL,
                wahoo_route_id   INTEGER,
                wahoo_synced_at  TEXT
              )
              """,
              "CREATE INDEX IF NOT EXISTS route_updated_at ON route (updated_at)",
              """
              CREATE TABLE IF NOT EXISTS wahoo_token (
                id            INTEGER PRIMARY KEY CHECK (id = 1),
                access_token  TEXT NOT NULL,
                refresh_token TEXT NOT NULL,
                expires_at    TEXT NOT NULL
              )
              """),
          // 2: route library: trash, import source and external id (e.g. a Komoot tour)
          List.of(
              "ALTER TABLE route ADD COLUMN source TEXT NOT NULL DEFAULT 'PLANNER'",
              "ALTER TABLE route ADD COLUMN external_id TEXT",
              "ALTER TABLE route ADD COLUMN deleted_at TEXT",
              "CREATE INDEX route_external_id ON route (external_id)"),
          // 3: links to cloud services become generic (one row per route and service), so device
          //    integrations need no columns of their own; existing Wahoo links move over.
          List.of(
              """
              CREATE TABLE route_cloud_link (
                route_id  INTEGER NOT NULL REFERENCES route (id) ON DELETE CASCADE,
                service   TEXT    NOT NULL,
                remote_id TEXT    NOT NULL,
                synced_at TEXT    NOT NULL,
                PRIMARY KEY (route_id, service)
              )
              """,
              """
              INSERT INTO route_cloud_link (route_id, service, remote_id, synced_at)
              SELECT id, 'wahoo', CAST(wahoo_route_id AS TEXT), COALESCE(wahoo_synced_at, updated_at)
              FROM route WHERE wahoo_route_id IS NOT NULL
              """,
              "ALTER TABLE route DROP COLUMN wahoo_route_id",
              "ALTER TABLE route DROP COLUMN wahoo_synced_at"));

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  public DatabaseMigrations(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @Override
  public void afterPropertiesSet() {
    jdbc.sql("PRAGMA journal_mode = WAL").query().singleValue();
    int version = currentVersion();
    for (int i = version; i < MIGRATIONS.size(); i++) {
      int target = i + 1;
      List<String> statements = MIGRATIONS.get(i);
      transactions.executeWithoutResult(status -> {
        statements.forEach(sql -> jdbc.sql(sql).update());
        // PRAGMA values cannot be bound as parameters; target is our own integer.
        jdbc.sql("PRAGMA user_version = " + target).update();
      });
      log.info("Database migrated to schema version {}", target);
    }
  }

  int currentVersion() {
    return jdbc.sql("PRAGMA user_version").query(Integer.class).single();
  }
}
