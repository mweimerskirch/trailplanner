package app.trailplanner.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.trailplanner.routing.TrackPoint;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GpxReaderTest {

  private final GpxReader reader = new GpxReader();

  private ParsedTrack read(String xml) {
    return reader.read(xml.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void readsTrackPointsFromAllSegmentsWithElevation() {
    ParsedTrack track =
        read(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="komoot" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Metadata name</name></metadata>
              <wpt lat="1" lon="1"><name>Not a track point</name></wpt>
              <trk>
                <name>Grünewiese Runde</name>
                <trkseg>
                  <trkpt lat="49.61" lon="6.13"><ele>300.5</ele><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="49.62" lon="6.14"></trkpt>
                </trkseg>
                <trkseg><trkpt lat="49.63" lon="6.15"><ele>320</ele></trkpt></trkseg>
              </trk>
            </gpx>
            """);

    assertThat(track.name()).isEqualTo("Grünewiese Runde");
    assertThat(track.points())
        .containsExactly(
            new TrackPoint(6.13, 49.61, 300.5),
            new TrackPoint(6.14, 49.62, null),
            new TrackPoint(6.15, 49.63, 320.0));
  }

  @Test
  void fallsBackToRoutePointsAndOtherNames() {
    ParsedTrack track =
        read(
            """
            <gpx version="1.0"><metadata><name>Fallback</name></metadata>
              <rte><rtept lat="49.61" lon="6.13"/><rtept lat="49.62" lon="6.14"/></rte></gpx>
            """);

    assertThat(track.name()).isEqualTo("Fallback");
    assertThat(track.points()).hasSize(2);
  }

  @Test
  void rejectsExternalEntities() {
    String xxe =
        """
        <?xml version="1.0"?>
        <!DOCTYPE gpx [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
        <gpx><trk><name>&secret;</name><trkseg>
          <trkpt lat="49.61" lon="6.13"/><trkpt lat="49.62" lon="6.14"/></trkseg></trk></gpx>
        """;

    // Either the parser refuses the DTD, or the entity stays unresolved; it must never be read.
    try {
      assertThat(read(xxe).name()).doesNotContain("root:");
    } catch (ImportException e) {
      assertThat(e).hasMessageContaining("GPX");
    }
  }

  @Test
  void rejectsNonXml() {
    assertThatThrownBy(() -> read("not xml at all")).isInstanceOf(ImportException.class);
  }
}
