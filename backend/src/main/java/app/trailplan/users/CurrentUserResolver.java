package app.trailplan.users;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Supplies {@link CurrentUser} controller parameters from the rider cookie. */
@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

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
    return request == null ? null : find(request).orElseThrow(NoUserSelectedException::new);
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
