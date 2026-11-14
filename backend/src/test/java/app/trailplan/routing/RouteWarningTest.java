package app.trailplan.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteWarningTest {

  @Test
  void bikesAreStoppedByBikeTags() {
    assertThat(RouteWarning.reasonFor(Map.of("highway", "path", "bicycle", "no")))
        .isEqualTo("No bikes allowed");
    assertThat(RouteWarning.reasonFor(Map.of("highway", "footway", "horse", "no"))).isNull();
  }

  @Test
  void horsesAreStoppedByHorseTagsFootwaysAndCycleways() {
    assertThat(RouteWarning.reasonFor(Map.of("highway", "path", "horse", "no"), Activity.HORSE))
        .isEqualTo("No horses allowed");
    assertThat(RouteWarning.reasonFor(Map.of("highway", "cycleway"), Activity.HORSE))
        .isEqualTo("Footway or cycleway, not open to horses");
    assertThat(
            RouteWarning.reasonFor(
                Map.of("highway", "footway", "horse", "designated"), Activity.HORSE))
        .isNull();
    assertThat(
            RouteWarning.reasonFor(
                Map.of("highway", "track", "access", "no", "horse", "yes"), Activity.HORSE))
        .isNull();
    assertThat(RouteWarning.reasonFor(Map.of("highway", "path", "bicycle", "no"), Activity.HORSE))
        .isNull();
  }
}
