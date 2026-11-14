package app.trailplanner.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import app.trailplanner.export.FitCourseWriter;
import app.trailplanner.imports.ImportException;
import app.trailplanner.routing.TrackPoint;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RideFileReaderTest {

  private static final Instant START = Instant.parse("2026-05-02T13:30:00Z");

  @Test
  void readsTheSessionTotalsAndTheTrack() {
    RecordedRide ride = RideFileReader.read(TestFit.ride(START, 3600, 0));

    assertThat(ride.startedAt()).isEqualTo(START);
    assertThat(ride.utcOffset()).isEqualTo(3600);
    assertThat(ride.sport()).isEqualTo("cycling");
    assertThat(ride.elapsed()).isEqualTo(100.0);
    assertThat(ride.moving()).isEqualTo(80.0); // the 20 s stop does not count
    assertThat(ride.distance()).isEqualTo(400.0);
    assertThat(ride.ascent()).isEqualTo(21.0);
    assertThat(ride.descent()).isEqualTo(2.0);
    assertThat(ride.maxSpeed()).isEqualTo(9.5);
    assertThat(ride.avgHeartRate()).isEqualTo(140);
    assertThat(ride.maxHeartRate()).isEqualTo(171);
    assertThat(ride.avgPower()).isNull();
    assertThat(ride.calories()).isNull(); // recorded as "invalid"
    assertThat(ride.track()).hasSize(101);
    assertThat(ride.track().getFirst().lat()).isCloseTo(49.60, within(1e-6));
    assertThat(ride.track().getFirst().ele()).isCloseTo(300.0, within(0.2));
  }

  @Test
  void namesRidesByTheirLocalStartTime() {
    RecordedRide ride = RideFileReader.read(TestFit.ride(START, 3600, 0));
    assertThat(RideService.defaultName(ride)).isEqualTo("Afternoon ride"); // 14:30 local

    RecordedRide early =
        RideFileReader.read(TestFit.ride(Instant.parse("2026-07-01T05:30:00Z"), 7200, 0));
    assertThat(RideService.defaultName(early)).isEqualTo("Morning ride"); // 07:30 local
  }

  @Test
  void rejectsCoursesAndOtherFiles() {
    byte[] course =
        new FitCourseWriter()
            .write(
                "A course",
                List.of(new TrackPoint(6.13, 49.61, 1.0), new TrackPoint(6.14, 49.62, 2.0)),
                0,
                0,
                Instant.EPOCH);
    assertThatThrownBy(() -> RideFileReader.read(course))
        .isInstanceOf(ImportException.class)
        .hasMessageContaining("not a recorded ride");
    assertThatThrownBy(() -> RideFileReader.read("<gpx/>".getBytes()))
        .isInstanceOf(ImportException.class);
  }
}
