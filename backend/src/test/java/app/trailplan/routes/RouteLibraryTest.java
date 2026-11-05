package app.trailplan.routes;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.TestHttp;
import app.trailplan.routing.LegBreakdown;
import app.trailplan.routing.PlannedRoute;
import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrackPoint;
import app.trailplan.routing.Waypoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouteLibraryTest {

  @DynamicPropertySource
  static void isolatedStorage(DynamicPropertyRegistry registry) throws IOException {
    Path dir = Files.createTempDirectory("trailplan-library-test");
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  private static final List<TrackPoint> TRACK =
      List.of(new TrackPoint(6.13, 49.61, 300.0), new TrackPoint(6.14, 49.62, 320.0));

  @LocalServerPort int port;
  @Autowired RouteRepository repository;
  @Autowired JdbcClient jdbc;
  TestHttp http;
  long rider;

  @BeforeEach
  void setUp() throws Exception {
    http = new TestHttp(port);
    jdbc.sql("DELETE FROM route").update();
    rider = http.chooseNewRider("Library");
  }

  private long plannedRoute(String name) {
    return repository.insert(
        rider,
        new RouteDraft(
            name,
            List.of(Waypoint.of(49.61, 6.13), Waypoint.of(49.62, 6.14)),
            RoutingPreferences.defaults()),
        new PlannedRoute(TRACK, 1500, 20, 0, LegBreakdown.empty()),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void trashRestoreAndDeletePermanently() throws Exception {
    long kept = plannedRoute("Kept");
    long trashed = plannedRoute("Trashed");

    assertThat(http.send("DELETE", "/api/routes/" + trashed, null).statusCode()).isEqualTo(204);
    assertThat(http.get("/api/routes").body()).contains("Kept").doesNotContain("Trashed");
    assertThat(http.get("/api/routes/trash").body())
        .contains("\"name\":\"Trashed\"")
        .contains("\"deletedAt\":\"");

    assertThat(http.send("POST", "/api/routes/" + trashed + "/restore", null).body())
        .contains("\"deletedAt\":null");
    assertThat(http.get("/api/routes").body()).contains("Trashed");

    http.send("DELETE", "/api/routes/" + trashed, null);
    assertThat(http.send("DELETE", "/api/routes/" + trashed + "/permanent", null).statusCode())
        .isEqualTo(204);
    assertThat(http.get("/api/routes/" + trashed).statusCode()).isEqualTo(404);
    assertThat(http.get("/api/routes/" + kept).statusCode()).isEqualTo(200);
  }

  @Test
  void emptyTrashOnlyRemovesTrashedRoutes() throws Exception {
    long kept = plannedRoute("Kept");
    http.send("DELETE", "/api/routes/" + plannedRoute("A"), null);
    http.send("DELETE", "/api/routes/" + plannedRoute("B"), null);

    assertThat(http.send("DELETE", "/api/routes/trash", null).body()).isEqualTo("{\"deleted\":2}");
    assertThat(http.get("/api/routes/trash").body()).isEqualTo("[]");
    assertThat(http.get("/api/routes/" + kept).statusCode()).isEqualTo(200);
  }

  @Test
  void renameAndDuplicate() throws Exception {
    long id = plannedRoute("Original");

    assertThat(http.send("PATCH", "/api/routes/" + id, "{\"name\":\"  Grünewiese  \"}").body())
        .contains("\"name\":\"Grünewiese\"");
    assertThat(http.send("PATCH", "/api/routes/" + id, "{\"name\":\" \"}").statusCode())
        .isEqualTo(400);

    var copy = http.send("POST", "/api/routes/" + id + "/duplicate", null);
    assertThat(copy.statusCode()).isEqualTo(201);
    assertThat(copy.body())
        .contains("\"name\":\"Grünewiese (copy)\"")
        .contains("\"editable\":true");
    assertThat(http.get("/api/routes").body()).contains("Grünewiese (copy)");
  }

  @Test
  void importedRoutesAreListedAsNotEditableAndKeepTheirTrack() throws Exception {
    long id =
        repository.insertImported(
            rider,
            new ImportedRoute("Komoot tour", RouteSource.KOMOOT, "komoot:42", TRACK, 1500, 20, 5),
            Instant.parse("2026-01-01T00:00:00Z"));

    assertThat(http.get("/api/routes").body())
        .contains("\"source\":\"KOMOOT\"")
        .contains("\"editable\":false");
    assertThat(http.get("/api/routes/" + id).body())
        .contains("\"waypoints\":[]")
        .contains("\"track\":[[6.13,49.61,300.0],[6.14,49.62,320.0]]");
    assertThat(repository.findByExternalId(rider, "komoot:42"))
        .hasValueSatisfying(m -> assertThat(m.trashed()).isFalse());

    http.send("DELETE", "/api/routes/" + id, null);
    assertThat(repository.findByExternalId(rider, "komoot:42"))
        .hasValueSatisfying(m -> assertThat(m.trashed()).isTrue());
  }

  @Test
  void editingAnImportKeepsTheOriginalWhichCanBeRestored() throws Exception {
    long id =
        repository.insertImported(
            rider,
            new ImportedRoute("Komoot tour", RouteSource.KOMOOT, "komoot:7", TRACK, 1500, 20, 5),
            Instant.parse("2026-01-01T00:00:00Z"));
    // Saving the edited route (as the planner does after "Edit route") replaces the track...
    List<TrackPoint> edited =
        List.of(new TrackPoint(6.13, 49.61, 300.0), new TrackPoint(6.15, 49.63, 330.0));
    repository.update(
        rider,
        id,
        new RouteDraft(
            "Komoot tour",
            List.of(Waypoint.of(49.61, 6.13), Waypoint.of(49.63, 6.15)),
            RoutingPreferences.defaults()),
        new PlannedRoute(edited, 2600, 30, 0, LegBreakdown.empty()),
        Instant.parse("2026-01-02T00:00:00Z"),
        null);
    assertThat(http.get("/api/routes/" + id).body())
        .contains("\"editable\":true")
        .contains("\"hasOriginal\":true")
        .contains("[6.15,49.63,330.0]");

    // ...but the original is still there: as a download, and restorable.
    assertThat(http.get("/api/routes/" + id + "/export/gpx?original=true").body())
        .contains("lat=\"49.620000\" lon=\"6.140000\"")
        .doesNotContain("49.630000");
    String reverted = http.send("POST", "/api/routes/" + id + "/revert", null).body();
    assertThat(reverted)
        .contains("\"editable\":false")
        .contains("\"waypoints\":[]")
        .contains("\"track\":[[6.13,49.61,300.0],[6.14,49.62,320.0]]")
        .contains("\"distance\":1500.0");

    long planned = plannedRoute("Planned");
    assertThat(http.send("POST", "/api/routes/" + planned + "/revert", null).body())
        .contains("\"reason\":\"NO_ORIGINAL\"");
  }

  @Test
  void routesInTheTrashOrChangedElsewhereAreNotOverwritten() throws Exception {
    long id = plannedRoute("Tour");
    String updatedAt =
        http.get("/api/routes/" + id).body().replaceAll(".*\"updatedAt\":\"([^\"]+)\".*", "$1");
    String draft =
        "{\"name\":\"Tour\",\"waypoints\":[{\"lat\":49.61,\"lng\":6.13},{\"lat\":49.62,"
            + "\"lng\":6.14}],\"preferences\":{},\"expectedUpdatedAt\":\"%s\"}";

    // Saved elsewhere in the meantime (here: a rename), so the planner's version is outdated.
    http.send("PATCH", "/api/routes/" + id, "{\"name\":\"Renamed elsewhere\"}");
    var outdated = http.send("PUT", "/api/routes/" + id, draft.formatted(updatedAt));
    assertThat(outdated.statusCode()).isEqualTo(409);
    assertThat(outdated.body()).contains("\"reason\":\"CHANGED_ELSEWHERE\"");

    http.send("DELETE", "/api/routes/" + id, null);
    var trashed = http.send("PATCH", "/api/routes/" + id, "{\"name\":\"x\"}");
    assertThat(trashed.statusCode()).isEqualTo(409);
    assertThat(trashed.body()).contains("\"reason\":\"IN_TRASH\"");
    assertThat(http.send("PUT", "/api/routes/" + id, draft.formatted(updatedAt)).body())
        .contains("\"reason\":\"IN_TRASH\"");
  }

  @Test
  void unknownRoutesAre404() throws Exception {
    assertThat(http.send("DELETE", "/api/routes/999999", null).statusCode()).isEqualTo(404);
    assertThat(http.send("POST", "/api/routes/999999/restore", null).statusCode()).isEqualTo(404);
    assertThat(http.send("POST", "/api/routes/999999/duplicate", null).statusCode()).isEqualTo(404);
    assertThat(http.send("PATCH", "/api/routes/999999", "{\"name\":\"x\"}").statusCode())
        .isEqualTo(404);
  }

  @Test
  void archivedRoutesLeaveTheListButAreKept() throws Exception {
    long kept = plannedRoute("Kept");
    long old = plannedRoute("Old favourite");

    assertThat(http.send("POST", "/api/routes/" + old + "/archive", null).body())
        .contains("\"archivedAt\":\"");
    assertThat(http.get("/api/routes").body()).contains("Kept").doesNotContain("Old favourite");
    assertThat(http.get("/api/routes/archive").body())
        .contains("\"name\":\"Old favourite\"")
        .doesNotContain("Kept");
    // Still a normal route: it can be opened and exported.
    assertThat(http.get("/api/routes/" + old).statusCode()).isEqualTo(200);

    // Emptying the trash never touches the archive.
    http.send("DELETE", "/api/routes/" + kept, null);
    http.send("DELETE", "/api/routes/trash", null);
    assertThat(http.get("/api/routes/archive").body()).contains("Old favourite");

    assertThat(http.send("DELETE", "/api/routes/" + old + "/archive", null).body())
        .contains("\"archivedAt\":null");
    assertThat(http.get("/api/routes").body()).contains("Old favourite");
    assertThat(http.get("/api/routes/archive").body()).isEqualTo("[]");
    assertThat(http.send("POST", "/api/routes/999999/archive", null).statusCode()).isEqualTo(404);
  }
}
