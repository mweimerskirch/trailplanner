package app.trailplanner.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;

/**
 * Turns a finished track (e.g. a Komoot import) into as few waypoints as possible such that the
 * router reproduces the track.
 *
 * <p>It starts with a handful of waypoints at the track's main turns, routes every leg and compares
 * the result with the original stretch. For a leg that strays more than {@link #TOLERANCE_M}:
 *
 * <ol>
 *   <li>it tries a forced leg (closed and restricted ways allowed). If that follows the original
 *       and actually uses such a way, the leg is kept as forced: the original rides a way that is
 *       closed or restricted in the map data now, and the route will carry a warning there;
 *   <li>otherwise the leg is split at the worst point and both halves are routed again;
 *   <li>a short leg that still strays (no mapped way at all) becomes straight lines along the
 *       original, instead of a long detour.
 * </ol>
 *
 * Legs are routed in parallel, round by round.
 */
public final class TrackConverter {

  /** How far the routed line may stray from the original track. */
  static final double TOLERANCE_M = 35;

  /** Initial waypoints: Douglas–Peucker tolerance and the longest first leg. */
  private static final double INITIAL_SIMPLIFY_M = 80;

  private static final double INITIAL_MAX_LEG_M = 4000;

  /** Legs shorter than this are not split further; if they still stray, they become straight. */
  private static final double MIN_SPLIT_M = 40;

  /** Shape of straight stretches: how closely the straight legs follow the original. */
  private static final double STRAIGHT_SIMPLIFY_M = 8;

  /** The waypoints, and how many legs are forced or straight (the rider should check those). */
  public record Result(List<Waypoint> waypoints, int forcedLegs, int straightLegs) {}

  /** A stretch of the original between two indices; {@code forced} = try the forced router. */
  private record Leg(int from, int to, boolean forced) {}

  private final BiFunction<GeoPoint, GeoPoint, RouteLeg> router;
  private final BiFunction<GeoPoint, GeoPoint, RouteLeg> forcedRouter;
  private final ExecutorService executor;
  private final int maxWaypoints;

  /**
   * @param router routes one leg normally; may throw {@link RoutingException}
   * @param forcedRouter routes one leg allowing closed and restricted ways (with warnings)
   */
  public TrackConverter(
      BiFunction<GeoPoint, GeoPoint, RouteLeg> router,
      BiFunction<GeoPoint, GeoPoint, RouteLeg> forcedRouter,
      ExecutorService executor,
      int maxWaypoints) {
    this.router = router;
    this.forcedRouter = forcedRouter;
    this.executor = executor;
    this.maxWaypoints = maxWaypoints;
  }

  public Result convert(List<TrackPoint> track) {
    if (track.size() < 2) {
      throw new IllegalArgumentException("The track has fewer than two points.");
    }
    Geometry geo = new Geometry(track);
    List<Leg> pending = initialLegs(geo);
    Map<Integer, LegType> done = new TreeMap<>(); // leg start index -> type
    Map<Integer, Integer> ends = new TreeMap<>(); // leg start index -> end index
    int waypoints = pending.size() + 1;

    while (!pending.isEmpty()) {
      List<CompletableFuture<RouteLeg>> results =
          pending.stream()
              .map(leg -> CompletableFuture.supplyAsync(() -> route(geo, leg), executor))
              .toList();
      List<Leg> next = new ArrayList<>();
      for (int i = 0; i < pending.size(); i++) {
        Leg leg = pending.get(i);
        RouteLeg result = results.get(i).join();
        int split = result == null ? -1 : worstPoint(geo, leg, result);
        boolean follows = result != null && split < 0;
        if (follows && (!leg.forced() || !result.warnings().isEmpty())) {
          done.put(leg.from(), leg.forced() ? LegType.FORCED : LegType.ROUTED);
          ends.put(leg.from(), leg.to());
        } else if (!leg.forced()) {
          next.add(new Leg(leg.from(), leg.to(), true)); // try allowing closed ways first
        } else if (geo.length(leg.from(), leg.to()) >= MIN_SPLIT_M
            && leg.to() - leg.from() >= 2
            && waypoints < maxWaypoints) {
          int at = split > leg.from() && split < leg.to() ? split : (leg.from() + leg.to()) / 2;
          next.add(new Leg(leg.from(), at, false));
          next.add(new Leg(at, leg.to(), false));
          waypoints++;
        } else {
          done.put(leg.from(), LegType.STRAIGHT);
          ends.put(leg.from(), leg.to());
        }
      }
      pending = next;
    }
    List<Leg> legs = new ArrayList<>();
    List<LegType> types = new ArrayList<>();
    done.forEach((from, type) -> {
      legs.add(new Leg(from, ends.get(from), type == LegType.FORCED));
      types.add(type);
    });
    merge(geo, legs, types);
    return assemble(geo, legs, types);
  }

