package app.trailplan.devices;

import app.trailplan.web.ApiException;
import org.springframework.http.HttpStatus;

/** Syncing over USB failed. The message is safe to show to the rider. */
public class UsbException extends ApiException {

  private static final long serialVersionUID = 1L;

  private UsbException(HttpStatus status, String reason, String message, Throwable cause) {
    super(status, "Sync failed", reason, message, cause);
  }

  public static UsbException noDevice(String label) {
    return new UsbException(
        HttpStatus.CONFLICT,
        "NO_DEVICE",
        "No " + label + " found. Switch it on and connect it with a USB data cable.",
        null);
  }

  public static UsbException busy(String label) {
    return new UsbException(
        HttpStatus.CONFLICT,
        "DEVICE_BUSY",
        "The "
            + label
            + " is connected but could not be opened. If it is open in a file manager, eject it"
            + " there and try again.",
        null);
  }

  public static UsbException noFolder(String label, String folder) {
    return new UsbException(
        HttpStatus.CONFLICT,
        "NO_FOLDER",
        "The " + label + " has no “" + folder + "” folder.",
        null);
  }

  public static UsbException unavailable(Throwable cause) {
    return new UsbException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "USB_UNAVAILABLE",
        "USB sync is not available on this server (the trailplan-mtp helper is missing).",
        cause);
  }

  public static UsbException failed(String label, Throwable cause) {
    return new UsbException(
        HttpStatus.BAD_GATEWAY,
        "USB_FAILED",
        "Talking to the " + label + " failed. Reconnect it and try again.",
        cause);
  }

  public static UsbException running() {
    return new UsbException(
        HttpStatus.CONFLICT,
        "SYNC_RUNNING",
        "The device is busy with another sync or import. Try again shortly.",
        null);
  }
}
