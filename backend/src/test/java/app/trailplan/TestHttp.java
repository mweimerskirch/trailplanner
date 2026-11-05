package app.trailplan;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Minimal HTTP helper for tests that run the whole application on a random port. */
public final class TestHttp {

  // Keeps the session cookie between requests, like a browser.
  private final HttpClient http =
      HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
  private final int port;

  public TestHttp(int port) {
    this.port = port;
  }

  public HttpResponse<String> get(String path) throws Exception {
    return send("GET", path, null);
  }

  public HttpResponse<String> send(String method, String path, String jsonBody) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (jsonBody == null) {
      request.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      request
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
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
