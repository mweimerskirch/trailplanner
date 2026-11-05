package app.trailplan.komoot;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.TestHttp;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Runs the Komoot import against a fake Komoot API that answers like the real one. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KomootImportTest {

  private static HttpServer fake;
  private static final List<String> requests = new CopyOnWriteArrayList<>();

  @DynamicPropertySource
  static void fakeKomoot(DynamicPropertyRegistry registry) throws IOException {
    fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fake.createContext("/", KomootImportTest::handle);
    fake.start();
    String base = "http://127.0.0.1:" + fake.getAddress().getPort();
    Path dir = Files.createTempDirectory("trailplan-komoot-test");
    registry.add("trailplan.komoot.api-base", () -> base);
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  @AfterAll
  static void stopFake() {
    fake.stop(0);
  }

  private static void handle(HttpExchange ex) throws IOException {
    String path = ex.getRequestURI().toString();
    String auth = ex.getRequestHeaders().getFirst("Authorization");
    requests.add(path + " " + auth);
    String base = "http://127.0.0.1:" + fake.getAddress().getPort();
    String body;
    int status = 200;
    if (path.startsWith("/v006/account/email/rider@example.com/")) {
      if (basic("rider@example.com", "s3cret").equals(auth)) {
        body = "{\"username\":\"12345\",\"password\":\"tok\",\"user\":{\"displayname\":\"Rider\"}}";
      } else {
        status = 401;
        body = "{\"error\":\"unauthorized\"}";
      }
    } else if (!basic("12345", "tok").equals(auth)) {
      status = 401;
      body = "{}";
    } else if (path.equals("/v007/users/12345/tours/?limit=100")) {
      body =
          """
          {"_embedded":{"tours":[
            {"id":111,"name":"Grünewiese","type":"tour_planned","sport":"mtb","distance":42000.5,
             "elevation_up":810,"elevation_down":805,"date":"2026-05-01T09:30:00.000+02:00"}]},
           "_links":{"next":{"href":"%s/v007/users/12345/tours/?limit=100&page=1"}}}
          """
              .formatted(base);
    } else if (path.equals("/v007/users/12345/tours/?limit=100&page=1")) {
      body =
          """
          {"_embedded":{"tours":[
            {"id":222,"name":"Evening ride","type":"tour_recorded","sport":"mtb","distance":15000,
             "elevation_up":200,"elevation_down":200,"date":"2026-05-02T18:00:00.000+02:00"}]},
           "_links":{"next":{"href":"https://evil.example.com/steal"}}}
          """;
    } else if (path.equals("/v007/tours/111?_embedded=coordinates")) {
      body =
          """
          {"id":111,"name":"Grünewiese","type":"tour_planned","distance":42000.5,
           "elevation_up":810,"elevation_down":805,
           "_embedded":{"coordinates":{"items":[
             {"lat":49.61,"lng":6.13,"alt":300.5,"t":0},{"lat":49.62,"lng":6.14,"alt":310,"t":1000},
             {"lat":49.63,"lng":6.15,"t":2000}]}}}
          """;
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

  private static String basic(String user, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  @LocalServerPort int port;

  @Test
  void signInListImportAndSignOut() throws Exception {
    TestHttp http = new TestHttp(port);
    http.chooseNewRider("Komoot rider");

    assertThat(http.get("/api/komoot/tours").statusCode()).isEqualTo(401);
    assertThat(
            http.send(
                    "POST",
                    "/api/komoot/login",
                    "{\"email\":\"rider@example.com\",\"password\":\"wrong\"}")
                .body())
        .contains("Komoot rejected the email or password.");

    var login =
        http.send(
            "POST",
            "/api/komoot/login",
            "{\"email\":\"rider@example.com\",\"password\":\"s3cret\"}");
    assertThat(login.body()).isEqualTo("{\"connected\":true,\"displayName\":\"Rider\"}");

    String tours = http.get("/api/komoot/tours").body();
    assertThat(tours)
        .contains("\"id\":111,\"name\":\"Grünewiese\",\"planned\":true")
        .contains("\"id\":222,\"name\":\"Evening ride\",\"planned\":false")
        .contains("\"date\":\"2026-05-01T07:30:00Z\"")
        .contains("\"imported\":false");
    assertThat(requests).noneMatch(r -> r.contains("evil.example.com") || r.contains("steal"));

    String result = http.send("POST", "/api/komoot/import", "{\"tourIds\":[111,999]}").body();
    assertThat(result)
        .contains("\"source\":\"111\",\"status\":\"IMPORTED\"")
        .contains("\"name\":\"Grünewiese\"")
        .contains("\"source\":\"999\",\"status\":\"FAILED\"");

    assertThat(http.get("/api/komoot/tours").body())
        .contains("\"id\":111,")
        .contains("\"imported\":true");
    assertThat(http.send("POST", "/api/komoot/import", "{\"tourIds\":[111]}").body())
        .contains("ALREADY_IMPORTED");

    String library = http.get("/api/routes").body();
    assertThat(library)
        .contains("\"name\":\"Grünewiese\"")
        .contains("\"distance\":42000.5")
        .contains("\"ascent\":810.0")
        .contains("\"source\":\"KOMOOT\"");

    assertThat(http.send("DELETE", "/api/komoot/login", null).statusCode()).isEqualTo(204);
    assertThat(http.get("/api/komoot/status").body()).contains("\"connected\":false");
    // The password went to Komoot's login endpoint only, never to the tour endpoints.
    assertThat(requests.stream().filter(r -> r.contains(basic("rider@example.com", "s3cret"))))
        .allMatch(r -> r.startsWith("/v006/account/email/"));
  }
}
