package app.trailplan.export;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.routing.TrackPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class GpxWriterTest {

  @Test
  void escapesNamesAndOmitsMissingElevation() {
    String gpx =
        new GpxWriter()
            .write(
                "Loop <A & B>",
                List.of(new TrackPoint(6.1, 49.6, 310.25), new TrackPoint(6.2, 49.7, null)));

    assertThat(gpx)
        .contains("<name>Loop &lt;A &amp; B&gt;</name>")
        .contains("<trkpt lat=\"49.600000\" lon=\"6.100000\"><ele>310.3</ele></trkpt>")
        .contains("<trkpt lat=\"49.700000\" lon=\"6.200000\"></trkpt>");
  }

  @Test
  void makesSafeFileNames() {
    assertThat(FileNames.forRoute("Grünewiese Runde #2!", "gpx"))
        .isEqualTo("Grunewiese_Runde_2.gpx");
    assertThat(FileNames.forRoute("???", "fit")).isEqualTo("route.fit");
  }
}
