package app.trailplan.db;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.TestHttp;
import app.trailplan.devices.CloudLinkRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** A database created by the first release (schema.sql, no version) must upgrade in place. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatabaseMigrationsTest {

  @DynamicPropertySource
  static void legacyDatabase(DynamicPropertyRegistry registry) throws Exception {
    Path dir = Files.createTempDirectory("trailplan-migration-test");
    String url = "jdbc:sqlite:" + dir.resolve("legacy.db");
    try (Connection c = DriverManager.getConnection(url);
        Statement s = c.createStatement()) {
      // The schema exactly as the old schema.sql created it.
      s.execute(
          """
          CREATE TABLE route (
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
            waypoints_json TEXT NOT NULL, preferences_json TEXT NOT NULL, track_json TEXT NOT NULL,
            distance_m REAL NOT NULL, ascent_m REAL NOT NULL, descent_m REAL NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
            wahoo_route_id INTEGER, wahoo_synced_at TEXT)
          """);
      s.execute("CREATE INDEX route_updated_at ON route (updated_at)");
      s.execute(
          """
          CREATE TABLE wahoo_token (id INTEGER PRIMARY KEY CHECK (id = 1),
            access_token TEXT NOT NULL, refresh_token TEXT NOT NULL, expires_at TEXT NOT NULL)
          """);
      s.execute(
          """
          INSERT INTO route (name, waypoints_json, preferences_json, track_json, distance_m,
            ascent_m, descent_m, created_at, updated_at, wahoo_route_id, wahoo_synced_at)
          VALUES ('Old route', '[{"lat":49.61,"lng":6.13},{"lat":49.62,"lng":6.14}]', '{}',
            '[[6.13,49.61,300.0],[6.14,49.62,null]]', 1500, 20, 5,
            '2025-06-01T10:00:00Z', '2025-06-01T10:00:00Z', 777, '2025-06-02T08:00:00Z')
          """);
    }
    registry.add("spring.datasource.url", () -> url);
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  @LocalServerPort int port;
  @Autowired DatabaseMigrations migrations;
  @Autowired CloudLinkRepository links;
  @Autowired JdbcClient jdbc;

  @Test
  void upgradesALegacyDatabaseAndKeepsItsRoutes() throws Exception {
    assertThat(migrations.currentVersion()).isEqualTo(DatabaseMigrations.MIGRATIONS.size());

    String library = new TestHttp(port).get("/api/routes").body();
    assertThat(library)
        .contains("\"name\":\"Old route\"")
        .contains("\"source\":\"PLANNER\"")
        .contains("\"editable\":true")
        .contains("\"deletedAt\":null");

    long id = jdbc.sql("SELECT id FROM route WHERE name = 'Old route'").query(Long.class).single();
    assertThat(links.findRemoteId(id, "wahoo")).contains("777");
    assertThat(
            jdbc.sql("SELECT synced_at FROM route_cloud_link WHERE route_id = :id")
                .param("id", id)
                .query(String.class)
                .single())
        .isEqualTo("2025-06-02T08:00:00Z");
  }

  @Test
  void runningAgainChangesNothing() {
    migrations.afterPropertiesSet();
    assertThat(migrations.currentVersion()).isEqualTo(DatabaseMigrations.MIGRATIONS.size());
  }
}
