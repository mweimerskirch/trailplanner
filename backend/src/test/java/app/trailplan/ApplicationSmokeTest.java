package app.trailplan;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.routes.RouteDraft;
import app.trailplan.routes.RouteRepository;
import app.trailplan.routing.GeoPoint;
import app.trailplan.routing.LegBreakdown;
import app.trailplan.routing.PlannedRoute;
import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrackPoint;
import java.io.IOException;
import java.net.http.HttpResponse;
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

/**
 * Starts the whole application on a throw-away SQLite database (no BRouter needed) and checks the
 * JSON the frontend relies on. Catches wiring and serialization changes on framework upgrades.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {

  @DynamicPropertySource
  static void isolatedStorage(DynamicPropertyRegistry registry) throws IOException {
    Path dir = Files.createTempDirectory("trailplan-test");
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  @LocalServerPort int port;
  @Autowired RouteRepository repository;

  @Test
  void servesDefaultPreferencesWithoutARider() throws Exception {
    HttpResponse<String> response = new TestHttp(port).get("/api/routing/preferences/defaults");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"TRAIL\":\"FAVOURITE\"")
        .contains("\"maxDifficulty\":\"S3\"")
        .contains("\"avoidClimbs\":false");
  }

  @Test
  void savedRoutesUseIsoTimestampsAndExplicitNulls() throws Exception {
    TestHttp http = new TestHttp(port);
    long rider = http.chooseNewRider("Smoke");
    List<GeoPoint> waypoints = List.of(new GeoPoint(49.61, 6.13), new GeoPoint(49.62, 6.14));
    PlannedRoute planned =
        new PlannedRoute(
            List.of(new TrackPoint(6.13, 49.61, 300.0), new TrackPoint(6.14, 49.62, null)),
            1500,
            20,
            5,
            LegBreakdown.empty());
    long id =
        repository.insert(
            rider,
            new RouteDraft("Smoke test", waypoints, RoutingPreferences.defaults()),
            planned,
            Instant.parse("2026-01-02T03:04:05Z"));

    HttpResponse<String> response = http.get("/api/routes/" + id);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"name\":\"Smoke test\"")
        .contains("\"updatedAt\":\"2026-01-02T03:04:05Z\"")
        .contains("\"deletedAt\":null");
    assertThat(http.get("/api/routes/" + id + "/export/gpx").body())
        .contains("<trkpt lat=\"49.610000\"");
    HttpResponse<String> fit = http.get("/api/routes/" + id + "/export/fit");
    assertThat(fit.headers().firstValue("Content-Disposition"))
        .hasValueSatisfying(v -> assertThat(v).contains("Smoke_test.fit"));
    assertThat(fit.body().substring(8, 12)).isEqualTo(".FIT");
    assertThat(http.get("/api/routes/" + id + "/export/kml").statusCode()).isEqualTo(400);
  }

  @Test
  void reportsProblemsAsProblemDetails() throws Exception {
    TestHttp http = new TestHttp(port);

    HttpResponse<String> noRider = http.get("/api/routes");
    assertThat(noRider.statusCode()).isEqualTo(401);
    assertThat(noRider.body()).contains("\"reason\":\"NO_USER\"");

    http.chooseNewRider("Problems");
    HttpResponse<String> missing = http.get("/api/routes/999999");
    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(missing.body()).contains("Route 999999 does not exist.");

    HttpResponse<String> invalid =
        http.send(
            "POST",
            "/api/routing/leg",
            "{\"from\":{\"lat\":91,\"lng\":6},\"to\":{\"lat\":49,\"lng\":6},\"preferences\":{}}");
    assertThat(invalid.statusCode()).isEqualTo(400);
  }
}
