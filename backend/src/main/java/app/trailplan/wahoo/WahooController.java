package app.trailplan.wahoo;

import app.trailplan.config.TrailplanProperties;
import app.trailplan.routes.RouteRepository;
import app.trailplan.routes.RouteService;
import app.trailplan.routes.StoredRoute;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WahooController {

  private static final Logger log = LoggerFactory.getLogger(WahooController.class);
  private static final String STATE_ATTRIBUTE = "wahoo-oauth-state";

  public record Status(boolean enabled, boolean connected) {}

  public record SyncResult(long wahooRouteId, Instant syncedAt) {}

  private final WahooClient wahoo;
  private final RouteService routes;
  private final RouteRepository repository;
  private final URI publicUrl;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public WahooController(
      WahooClient wahoo,
      RouteService routes,
      RouteRepository repository,
      TrailplanProperties props,
      Clock clock) {
    this.wahoo = wahoo;
    this.routes = routes;
    this.repository = repository;
    this.publicUrl = props.wahoo().publicUrl();
    this.clock = clock;
  }

  @GetMapping("/api/wahoo/status")
  public Status status() {
    return new Status(wahoo.enabled(), wahoo.enabled() && wahoo.connected());
  }

  /** Starts the OAuth flow; the browser navigates here directly. */
  @GetMapping("/api/wahoo/connect")
  public ResponseEntity<Void> connect(HttpSession session) {
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    String state = HexFormat.of().formatHex(bytes);
    session.setAttribute(STATE_ATTRIBUTE, state);
    return redirect(URI.create(wahoo.authorizationUrl(state)));
  }

  @GetMapping("/api/wahoo/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      HttpSession session) {
    Object expected = session.getAttribute(STATE_ATTRIBUTE);
    session.removeAttribute(STATE_ATTRIBUTE);
    if (error != null || code == null || state == null || !state.equals(expected)) {
      return redirect(publicUrl.resolve("/?wahoo=failed"));
    }
    try {
      wahoo.exchangeCode(code);
    } catch (WahooException e) {
      // The browser is mid-redirect here: send it back to the planner, not to a JSON error page.
      log.warn("Wahoo token exchange failed: {}", e.getMessage(), e.getCause());
      return redirect(publicUrl.resolve("/?wahoo=failed"));
    }
    return redirect(publicUrl.resolve("/?wahoo=connected"));
  }

  @DeleteMapping("/api/wahoo/connection")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disconnect() {
    wahoo.disconnect();
  }

  @PostMapping("/api/routes/{id}/wahoo")
  public SyncResult sync(@PathVariable long id) {
    StoredRoute route = routes.get(id);
    long wahooId;
    try {
      wahooId = wahoo.uploadRoute(route);
    } catch (WahooRouteGoneException e) {
      // Deleted on the Wahoo side: forget the link and create it again.
      repository.clearWahooLink(id);
      wahooId = wahoo.uploadRoute(routes.get(id));
    }
    Instant now = clock.instant();
    repository.markSyncedToWahoo(id, wahooId, now);
    return new SyncResult(wahooId, now);
  }

  private static ResponseEntity<Void> redirect(URI target) {
    return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
  }
}
