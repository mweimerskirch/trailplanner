package app.trailplan.routing;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Metres per way category and per surface class. {@code other} covers uncategorised ways. */
public record LegBreakdown(
    Map<WayCategory, Double> wayTypes, double other, Map<Surface, Double> surfaces) {

  public LegBreakdown {
    wayTypes = Collections.unmodifiableMap(copy(wayTypes, WayCategory.class));
    surfaces = Collections.unmodifiableMap(copy(surfaces, Surface.class));
  }

  public static LegBreakdown empty() {
    return new LegBreakdown(Map.of(), 0, Map.of());
  }

  public LegBreakdown plus(LegBreakdown other) {
    EnumMap<WayCategory, Double> ways = copy(wayTypes, WayCategory.class);
    other.wayTypes.forEach((k, v) -> ways.merge(k, v, Double::sum));
    EnumMap<Surface, Double> surf = copy(surfaces, Surface.class);
    other.surfaces.forEach((k, v) -> surf.merge(k, v, Double::sum));
    return new LegBreakdown(ways, this.other + other.other, surf);
  }

  private static <K extends Enum<K>> EnumMap<K, Double> copy(Map<K, Double> source, Class<K> type) {
    EnumMap<K, Double> result = new EnumMap<>(type);
    if (source != null) {
      result.putAll(source);
    }
    return result;
  }
}
