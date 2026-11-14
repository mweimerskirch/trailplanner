package app.trailplanner.imports;

import app.trailplanner.routing.TrackPoint;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads the points of a GPX 1.0/1.1 file: all track points ({@code trkpt}) in order, or the route
 * points ({@code rtept}) when there is no track. Namespaces are ignored, so files from Komoot,
 * Garmin, Strava etc. all work.
 */
public final class GpxReader {

  private static final XMLInputFactory XML = secureFactory();

  public ParsedTrack read(byte[] data) {
    List<TrackPoint> trackPoints = new ArrayList<>();
    List<TrackPoint> routePoints = new ArrayList<>();
    String metadataName = null;
    String trackName = null;
    String routeName = null;
    try {
      XMLStreamReader xml = XML.createXMLStreamReader(new ByteArrayInputStream(data));
      List<String> path = new ArrayList<>();
      Double lat = null;
      Double lon = null;
      Double ele = null;
      while (xml.hasNext()) {
        int event = xml.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String element = xml.getLocalName();
          path.add(element);
          if (element.equals("trkpt") || element.equals("rtept")) {
            lat = parse(xml.getAttributeValue(null, "lat"));
            lon = parse(xml.getAttributeValue(null, "lon"));
            ele = null;
          } else if (element.equals("ele") && isPoint(parent(path))) {
            ele = parse(xml.getElementText());
            path.remove(path.size() - 1); // getElementText consumed the end tag
          } else if (element.equals("name")) {
            String context = parent(path);
            String text = xml.getElementText().strip();
            path.remove(path.size() - 1);
            if (!text.isEmpty()) {
              switch (context) {
                case "metadata" -> metadataName = metadataName == null ? text : metadataName;
                case "trk" -> trackName = trackName == null ? text : trackName;
                case "rte" -> routeName = routeName == null ? text : routeName;
                default -> {}
              }
            }
          }
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          String element = xml.getLocalName();
          if ((element.equals("trkpt") || element.equals("rtept")) && lat != null && lon != null) {
            TrackPoint point = new TrackPoint(lon, lat, ele);
            (element.equals("trkpt") ? trackPoints : routePoints).add(point);
          }
          if (!path.isEmpty()) {
            path.remove(path.size() - 1);
          }
        }
      }
    } catch (XMLStreamException e) {
      throw new ImportException("This is not a readable GPX file.", e);
    }
    List<TrackPoint> points = trackPoints.isEmpty() ? routePoints : trackPoints;
    String name = trackName != null ? trackName : routeName != null ? routeName : metadataName;
    return new ParsedTrack(name, points);
  }

  private static boolean isPoint(String element) {
    return element.equals("trkpt") || element.equals("rtept");
  }

  /** The element enclosing the current one. */
  private static String parent(List<String> path) {
    return path.size() >= 2 ? path.get(path.size() - 2) : "";
  }

  private static Double parse(String value) {
    if (value == null) {
      return null;
    }
    try {
      double d = Double.parseDouble(value.strip());
      return Double.isFinite(d) ? d : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** No DTDs and no external entities: uploaded files must not be able to read local files. */
  private static XMLInputFactory secureFactory() {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
    return factory;
  }
}
