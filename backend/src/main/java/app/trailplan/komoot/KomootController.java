package app.trailplan.komoot;

import app.trailplan.imports.ImportResult;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Komoot import. The Komoot login lives in the HTTP session only: the password is used once to sign
 * in and is never stored; the token Komoot returns is forgotten when the session ends.
 */
@RestController
@RequestMapping("/api/komoot")
public class KomootController {

  private static final String SESSION_ATTRIBUTE = "komoot-session";

  public record Status(boolean connected, String displayName) {}

  public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

  public record ImportRequest(@NotEmpty @Size(max = 500) List<Long> tourIds) {}

  private final KomootClient komoot;
  private final KomootImportService imports;

  public KomootController(KomootClient komoot, KomootImportService imports) {
    this.komoot = komoot;
    this.imports = imports;
  }

  @GetMapping("/status")
  public Status status(HttpSession session) {
    KomootClient.Session login = (KomootClient.Session) session.getAttribute(SESSION_ATTRIBUTE);
    return login == null ? new Status(false, null) : new Status(true, login.displayName());
  }

  @PostMapping("/login")
  public Status login(@Valid @RequestBody LoginRequest request, HttpSession session) {
    KomootClient.Session login = komoot.login(request.email().strip(), request.password());
    session.setAttribute(SESSION_ATTRIBUTE, login);
    return new Status(true, login.displayName());
  }

  @DeleteMapping("/login")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpSession session) {
    session.removeAttribute(SESSION_ATTRIBUTE);
  }

  @GetMapping("/tours")
  public List<KomootImportService.TourEntry> tours(HttpSession session) {
    return imports.tours(requireLogin(session));
  }

  @PostMapping("/import")
  public List<ImportResult> importTours(
      @Valid @RequestBody ImportRequest request, HttpSession session) {
    return imports.importTours(requireLogin(session), request.tourIds());
  }

  private static KomootClient.Session requireLogin(HttpSession session) {
    Object login = session.getAttribute(SESSION_ATTRIBUTE);
    if (login instanceof KomootClient.Session s) {
      return s;
    }
    throw new KomootException(HttpStatus.UNAUTHORIZED, "Sign in to Komoot first.");
  }
}
