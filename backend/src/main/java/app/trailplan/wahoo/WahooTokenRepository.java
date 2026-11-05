package app.trailplan.wahoo;

import app.trailplan.devices.CloudAccountRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** A rider's Wahoo tokens, stored as the {@code wahoo} cloud account. */
@Repository
public class WahooTokenRepository {

  static final String SERVICE = "wahoo";

  private final CloudAccountRepository accounts;

  public WahooTokenRepository(CloudAccountRepository accounts) {
    this.accounts = accounts;
  }

  public Optional<WahooToken> find(long userId) {
    return accounts
        .find(userId, SERVICE)
        .map(t -> new WahooToken(t.accessToken(), t.refreshToken(), t.expiresAt()));
  }

  public void save(long userId, WahooToken token) {
    accounts.save(
        userId,
        SERVICE,
        new CloudAccountRepository.Tokens(
            token.accessToken(), token.refreshToken(), token.expiresAt()));
  }

  public void delete(long userId) {
    accounts.delete(userId, SERVICE);
  }
}
