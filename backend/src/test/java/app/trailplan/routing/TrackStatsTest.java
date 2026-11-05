package app.trailplan.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class TrackStatsTest {

  private static TrackPoint p(double lng, Double ele) {
    return new TrackPoint(lng, 49.6, ele);
  }

  @Test
  void climbingIgnoresNoiseBelowThreeMetres() {
    List<TrackPoint> track =
        List.of(
            p(6.10, 300.0),
            p(6.11, 302.0),
            p(6.12, 301.0),
            p(6.13, 350.0),
            p(6.14, null),
            p(6.15, 320.0));

    assertThat(TrackStats.ascent(track)).isEqualTo(50);
    assertThat(TrackStats.descent(track)).isEqualTo(30);
  }

  @Test
  void distanceFollowsTheTrack() {
    // 0.01° of longitude at 49.6° N is about 721 m.
    assertThat(TrackStats.distance(List.of(p(6.10, null), p(6.11, null), p(6.12, null))))
        .isCloseTo(1442, within(5.0));
  }
}
