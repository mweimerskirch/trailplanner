package app.trailplanner.routing;

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
                .routingReason())
        .isEqualTo(RoutingException.Reason.POINT_NOT_ON_MAP);
    assertThat(
            BRouterClient.fromRouterMessage("datafile E10_N45.rd5 not found", null).routingReason())
        .isEqualTo(RoutingException.Reason.POINT_NOT_ON_MAP);
    assertThat(BRouterClient.fromRouterMessage("target island detected", null).routingReason())
        .isEqualTo(RoutingException.Reason.NO_ROUTE);
    assertThatThrownBy(() -> parser.parse("{\"features\":[]}"))
        .isInstanceOf(RoutingException.class);
  }

  @Test
  void reportsClosedAndRestrictedWaysAsWarnings() {
    String geoJson =
        """
        {"type":"FeatureCollection","features":[{"type":"Feature","properties":{
          "track-length":"900","filtered ascend":"0",
          "messages":[
            ["Longitude","Latitude","Elevation","Distance","CostPerKm","ElevCost","TurnCost","NodeCost","InitialCost","WayTags","NodeTags","Time","Energy"],
            ["6130000","49610000","300","300","1000","0","0","0","0","highway=track","","0","0"],
            ["6131000","49611000","300","200","8000","0","0","0","0","highway=construction construction=secondary","","0","0"],
            ["6132000","49612000","300","100","8000","0","0","0","0","highway=construction construction=secondary surface=asphalt","","0","0"],
            ["6133000","49613000","300","100","8000","0","0","0","0","highway=path access=private bicycle=yes","","0","0"],
            ["6134000","49614000","300","200","8000","0","0","0","0","highway=service access=private","","0","0"]
          ]},
          "geometry":{"type":"LineString","coordinates":[[6.13,49.61],[6.134,49.614]]}}]}
        """;

    RouteLeg leg = parser.parse(geoJson);

    // One warning per stretch: the two construction rows are one stretch; bicycle=yes overrides.
    assertThat(leg.warnings())
        .containsExactly(
            new RouteWarning(6.131, 49.611, RouteWarning.Reason.UNDER_CONSTRUCTION),
            new RouteWarning(6.134, 49.614, RouteWarning.Reason.PRIVATE));
  }
}
