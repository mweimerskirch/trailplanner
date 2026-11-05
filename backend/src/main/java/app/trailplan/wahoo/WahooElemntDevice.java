package app.trailplan.wahoo;

import app.trailplan.devices.CloudSync;
import app.trailplan.devices.DeviceIntegration;
import app.trailplan.devices.TransferMethod;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Wahoo ELEMNT bike computers (BOLT, ROAM, ACE). */
@Component
@Order(10)
public class WahooElemntDevice implements DeviceIntegration {

  private final WahooCloudSync cloud;

  public WahooElemntDevice(WahooCloudSync cloud) {
    this.cloud = cloud;
  }

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
                "Switch the ELEMNT on and connect it with a USB data cable.",
                "Copy the GPX into the device's routes folder. Windows: it appears under “This"
                    + " PC”. macOS: use an MTP app such as OpenMTP. Linux: your file manager,"
                    + " or scripts/copy-to-bolt.sh.",
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
  public Optional<CloudSync> cloudSync() {
    return Optional.of(cloud);
  }
}
