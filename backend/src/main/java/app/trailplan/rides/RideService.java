package app.trailplan.rides;

import app.trailplan.devices.DeviceLock;
import app.trailplan.devices.DeviceService;
import app.trailplan.devices.MtpDevice;
import app.trailplan.devices.UsbSync;
import app.trailplan.imports.ExistingImports;
import app.trailplan.imports.ImportException;
import app.trailplan.imports.ImportResult;
import app.trailplan.routes.ImportedRoute;
import app.trailplan.routes.RouteRepository;
import app.trailplan.routes.RouteSource;
import app.trailplan.routing.TrackPoint;
import app.trailplan.routing.TrackStats;
import app.trailplan.users.CurrentUser;
import app.trailplan.web.ApiException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The rider's recorded rides: imported from FIT files (uploaded, or read from a device over USB),
 * shown with their track, and turned into library routes on request.
 */
@Service
public class RideService {

  /** Files read from the device per helper call, so that one call stays well within its timeout. */
  static final int FETCH_BATCH = 20;

  private static final int MAX_NAME_LENGTH = 120;

  /**
   * @param ride the ride as listed
   * @param track the recorded positions
   * @param routeId the library route made from this ride, if there is one (not in the trash)
   */
  public record RideDetails(RideSummary ride, List<TrackPoint> track, Long routeId) {}

  /** Outcome of importing one file; {@code rideId} is set unless the import failed. */
  public record RideImportResult(
      String fileName, ImportResult.Status status, Long rideId, String name, String message) {}

  /**
   * Outcome of importing from a device.
   *
   * @param imported the new rides
   * @param skipped files that were imported before (also those of deleted rides)
   * @param withoutTrack recordings without GPS positions (e.g. started by accident), not imported
   * @param failed files that could not be read
   */
  public record DeviceImportResult(
      String device, int imported, int skipped, int withoutTrack, List<RideImportResult> failed) {}

  private final RideRepository rides;
  private final RouteRepository routes;
  private final DeviceService devices;
  private final MtpDevice mtp;
  private final DeviceLock lock;
  private final Clock clock;

  public RideService(
      RideRepository rides,
      RouteRepository routes,
      DeviceService devices,
      MtpDevice mtp,
      DeviceLock lock,
      Clock clock) {
    this.rides = rides;
    this.routes = routes;
    this.devices = devices;
    this.mtp = mtp;
    this.lock = lock;
    this.clock = clock;
  }

  public List<RideSummary> list(CurrentUser user) {
    return rides.findSummaries(user.id());
  }

  public RideDetails get(CurrentUser user, long id) {
    RideSummary ride = rides.findSummary(user.id(), id).orElseThrow(() -> notFound(id));
    RideRepository.StoredFile file = rides.findFile(user.id(), id).orElseThrow(() -> notFound(id));
    List<TrackPoint> track = RideFileReader.read(file.data()).track();
    Long routeId =
        routes
            .findByExternalId(user.id(), routeExternalId(file))
            .filter(match -> !match.trashed())
            .map(RouteRepository.ExternalMatch::id)
            .orElse(null);
    return new RideDetails(ride, track, routeId);
  }

  public RideRepository.StoredFile file(CurrentUser user, long id) {
    return rides.findFile(user.id(), id).orElseThrow(() -> notFound(id));
  }

