package app.trailplanner.devices;

import app.trailplanner.export.ExportedRoute;
import app.trailplanner.export.FileNames;
import app.trailplanner.export.PlainNames;
import app.trailplanner.export.RouteFormat;
import app.trailplanner.export.RouteFormats;
import app.trailplanner.routes.RouteService;
import app.trailplanner.routes.RouteSummary;
import app.trailplanner.users.CurrentUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Makes a device's route folder hold exactly the rider's active routes (not archived, not in the
 * trash). Files are compared by content, so unchanged routes are not copied again. Files on the
 * device that are not active routes are only deleted when the rider confirms them by name.
 */
@Service
public class DeviceSyncService {

  /** A route file on the device: its name there and the route it is (null if none). */
  public record SyncFile(String fileName, String routeName) {}

  /**
   * What a sync would change.
   *
   * @param device the connected device's model
   * @param remove files on the device that are not active routes; deleted only if confirmed
   */
  public record SyncPlan(
      String device,
      List<SyncFile> add,
      List<SyncFile> update,
      List<SyncFile> remove,
      int unchanged) {}

  public record SyncResult(String device, int added, int updated, int removed, int unchanged) {}

  private final DeviceService devices;
  private final RouteService routes;
  private final RouteFormats formats;
  private final MtpDevice mtp;
  private final DeviceLock lock;

  public DeviceSyncService(
      DeviceService devices,
      RouteService routes,
      RouteFormats formats,
      MtpDevice mtp,
      DeviceLock lock) {
    this.devices = devices;
    this.routes = routes;
    this.formats = formats;
    this.mtp = mtp;
    this.lock = lock;
  }

  public SyncPlan plan(CurrentUser user, String deviceId) {
    UsbSync target = devices.usbSync(deviceId);
    return lock.run(() -> compare(wanted(user, target), mtp.list(target)).plan());
  }

  /**
   * Copies new and changed routes, then deletes the files in {@code remove} that are still not
   * active routes. The comparison is made again, so a stale plan cannot delete a route file.
   */
  public SyncResult sync(CurrentUser user, String deviceId, Collection<String> remove) {
    UsbSync target = devices.usbSync(deviceId);
    return lock.run(() -> {
      Comparison c = compare(wanted(user, target), mtp.list(target));
      Set<String> confirmed = new HashSet<>(remove);
      List<String> deletions =
          c.plan().remove().stream().map(SyncFile::fileName).filter(confirmed::contains).toList();
      Map<String, byte[]> uploads = new LinkedHashMap<>();
      for (SyncFile f : c.plan().add()) uploads.put(f.fileName(), c.wanted().get(f.fileName()));
      for (SyncFile f : c.plan().update()) {
        uploads.put(f.fileName(), c.wanted().get(f.fileName()));
      }
      String device =
          uploads.isEmpty() && deletions.isEmpty()
              ? c.plan().device()
              : mtp.apply(target, uploads, deletions);
      return new SyncResult(
          device,
          c.plan().add().size(),
          c.plan().update().size(),
          deletions.size(),
          c.plan().unchanged());
    });
  }

  /** A route as a file: its file name, route name and contents. */
  private record RouteFile(String fileName, String routeName, byte[] content) {}

  private record Comparison(SyncPlan plan, Map<String, byte[]> wanted) {}

  /**
   * The rider's active routes as files, oldest route first. Routes whose names give the same file
   * name get their id appended, so every route has its own file.
   */
  private List<RouteFile> wanted(CurrentUser user, UsbSync target) {
    RouteFormat format = formats.get(target.format());
    List<RouteSummary> active = new ArrayList<>(routes.list(user));
    active.sort(Comparator.comparingLong(RouteSummary::id));
    Set<String> taken = new HashSet<>();
    List<RouteFile> files = new ArrayList<>();
    for (RouteSummary summary : active) {
      String name = FileNames.forRoute(summary.name(), format.extension());
      if (!taken.add(name.toLowerCase(Locale.ROOT))) {
        String base = name.substring(0, name.length() - format.extension().length() - 1);
        name = base + "_" + summary.id() + "." + format.extension();
        taken.add(name.toLowerCase(Locale.ROOT));
      }
      ExportedRoute route = ExportedRoute.of(routes.get(user, summary.id()));
      if (target.plainNames()) {
        route =
            new ExportedRoute(
                PlainNames.of(route.name()), route.track(), route.ascent(), route.descent());
      }
      byte[] content = format.write(route);
      files.add(new RouteFile(name, summary.name(), content));
    }
    return files;
  }

  private static Comparison compare(List<RouteFile> wanted, MtpDevice.Listing onDevice) {
    List<SyncFile> add = new ArrayList<>();
    List<SyncFile> update = new ArrayList<>();
    int unchanged = 0;
    Map<String, byte[]> contents = new LinkedHashMap<>();
    for (RouteFile f : wanted) {
      contents.put(f.fileName(), f.content());
      byte[] existing = onDevice.files().get(f.fileName());
      if (existing == null) {
        add.add(new SyncFile(f.fileName(), f.routeName()));
      } else if (!Arrays.equals(existing, f.content())) {
        update.add(new SyncFile(f.fileName(), f.routeName()));
      } else {
        unchanged++;
      }
    }
    // Hidden files belong to the device, not to the rider's routes.
    List<SyncFile> remove =
        onDevice.files().keySet().stream()
            .filter(name -> !contents.containsKey(name) && !name.startsWith("."))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .map(name -> new SyncFile(name, null))
            .toList();
    String device = onDevice.device();
    return new Comparison(new SyncPlan(device, add, update, remove, unchanged), contents);
  }
}
