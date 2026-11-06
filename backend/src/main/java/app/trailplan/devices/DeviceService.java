package app.trailplan.devices;

import app.trailplan.export.RouteFormat;
import app.trailplan.export.RouteFormats;
import app.trailplan.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** The registered {@link DeviceIntegration}s. */
@Service
public class DeviceService {

  public record FormatInfo(String id, String label, String extension) {}

  /** Syncing over USB as the UI sees it; see {@link UsbSync}. */
  public record UsbSyncInfo(
      String label, String folder, String afterSync, String removalNote, boolean importsRides) {}

  /** A device as the UI sees it. {@code importInstructions} and {@code usbSync} may be null. */
  public record DeviceInfo(
      String id,
      String name,
      List<FormatInfo> formats,
      List<TransferMethod> transferMethods,
      String importInstructions,
      UsbSyncInfo usbSync) {}

  private final Map<String, DeviceIntegration> devices = new LinkedHashMap<>();
  private final RouteFormats formats;

  /** Spring passes the integrations sorted by their {@code @Order}. */
  public DeviceService(List<DeviceIntegration> integrations, RouteFormats formats) {
    for (DeviceIntegration device : integrations) {
      if (devices.put(device.id(), device) != null) {
        throw new IllegalStateException("Two devices share the id " + device.id());
      }
      device.exportFormats().forEach(formats::get); // fail at startup on unknown format ids
      device.usbSync().ifPresent(sync -> formats.get(sync.format()));
    }
    this.formats = formats;
  }

  public List<DeviceInfo> devices() {
    return devices.values().stream().map(this::info).toList();
  }

  /** The device's USB sync; 404 if the device is unknown or cannot sync over USB. */
  public UsbSync usbSync(String deviceId) {
    DeviceIntegration device = devices.get(deviceId);
    if (device == null) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Unknown device", "Unknown device: " + deviceId);
    }
    return device
        .usbSync()
        .orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND, "No USB sync", device.name() + " cannot sync over USB."));
  }

  private DeviceInfo info(DeviceIntegration device) {
    List<FormatInfo> formatInfos =
        device.exportFormats().stream().map(formats::get).map(DeviceService::formatInfo).toList();
    return new DeviceInfo(
        device.id(),
        device.name(),
        formatInfos,
        device.transferMethods(),
        device.importInstructions().orElse(null),
        device
            .usbSync()
            .map(s ->
                new UsbSyncInfo(
                    s.label(), s.folder(), s.afterSync(), s.removalNote(), s.ridesFolder() != null))
            .orElse(null));
  }

  private static FormatInfo formatInfo(RouteFormat format) {
    return new FormatInfo(format.id(), format.label(), format.extension());
  }
}
