package app.trailplan;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Minimal HTTP helper for tests that run the whole application on a random port. */
public final class TestHttp {

  // Keeps the session cookie between requests, like a browser.
  private final HttpClient http =
      HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
  private final int port;
  private String rider;
  private String language;

  public TestHttp(int port) {
    this.port = port;
  }

  /** Sends Accept-Language with every request, as the UI does for the chosen language. */
  public TestHttp inLanguage(String language) {
    this.language = language;
    return this;
  }

  /** Sends X-Trailplan-Rider with every request, as a page showing this rider would. */
  public TestHttp asRider(long riderId) {
    this.rider = Long.toString(riderId);
    return this;
  }

  public HttpResponse<String> get(String path) throws Exception {
    return send("GET", path, null);
  }

  public HttpResponse<String> send(String method, String path, String jsonBody) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("X-Trailplan-Client", "web"); // as the app sends it (see ApiRequestGuard)
    if (rider != null) {
      request.header("X-Trailplan-Rider", rider);
    }
    if (language != null) {
      request.header("Accept-Language", language);
    }
    if (jsonBody == null) {
      request.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      request
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** Uploads files as the form field {@code files}, as a browser would. */
  public HttpResponse<String> upload(String path, Map<String, byte[]> files) throws Exception {
    String boundary = "----trailplan-test";
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    for (Map.Entry<String, byte[]> file : files.entrySet()) {
      body.writeBytes(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\""
                  + file.getKey()
                  + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      body.writeBytes(file.getValue());
      body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("X-Trailplan-Client", "web")
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
    if (rider != null) {
      request.header("X-Trailplan-Rider", rider);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** Creates a rider and chooses it for this client (the cookie is kept). Returns its id. */
  public long chooseNewRider(String name) throws Exception {
    String body = send("POST", "/api/users", "{\"name\":\"" + name + "\"}").body();
    long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    choose(id);
    return id;
  }

  public void choose(long riderId) throws Exception {
    HttpResponse<String> response = send("PUT", "/api/session", "{\"userId\":" + riderId + "}");
    if (response.statusCode() != 200) {
      throw new IllegalStateException("Choosing rider failed: " + response.body());
    }
  }
}
