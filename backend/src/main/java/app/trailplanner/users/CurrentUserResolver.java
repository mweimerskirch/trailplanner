package app.trailplanner.users;

import app.trailplanner.web.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Supplies {@link CurrentUser} controller parameters from the rider cookie. */
@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

  /**
   * The rider the page believes is chosen. Optional; if sent and different from the cookie (the
   * rider was switched in another tab), the request is refused instead of landing in the wrong
   * library.
   */
  public static final String RIDER_HEADER = "X-TrailPlanner-Rider";

  private final UserRepository users;

  public CurrentUserResolver(UserRepository users) {
    this.users = users;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.getParameterType() == CurrentUser.class;
  }

  @Override
  public CurrentUser resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
    if (request == null) {
      return null;
    }
    CurrentUser user = find(request).orElseThrow(NoUserSelectedException::new);
    String expected = request.getHeader(RIDER_HEADER);
    if (expected != null && !expected.equals(Long.toString(user.id()))) {
      // The browser switched rider in another tab; this tab still shows the previous one.
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Rider changed",
          "RIDER_CHANGED",
          "Another rider was chosen in another tab. Reload this page to continue as "
              + user.name()
              + ".",
          null);
    }
    return user;
  }

  /** The chosen rider, if the cookie names one that exists. */
  public Optional<CurrentUser> find(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (CurrentUser.COOKIE.equals(cookie.getName())) {
        try {
          return users.findById(Long.parseLong(cookie.getValue()));
        } catch (NumberFormatException e) {
          return Optional.empty();
        }
      }
    }
    return Optional.empty();
  }
}
