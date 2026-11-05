package app.trailplan.users;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.TestHttp;
import app.trailplan.routes.RouteDraft;
import app.trailplan.routes.RouteRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Riders have separate libraries; choosing one is per browser (cookie). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RidersTest {

  @DynamicPropertySource
  static void isolatedStorage(DynamicPropertyRegistry registry) throws IOException {
    Path dir = Files.createTempDirectory("trailplan-riders-test");
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  @LocalServerPort int port;
  @Autowired RouteRepository routes;

  private long route(long rider, String name) {
    return routes.insert(
        rider,
        new RouteDraft(
            name,
            List.of(Waypoint.of(49.61, 6.13), Waypoint.of(49.62, 6.14)),
            RoutingPreferences.defaults()),
        new PlannedRoute(
            List.of(new TrackPoint(6.13, 49.61, 300.0), new TrackPoint(6.14, 49.62, 320.0)),
            1500,
            20,
            0,
            LegBreakdown.empty()),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void ridersOnlySeeTheirOwnRoutes() throws Exception {
    TestHttp anna = new TestHttp(port);
    TestHttp ben = new TestHttp(port);
    assertThat(anna.get("/api/session").body()).isEqualTo("{\"user\":null}");

    long annaId = anna.chooseNewRider("Anna");
    long benId = ben.chooseNewRider("Ben");
    long annasRoute = route(annaId, "Anna's loop");
    route(benId, "Ben's loop");

    assertThat(anna.get("/api/session").body()).contains("\"name\":\"Anna\"");
    assertThat(anna.get("/api/routes").body()).contains("Anna's loop").doesNotContain("Ben's loop");
    assertThat(ben.get("/api/routes").body()).contains("Ben's loop").doesNotContain("Anna's loop");

    // Another rider's route behaves as if it did not exist, whatever you try.
    assertThat(ben.get("/api/routes/" + annasRoute).statusCode()).isEqualTo(404);
    assertThat(ben.get("/api/routes/" + annasRoute + "/export/gpx").statusCode()).isEqualTo(404);
    assertThat(ben.send("DELETE", "/api/routes/" + annasRoute, null).statusCode()).isEqualTo(404);
    assertThat(ben.send("PATCH", "/api/routes/" + annasRoute, "{\"name\":\"mine\"}").statusCode())
        .isEqualTo(404);
    assertThat(ben.send("POST", "/api/routes/" + annasRoute + "/duplicate", null).statusCode())
        .isEqualTo(404);
    assertThat(anna.get("/api/routes/" + annasRoute).body()).contains("Anna's loop");

    // Switching rider in a browser switches the library.
    ben.choose(annaId);
    assertThat(ben.get("/api/routes").body()).contains("Anna's loop");
  }

  @Test
  void renameAndDeleteRiders() throws Exception {
    TestHttp http = new TestHttp(port);
    long id = http.chooseNewRider("Carla");
    route(id, "Carla's loop");

    assertThat(http.send("PATCH", "/api/users/" + id, "{\"name\":\" Carla M. \"}").body())
        .contains("\"name\":\"Carla M.\"");
    assertThat(http.get("/api/users").body())
        .contains("{\"id\":" + id + ",\"name\":\"Carla M.\",\"routes\":1}");
    assertThat(http.send("POST", "/api/users", "{\"name\":\"\"}").statusCode()).isEqualTo(400);

    assertThat(http.send("DELETE", "/api/users/" + id, null).statusCode()).isEqualTo(204);
    assertThat(http.get("/api/users").body()).doesNotContain("Carla");
    assertThat(routes.findActiveSummaries(id)).isEmpty(); // routes went with the rider
    // The browser still has the cookie of the deleted rider: treated as "no rider chosen".
    assertThat(http.get("/api/session").body()).isEqualTo("{\"user\":null}");
    assertThat(http.get("/api/routes").body()).contains("\"reason\":\"NO_USER\"");
  }

  @Test
  void leavingForgetsTheRider() throws Exception {
    TestHttp http = new TestHttp(port);
    http.chooseNewRider("Dora");

    assertThat(http.send("DELETE", "/api/session", null).statusCode()).isEqualTo(204);
    assertThat(http.get("/api/session").body()).isEqualTo("{\"user\":null}");
    assertThat(http.send("PUT", "/api/session", "{\"userId\":999999}").statusCode()).isEqualTo(404);
  }

  @Test
  void aTabStillShowingAnotherRiderIsStopped() throws Exception {
    TestHttp browser = new TestHttp(port);
    long first = browser.chooseNewRider("First");
    // Another tab of the same browser switches to a second rider (shared cookie)...
    browser.chooseNewRider("Second");

    // ...while this tab still shows the first one and says so in every request.
    var stale = browser.asRider(first).get("/api/routes");
    assertThat(stale.statusCode()).isEqualTo(409);
    assertThat(stale.body()).contains("\"reason\":\"RIDER_CHANGED\"").contains("Second");
  }
}
