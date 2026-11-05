package app.trailplan.export;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** All registered {@link RouteFormat}s by id. */
@Component
public class RouteFormats {

  private final Map<String, RouteFormat> byId = new LinkedHashMap<>();

  public RouteFormats(List<RouteFormat> formats) {
    for (RouteFormat format : formats) {
      if (byId.put(format.id(), format) != null) {
        throw new IllegalStateException("Two route formats share the id " + format.id());
      }
    }
  }

  public RouteFormat get(String id) {
    RouteFormat format = byId.get(id);
    if (format == null) {
      throw new IllegalArgumentException("Unknown export format: " + id);
    }
    return format;
  }

  public List<RouteFormat> all() {
    return List.copyOf(byId.values());
  }
}
