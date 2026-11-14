package app.trailplan.routing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads BRouter's GeoJSON answer into a {@link RouteLeg}. */
public final class BRouterResponseParser {

  private final ObjectMapper mapper;

  public BRouterResponseParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public RouteLeg parse(String geoJson) {
    return parse(geoJson, Activity.MTB);
  }

  /** As {@link #parse(String)}; warnings use the access rules of the activity. */
  public RouteLeg parse(String geoJson, Activity activity) {
    JsonNode feature;
    try {
      feature = mapper.readTree(geoJson).path("features").path(0);
    } catch (JacksonException e) {
      throw new RoutingException(
          RoutingException.Reason.ROUTER_ERROR, "The routing server sent an unreadable answer.", e);
    }
    if (feature.isMissingNode()) {
      throw new RoutingException(
          RoutingException.Reason.ROUTER_ERROR, "The routing server returned no route.");
    }

    List<TrackPoint> points = new ArrayList<>();
    for (JsonNode c : feature.path("geometry").path("coordinates")) {
      JsonNode ele = c.path(2);
      points.add(
          new TrackPoint(
              c.path(0).asDouble(), c.path(1).asDouble(), ele.isNumber() ? ele.asDouble() : null));
    }

    JsonNode props = feature.path("properties");
    double distance = props.path("track-length").asDouble(0);
    double ascent = props.path("filtered ascend").asDouble(0);
    JsonNode messages = props.path("messages");
    return new RouteLeg(
        points, distance, ascent, breakdown(messages), warnings(messages, activity));
  }

  /**
   * The "messages" table has a header row followed by one row per stretch of identical way tags.
   * Columns are looked up by name so a reordering in BRouter does not break us.
   */
  private static LegBreakdown breakdown(JsonNode messages) {
    if (!messages.isArray() || messages.size() < 2) {
      return LegBreakdown.empty();
    }
    JsonNode header = messages.get(0);
    int distanceCol = column(header, "Distance");
    int tagsCol = column(header, "WayTags");
    if (distanceCol < 0 || tagsCol < 0) {
      return LegBreakdown.empty();
    }

    EnumMap<WayCategory, Double> ways = new EnumMap<>(WayCategory.class);
    EnumMap<Surface, Double> surfaces = new EnumMap<>(Surface.class);
    double other = 0;
    for (int i = 1; i < messages.size(); i++) {
      JsonNode row = messages.get(i);
      double metres = row.path(distanceCol).asDouble(0);
      Map<String, String> tags = parseTags(row.path(tagsCol).asString(""));
      var category = WayCategory.classify(tags);
      if (category.isPresent()) {
        ways.merge(category.get(), metres, Double::sum);
      } else {
        other += metres;
      }
      surfaces.merge(Surface.classify(tags.get("surface")), metres, Double::sum);
    }
    return new LegBreakdown(ways, other, surfaces);
  }

  /**
   * Restricted ways on the leg (only forced legs can use them), one warning per stretch with the
   * same reason, placed where that stretch ends.
   */
  private static List<RouteWarning> warnings(JsonNode messages, Activity activity) {
    List<RouteWarning> warnings = new ArrayList<>();
    if (!messages.isArray() || messages.size() < 2) {
      return warnings;
    }
    JsonNode header = messages.get(0);
    int lngCol = column(header, "Longitude");
    int latCol = column(header, "Latitude");
    int tagsCol = column(header, "WayTags");
    if (lngCol < 0 || latCol < 0 || tagsCol < 0) {
      return warnings;
    }
    RouteWarning.Reason previous = null;
    for (int i = 1; i < messages.size(); i++) {
      JsonNode row = messages.get(i);
      RouteWarning.Reason reason =
          RouteWarning.restriction(parseTags(row.path(tagsCol).asString("")), activity);
      if (reason != null && reason != previous) {
        warnings.add(
            new RouteWarning(
                row.path(lngCol).asDouble() / 1_000_000,
                row.path(latCol).asDouble() / 1_000_000,
                reason));
      }
      previous = reason;
    }
    return warnings;
  }

  private static int column(JsonNode header, String name) {
    for (int i = 0; i < header.size(); i++) {
      if (name.equals(header.get(i).asString())) {
        return i;
      }
    }
    return -1;
  }

  static Map<String, String> parseTags(String wayTags) {
    Map<String, String> tags = new HashMap<>();
    for (String pair : wayTags.split(" ")) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        tags.put(pair.substring(0, eq), pair.substring(eq + 1));
      }
    }
    return tags;
  }
}
