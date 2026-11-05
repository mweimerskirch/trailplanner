package app.trailplan.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WayCategoryTest {

  @Test
  void classifiesTracksByGrade() {
    assertThat(WayCategory.classify(Map.of("highway", "track", "tracktype", "grade2")))
        .contains(WayCategory.GRAVEL_TRACK);
    assertThat(WayCategory.classify(Map.of("highway", "track", "tracktype", "grade4")))
        .contains(WayCategory.ROUGH_TRACK);
    assertThat(WayCategory.classify(Map.of("highway", "track"))).contains(WayCategory.ROUGH_TRACK);
  }

  @Test
  void classifiesRoadsAndTrails() {
    assertThat(WayCategory.classify(Map.of("highway", "bridleway"))).contains(WayCategory.TRAIL);
    assertThat(WayCategory.classify(Map.of("highway", "service"))).contains(WayCategory.QUIET_ROAD);
    assertThat(WayCategory.classify(Map.of("highway", "tertiary_link")))
        .contains(WayCategory.MAIN_ROAD);
    assertThat(WayCategory.classify(Map.of("highway", "trunk"))).contains(WayCategory.BUSY_ROAD);
    assertThat(WayCategory.classify(Map.of("route", "ferry"))).isEmpty();
  }

  @Test
  void classifiesSurfaces() {
    assertThat(Surface.classify("asphalt")).isEqualTo(Surface.PAVED);
    assertThat(Surface.classify("ground")).isEqualTo(Surface.UNPAVED);
    assertThat(Surface.classify(null)).isEqualTo(Surface.UNKNOWN);
  }
}
