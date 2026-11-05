package app.trailplan.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Flyway creates the schema on an empty database; SQLite settings are as the code relies on. */
@SpringBootTest
class SchemaTest {

  @DynamicPropertySource
  static void isolatedStorage(DynamicPropertyRegistry registry) throws IOException {
    Path dir = Files.createTempDirectory("trailplan-schema-test");
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  @Autowired JdbcClient jdbc;

  @Test
  void flywayAppliedTheMigrations() {
    assertThat(
            jdbc.sql(
                    "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank")
                .query(String.class)
                .list())
        .containsExactly("1", "2");
    assertThat(jdbc.sql("PRAGMA journal_mode").query(String.class).single()).isEqualTo("wal");
  }

  @Test
  void deletingARiderDeletesTheirRoutes() {
    jdbc.sql("INSERT INTO rider (id, name, created_at) VALUES (99, 'Gone', :now)")
        .param("now", Timestamps.format(Instant.now()))
        .update();
    jdbc.sql(
            """
            INSERT INTO route (rider_id, name, source, preferences_json, track_json, distance_m,
              ascent_m, descent_m, created_at, updated_at)
            VALUES (99, 'r', 'GPX', '{}', '[]', 0, 0, 0, 'x', 'x')
            """)
        .update();

    jdbc.sql("DELETE FROM rider WHERE id = 99").update();

    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM route WHERE rider_id = 99")
                .query(Integer.class)
                .single())
        .isZero();
  }

  @Test
  void timestampsHaveAFixedWidthSoTheySortAsText() {
    assertThat(Timestamps.format(Instant.parse("2026-01-02T03:04:05Z")))
        .isEqualTo("2026-01-02T03:04:05.000Z");
    assertThat(Timestamps.format(Instant.parse("2026-01-02T03:04:05.123456Z")))
        .isEqualTo("2026-01-02T03:04:05.123Z");
  }
}
