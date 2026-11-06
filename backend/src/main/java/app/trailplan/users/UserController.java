package app.trailplan.users;

import app.trailplan.presets.PresetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Riders (no passwords) and which one is chosen in this browser. */
@RestController
@RequestMapping("/api")
public class UserController {

  public record NameRequest(@NotBlank @Size(max = 60) String name) {}

  public record ChooseRequest(@NotNull Long userId) {}

  /** {@code user} is null when no rider is chosen. */
  public record Session(CurrentUser user) {}

  /** Session attributes that belong to the person at the keyboard, cleared when switching. */
  private static final List<String> PERSONAL_SESSION_ATTRIBUTES = List.of("komoot-session");

  private final UserRepository users;
  private final CurrentUserResolver currentUser;
  private final Clock clock;
  private final PresetService presets;

  public UserController(
      UserRepository users, CurrentUserResolver currentUser, Clock clock, PresetService presets) {
    this.users = users;
    this.currentUser = currentUser;
    this.clock = clock;
    this.presets = presets;
  }

  @GetMapping("/users")
  public List<UserRepository.UserSummary> list() {
    return users.findAll();
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  public CurrentUser create(@Valid @RequestBody NameRequest request) {
    long id = users.insert(request.name().strip(), clock.instant());
    presets.seed(id); // new riders start with the built-in riding-style presets
    return users.findById(id).orElseThrow();
  }

  @PatchMapping("/users/{id}")
  public CurrentUser rename(@PathVariable long id, @Valid @RequestBody NameRequest request) {
    if (!users.rename(id, request.name().strip())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rider " + id + " does not exist.");
    }
    return users.findById(id).orElseThrow();
  }

  /** Deletes the rider with all their routes. */
  @DeleteMapping("/users/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable long id) {
    if (!users.delete(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rider " + id + " does not exist.");
    }
  }

  @GetMapping("/session")
  public Session session(HttpServletRequest request) {
    return new Session(currentUser.find(request).orElse(null));
  }

  /** Chooses the rider for this browser. Komoot logins of the previous rider are forgotten. */
  @PutMapping("/session")
  public Session choose(
      @Valid @RequestBody ChooseRequest request,
      HttpSession session,
      HttpServletResponse response) {
    CurrentUser user =
        users
            .findById(request.userId())
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Rider " + request.userId() + " does not exist."));
    PERSONAL_SESSION_ATTRIBUTES.forEach(session::removeAttribute);
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookie(Long.toString(user.id()), Duration.ofDays(365)).toString());
    return new Session(user);
  }

  @DeleteMapping("/session")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void leave(HttpSession session, HttpServletResponse response) {
    PERSONAL_SESSION_ATTRIBUTES.forEach(session::removeAttribute);
    response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
  }

  private static ResponseCookie cookie(String value, Duration maxAge) {
    return ResponseCookie.from(CurrentUser.COOKIE, value)
        .path("/")
        .httpOnly(true)
        .sameSite("Lax")
        .maxAge(maxAge)
        .build();
  }
}
