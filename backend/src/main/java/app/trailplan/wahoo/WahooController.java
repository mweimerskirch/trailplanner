package app.trailplan.wahoo;

import app.trailplan.config.TrailplanProperties;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wahoo's OAuth flow. Everything else (status, sending, disconnecting) goes through the generic
 * device API. The callback path is registered at Wahoo as the redirect URI, so it must not change.
 */
@RestController
public class WahooController {

  private static final Logger log = LoggerFactory.getLogger(WahooController.class);
  private static final String STATE_ATTRIBUTE = "wahoo-oauth-state";

  private final WahooClient wahoo;
  private final URI publicUrl;
  private final SecureRandom random = new SecureRandom();

  public WahooController(WahooClient wahoo, TrailplanProperties props) {
    this.wahoo = wahoo;
    this.publicUrl = props.wahoo().publicUrl();
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

  private static ResponseEntity<Void> redirect(URI target) {
    return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
  }
}
