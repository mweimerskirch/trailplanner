package app.trailplan.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** The frontend and the database rely on these exact JSON shapes. */
class JsonMappingTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void trackPointsAreCompactArraysAndRoundTrip() throws Exception {
    List<TrackPoint> track =
        List.of(new TrackPoint(6.1, 49.6, 310.5), new TrackPoint(6.2, 49.7, null));

    String written = json.writeValueAsString(track);

    assertThat(written).isEqualTo("[[6.1,49.6,310.5],[6.2,49.7,null]]");
    assertThat(json.readValue(written, new TypeReference<List<TrackPoint>>() {})).isEqualTo(track);
  }

  @Test
  void partialPreferencesAreCompletedWithDefaults() throws Exception {
    RoutingPreferences prefs =
        json.readValue(
            "{\"levels\":{\"TRAIL\":\"NEVER\"},\"avoidClimbs\":true}", RoutingPreferences.class);

    assertThat(prefs.level(WayCategory.TRAIL)).isEqualTo(PreferenceLevel.NEVER);
    assertThat(prefs.level(WayCategory.BUSY_ROAD)).isEqualTo(WayCategory.BUSY_ROAD.defaultLevel());
    assertThat(prefs.maxDifficulty()).isEqualTo(TrailDifficulty.S3);
    assertThat(prefs.avoidClimbs()).isTrue();
  }

  @Test
  void emptyPreferencesAreAllDefaults() throws Exception {
    assertThat(json.readValue("{}", RoutingPreferences.class))
        .isEqualTo(RoutingPreferences.defaults());
  }
}
