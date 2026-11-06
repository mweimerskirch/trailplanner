package app.trailplan.export;

import app.trailplan.routing.TrackPoint;
import java.util.List;
import java.util.Locale;

/** Writes a route as a GPX 1.1 track, which the ELEMNT/Wahoo app can import. */
public final class GpxWriter {

  public String write(String name, List<TrackPoint> points) {
    StringBuilder gpx = new StringBuilder(points.size() * 64 + 512);
    gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        .append(
            "<gpx version=\"1.1\" creator=\"Trailplan\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        .append("  <metadata><name>")
        .append(escape(name))
        .append("</name></metadata>\n")
        .append("  <trk><name>")
        .append(escape(name))
        .append("</name><trkseg>\n");
    for (TrackPoint p : points) {
      gpx.append(
          String.format(Locale.ROOT, "    <trkpt lat=\"%.6f\" lon=\"%.6f\">", p.lat(), p.lng()));
      if (p.ele() != null) {
        gpx.append(String.format(Locale.ROOT, "<ele>%.1f</ele>", p.ele()));
      }
      gpx.append("</trkpt>\n");
    }
    gpx.append("  </trkseg></trk>\n</gpx>\n");
    return gpx.toString();
  }

  /** Escapes element text. Quotes need no escaping there, and some devices show {@code &quot;}. */
  static String escape(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (char c : text.toCharArray()) {
      switch (c) {
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '&' -> out.append("&amp;");
        default -> {
          if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
            out.append(c);
          }
        }
      }
    }
    return out.toString();
  }
}
