package app.trailplan.komoot;

import app.trailplan.imports.ImportResult;
import app.trailplan.routes.ImportedRoute;
import app.trailplan.routes.RouteRepository;
import app.trailplan.routes.RouteSource;
import app.trailplan.routing.TrackStats;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Lists Komoot tours with their import state and imports them as fixed-track routes. */
@Service
public class KomootImportService {

  static final String EXTERNAL_ID_PREFIX = "komoot:";
  private static final int MAX_NAME_LENGTH = 120;

  /** A Komoot tour as shown in the import list. */
  public record TourEntry(
      long id,
      String name,
      boolean planned,
      String sport,
      double distance,
      double ascent,
      Instant date,
      boolean imported) {}

  private final KomootClient komoot;
  private final RouteRepository routes;
  private final Clock clock;

  public KomootImportService(KomootClient komoot, RouteRepository routes, Clock clock) {
    this.komoot = komoot;
    this.routes = routes;
    this.clock = clock;
  }

  public List<TourEntry> tours(KomootClient.Session session) {
    Set<String> imported = new HashSet<>(routes.findActiveExternalIds(EXTERNAL_ID_PREFIX));
    return komoot.tours(session).stream()
        .map(t ->
            new TourEntry(
                t.id(),
                t.name(),
                t.planned(),
                t.sport(),
                t.distance(),
                t.elevationUp(),
                t.date(),
                imported.contains(EXTERNAL_ID_PREFIX + t.id())))
        .toList();
  }

  public List<ImportResult> importTours(KomootClient.Session session, List<Long> tourIds) {
    return tourIds.stream().map(id -> importTour(session, id)).toList();
  }

  private ImportResult importTour(KomootClient.Session session, long tourId) {
    String source = Long.toString(tourId);
    String externalId = EXTERNAL_ID_PREFIX + tourId;
    var existing = routes.findActiveIdByExternalId(externalId);
    if (existing.isPresent()) {
      String name = routes.findById(existing.get()).map(r -> r.name()).orElse(source);
      return new ImportResult(
          source,
          ImportResult.Status.ALREADY_IMPORTED,
          existing.get(),
          name,
          "This tour is already in the library.");
    }
    try {
      KomootClient.TourTrack track = komoot.track(session, tourId);
      if (track.points().size() < 2) {
        return new ImportResult(
            source,
            ImportResult.Status.FAILED,
            null,
            track.tour().name(),
            "The tour has no track.");
      }
      String name = track.tour().name().strip();
      if (name.isEmpty()) {
        name = "Komoot tour " + tourId;
      }
      if (name.length() > MAX_NAME_LENGTH) {
        name = name.substring(0, MAX_NAME_LENGTH);
      }
      // Komoot's own totals are smoothed better than ours; fall back if they are missing.
      double distance =
          track.tour().distance() > 0
              ? track.tour().distance()
              : TrackStats.distance(track.points());
      double ascent =
          track.tour().elevationUp() > 0
              ? track.tour().elevationUp()
              : TrackStats.ascent(track.points());
      double descent =
          track.tour().elevationDown() > 0
              ? track.tour().elevationDown()
              : TrackStats.descent(track.points());
      long id =
          routes.insertImported(
              new ImportedRoute(
                  name, RouteSource.KOMOOT, externalId, track.points(), distance, ascent, descent),
              clock.instant());
      return new ImportResult(source, ImportResult.Status.IMPORTED, id, name, null);
    } catch (KomootException e) {
      return new ImportResult(source, ImportResult.Status.FAILED, null, null, e.getMessage());
    }
  }
}
