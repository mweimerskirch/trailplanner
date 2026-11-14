package app.trailplanner.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("trailplanner")
/**
 * @param allowedHosts host names (besides IP addresses and localhost) under which the API may be
 *     used; see {@link app.trailplanner.web.ApiRequestGuard}
 * @param area where the map opens, "latitude,longitude[,zoom]" (see {@link StartArea}); blank for
 *     the default
 */
public record TrailPlannerProperties(
    Router router,
    @DefaultValue Komoot komoot,
    @DefaultValue Usb usb,
    @DefaultValue List<String> allowedHosts,
    @DefaultValue("") String area) {

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
   * Komoot import. Komoot has no official API for personal accounts; this uses the same unofficial
   * API as komoot.com, which may change without notice.
   *
   * @param apiBase Komoot API server (overridden in tests)
   */
  public record Komoot(@DefaultValue("https://api.komoot.de") URI apiBase) {}

  /**
   * Syncing routes to devices connected over USB (MTP).
   *
   * @param helper the {@code trailplanner-mtp} program (see {@code backend/mtp}), a path or a name
   *     on the PATH
   * @param timeout how long one call of the helper may take
   */
  public record Usb(
      @DefaultValue("trailplanner-mtp") String helper, @DefaultValue("PT2M") Duration timeout) {}
}