  public RideSummary rename(CurrentUser user, long id, String name) {
    String clean = name == null ? "" : name.strip();
    if (clean.isEmpty() || clean.length() > MAX_NAME_LENGTH) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Invalid name",
          "A ride needs a name of at most " + MAX_NAME_LENGTH + " characters.");
    }
    if (!rides.rename(user.id(), id, clean)) throw notFound(id);
    return rides.findSummary(user.id(), id).orElseThrow(() -> notFound(id));
  }

  /** Deletes the ride. Importing from a device does not bring it back; uploading its file does. */
  public void delete(CurrentUser user, long id) {
    if (!rides.delete(user.id(), id, clock.instant())) throw notFound(id);
  }

  /** Imports an uploaded FIT file. A deleted ride whose file is uploaded again is restored. */
  public RideImportResult importFile(CurrentUser user, String fileName, byte[] data) {
    return importFile(user, fileName, data, true);
  }

  private RideImportResult importFile(
      CurrentUser user, String fileName, byte[] data, boolean restoreDeleted) {
    String safeName = fileName == null || fileName.isBlank() ? "ride.fit" : fileName;
    String sha256 = sha256(data);
    Optional<RideRepository.FileMatch> existing = rides.findBySha256(user.id(), sha256);
    if (existing.isPresent()) {
      RideRepository.FileMatch match = existing.get();
      boolean restore = match.deleted() && restoreDeleted;
      if (restore) rides.restore(user.id(), match.id(), data);
      String message =
          restore
              ? "This ride was deleted and is restored."
              : match.deleted() ? "This ride was deleted." : "This ride is already imported.";
      return new RideImportResult(
          safeName, ImportResult.Status.ALREADY_IMPORTED, match.id(), match.name(), message);
    }
    try {
      RecordedRide ride = RideFileReader.read(data);
      String name = defaultName(ride);
      long id = rides.insert(user.id(), name, ride, safeName, data, sha256, clock.instant());
      return new RideImportResult(safeName, ImportResult.Status.IMPORTED, id, name, null);
    } catch (ImportException e) {
      return new RideImportResult(safeName, ImportResult.Status.FAILED, null, null, e.getMessage());
    }
  }

  /**
   * Imports the rides recorded on the connected device that are not imported yet. Files are
   * recognised by name and size, so known ones are not even downloaded.
   */
  public DeviceImportResult importFromDevice(CurrentUser user, String deviceId) {
    UsbSync target = devices.usbSync(deviceId);
    String folder = target.ridesFolder();
    if (folder == null) {
      throw new ApiException(
          HttpStatus.NOT_FOUND, "No ride import", "Rides cannot be imported from this device.");
    }
    return lock.run(() -> {
      MtpDevice.FileList list = mtp.files(target, folder);
      Set<String> known = rides.findFileKeys(user.id());
      List<MtpDevice.DeviceFile> fitFiles =
          list.files().stream()
              .filter(f -> f.name().toLowerCase(Locale.ROOT).endsWith(".fit"))
              .sorted(Comparator.comparing(MtpDevice.DeviceFile::name))
              .toList();
      List<String> wanted =
          fitFiles.stream()
              .filter(f -> !known.contains(RideRepository.fileKey(f.name(), f.size())))
              .map(MtpDevice.DeviceFile::name)
              .toList();
      int imported = 0;
      int skipped = fitFiles.size() - wanted.size();
      int withoutTrack = 0;
      List<RideImportResult> failed = new ArrayList<>();
      for (int i = 0; i < wanted.size(); i += FETCH_BATCH) {
        List<String> batch = wanted.subList(i, Math.min(i + FETCH_BATCH, wanted.size()));
        Map<String, byte[]> files = mtp.fetch(target, folder, batch);
        for (String name : batch) {
          byte[] data = files.get(name);
          if (data == null) continue; // removed from the device meanwhile
          RideImportResult result = importFile(user, name, data, false);
          switch (result.status()) {
            case IMPORTED -> imported++;
            case ALREADY_IMPORTED -> skipped++;
            case FAILED -> {
              if (RideFileReader.NO_TRACK.equals(result.message())) {
                withoutTrack++;
              } else {
                failed.add(result);
              }
            }
          }
        }
      }
      return new DeviceImportResult(list.device(), imported, skipped, withoutTrack, failed);
    });
  }

  /**
   * Adds the ride's track to the library as a route. Done once per ride: asking again finds the
   * route (and restores it from the trash).
   */
  public ImportResult saveAsRoute(CurrentUser user, long id) {
    RideSummary ride = rides.findSummary(user.id(), id).orElseThrow(() -> notFound(id));
    RideRepository.StoredFile file = rides.findFile(user.id(), id).orElseThrow(() -> notFound(id));
    String externalId = routeExternalId(file);
    Optional<ImportResult> existing =
        ExistingImports.check(routes, user.id(), externalId, ride.name(), "ride");
    if (existing.isPresent()) return existing.get();
    List<TrackPoint> track = RideFileReader.read(file.data()).track();
    long routeId =
        routes.insertImported(
            user.id(),
            new ImportedRoute(
                ride.name(),
                RouteSource.RIDE,
                externalId,
                track,
                TrackStats.distance(track),
                TrackStats.ascent(track),
                TrackStats.descent(track)),
            clock.instant());
    return new ImportResult(ride.name(), ImportResult.Status.IMPORTED, routeId, ride.name(), null);
  }

  private static String routeExternalId(RideRepository.StoredFile file) {
    return "ride:" + file.sha256();
  }

  /** "Morning ride", "Evening run" …, by the local start time. */
  static String defaultName(RecordedRide ride) {
    int offset = ride.utcOffset() == null ? 0 : ride.utcOffset();
    int hour = ride.startedAt().atOffset(ZoneOffset.ofTotalSeconds(offset)).getHour();
    String time =
        hour >= 5 && hour < 12
            ? "Morning"
            : hour >= 12 && hour < 18 ? "Afternoon" : hour >= 18 && hour < 22 ? "Evening" : "Night";
    String what =
        switch (ride.sport() == null ? "cycling" : ride.sport()) {
          case "running" -> "run";
          case "walking" -> "walk";
          case "hiking" -> "hike";
          case "cycling", "e_biking" -> "ride";
          default -> "activity";
        };
    return time + " " + what;
  }

  private static ApiException notFound(long id) {
    return new ApiException(
        HttpStatus.NOT_FOUND, "Ride not found", "Ride " + id + " does not exist.");
  }

  private static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
