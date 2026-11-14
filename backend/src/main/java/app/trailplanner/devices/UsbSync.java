package app.trailplanner.devices;

/**
 * How a device takes routes over USB (and gives recorded rides): Trail Planner makes {@code folder}
 * on the device hold exactly the rider's active routes, as files in {@code format}.
 *
 * @param label short device name for buttons, e.g. {@code ELEMNT}
 * @param match text that the device's manufacturer or model contains, e.g. {@code Wahoo}
 * @param folder folder at the top of the device's storage, e.g. {@code routes}
 * @param format export format id, e.g. {@code gpx}
 * @param afterSync what the rider does on the device afterwards, or null
 * @param plainNames whether route names must be plain ASCII on the device (see {@link
 *     app.trailplanner.export.PlainNames})
 * @param removalNote what deleting a file does not do on the device, or null if it removes the
 *     route there too
 * @param ridesFolder folder at the top of the device's storage with recorded rides (FIT files), or
 *     null if rides cannot be imported from the device
 */
public record UsbSync(
    String label,
    String match,
    String folder,
    String format,
    String afterSync,
    boolean plainNames,
    String removalNote,
    String ridesFolder) {}
