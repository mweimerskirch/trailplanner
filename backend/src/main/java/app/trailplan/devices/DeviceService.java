package app.trailplan.devices;

import app.trailplan.export.RouteFormat;
import app.trailplan.export.RouteFormats;
import app.trailplan.routes.RouteService;
import app.trailplan.routes.StoredRoute;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** The registered {@link DeviceIntegration}s, and sending routes to their clouds. */
@Service
public class DeviceService {

  public record FormatInfo(String id, String label, String extension) {}

  public record CloudInfo(
      String serviceName, boolean enabled, boolean connected, String connectPath) {}

  /** A device as the UI sees it. {@code importInstructions} and {@code cloud} may be null. */
  public record DeviceInfo(
      String id,
      String name,
      List<FormatInfo> formats,
      List<TransferMethod> transferMethods,
      String importInstructions,
      CloudInfo cloud) {}

  public record SyncResult(String service, String remoteId, Instant syncedAt) {}

  private final Map<String, DeviceIntegration> devices = new LinkedHashMap<>();
  private final RouteFormats formats;
  private final RouteService routes;
  private final CloudLinkRepository links;
  private final Clock clock;

  /** Spring passes the integrations sorted by their {@code @Order}. */
  public DeviceService(
      List<DeviceIntegration> integrations,
      RouteFormats formats,
      RouteService routes,
      CloudLinkRepository links,
      Clock clock) {
    for (DeviceIntegration device : integrations) {
      if (devices.put(device.id(), device) != null) {
        throw new IllegalStateException("Two devices share the id " + device.id());
      }
      device.exportFormats().forEach(formats::get); // fail at startup on unknown format ids
    }
    this.formats = formats;
    this.routes = routes;
    this.links = links;
    this.clock = clock;
  }

  public List<DeviceInfo> devices() {
    return devices.values().stream().map(this::info).toList();
  }

  public SyncResult send(String deviceId, long routeId) {
    CloudSync cloud = cloud(deviceId);
    if (!cloud.enabled()) {
      throw new DeviceException(
          HttpStatus.CONFLICT,
          cloud.serviceName()
              + " sync is not set up. Add the API credentials to the configuration.");
    }
    if (!cloud.connected()) {
      throw new DeviceException(
          HttpStatus.CONFLICT, "Connect your " + cloud.serviceName() + " account first.");
    }
    StoredRoute route = routes.get(routeId);
    Optional<String> existing = links.findRemoteId(routeId, cloud.service());
    String remoteId;
    try {
      remoteId = cloud.upload(route, existing);
    } catch (CloudRouteGoneException e) {
      // Deleted on the cloud side: forget the link and create the route again.
      links.delete(routeId, cloud.service());
      remoteId = cloud.upload(route, Optional.empty());
    }
    Instant now = clock.instant();
    links.save(routeId, cloud.service(), remoteId, now);
    return new SyncResult(cloud.service(), remoteId, now);
  }

  public void disconnect(String deviceId) {
    cloud(deviceId).disconnect();
  }

  private CloudSync cloud(String deviceId) {
    DeviceIntegration device = devices.get(deviceId);
    if (device == null) {
      throw new DeviceException(HttpStatus.NOT_FOUND, "Unknown device: " + deviceId);
    }
    return device
        .cloudSync()
        .orElseThrow(
            () -> new DeviceException(HttpStatus.NOT_FOUND, device.name() + " has no cloud sync."));
  }

  private DeviceInfo info(DeviceIntegration device) {
    List<FormatInfo> formatInfos =
        device.exportFormats().stream().map(formats::get).map(DeviceService::formatInfo).toList();
    CloudInfo cloud =
        device
            .cloudSync()
            .map(c ->
                new CloudInfo(
                    c.serviceName(), c.enabled(), c.enabled() && c.connected(), c.connectPath()))
            .orElse(null);
    return new DeviceInfo(
        device.id(),
        device.name(),
        formatInfos,
        device.transferMethods(),
        device.importInstructions().orElse(null),
        cloud);
  }

  private static FormatInfo formatInfo(RouteFormat format) {
    return new FormatInfo(format.id(), format.label(), format.extension());
  }
}
