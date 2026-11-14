package app.trailplanner.imports;

/** A file or a Komoot tour could not be imported. The message is safe to show to the rider. */
public class ImportException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ImportException(String message) {
    super(message);
  }

  public ImportException(String message, Throwable cause) {
    super(message, cause);
  }
}
