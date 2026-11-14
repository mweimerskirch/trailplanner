package app.trailplanner.devices;

import app.trailplanner.export.RouteFormat;
import app.trailplanner.export.RouteFormats;
import app.trailplanner.web.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The registered {@link DeviceIntegration}s. Their texts are English; translations come from the
 * {@code messages_<language>.properties} under {@code device.<id>.…} and {@code format.<id>.label}
 * (see messages_fr.properties), falling back to the integration's own text.
 */
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
  private final MessageSource messages;

  /** Spring passes the integrations sorted by their {@code @Order}. */
  public DeviceService(
      List<DeviceIntegration> integrations, RouteFormats formats, MessageSource messages) {
    for (DeviceIntegration device : integrations) {
      if (devices.put(device.id(), device) != null) {
        throw new IllegalStateException("Two devices share the id " + device.id());
      }
      device.exportFormats().forEach(formats::get); // fail at startup on unknown format ids
      device.usbSync().ifPresent(sync -> formats.get(sync.format()));
    }
    this.formats = formats;
    this.messages = messages;
  }

  /** The devices with their texts in the language (English where there is no translation). */
  public List<DeviceInfo> devices(Locale locale) {
    return devices.values().stream().map(device -> info(device, locale)).toList();
  }

  /** The device's USB sync; 404 if the device is unknown or cannot sync over USB. */
  /** Whether a device with this id is registered. */
  public boolean exists(String deviceId) {
    return devices.containsKey(deviceId);
  }

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

  private DeviceInfo info(DeviceIntegration device, Locale locale) {
    String prefix = "device." + device.id() + ".";
    List<FormatInfo> formatInfos =
        device.exportFormats().stream()
            .map(formats::get)
            .map(format -> formatInfo(format, locale))
            .toList();
    List<TransferMethod> methods = new ArrayList<>();
    for (int i = 0; i < device.transferMethods().size(); i++) {
      TransferMethod method = device.transferMethods().get(i);
      String key = prefix + "transfer." + i + ".";
      List<String> steps = new ArrayList<>();
      for (int j = 0; j < method.steps().size(); j++) {
        steps.add(text(key + "step." + j, method.steps().get(j), locale));
      }
      methods.add(new TransferMethod(text(key + "title", method.title(), locale), steps));
    }
    return new DeviceInfo(
        device.id(),
        text(prefix + "name", device.name(), locale),
        formatInfos,
        methods,
        device.importInstructions().map(t -> text(prefix + "import", t, locale)).orElse(null),
        device
            .usbSync()
            .map(s ->
                new UsbSyncInfo(
                    s.label(),
                    s.folder(),
                    text(prefix + "sync.afterSync", s.afterSync(), locale),
                    s.removalNote() == null
                        ? null
                        : text(prefix + "sync.removalNote", s.removalNote(), locale),
                    s.ridesFolder() != null))
            .orElse(null));
  }

  private String text(String key, String english, Locale locale) {
    return messages.getMessage(key, null, english, locale);
  }

  private FormatInfo formatInfo(RouteFormat format, Locale locale) {
    return new FormatInfo(
        format.id(),
        text("format." + format.id() + ".label", format.label(), locale),
        format.extension());
  }
}
