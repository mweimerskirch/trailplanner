package app.trailplan.export;

import org.springframework.http.MediaType;

/**
 * A file format routes can be exported in. Implementations are Spring beans and are picked up
 * automatically: adding a format (TCX, KML, …) means adding one class.
 */
public interface RouteFormat {

  /** Stable id used in URLs, e.g. {@code gpx}. */
  String id();

  /** Shown to the rider, e.g. {@code GPX track}. */
  String label();

  /** File name extension without the dot. */
  String extension();

  MediaType mediaType();

  byte[] write(ExportedRoute route);
}
