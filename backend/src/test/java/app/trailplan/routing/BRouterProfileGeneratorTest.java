package app.trailplan.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BRouterProfileGeneratorTest {

  private final BRouterProfileGenerator generator = new BRouterProfileGenerator();

  @Test
  void writesCostFactorsForEachCategory() {
    var prefs =
        new RoutingPreferences(
            Map.of(
                WayCategory.TRAIL,
                PreferenceLevel.FAVOURITE,
                WayCategory.BUSY_ROAD,
                PreferenceLevel.NEVER),
            TrailDifficulty.S3,
            WoodsPreference.INDIFFERENT,
            false);

    String profile = generator.generate(prefs);

    assertThat(profile)
        .contains("switch highway=path|bridleway 1\n")
        .contains("switch highway=trunk|trunk_link|primary|primary_link 10000\n")
        .contains("assign forestmult 1\n")
        .contains("assign uphillcost 0\n");
  }

  @Test
  void blocksTrailsAboveTheChosenDifficultyIncludingPlusMinusGrades() {
    var prefs = new RoutingPreferences(Map.of(), TrailDifficulty.S1, null, false);

    assertThat(generator.generate(prefs)).contains("assign toohard mtb:scale=2-|2|2+|3|4|5|6\n");
  }

  @Test
  void noDifficultyLimitForAny() {
    var prefs = new RoutingPreferences(Map.of(), TrailDifficulty.ANY, null, false);

    assertThat(generator.generate(prefs)).contains("assign toohard 0\n");
  }

  @Test
  void penalisesWaysOutsideForestWhenWoodsArePreferred() {
    var prefs = new RoutingPreferences(Map.of(), null, WoodsPreference.STRONGLY_PREFER, true);

    assertThat(generator.generate(prefs))
        .contains("assign forestmult switch estimated_forest_class=3|4|5|6 1 2.2\n")
        .contains("assign uphillcost 80\n");
  }

  @Test
  void identicalPreferencesGiveIdenticalProfiles() {
    assertThat(generator.generate(RoutingPreferences.defaults()))
        .isEqualTo(generator.generate(new RoutingPreferences(null, null, null, false)));
  }

  @Test
  void forcedProfilesAllowClosedAndRestrictedWaysAtACost() {
    String normal = generator.generate(RoutingPreferences.defaults());
    String forced = generator.generate(RoutingPreferences.defaults(), true);

    assertThat(normal)
        .contains("switch or blocked badoneway 10000\n")
        .doesNotContain("construction");
    assertThat(forced)
        .contains("switch highway=construction 8\n")
        .contains("switch or blocked badoneway 8\n")
        .contains("switch highway=motorway|motorway_link 10000\n");
  }

  @Test
  void mountainBikeIsTheDefaultActivity() {
    assertThat(RoutingPreferences.defaults().activity()).isEqualTo(Activity.MTB);
    assertThat(generator.generate(RoutingPreferences.defaults()))
        .isEqualTo(generator.generate(withActivity(Activity.MTB)))
        .doesNotContain("surfacemult")
        .doesNotContain("horse");
  }

  @Test
  void roadBikesStayOnPavedWaysAndOffGradedTrails() {
    String profile = generator.generate(withActivity(Activity.ROAD));

    assertThat(profile)
        .contains("assign rough or surface=unpaved|gravel|ground")
        .contains("|grass_paver or smoothness=bad|")
        .contains("and highway=track and surface= not tracktype=grade1")
        .contains("  switch rough 10000\n")
        .contains("  switch surface=compacted|fine_gravel 3\n")
        .contains("assign toohard not mtb:scale=\n")
        .contains("  switch highway=path switch bicycle=designated ")
        .contains("  switch highway=steps 15\n")
        .contains("assign forestmult 1\n")
        .contains("  multiply multiply typecost surfacemult trafficmult\n");
  }

  @Test
  void horsesFollowHorseAccessAndCannotUseStepsOrStiles() {
    String profile = generator.generate(withActivity(Activity.HORSE));

    assertThat(profile)
        .contains("assign horseno horse=no|private\n")
        .contains("assign notforhorses and highway=footway|pedestrian|cycleway not horseyes\n")
        .contains("assign blocked or or horseno notforhorses and accessno not horseyes\n")
        .doesNotContain("bicycle")
        .contains("  switch highway=trunk|trunk_link 10000\n")
        .contains("  switch highway=steps 10000\n")
        .contains("assign toohard sac_scale=demanding_mountain_hiking|")
        .contains("  switch surface=asphalt|concrete|paved 1.3\n")
        .contains("assign initialcost switch and barrier=stile|");
  }

  private static RoutingPreferences withActivity(Activity activity) {
    return new RoutingPreferences(null, null, null, false, activity);
  }
}
