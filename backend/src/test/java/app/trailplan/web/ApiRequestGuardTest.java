package app.trailplan.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.config.TrailplanProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRequestGuardTest {

  private final ApiRequestGuard guard =
      new ApiRequestGuard(
          new TrailplanProperties(
              new TrailplanProperties.Router(
                  URI.create("http://brouter"), null, Duration.ofSeconds(1), 10),
              new TrailplanProperties.Wahoo(
                  false,
                  URI.create("https://api.wahooligan.com"),
                  "",
                  "",
                  URI.create("http://nas.local:8080")),
              new TrailplanProperties.Komoot(URI.create("https://api.komoot.de")),
              List.of("trailplan.home")));

  /** 200 if the request was let through, else the status the guard answered with. */
  private int status(String method, String host, boolean clientHeader) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/routes");
    request.addHeader("Host", host);
    if (clientHeader) {
      request.addHeader(ApiRequestGuard.CLIENT_HEADER, "web");
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    guard.doFilter(request, response, chain);
    return chain.getRequest() != null ? 200 : response.getStatus();
  }

  @Test
  void acceptsIpAddressesLocalhostAndConfiguredHosts() throws Exception {
    assertThat(status("GET", "192.168.1.20:8080", false)).isEqualTo(200);
    assertThat(status("GET", "[::1]:8080", false)).isEqualTo(200);
    assertThat(status("GET", "localhost:5173", false)).isEqualTo(200);
    assertThat(status("GET", "nas.local:8080", false)).isEqualTo(200); // public URL's host
    assertThat(status("GET", "Trailplan.Home", false)).isEqualTo(200); // allowed-hosts
  }

  @Test
  void refusesOtherHostNamesAgainstDnsRebinding() throws Exception {
    assertThat(status("GET", "evil.example.com", false)).isEqualTo(403);
    assertThat(status("POST", "evil.example.com", true)).isEqualTo(403);
  }

  @Test
  void changesNeedTheClientHeader() throws Exception {
    assertThat(status("POST", "localhost", false)).isEqualTo(403);
    assertThat(status("DELETE", "localhost", false)).isEqualTo(403);
    assertThat(status("POST", "localhost", true)).isEqualTo(200);
  }

  @Test
  void parsesHostHeaders() {
    assertThat(ApiRequestGuard.hostName("Example.com:8080")).isEqualTo("example.com");
    assertThat(ApiRequestGuard.hostName("[fe80::1]:80")).isEqualTo("[fe80::1]");
    assertThat(ApiRequestGuard.hostName(null)).isEmpty();
  }
}
