package app.trailplan.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BRouterResponseParserTest {

  private static final String GEOJSON =
      """
      {"type":"FeatureCollection","features":[{"type":"Feature","properties":{
        "track-length":"1500","filtered ascend":"42",
        "messages":[
          ["Longitude","Latitude","Elevation","Distance","CostPerKm","ElevCost","TurnCost","NodeCost","InitialCost","WayTags","NodeTags","Time","Energy"],
          ["6130000","49610000","300","1000","1000","0","0","0","0","highway=path surface=ground","","0","0"],
          ["6140000","49620000","320","500","6000","0","0","0","0","highway=residential surface=asphalt","","0","0"]
        ]},
        "geometry":{"type":"LineString","coordinates":[[6.13,49.61,300.0],[6.135,49.615],[6.14,49.62,320.0]]}}]}
      """;

  private final BRouterResponseParser parser = new BRouterResponseParser(new ObjectMapper());

  @Test
  void readsTrackStatsAndBreakdown() {
    RouteLeg leg = parser.parse(GEOJSON);

    assertThat(leg.points()).hasSize(3);
    assertThat(leg.points().get(1).ele()).isNull();
    assertThat(leg.distance()).isEqualTo(1500);
    assertThat(leg.ascent()).isEqualTo(42);
    assertThat(leg.breakdown().wayTypes())
        .containsEntry(WayCategory.TRAIL, 1000.0)
        .containsEntry(WayCategory.QUIET_ROAD, 500.0);
    assertThat(leg.breakdown().surfaces())
        .containsEntry(Surface.UNPAVED, 1000.0)
        .containsEntry(Surface.PAVED, 500.0);
  }

  @Test
  void mapsRouterErrorsToRiderMessages() {
    assertThat(
            BRouterClient.fromRouterMessage("from-position not mapped in existing datafile", null)
                .reason())
        .isEqualTo(RoutingException.Reason.POINT_NOT_ON_MAP);
    assertThat(BRouterClient.fromRouterMessage("datafile E10_N45.rd5 not found", null).reason())
        .isEqualTo(RoutingException.Reason.POINT_NOT_ON_MAP);
    assertThat(BRouterClient.fromRouterMessage("target island detected", null).reason())
        .isEqualTo(RoutingException.Reason.NO_ROUTE);
    assertThatThrownBy(() -> parser.parse("{\"features\":[]}"))
        .isInstanceOf(RoutingException.class);
  }
}
