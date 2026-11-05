package app.trailplan.wahoo;

import app.trailplan.config.TrailplanProperties;
import app.trailplan.devices.CloudRouteGoneException;
import app.trailplan.export.FileNames;
import app.trailplan.export.FitCourseWriter;
import app.trailplan.routes.StoredRoute;
import app.trailplan.routing.TrackPoint;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Wahoo Cloud API: OAuth (authorization code flow, confidential client) and route upload.
 *
 * <p>Routes are sent as FIT course files, base64-encoded in a data URI, with {@code external_id}
 * set to our route id so re-sending updates the same Wahoo route instead of creating duplicates.
 */
@Component
public class WahooClient {

  static final String SCOPES = "user_read routes_read routes_write";

  private final TrailplanProperties.Wahoo config;
  private final RestClient http;
  private final WahooTokenRepository tokens;
  private final FitCourseWriter fit;
  private final Clock clock;

  public WahooClient(
      TrailplanProperties props,
      RestClient.Builder builder,
      WahooTokenRepository tokens,
      FitCourseWriter fit,
      Clock clock) {
    this.config = props.wahoo();
    this.http = builder.clone().baseUrl(config.apiBase().toString()).build();
    this.tokens = tokens;
    this.fit = fit;
    this.clock = clock;
  }

  public boolean enabled() {
    return config.enabled() && !config.clientId().isBlank() && !config.clientSecret().isBlank();
  }

  public boolean connected() {
    return tokens.find().isPresent();
  }

  public String authorizationUrl(String state) {
    requireEnabled();
    return UriComponentsBuilder.fromUri(config.apiBase())
        .path("/oauth/authorize")
        .queryParam("client_id", config.clientId())
        .queryParam("redirect_uri", config.redirectUri().toString())
        .queryParam("scope", SCOPES)
        .queryParam("response_type", "code")
        .queryParam("state", state)
        .encode()
        .build()
        .toUriString();
  }

  public void exchangeCode(String code) {
    requireEnabled();
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", config.clientId());
    form.add("client_secret", config.clientSecret());
    form.add("grant_type", "authorization_code");
    form.add("redirect_uri", config.redirectUri().toString());
    form.add("code", code);
    tokens.save(requestToken(form));
  }

  public void disconnect() {
    tokens.delete();
  }

  /**
   * Creates the route in the rider's Wahoo account, or updates the Wahoo route {@code existing}.
   *
   * @return the Wahoo route id
   * @throws CloudRouteGoneException if {@code existing} no longer exists at Wahoo
   */
  public long uploadRoute(StoredRoute route, Optional<Long> existing) {
    requireEnabled();
    String token = validAccessToken();
    MultiValueMap<String, String> form = routeForm(route);
    try {
      JsonNode response;
      if (existing.isPresent()) {
        response =
            http.put()
                .uri("/v1/routes/{id}", existing.get())
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
      } else {
        form.add("route[external_id]", "trailplan-" + route.id());
        response =
            http.post()
                .uri("/v1/routes")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
      }
      if (response == null || !response.path("id").canConvertToLong()) {
        throw new WahooException(HttpStatus.BAD_GATEWAY, "Wahoo did not return a route id.");
      }
      return response.path("id").asLong();
    } catch (HttpClientErrorException.NotFound e) {
      throw new CloudRouteGoneException(e);
    } catch (HttpClientErrorException.Unauthorized e) {
      throw new WahooException(
          HttpStatus.CONFLICT, "Wahoo rejected the login. Disconnect and connect Wahoo again.", e);
    } catch (RestClientException e) {
      throw new WahooException(HttpStatus.BAD_GATEWAY, "Wahoo upload failed: " + e.getMessage(), e);
    }
  }

  private MultiValueMap<String, String> routeForm(StoredRoute route) {
    TrackPoint start = route.track().get(0);
    byte[] course =
        fit.write(route.name(), route.track(), route.ascent(), route.descent(), clock.instant());
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add(
        "route[file]",
        "data:application/vnd.fit;base64," + Base64.getEncoder().encodeToString(course));
    form.add("route[filename]", FileNames.forRoute(route.name(), "fit"));
    form.add("route[provider_updated_at]", route.updatedAt().toString());
    form.add("route[name]", route.name());
    form.add("route[start_lat]", decimal(start.lat()));
    form.add("route[start_lng]", decimal(start.lng()));
    form.add("route[distance]", decimal(route.distance()));
    form.add("route[ascent]", decimal(route.ascent()));
    form.add("route[descent]", decimal(route.descent()));
    return form;
  }

  private String validAccessToken() {
    WahooToken token =
        tokens
            .find()
            .orElseThrow(
                () -> new WahooException(HttpStatus.CONFLICT, "Connect your Wahoo account first."));
    if (!token.expiresWithin(Duration.ofMinutes(2), clock.instant())) {
      return token.accessToken();
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", config.clientId());
    form.add("client_secret", config.clientSecret());
    form.add("grant_type", "refresh_token");
    form.add("refresh_token", token.refreshToken());
    // Wahoo invalidates the old refresh token, so always store the new pair immediately.
    WahooToken refreshed = requestToken(form);
    tokens.save(refreshed);
    return refreshed.accessToken();
  }

  private WahooToken requestToken(MultiValueMap<String, String> form) {
    JsonNode body;
    try {
      body =
          http.post()
              .uri("/oauth/token")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(JsonNode.class);
    } catch (RestClientException e) {
      throw new WahooException(
          HttpStatus.BAD_GATEWAY, "Wahoo login failed. Try connecting again.", e);
    }
    if (body == null || !body.hasNonNull("access_token") || !body.hasNonNull("refresh_token")) {
      throw new WahooException(HttpStatus.BAD_GATEWAY, "Wahoo returned an incomplete token.");
    }
    Instant expiresAt = clock.instant().plusSeconds(body.path("expires_in").asLong(7200));
    return new WahooToken(
        body.get("access_token").asString(), body.get("refresh_token").asString(), expiresAt);
  }

  private void requireEnabled() {
    if (!enabled()) {
      throw new WahooException(
          HttpStatus.CONFLICT,
          "Wahoo sync is not set up. Add your Wahoo app credentials to the backend configuration.");
    }
  }

  private static String decimal(double value) {
    return String.format(Locale.ROOT, "%.6f", value);
  }
}
