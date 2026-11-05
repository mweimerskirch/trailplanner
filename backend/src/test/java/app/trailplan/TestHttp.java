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
}
