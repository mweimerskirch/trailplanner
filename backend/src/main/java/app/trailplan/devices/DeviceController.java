package app.trailplan.devices;

import app.trailplan.users.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

  private final DeviceService devices;

  public DeviceController(DeviceService devices) {
    this.devices = devices;
  }

  /** All supported devices with their formats, instructions and cloud status. */
  @GetMapping
  public List<DeviceService.DeviceInfo> list(CurrentUser user) {
    return devices.devices(user);
  }

  /** Sends a saved route to the device maker's cloud account. */
  @PostMapping("/{deviceId}/cloud/routes/{routeId}")
  public DeviceService.SyncResult send(
      CurrentUser user, @PathVariable String deviceId, @PathVariable long routeId) {
    return devices.send(user, deviceId, routeId);
  }

  @DeleteMapping("/{deviceId}/cloud/connection")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disconnect(CurrentUser user, @PathVariable String deviceId) {
    devices.disconnect(user, deviceId);
  }
}