  /**
   * Splitting leaves more waypoints than needed: neighbouring legs often route fine as one. Tries
   * to merge pairs of neighbouring legs of the same type, keeping a merge only if the merged leg
   * still follows the original, until no pair merges any more.
   */
  private void merge(Geometry geo, List<Leg> legs, List<LegType> types) {
    boolean progress = true;
    int offset = 0;
    int idleRounds = 0;
    while (idleRounds < 2) {
      List<Integer> candidates = new ArrayList<>();
      for (int k = offset; k + 1 < legs.size(); k += 2) {
        if (types.get(k) == types.get(k + 1) && types.get(k) != LegType.STRAIGHT) {
          candidates.add(k);
        }
      }
      List<CompletableFuture<RouteLeg>> results =
          candidates.stream()
              .map(k -> {
                Leg merged =
                    new Leg(legs.get(k).from(), legs.get(k + 1).to(), legs.get(k).forced());
                return CompletableFuture.supplyAsync(() -> route(geo, merged), executor);
              })
              .toList();
      progress = false;
      // Apply from the end, so earlier indices stay valid.
      for (int c = candidates.size() - 1; c >= 0; c--) {
        int k = candidates.get(c);
        Leg merged = new Leg(legs.get(k).from(), legs.get(k + 1).to(), legs.get(k).forced());
        RouteLeg result = results.get(c).join();
        boolean follows = result != null && worstPoint(geo, merged, result) < 0;
        if (follows && (!merged.forced() || !result.warnings().isEmpty())) {
          legs.set(k, merged);
          legs.remove(k + 1);
          types.remove(k + 1);
          progress = true;
        }
      }
      idleRounds = progress ? 0 : idleRounds + 1;
      offset = 1 - offset; // pair up the other way next time
    }
  }

  private RouteLeg route(Geometry geo, Leg leg) {
    try {
      return (leg.forced() ? forcedRouter : router)
          .apply(geo.point(leg.from()), geo.point(leg.to()));
    } catch (RoutingException e) {
      return null; // e.g. a point too far from any way: treated like a leg that strays
    }
  }

  /** First waypoints: the track's main turns, with no leg longer than INITIAL_MAX_LEG_M. */
  private static List<Leg> initialLegs(Geometry geo) {
    List<Integer> kept = geo.simplify(0, geo.size() - 1, INITIAL_SIMPLIFY_M);
    List<Integer> indices = new ArrayList<>();
    for (int k = 0; k < kept.size() - 1; k++) {
      int from = kept.get(k);
      int to = kept.get(k + 1);
      indices.add(from);
      int pieces = (int) Math.ceil(geo.length(from, to) / INITIAL_MAX_LEG_M);
      for (int p = 1; p < pieces; p++) {
        int at = geo.indexAt(geo.along(from) + geo.length(from, to) * p / pieces);
        if (at > indices.getLast() && at < to) {
          indices.add(at);
        }
      }
    }
    indices.add(geo.size() - 1);
    List<Leg> legs = new ArrayList<>();
    for (int i = 0; i < indices.size() - 1; i++) {
      legs.add(new Leg(indices.get(i), indices.get(i + 1), false));
    }
    return legs;
  }

  /**
   * Where the routed leg strays furthest from the original stretch, as an index into the original;
   * -1 if it stays within the tolerance everywhere. Checks both directions: original points far
   * from the routed line (the router took another way) and routed points far from the original (the
   * router made a detour, e.g. out and back along a side road).
   */
  private static int worstPoint(Geometry geo, Leg leg, RouteLeg result) {
    List<double[]> routedLine = result.points().stream().map(geo::project).toList();
    double worst = TOLERANCE_M;
    int worstIndex = -1;
    for (int i = leg.from() + 1; i < leg.to(); i++) {
      double d = Geometry.distanceToLine(geo.xy(i), routedLine);
      if (d > worst) {
        worst = d;
        worstIndex = i;
      }
    }
    List<double[]> original = geo.xy(leg.from(), leg.to());
    double[] worstDetour = null;
    for (double[] p : routedLine) {
      double d = Geometry.distanceToLine(p, original);
      if (d > worst) {
        worst = d;
        worstDetour = p;
      }
    }
    if (worstDetour != null) {
      // Pin the router to the original next to the detour.
      worstIndex = geo.nearestIndex(worstDetour, leg.from() + 1, leg.to() - 1);
    }
    if (worstIndex < 0 && worst == TOLERANCE_M) {
      return -1;
    }
    return worstIndex < 0 ? (leg.from() + leg.to()) / 2 : worstIndex;
  }

