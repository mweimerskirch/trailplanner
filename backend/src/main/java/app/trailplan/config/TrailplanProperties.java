package app.trailplan.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("trailplan")
public record TrailplanProperties(Router router, Wahoo wahoo) {

  /**
   * @param baseUrl BRouter server, e.g. http://brouter:17777
   * @param customProfileDir directory shared with BRouter's custom profile directory
   * @param timeout read timeout for one routing request
   * @param legCacheSize number of calculated legs kept in memory
   */
  public record Router(
      URI baseUrl,
      Path customProfileDir,
      @DefaultValue("PT60S") Duration timeout,
      @DefaultValue("2000") int legCacheSize) {}

  /**
   * Wahoo Cloud API. Leave disabled until you have registered an app at developers.wahooligan.com.
   *
   * @param publicUrl where the planner is reachable in the browser; used for redirects
   */
  public record Wahoo(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("https://api.wahooligan.com") URI apiBase,
      @DefaultValue("") String clientId,
      @DefaultValue("") String clientSecret,
      @DefaultValue("http://localhost:8080") URI publicUrl) {

    public URI redirectUri() {
      return publicUrl.resolve("/api/wahoo/callback");
    }
  }
}
