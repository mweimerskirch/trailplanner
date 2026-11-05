package app.trailplan.export;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.routing.TrackPoint;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FitCourseWriterTest {

  @Test
  void writesAValidFitContainer() {
    List<TrackPoint> points =
        List.of(
            new TrackPoint(6.13, 49.61, 300.0),
            new TrackPoint(6.14, 49.62, null),
            new TrackPoint(6.15, 49.63, 320.0));

    byte[] fit =
        new FitCourseWriter()
            .write("Test route", points, 20, 0, Instant.parse("2026-01-01T00:00:00Z"));

    assertThat(fit[0]).isEqualTo((byte) 14);
    assertThat(new String(fit, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo(".FIT");
    int dataSize =
        (fit[4] & 0xFF) | (fit[5] & 0xFF) << 8 | (fit[6] & 0xFF) << 16 | (fit[7] & 0xFF) << 24;
    assertThat(fit.length).isEqualTo(14 + dataSize + 2);
    // A FIT file including its trailing CRC has a CRC of zero.
    assertThat(FitCourseWriter.crc(0, fit, 0, fit.length)).isZero();
  }
}
