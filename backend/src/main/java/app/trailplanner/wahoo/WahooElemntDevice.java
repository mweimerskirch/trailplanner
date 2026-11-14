package app.trailplanner.wahoo;

import app.trailplanner.devices.DeviceIntegration;
import app.trailplanner.devices.TransferMethod;
import app.trailplanner.devices.UsbSync;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Wahoo ELEMNT bike computers (BOLT, ROAM, ACE). */
@Component
@Order(10)
public class WahooElemntDevice implements DeviceIntegration {

  @Override
  public String id() {
    return "wahoo-elemnt";
  }

  @Override
  public String name() {
    return "Wahoo ELEMNT (BOLT, ROAM, ACE)";
  }

  @Override
  public List<String> exportFormats() {
    return List.of("gpx", "fit");
  }

  @Override
  public List<TransferMethod> transferMethods() {
    return List.of(
        new TransferMethod(
            "Copy over USB",
            List.of(
                "Switch the ELEMNT on and connect it with a USB data cable to the computer that"
                    + " runs Trail Planner, then choose “Sync to ELEMNT” in the library: it copies all"
                    + " your active routes.",
                "Or copy this GPX into the device's routes folder yourself. Windows: it appears"
                    + " under “This PC”. macOS: use an MTP app such as OpenMTP. Linux: your file"
                    + " manager, or scripts/copy-to-bolt.sh.",
                "On the ELEMNT, open the map page, choose ROUTE, and press SYNC if the route"
                    + " isn't listed.")),
        new TransferMethod(
            "Import with the ELEMNT app",
            List.of(
                "On your phone, open the GPX (or use “Share”) and choose the ELEMNT" + " app.",
                "The route syncs to the ELEMNT over Bluetooth or Wi‑Fi.")));
  }

  @Override
  public Optional<String> importInstructions() {
    return Optional.of(
        "Connect the ELEMNT over USB, copy the files from its routes folder to your computer,"
            + " then import them here.");
  }

  @Override
  public Optional<UsbSync> usbSync() {
    return Optional.of(
        new UsbSync(
            "ELEMNT",
            "Wahoo",
            "routes",
            "gpx",
            "On the ELEMNT, open the map page and choose ROUTE. Press SYNC if a route is missing.",
            true, // the ELEMNT drops accented letters and shows XML escapes literally
            // Tested on a BOLT V2: it keeps routes it has loaded when their file is deleted.
            "The ELEMNT keeps routes it has already loaded: delete them on the device too.",
            "exports")); // recorded rides as FIT files
  }
}
