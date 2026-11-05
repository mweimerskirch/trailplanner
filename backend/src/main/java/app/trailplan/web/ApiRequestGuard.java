package app.trailplan.web;

import app.trailplan.config.TrailplanProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects the API of a server without logins against other websites in the rider's browser:
 *
 * <ul>
 *   <li><b>DNS rebinding</b>: a page on another domain can make its domain point to this server.
 *       Requests then carry that domain as Host, so only IP addresses, {@code localhost}, the
 *       public URL's host and {@code trailplan.allowed-hosts} are accepted.
 *   <li><b>Cross-site requests</b>: state-changing requests must carry {@value #CLIENT_HEADER}. A
 *       page on another origin cannot add custom headers without a CORS preflight, which fails.
 * </ul>
 */
@Component
public class ApiRequestGuard extends OncePerRequestFilter {

  public static final String CLIENT_HEADER = "X-Trailplan-Client";

  private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  private final Set<String> allowedHosts = new HashSet<>();

  public ApiRequestGuard(TrailplanProperties props) {
    allowedHosts.add("localhost");
    String publicHost = props.wahoo().publicUrl().getHost();
    if (publicHost != null) {
      allowedHosts.add(publicHost.toLowerCase(Locale.ROOT));
    }
    for (String host : props.allowedHosts()) {
      if (!host.isBlank()) {
        allowedHosts.add(host.strip().toLowerCase(Locale.ROOT));
      }
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String host = hostName(request.getHeader("Host"));
    if (!hostAllowed(host)) {
      reject(
          response,
          HttpStatus.FORBIDDEN,
          "UNKNOWN_HOST",
          "Trailplan is not set up for the host name \""
              + host
              + "\". Add it to TRAILPLAN_ALLOWED_HOSTS.");
      return;
    }
    if (!SAFE_METHODS.contains(request.getMethod()) && request.getHeader(CLIENT_HEADER) == null) {
      reject(
          response,
          HttpStatus.FORBIDDEN,
          "MISSING_CLIENT_HEADER",
          "Changes are only accepted from the Trailplan app.");
      return;
    }
    chain.doFilter(request, response);
  }

  boolean hostAllowed(String host) {
    return host.isEmpty()
        || allowedHosts.contains(host)
        || IPV4.matcher(host).matches()
        || host.startsWith("["); // IPv6 literal
  }

  /** The host part of a Host header, lower-case, without the port. */
  static String hostName(String header) {
    if (header == null) {
      return "";
    }
    String h = header.strip().toLowerCase(Locale.ROOT);
    if (h.startsWith("[")) {
      int end = h.indexOf(']');
      return end > 0 ? h.substring(0, end + 1) : h;
    }
    int colon = h.indexOf(':');
    return colon >= 0 ? h.substring(0, colon) : h;
  }

  private static void reject(
      HttpServletResponse response, HttpStatus status, String reason, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write(
            "{\"status\":%d,\"title\":\"Request refused\",\"reason\":\"%s\",\"detail\":\"%s\"}"
                .formatted(status.value(), reason, message.replace("\"", "\\\"")));
  }
}
