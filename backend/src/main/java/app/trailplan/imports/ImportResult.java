package app.trailplan.imports;

/**
 * Outcome of importing one file or Komoot tour; {@code routeId} is set unless the import failed.
 *
 * @param source the file name or the Komoot tour id
 */
public record ImportResult(
    String source, Status status, Long routeId, String name, String message) {

  public enum Status {
    IMPORTED,
    ALREADY_IMPORTED,
    FAILED
  }
}
