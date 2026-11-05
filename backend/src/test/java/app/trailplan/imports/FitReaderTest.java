package app.trailplan.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.trailplan.export.FitCourseWriter;
import app.trailplan.routing.TrackPoint;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FitReaderTest {

  private final FitReader reader = new FitReader();

  @Test
  void readsBackACourseWrittenByOurWriter() {
    List<TrackPoint> points =
        List.of(
            new TrackPoint(6.13, 49.61, 300.0),
            new TrackPoint(6.14, 49.62, null),
            new TrackPoint(6.15, 49.63, 320.4));
    byte[] fit =
        new FitCourseWriter()
            .write("Komoot Runde", points, 20, 0, Instant.parse("2026-01-01T00:00:00Z"));

    ParsedTrack track = reader.read(fit);

    assertThat(track.name()).isEqualTo("Komoot Runde");
    assertThat(track.points()).hasSize(3);
    for (int i = 0; i < 3; i++) {
      assertThat(track.points().get(i).lat()).isCloseTo(points.get(i).lat(), within());
      assertThat(track.points().get(i).lng()).isCloseTo(points.get(i).lng(), within());
    }
    assertThat(track.points().get(0).ele()).isEqualTo(300.0);
    assertThat(track.points().get(1).ele()).isNull();
    assertThat(track.points().get(2).ele()).isEqualTo(320.4);
  }

  @Test
  void readsBigEndianRecordsWithEnhancedAltitudeAndSkipsPositionlessOnes() {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    // Definition, local type 0, big endian, global 20 (record): lat, long, enhanced_altitude.
    body.writeBytes(
        new byte[] {
          0x40, 0, 1, 0, 20, 3, 0, 4, (byte) 0x85, 1, 4, (byte) 0x85, 78, 4, (byte) 0x86
        });
    body.writeBytes(record(49.61, 6.13, 412.2));
    body.writeBytes(recordWithoutPosition());
    body.writeBytes(record(49.62, 6.14, 415.0));
    byte[] fit = file(body.toByteArray());

    ParsedTrack track = reader.read(fit);

    assertThat(track.name()).isNull();
    assertThat(track.points()).hasSize(2);
    assertThat(track.points().get(0).ele()).isCloseTo(412.2, within());
    assertThat(track.points().get(1).lat()).isCloseTo(49.62, within());
  }

  @Test
  void rejectsOtherFilesAndTruncatedOnes() {
    assertThatThrownBy(() -> reader.read("<gpx/>".getBytes())).isInstanceOf(ImportException.class);

    byte[] fit =
        new FitCourseWriter()
            .write(
                "x",
                List.of(new TrackPoint(6.13, 49.61, 1.0), new TrackPoint(6.14, 49.62, 2.0)),
                0,
                0,
                Instant.EPOCH);
    byte[] truncated = Arrays.copyOf(fit, fit.length - 12);
    assertThatThrownBy(() -> reader.read(truncated)).isInstanceOf(ImportException.class);
  }

  private static org.assertj.core.data.Offset<Double> within() {
    return org.assertj.core.data.Offset.offset(1e-6);
  }

  private static byte[] record(double lat, double lng, double ele) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(0x00); // data message, local type 0
    writeBigEndian(out, Math.round(lat * 2147483648.0 / 180.0));
    writeBigEndian(out, Math.round(lng * 2147483648.0 / 180.0));
    writeBigEndian(out, Math.round((ele + 500) * 5));
    return out.toByteArray();
  }

  private static byte[] recordWithoutPosition() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(0x00);
    writeBigEndian(out, 0x7FFFFFFFL);
    writeBigEndian(out, 0x7FFFFFFFL);
    writeBigEndian(out, 0xFFFFFFFFL);
    return out.toByteArray();
  }

  private static void writeBigEndian(ByteArrayOutputStream out, long value) {
    for (int shift = 24; shift >= 0; shift -= 8) {
      out.write((int) (value >>> shift) & 0xFF);
    }
  }

  /** Wraps records in a 14-byte header (CRCs are not checked by the reader). */
  private static byte[] file(byte[] records) {
    byte[] file = new byte[14 + records.length + 2];
    file[0] = 14;
    file[1] = 0x20;
    for (int i = 0; i < 4; i++) {
      file[4 + i] = (byte) (records.length >>> (8 * i));
    }
    file[8] = '.';
    file[9] = 'F';
    file[10] = 'I';
    file[11] = 'T';
    System.arraycopy(records, 0, file, 14, records.length);
    return file;
  }
}