  /** Waypoints in track order; straight stretches keep the shape of the original. */
  private static Result assemble(Geometry geo, List<Leg> legs, List<LegType> types) {
    List<Waypoint> waypoints = new ArrayList<>();
    int forced = 0;
    int straight = 0;
    for (int i = 0; i < legs.size(); i++) {
      Leg leg = legs.get(i);
      LegType type = types.get(i);
      if (type == LegType.STRAIGHT) {
        List<Integer> shape = geo.simplify(leg.from(), leg.to(), STRAIGHT_SIMPLIFY_M);
        for (int k = 0; k < shape.size() - 1; k++) {
          waypoints.add(geo.waypoint(shape.get(k), LegType.STRAIGHT));
          straight++;
        }
      } else {
        waypoints.add(geo.waypoint(leg.from(), type));
        if (type == LegType.FORCED) {
          forced++;
        }
      }
    }
    waypoints.add(geo.waypoint(geo.size() - 1, LegType.ROUTED));
    return new Result(List.copyOf(waypoints), forced, straight);
  }

  /** The track in local planar metres, with distances along it. */
  private static final class Geometry {
    private final List<TrackPoint> track;
    private final double[][] xy;
    private final double[] along;
    private final double cosLat;

    Geometry(List<TrackPoint> track) {
      this.track = track;
      this.cosLat = Math.cos(Math.toRadians(track.getFirst().lat()));
      this.xy = new double[track.size()][];
      this.along = new double[track.size()];
      for (int i = 0; i < track.size(); i++) {
        xy[i] = project(track.get(i));
        if (i > 0) {
          along[i] = along[i - 1] + Math.hypot(xy[i][0] - xy[i - 1][0], xy[i][1] - xy[i - 1][1]);
        }
      }
    }

    int size() {
      return track.size();
    }

    double[] project(TrackPoint p) {
      return new double[] {p.lng() * 111_320 * cosLat, p.lat() * 110_540};
    }

    double[] xy(int i) {
      return xy[i];
    }

    List<double[]> xy(int from, int to) {
      List<double[]> line = new ArrayList<>(to - from + 1);
      for (int i = from; i <= to; i++) {
        line.add(xy[i]);
      }
      return line;
    }

    GeoPoint point(int i) {
      return new GeoPoint(track.get(i).lat(), track.get(i).lng());
    }

    Waypoint waypoint(int i, LegType leg) {
      return new Waypoint(track.get(i).lat(), track.get(i).lng(), leg);
    }

    double along(int i) {
      return along[i];
    }

    double length(int from, int to) {
      return along[to] - along[from];
    }

    /** The first index at or beyond this distance along the track. */
    int indexAt(double distance) {
      int lo = 0;
      int hi = along.length - 1;
      while (lo < hi) {
        int mid = (lo + hi) >>> 1;
        if (along[mid] < distance) {
          lo = mid + 1;
        } else {
          hi = mid;
        }
      }
      return lo;
    }

    int nearestIndex(double[] p, int from, int to) {
      int best = from;
      double bestDistance = Double.MAX_VALUE;
      for (int i = from; i <= to; i++) {
        double d = Math.hypot(xy[i][0] - p[0], xy[i][1] - p[1]);
        if (d < bestDistance) {
          bestDistance = d;
          best = i;
        }
      }
      return best;
    }

    /** Douglas–Peucker over [from, to]; returns the kept indices including both ends. */
    List<Integer> simplify(int from, int to, double tolerance) {
      boolean[] keep = new boolean[track.size()];
      keep[from] = true;
      keep[to] = true;
      List<int[]> stack = new ArrayList<>();
      stack.add(new int[] {from, to});
      while (!stack.isEmpty()) {
        int[] range = stack.removeLast();
        int farthest = -1;
        double max = tolerance;
        for (int i = range[0] + 1; i < range[1]; i++) {
          double d = distanceToSegment(xy[i], xy[range[0]], xy[range[1]]);
          if (d > max) {
            max = d;
            farthest = i;
          }
        }
        if (farthest >= 0) {
          keep[farthest] = true;
          stack.add(new int[] {range[0], farthest});
          stack.add(new int[] {farthest, range[1]});
        }
      }
      List<Integer> kept = new ArrayList<>();
      for (int i = from; i <= to; i++) {
        if (keep[i]) {
          kept.add(i);
        }
      }
      return kept;
    }

    static double distanceToLine(double[] p, List<double[]> line) {
      if (line.size() == 1) {
        return Math.hypot(p[0] - line.getFirst()[0], p[1] - line.getFirst()[1]);
      }
      double min = Double.MAX_VALUE;
      for (int i = 1; i < line.size(); i++) {
        min = Math.min(min, distanceToSegment(p, line.get(i - 1), line.get(i)));
      }
      return min;
    }

    static double distanceToSegment(double[] p, double[] a, double[] b) {
      double dx = b[0] - a[0];
      double dy = b[1] - a[1];
      double lengthSq = dx * dx + dy * dy;
      double t =
          lengthSq == 0
              ? 0
              : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / lengthSq));
      return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
    }
  }
}
