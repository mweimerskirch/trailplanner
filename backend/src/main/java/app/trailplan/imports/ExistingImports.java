package app.trailplan.imports;

import app.trailplan.routes.RouteRepository;
import java.util.Optional;

/**
 * Importing a file or tour that the rider already has: it is not imported twice. If it is in the
 * trash, it is restored instead (the rider evidently wants it back).
 */
public final class ExistingImports {

  private ExistingImports() {}

  public static Optional<ImportResult> check(
      RouteRepository routes, long userId, String externalId, String source, String what) {
    return routes
        .findByExternalId(userId, externalId)
        .map(match -> {
          if (match.trashed()) {
            routes.restore(userId, match.id());
          }
          String message =
              match.trashed()
                  ? "This " + what + " was in the trash and is restored."
                  : "This " + what + " is already in the library.";
          return new ImportResult(
              source, ImportResult.Status.ALREADY_IMPORTED, match.id(), match.name(), message);
        });
  }
}
