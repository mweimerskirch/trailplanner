package app.trailplanner.devices;

import java.util.List;
import java.util.Map;

/** Files in one folder of a USB device (MTP). The real one is {@link LibmtpHelper}. */
public interface MtpDevice {

  /**
   * @param device the device's model as it reports it, e.g. {@code ELEMNT-BOLT2}
   * @param files the folder's files by name
   */
  record Listing(String device, Map<String, byte[]> files) {
    public Listing {
      files = Map.copyOf(files);
    }
  }

  /**
   * Reads the files in the target's folder, on the first connected device whose manufacturer or
   * model contains the target's {@code match}.
   *
   * @throws UsbException if there is no such device, it is busy, or it has no such folder
   */
  Listing list(UsbSync target);

  /**
   * Writes {@code uploads} into the target's folder (replacing files of the same name), then
   * deletes the files named in {@code deletions}.
   *
   * @return the device's model
   * @throws UsbException as for {@link #list}
   */
  String apply(UsbSync target, Map<String, byte[]> uploads, List<String> deletions);

  /** A file on the device: its name and size in bytes. */
  record DeviceFile(String name, long size) {}

  /**
   * @param device the device's model
   * @param files the folder's files, not downloaded
   */
  record FileList(String device, List<DeviceFile> files) {
    public FileList {
      files = List.copyOf(files);
    }
  }

  /**
   * Lists the files in {@code folder} (at the top of the device's storage) without reading them.
   *
   * @throws UsbException as for {@link #list}
   */
  FileList files(UsbSync target, String folder);

  /**
   * Reads the named files from {@code folder}. Names that are not there are left out.
   *
   * @throws UsbException as for {@link #list}
   */
  Map<String, byte[]> fetch(UsbSync target, String folder, List<String> names);
}
