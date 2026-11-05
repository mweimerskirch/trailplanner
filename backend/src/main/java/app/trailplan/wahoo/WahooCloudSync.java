package app.trailplan.wahoo;

import app.trailplan.devices.CloudSync;
import app.trailplan.routes.StoredRoute;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Wahoo Cloud API: routes are uploaded as FIT courses and reach the device on its next sync. */
@Component
public class WahooCloudSync implements CloudSync {

  private final WahooClient wahoo;

  public WahooCloudSync(WahooClient wahoo) {
    this.wahoo = wahoo;
  }

  @Override
  public String service() {
    return "wahoo";
  }

  @Override
  public String serviceName() {
    return "Wahoo";
  }

  @Override
  public boolean enabled() {
    return wahoo.enabled();
  }

  @Override
  public boolean connected() {
    return wahoo.connected();
  }

  @Override
  public String connectPath() {
    return "/api/wahoo/connect";
  }

  @Override
  public void disconnect() {
    wahoo.disconnect();
  }

  @Override
  public String upload(StoredRoute route, Optional<String> remoteId) {
    return Long.toString(wahoo.uploadRoute(route, remoteId.map(Long::valueOf)));
  }
}
