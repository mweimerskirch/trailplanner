package app.trailplanner.devices;

import java.util.List;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Fallback for any device or app that can open GPX or FIT files. Always listed last. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GenericDevice implements DeviceIntegration {

  /** Also what a rider without a device gets (files only). */
  public static final String ID = "generic";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String name() {
    return "No device: GPX and FIT files";
  }

  @Override
  public List<String> exportFormats() {
    return List.of("gpx", "fit");
  }

  @Override
  public List<TransferMethod> transferMethods() {
    return List.of(
        new TransferMethod(
            "Use the file",
            List.of(
                "GPX works with almost every app and device; Garmin and Wahoo devices also take"
                    + " FIT course files.",
                "Copy the file to the device over USB, or import it in the device's app.")));
  }

  @Override
  public Optional<String> importInstructions() {
    return Optional.of("Export routes as GPX or FIT from the device or its app, then import them.");
  }
}
