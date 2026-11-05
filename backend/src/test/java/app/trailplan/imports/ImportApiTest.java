package app.trailplan.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImportApiTest {

  @DynamicPropertySource
  static void isolatedStorage(DynamicPropertyRegistry registry) throws IOException {
    Path dir = Files.createTempDirectory("trailplan-import-test");
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  private static final String GPX =
      """
      <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><name>Uploaded tour</name>
        <trkseg><trkpt lat="49.61" lon="6.13"><ele>300</ele></trkpt>
                <trkpt lat="49.62" lon="6.14"><ele>340</ele></trkpt></trkseg></trk></gpx>
      """;

  @LocalServerPort int port;
  private final HttpClient client =
      HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();

  @Test
  void importsFilesReportsDuplicatesAndFailures() throws Exception {
    choose(new app.trailplan.TestHttp(port));
    HttpResponse<String> first =
        upload(new String[] {"tour.gpx", GPX}, new String[] {"notes.txt", "hello"});

    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(first.body())
        .contains("\"source\":\"tour.gpx\",\"status\":\"IMPORTED\"")
        .contains("\"name\":\"Uploaded tour\"")
        .contains("\"source\":\"notes.txt\",\"status\":\"FAILED\"")
        .contains("Unsupported file type");

    HttpResponse<String> again = upload(new String[] {"tour-copy.gpx", GPX});
    assertThat(again.body()).contains("\"status\":\"ALREADY_IMPORTED\"");

    // Re-importing a file whose route is in the trash restores it instead.
    long id = Long.parseLong(again.body().replaceAll(".*\"routeId\":(\\d+).*", "$1"));
    client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/routes/" + id))
            .header("X-Trailplan-Client", "web")
            .DELETE()
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(get("/api/routes")).doesNotContain("Uploaded tour");
    assertThat(upload(new String[] {"tour.gpx", GPX}).body())
        .contains("was in the trash and is restored");

    String library = get("/api/routes");
    assertThat(library).contains("\"source\":\"GPX\"").contains("\"editable\":false");
    assertThat(library.split("Uploaded tour", -1)).hasSize(2); // exactly one copy
  }

  private String get(String path) throws Exception {
    return client
        .send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
            HttpResponse.BodyHandlers.ofString())
        .body();
  }

  private HttpResponse<String> upload(String[]... files) throws Exception {
    String boundary = "----trailplan-test";
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    for (String[] file : files) {
      body.writeBytes(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\""
                  + file[0]
                  + "\"\r\nContent-Type: application/octet-stream\r\n\r\n"
                  + file[1]
                  + "\r\n")
              .getBytes(StandardCharsets.UTF_8));
    }
    body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/import/files"))
            .header("X-Trailplan-Client", "web")
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** Creates a rider with a helper client and hands its cookie to this test's client. */
  private void choose(app.trailplan.TestHttp helper) throws Exception {
    String body =
        client
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users"))
                    .header("X-Trailplan-Client", "web")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Importer\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString())
            .body();
    String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");
    client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/session"))
            .header("X-Trailplan-Client", "web")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"userId\":" + id + "}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
