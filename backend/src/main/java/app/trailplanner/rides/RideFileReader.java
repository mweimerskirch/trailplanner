package app.trailplanner.rides;

import app.trailplanner.imports.FitReader;
import app.trailplanner.imports.ImportException;
import app.trailplanner.routing.TrackPoint;
import app.trailplanner.routing.TrackStats;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads a recorded ride from a FIT activity file: the totals from its session messages (summed if
 * there are several) and the track from its records. Files without sessions (e.g. a recording cut
 * short) get their totals from the records.
 */
public final class RideFileReader {

  /** Below this speed (about 3 km/h) the rider counts as stopped. */
  private static final double MOVING_SPEED = 0.8;

  /** Longer gaps between records are pauses (auto-pause), not riding. */
  private static final long MAX_GAP_S = 30;

  private static final Map<Long, String> SPORTS =
      Map.of(
          0L, "generic",
          1L, "running",
          2L, "cycling",
          11L, "walking",
          17L, "hiking",
          21L, "e_biking");

  /** Why a recording without positions (e.g. started by accident, or indoors) is not imported. */
  public static final String NO_TRACK = "The ride has no GPS track.";

  private RideFileReader() {}

  /** A record message: position (may be null), time in FIT seconds, distance in metres. */
  private record Sample(TrackPoint point, Long time, Double distance) {}

  /**
   * @throws ImportException if the file is not a FIT activity with a track
   */
  public static RecordedRide read(byte[] data) {
    List<Sample> samples = new ArrayList<>();
    Totals totals = new Totals();
    Long[] activity = {null, null}; // timestamp, local_timestamp
    boolean[] isActivity = {false};

    FitReader.decode(
        data,
        (global, m) -> {
          switch (global) {
            case 0 -> isActivity[0] = Long.valueOf(4).equals(m.uint(0)); // file_id.type: activity
            case FitReader.MESG_RECORD -> {
              Long distance = m.uint(5);
              samples.add(
                  new Sample(
                      m.position(m.altitude()),
                      m.time(),
                      distance == null ? null : distance / 100.0));
            }
            case FitReader.MESG_SESSION -> totals.add(m);
            case FitReader.MESG_ACTIVITY -> {
              activity[0] = m.time();
              activity[1] = m.uint(5);
            }
            default -> {}
          }
        });

    List<TrackPoint> track = samples.stream().map(Sample::point).filter(p -> p != null).toList();
    if (!isActivity[0] && totals.sessions == 0) {
      throw new ImportException("This FIT file is not a recorded ride.");
    }
    if (track.size() < 2) {
      throw new ImportException(NO_TRACK);
    }
    List<Long> times = samples.stream().map(Sample::time).filter(t -> t != null).toList();
    Long start = totals.start != null ? totals.start : times.isEmpty() ? null : times.getFirst();
    if (start == null) {
      throw new ImportException("The ride has no start time.");
    }
    double recordedElapsed = times.isEmpty() ? 0 : times.getLast() - times.getFirst();
    double distance = totals.distance != null ? totals.distance : TrackStats.distance(track);
    Integer utcOffset =
        activity[0] != null && activity[1] != null
            ? Math.toIntExact(activity[1] - activity[0])
            : null;
    return new RecordedRide(
        Instant.ofEpochSecond(start + FitReader.FIT_EPOCH_SECONDS),
        utcOffset,
        totals.sport,
        totals.elapsed != null ? totals.elapsed : recordedElapsed,
        movingTime(samples, totals.timer),
        distance,
        totals.ascent != null ? totals.ascent : TrackStats.ascent(track),
        totals.descent != null ? totals.descent : TrackStats.descent(track),
        totals.maxSpeed,
        totals.avgHeartRate,
        totals.maxHeartRate,
        totals.avgPower,
        totals.calories,
        track);
  }

  /**
   * Seconds spent moving: the time between records in which the rider covered ground at least at
   * walking pace. Without record times, the session's timer time (which excludes auto-pauses).
   */
  private static double movingTime(List<Sample> samples, Double timer) {
    double moving = 0;
    boolean any = false;
    Sample previous = null;
    for (Sample s : samples) {
      if (s.time() == null) continue;
      if (previous != null) {
        long gap = s.time() - previous.time();
        double covered = covered(previous, s);
        if (gap > 0 && gap <= MAX_GAP_S && !Double.isNaN(covered)) {
          any = true;
          if (covered / gap >= MOVING_SPEED) moving += gap;
        }
      }
      previous = s;
    }
    if (!any) return timer != null ? timer : 0;
    return moving;
  }

  private static double covered(Sample a, Sample b) {
    if (a.distance() != null && b.distance() != null) return b.distance() - a.distance();
    if (a.point() != null && b.point() != null) return a.point().distanceTo(b.point());
    return Double.NaN;
  }

  /** The totals of the session messages. */
  private static final class Totals {
    int sessions;
    Long start;
    String sport;
    Double elapsed;
    Double timer;
    Double distance;
    Double ascent;
    Double descent;
    Double maxSpeed;
    Integer avgHeartRate;
    Integer maxHeartRate;
    Integer avgPower;
    Integer calories;

    void add(FitReader.Message m) {
      sessions++;
      Long sessionStart = m.uint(2);
      if (sessionStart != null && (start == null || sessionStart < start)) start = sessionStart;
      if (sport == null) {
        Long code = m.uint(5);
        if (code != null) sport = SPORTS.getOrDefault(code, "other");
      }
      elapsed = sum(elapsed, m.uint(7), 1000);
      timer = sum(timer, m.uint(8), 1000);
      distance = sum(distance, m.uint(9), 100);
      ascent = sum(ascent, m.uint(22), 1);
      descent = sum(descent, m.uint(23), 1);
      Long speed = m.uint(124) != null ? m.uint(124) : m.uint(15); // enhanced first
      if (speed != null) maxSpeed = Math.max(maxSpeed == null ? 0 : maxSpeed, speed / 1000.0);
      avgHeartRate = first(avgHeartRate, m.uint(16));
      maxHeartRate = max(maxHeartRate, m.uint(17));
      avgPower = first(avgPower, m.uint(20));
      Long kcal = m.uint(11);
      if (kcal != null) calories = (calories == null ? 0 : calories) + kcal.intValue();
    }

    private static Double sum(Double total, Long value, double scale) {
      if (value == null) return total;
      return (total == null ? 0 : total) + value / scale;
    }

    private static Integer first(Integer current, Long value) {
      if (current != null || value == null) return current;
      return value.intValue();
    }

    private static Integer max(Integer current, Long value) {
      if (value == null) return current;
      if (current == null) return value.intValue();
      return Math.max(current, value.intValue());
    }
  }
}
