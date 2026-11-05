package app.trailplan.devices;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.TestHttp;
import app.trailplan.routes.RouteDraft;
import app.trailplan.routes.RouteRepository;
import app.trailplan.routing.LegBreakdown;
import app.trailplan.routing.PlannedRoute;
import app.trailplan.routing.RoutingPreferences;
import app.trailplan.routing.TrackPoint;
import app.trailplan.routing.Waypoint;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The generic device API, with Wahoo as the cloud integration against a fake Wahoo API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceApiTest {

  private static HttpServer fake;
  private static final List<String> requests = new CopyOnWriteArrayList<>();
  private static final AtomicInteger nextId = new AtomicInteger(555);
  private static final AtomicBoolean routeDeletedAtWahoo = new AtomicBoolean(false);

  @DynamicPropertySource
  static void fakeWahoo(DynamicPropertyRegistry registry) throws IOException {
    fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fake.createContext("/", DeviceApiTest::handle);
    fake.start();
    Path dir = Files.createTempDirectory("trailplan-device-test");
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
    registry.add("trailplan.wahoo.enabled", () -> "true");
    registry.add("trailplan.wahoo.client-id", () -> "id");
    registry.add("trailplan.wahoo.client-secret", () -> "secret");
    registry.add(
        "trailplan.wahoo.api-base", () -> "http://127.0.0.1:" + fake.getAddress().getPort());
  }

  @AfterAll
  static void stopFake() {
    fake.stop(0);
  }

  private static void handle(HttpExchange ex) throws IOException {
    String request = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
    requests.add(request);
    int status = 200;
    String body;
    if (request.equals("POST /v1/routes")) {
      body = "{\"id\":" + nextId.getAndIncrement() + "}";
    } else if (request.startsWith("PUT /v1/routes/")) {
      if (routeDeletedAtWahoo.getAndSet(false)) {
        status = 404;
        body = "{}";
      } else {
        body = "{\"id\":" + request.substring("PUT /v1/routes/".length()) + "}";
      }
    } else {
      status = 404;
      body = "{}";
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(status, bytes.length);
    ex.getResponseBody().write(bytes);
    ex.close();
  }

  @LocalServerPort int port;
  @Autowired RouteRepository routes;
  @Autowired CloudLinkRepository links;
  @Autowired JdbcClient jdbc;

  private long route(long rider) {
    return routes.insert(
        rider,
        new RouteDraft(
            "Tour",
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
  void listsDevicesAndSendsRoutesToTheCloud() throws Exception {
    TestHttp http = new TestHttp(port);
    long rider = http.chooseNewRider("Wahoo rider");
    jdbc.sql(
            "INSERT OR REPLACE INTO wahoo_token VALUES (:user, 'access', 'refresh', '2099-01-01T00:00:00Z')")
        .param("user", rider)
        .update();

    String devices = http.get("/api/devices").body();
    assertThat(devices.indexOf("\"id\":\"wahoo-elemnt\""))
        .isGreaterThanOrEqualTo(0)
        .isLessThan(devices.indexOf("\"id\":\"generic\""));
    assertThat(devices)
        .contains("{\"id\":\"gpx\",\"label\":\"GPX\",\"extension\":\"gpx\"}")
        .contains(
            "\"cloud\":{\"serviceName\":\"Wahoo\",\"enabled\":true,\"connected\":true,"
                + "\"connectPath\":\"/api/wahoo/connect\"}")
        .contains("\"title\":\"Copy over USB\"");

    long id = route(rider);
    assertThat(http.send("POST", "/api/devices/wahoo-elemnt/cloud/routes/" + id, null).body())
        .contains("\"service\":\"wahoo\",\"remoteId\":\"555\"");

    // Sending again updates the same Wahoo route.
    http.send("POST", "/api/devices/wahoo-elemnt/cloud/routes/" + id, null);
    assertThat(requests).contains("PUT /v1/routes/555");
    assertThat(links.findRemoteId(id, "wahoo")).contains("555");

    // Deleted in the Wahoo app: the route is created again and the link updated.
    routeDeletedAtWahoo.set(true);
    assertThat(http.send("POST", "/api/devices/wahoo-elemnt/cloud/routes/" + id, null).body())
        .contains("\"remoteId\":\"556\"");
    assertThat(links.findRemoteId(id, "wahoo")).contains("556");

    // A copy is a new route at Wahoo, not an update of the original's.
    long copy = routes.duplicate(rider, id, "Tour (copy)", Instant.now()).orElseThrow();
    assertThat(links.findRemoteId(copy, "wahoo")).isEmpty();

    // Links go with their route.
    routes.deletePermanently(rider, id);
    assertThat(links.findRemoteId(id, "wahoo")).isEmpty();

    assertThat(http.send("POST", "/api/devices/generic/cloud/routes/" + copy, null).statusCode())
        .isEqualTo(404);
    assertThat(http.send("DELETE", "/api/devices/wahoo-elemnt/cloud/connection", null).statusCode())
        .isEqualTo(204);
    assertThat(http.get("/api/devices").body()).contains("\"connected\":false");
    var notConnected = http.send("POST", "/api/devices/wahoo-elemnt/cloud/routes/" + copy, null);
    assertThat(notConnected.statusCode()).isEqualTo(409);
    assertThat(notConnected.body()).contains("Connect your Wahoo account first.");
  }
}
