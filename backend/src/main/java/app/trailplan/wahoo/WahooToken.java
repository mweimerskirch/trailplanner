package app.trailplan.wahoo;

import java.time.Duration;
import java.time.Instant;

public record WahooToken(String accessToken, String refreshToken, Instant expiresAt) {

  public boolean expiresWithin(Duration margin, Instant now) {
    return expiresAt.minus(margin).isBefore(now);
  }
}
