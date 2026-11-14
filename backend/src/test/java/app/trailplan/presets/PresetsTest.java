package app.trailplan.presets;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.TestHttp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PresetsTest {

  @DynamicPropertySource
  static void isolatedStorage(DynamicPropertyRegistry registry) throws IOException {
    Path dir = Files.createTempDirectory("trailplan-presets-test");
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  @LocalServerPort int port;
  @Autowired PresetService service;
  @Autowired PresetRepository repository;
  @Autowired JdbcClient jdbc;

  @Test
  void newRidersStartWithTheBuiltInPresets() throws Exception {
    TestHttp http = new TestHttp(port);
    http.chooseNewRider("Newcomer");

    String presets = http.get("/api/presets").body();
    assertThat(presets)
        .contains("\"name\":\"Flowy trails\"")
        .contains("\"name\":\"Technical\"")
        .contains("\"name\":\"Gravel / XC\"")
        .contains("\"name\":\"Easy / family\"")
        .contains("\"name\":\"Fast connection\"");
    assertThat(presets.indexOf("Flowy trails")).isLessThan(presets.indexOf("Fast connection"));
    assertThat(presets).contains("\"maxDifficulty\":\"S2\"").contains("\"avoidClimbs\":true");
    assertThat(presets)
        .contains("\"name\":\"Quiet roads\"")
        .contains("\"name\":\"Bridleways & forest\"")
        .contains("\"activity\":\"ROAD\"")
        .contains("\"activity\":\"HORSE\"");
  }

  @Test
  void ridersCanAddChangeAndDeleteTheirOwnPresets() throws Exception {
    TestHttp http = new TestHttp(port);
    http.chooseNewRider("Tinkerer");
    String body =
        "{\"name\":\"%s\",\"preferences\":{\"maxDifficulty\":\"S3\",\"levels\":{\"TRAIL\":\"NEVER\"}}}";

    var created = http.send("POST", "/api/presets", body.formatted("Evening loop"));
    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(created.body())
        .contains("\"TRAIL\":\"NEVER\"")
        .contains("\"position\":" + (DefaultPresets.ALL.size() + 1));
    long id = Long.parseLong(created.body().replaceAll(".*?\"id\":(\\d+).*", "$1"));

    assertThat(http.send("POST", "/api/presets", body.formatted("Technical")).body())
        .contains("\"reason\":\"NAME_TAKEN\"");
    assertThat(http.send("PUT", "/api/presets/" + id, body.formatted("Technical")).statusCode())
        .isEqualTo(409);
    assertThat(http.send("PUT", "/api/presets/" + id, body.formatted("Evening loop 2")).body())
        .contains("\"name\":\"Evening loop 2\"");

    // Another rider can neither see nor touch it.
    TestHttp other = new TestHttp(port);
    other.chooseNewRider("Other");
    assertThat(other.get("/api/presets").body()).doesNotContain("Evening loop");
    assertThat(other.send("DELETE", "/api/presets/" + id, null).statusCode()).isEqualTo(404);

    assertThat(http.send("DELETE", "/api/presets/" + id, null).statusCode()).isEqualTo(204);
    assertThat(http.get("/api/presets").body()).doesNotContain("Evening loop");
  }

  @Test
  void existingRidersGetThePresetsOnceAndDeletedOnesStayDeleted() throws Exception {
    // A rider from before presets existed (as after the V3 migration).
    jdbc.sql(
            "INSERT INTO rider (name, created_at, presets_seeded) VALUES ('Veteran', '2026-01-01T00:00:00.000Z', 0)")
        .update();
    long veteran =
        jdbc.sql("SELECT id FROM rider WHERE name = 'Veteran'").query(Long.class).single();

    service.seedMissing();
    assertThat(repository.findAll(veteran)).hasSize(DefaultPresets.ALL.size());

    repository.delete(veteran, repository.findAll(veteran).getFirst().id());
    service.seedMissing(); // e.g. the next start
    assertThat(repository.findAll(veteran)).hasSize(DefaultPresets.ALL.size() - 1);
  }

  @Test
  void ridersSeededBeforeTheRoadAndHorsePresetsGetOnlyThose() {
    // A rider who got the mountain-bike presets (version 1) and renamed one of them.
    jdbc.sql(
            "INSERT INTO rider (name, created_at, presets_seeded) VALUES ('Early', '2026-01-01T00:00:00.000Z', 0)")
        .update();
    long early = jdbc.sql("SELECT id FROM rider WHERE name = 'Early'").query(Long.class).single();
    service.seed(early, 0);
    jdbc.sql("UPDATE rider SET presets_seeded = 1 WHERE id = :id").param("id", early).update();
    Preset flowy = repository.findAll(early).getFirst();
    repository.update(early, flowy.id(), "My trails", flowy.preferences(), java.time.Instant.EPOCH);
    for (DefaultPresets.Preset p : DefaultPresets.ALL) {
      if (p.since() > 1) {
        repository.delete(
            early,
            repository.findAll(early).stream()
                .filter(x -> x.name().equals(p.name()))
                .findFirst()
                .orElseThrow()
                .id());
      }
    }
    int mtbPresets = repository.findAll(early).size();

    service.seedMissing();

    var names = repository.findAll(early).stream().map(Preset::name).toList();
    assertThat(names)
        .contains("My trails", "Quiet roads", "Gentle hack")
        .doesNotContain("Flowy trails");
    assertThat(names).hasSize(mtbPresets + 6);
  }
}
