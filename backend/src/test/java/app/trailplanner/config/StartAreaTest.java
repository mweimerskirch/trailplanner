package app.trailplanner.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StartAreaTest {

  @Test
  void parsesLatitudeLongitudeAndOptionalZoom() {
    assertThat(StartArea.parse("46.95, 7.45, 12")).isEqualTo(new StartArea(46.95, 7.45, 12));
    assertThat(StartArea.parse("-33.9,18.4")).isEqualTo(new StartArea(-33.9, 18.4, 10));
    assertThat(StartArea.parse("")).isEqualTo(StartArea.DEFAULT);
    assertThat(StartArea.parse(null)).isEqualTo(StartArea.DEFAULT);
  }

  @Test
  void rejectsWhatIsNotAPlace() {
    assertThatThrownBy(() -> StartArea.parse("Bern")).hasMessageContaining("TRAILPLANNER_AREA");
    assertThatThrownBy(() -> StartArea.parse("7.45")).hasMessageContaining("TRAILPLANNER_AREA");
    assertThatThrownBy(() -> StartArea.parse("200,7.45")).hasMessageContaining("TRAILPLANNER_AREA");
  }
}
