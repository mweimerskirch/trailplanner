package app.trailplan.imports;

import app.trailplan.routes.ImportedRoute;
import app.trailplan.routes.RouteRepository;
import app.trailplan.routes.RouteSource;
import app.trailplan.routing.TrackPoint;
import app.trailplan.routing.TrackStats;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Imports GPX and FIT files as fixed tracks. Re-importing an identical file finds the route that is
 * already in the library instead of creating a duplicate.
 */
@Service
public class FileImportService {

  private static final int MAX_NAME_LENGTH = 120;

  private final RouteRepository routes;
  private final Clock clock;
  private final GpxReader gpx = new GpxReader();
  private final FitReader fit = new FitReader();

  public FileImportService(RouteRepository routes, Clock clock) {
    this.routes = routes;
    this.clock = clock;
  }

  public ImportResult importFile(String fileName, byte[] data) {
    String safeName = fileName == null || fileName.isBlank() ? "route" : fileName;
    try {
      String externalId = "file:" + sha256(data);
      Optional<Long> existing = routes.findActiveIdByExternalId(externalId);
      if (existing.isPresent()) {
        String name = routes.findById(existing.get()).map(r -> r.name()).orElse(safeName);
        return new ImportResult(
            safeName,
            ImportResult.Status.ALREADY_IMPORTED,
            existing.get(),
            name,
            "This file is already in the library.");
      }

      boolean isFit = FitReader.looksLikeFit(data);
      ParsedTrack track = isFit ? fit.read(data) : readGpx(safeName, data);
      List<TrackPoint> points = track.points();
      if (points.size() < 2) {
        throw new ImportException("The file contains no track.");
      }
      String name = routeName(track.name(), safeName);
      long id =
          routes.insertImported(
              new ImportedRoute(
                  name,
                  isFit ? RouteSource.FIT : RouteSource.GPX,
                  externalId,
                  points,
                  TrackStats.distance(points),
                  TrackStats.ascent(points),
                  TrackStats.descent(points)),
              clock.instant());
      return new ImportResult(safeName, ImportResult.Status.IMPORTED, id, name, null);
    } catch (ImportException e) {
      return new ImportResult(safeName, ImportResult.Status.FAILED, null, null, e.getMessage());
    }
  }

  private ParsedTrack readGpx(String fileName, byte[] data) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    String start = new String(data, 0, Math.min(data.length, 512), StandardCharsets.UTF_8);
    if (!lower.endsWith(".gpx") && !start.contains("<gpx")) {
      throw new ImportException("Unsupported file type. Import GPX or FIT files.");
    }
    return gpx.read(data);
  }

  /** The name stored in the file, else the file name without its extension. */
  static String routeName(String fromFile, String fileName) {
    String name = fromFile;
    if (name == null || name.isBlank()) {
      int dot = fileName.lastIndexOf('.');
      name = dot > 0 ? fileName.substring(0, dot) : fileName;
    }
    name = name.strip();
    return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
  }

  private static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
