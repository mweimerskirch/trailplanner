package app.trailplanner.komoot;

import app.trailplanner.config.TrailPlannerProperties;
import app.trailplanner.routing.TrackPoint;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

/**
 * Komoot's unofficial web API (the one komoot.com itself uses): sign in with email and password,
 * list the rider's tours and fetch a tour's coordinates. The password is only used for signing in;
 * later requests use the user id and token Komoot returns.
 */
@Component
public class KomootClient {

  /** A signed-in Komoot account. Kept in the HTTP session, never in the database. */
  public record Session(String userId, String token, String displayName) implements Serializable {
    private static final long serialVersionUID = 1L;
  }

  /** {@code planned} is false for recorded tours. Distances in metres. */
  public record Tour(
      long id,
      String name,
      boolean planned,
      String sport,
      double distance,
      double elevationUp,
      double elevationDown,
      Instant date) {}

  public record TourTrack(Tour tour, List<TrackPoint> points) {
    public TourTrack {
      points = List.copyOf(points);
    }
  }

  private static final int MAX_PAGES = 100;

  private final RestClient http;
  private final URI apiBase;

  public KomootClient(TrailPlannerProperties props, RestClient.Builder builder) {
    this.apiBase = props.komoot().apiBase();
    this.http = builder.clone().baseUrl(apiBase.toString()).build();
  }

  public Session login(String email, String password) {
    JsonNode body;
    try {
      body =
          http.get()
              // Path-segment encoding keeps "@" literal, as komoot.com sends it.
              .uri(
                  apiBase.resolve(
                      "/v006/account/email/"
                          + UriUtils.encodePathSegment(email, StandardCharsets.UTF_8)
                          + "/"))
              .headers(h -> h.setBasicAuth(email, password, StandardCharsets.UTF_8))
              .retrieve()
              .body(JsonNode.class);
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
      throw new KomootException(
          HttpStatus.UNAUTHORIZED, "Komoot rejected the email or password.", e);
    } catch (HttpClientErrorException.NotFound e) {
      throw new KomootException(HttpStatus.UNAUTHORIZED, "Komoot does not know this email.", e);
    } catch (RestClientException e) {
      throw unavailable(e);
    }
    if (body == null || !body.hasNonNull("username") || !body.hasNonNull("password")) {
      throw new KomootException(HttpStatus.BAD_GATEWAY, "Komoot returned an unexpected answer.");
    }
    String displayName = body.path("user").path("displayname").asString(email);
    return new Session(
        body.get("username").asString(), body.get("password").asString(), displayName);
  }

  /** All tours of the account, following Komoot's pagination. */
  public List<Tour> tours(Session session) {
    List<Tour> tours = new ArrayList<>();
    URI next = apiBase.resolve("/v007/users/" + session.userId() + "/tours/?limit=100");
    for (int page = 0; next != null && page < MAX_PAGES; page++) {
      JsonNode body = get(session, next);
      for (JsonNode tour : body.path("_embedded").path("tours")) {
        tours.add(tour(tour));
      }
      next = nextPage(body);
    }
    return tours;
  }

  public TourTrack track(Session session, long tourId) {
    JsonNode body =
        get(session, apiBase.resolve("/v007/tours/" + tourId + "?_embedded=coordinates"));
    List<TrackPoint> points = new ArrayList<>();
    for (JsonNode c : body.path("_embedded").path("coordinates").path("items")) {
      if (c.path("lat").isNumber() && c.path("lng").isNumber()) {
        JsonNode alt = c.path("alt");
        points.add(
            new TrackPoint(
                c.path("lng").asDouble(),
                c.path("lat").asDouble(),
                alt.isNumber() ? alt.asDouble() : null));
      }
    }
    return new TourTrack(tour(body), points);
  }

  private JsonNode get(Session session, URI uri) {
    try {
      JsonNode body =
          http.get()
              .uri(uri)
              .headers(
                  h -> h.setBasicAuth(session.userId(), session.token(), StandardCharsets.UTF_8))
              .retrieve()
              .body(JsonNode.class);
      if (body == null) {
        throw new KomootException(HttpStatus.BAD_GATEWAY, "Komoot returned an empty answer.");
      }
      return body;
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
      throw new KomootException(
          HttpStatus.UNAUTHORIZED, "The Komoot login has expired. Sign in again.", e);
    } catch (HttpClientErrorException.NotFound e) {
      throw new KomootException(HttpStatus.NOT_FOUND, "Komoot does not know this tour.", e);
    } catch (RestClientException e) {
      throw unavailable(e);
    }
  }

  /** The next page link, but only if it points back to the Komoot API (credentials go along). */
  private URI nextPage(JsonNode body) {
    JsonNode href = body.path("_links").path("next").path("href");
    if (!href.isString()) {
      return null;
    }
    URI next = apiBase.resolve(href.asString());
    boolean sameServer =
        Objects.equals(next.getScheme(), apiBase.getScheme())
            && Objects.equals(next.getHost(), apiBase.getHost())
            && next.getPort() == apiBase.getPort();
    return sameServer ? next : null;
  }

  private static Tour tour(JsonNode t) {
    return new Tour(
        t.path("id").asLong(),
        t.path("name").asString("Komoot tour"),
        !"tour_recorded".equals(t.path("type").asString("")),
        t.path("sport").asString(""),
        t.path("distance").asDouble(0),
        t.path("elevation_up").asDouble(0),
        t.path("elevation_down").asDouble(0),
        date(t.path("date").asString(null)));
  }

  private static Instant date(String value) {
    if (value == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static KomootException unavailable(RestClientException e) {
    String message =
        e instanceof ResourceAccessException
            ? "Komoot is not reachable. Check the internet connection of the server."
            : "Komoot did not answer as expected. Try again later, or import GPX files instead.";
    return new KomootException(HttpStatus.BAD_GATEWAY, message, e);
  }
}
