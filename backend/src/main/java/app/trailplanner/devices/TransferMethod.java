package app.trailplanner.devices;

import java.util.List;

/** One way to get a route file onto a device, e.g. "Copy over USB", as steps for the rider. */
public record TransferMethod(String title, List<String> steps) {

  public TransferMethod {
    steps = List.copyOf(steps);
  }
}
