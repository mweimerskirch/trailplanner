package app.trailplan.devices;

import app.trailplan.users.CurrentUser;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

  /**
   * @param remove files to delete from the device, as listed in the plan's {@code remove}
   */
  public record SyncRequest(List<String> remove) {
    public SyncRequest {
      remove = remove == null ? List.of() : List.copyOf(remove);
    }
  }

  private final DeviceService devices;
  private final DeviceSyncService sync;

  public DeviceController(DeviceService devices, DeviceSyncService sync) {
    this.devices = devices;
    this.sync = sync;
  }

  /** All supported devices with their formats and instructions. */
  @GetMapping
  public List<DeviceService.DeviceInfo> list(CurrentUser user, Locale locale) {
    return devices.devices(locale);
  }

  /** What syncing the connected device would change; nothing is changed. */
  @GetMapping("/{deviceId}/sync")
  public DeviceSyncService.SyncPlan syncPlan(CurrentUser user, @PathVariable String deviceId) {
    return sync.plan(user, deviceId);
  }

  /** Copies the rider's active routes to the connected device; deletes only confirmed files. */
  @PostMapping("/{deviceId}/sync")
  public DeviceSyncService.SyncResult sync(
      CurrentUser user, @PathVariable String deviceId, @RequestBody SyncRequest request) {
    return sync.sync(user, deviceId, request.remove());
  }
}
