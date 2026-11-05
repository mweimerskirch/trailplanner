package app.trailplan.devices;

import java.util.List;
import java.util.Optional;

/**
 * A bike computer, watch or app that routes are sent to. Implementations are Spring beans and are
 * picked up automatically; the UI builds its export options from them, so supporting another device
 * means adding one class (plus a {@link CloudSync} if the maker offers a cloud API).
 *
 * <p>Annotate implementations with {@code @Order} to control their position in the device list.
 */
public interface DeviceIntegration {

  /** Stable id, used in URLs and remembered by the browser. */
  String id();

  /** Shown to the rider, e.g. {@code Wahoo ELEMNT (BOLT, ROAM)}. */
  String name();

  /** Ids of the export formats offered for this device, the preferred one first. */
  List<String> exportFormats();

  /** Ways to get a downloaded file onto the device, shown as step-by-step instructions. */
  List<TransferMethod> transferMethods();

  /** How to bring routes from the device into Trailplan with the file import, if possible. */
  Optional<String> importInstructions();

  /** Direct upload to the maker's cloud account, if supported. */
  default Optional<CloudSync> cloudSync() {
    return Optional.empty();
  }
}
