package app.trailplan.devices;

import app.trailplan.routes.StoredRoute;
import java.util.Optional;

/**
 * Uploads routes to a device maker's cloud account, from where the device syncs them. Which remote
 * route belongs to which saved route is stored by the core ({@link CloudLinkRepository}), so a
 * cloud integration does not need its own database columns.
 */
public interface CloudSync {

  /** Stable key for stored links, e.g. {@code wahoo}. Never change it once released. */
  String service();

  /** Shown to the rider, e.g. {@code Wahoo}. */
  String serviceName();

  /** False when the integration is not configured (e.g. no API credentials). */
  boolean enabled();

  /** Whether an account is connected. */
  boolean connected();

  /** Where the browser goes to connect an account (usually the start of an OAuth flow). */
  String connectPath();

  void disconnect();

  /**
   * Creates the route in the cloud account, or updates it if {@code remoteId} is present.
   *
   * @return the remote route id
   * @throws CloudRouteGoneException if the remote route no longer exists
   */
  String upload(StoredRoute route, Optional<String> remoteId);
}
