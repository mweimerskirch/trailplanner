package app.trailplan.routing;

import java.util.Locale;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Calls the BRouter HTTP server for a single leg. */
public final class BRouterClient {

  private final RestClient http;
  private final BRouterResponseParser parser;

  public BRouterClient(RestClient http, BRouterResponseParser parser) {
    this.http = http;
    this.parser = parser;
  }

  public RouteLeg route(GeoPoint from, GeoPoint to, String profileId) {
    String body;
    try {
      body =
          http.get()
              .uri(uri ->
                  uri.path("/brouter")
                      .queryParam("lonlats", "{lonlats}")
                      .queryParam("profile", "{profile}")
                      .queryParam("alternativeidx", 0)
                      .queryParam("format", "geojson")
                      .build(from.toLonLat() + "|" + to.toLonLat(), profileId))
              .retrieve()
              .body(String.class);
    } catch (RestClientResponseException e) {
      throw fromRouterMessage(e.getResponseBodyAsString(), e);
    } catch (ResourceAccessException e) {
      throw new RoutingException(
          RoutingException.Reason.ROUTER_UNAVAILABLE,
          "The routing server is not reachable. Check that the BRouter container is running.",
          e);
    }
    if (body == null || !body.stripLeading().startsWith("{")) {
      // BRouter sometimes reports errors as plain text with a 200 status.
      throw fromRouterMessage(body == null ? "" : body, null);
    }
    return parser.parse(body);
  }

  static RoutingException fromRouterMessage(String message, Throwable cause) {
    String lower = message.toLowerCase(Locale.ROOT);
    if (lower.contains("datafile") && lower.contains("not found")) {
      return new RoutingException(
          RoutingException.Reason.POINT_NOT_ON_MAP,
          "A point is outside the downloaded map area. Add the missing tile with"
              + " scripts/download-segments.sh.",
          cause);
    }
    if (lower.contains("not mapped")) {
      return new RoutingException(
          RoutingException.Reason.POINT_NOT_ON_MAP,
          "A point is too far from any trail or road. Move it closer to a way.",
          cause);
    }
    if (lower.contains("not reachable")
        || lower.contains("target island")
        || lower.contains("no track found")) {
      return new RoutingException(
          RoutingException.Reason.NO_ROUTE,
          "No route found with these preferences. Allow more way types or move the point.",
          cause);
    }
    String detail = message.strip();
    return new RoutingException(
        RoutingException.Reason.ROUTER_ERROR,
        "Routing failed: " + (detail.length() > 200 ? detail.substring(0, 200) + "…" : detail),
        cause);
  }
}
