package app.trailplanner.devices;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplanner.TestHttp;
import app.trailplanner.routes.RouteDraft;
import app.trailplanner.routes.RouteRepository;
import app.trailplanner.routing.LegBreakdown;
import app.trailplanner.routing.PlannedRoute;
import app.trailplanner.routing.RoutingPreferences;
import app.trailplanner.routing.TrackPoint;
import app.trailplanner.routing.Waypoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Syncing active routes to a USB device, against an in-memory device. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceSyncApiTest {

  /** A device with one folder, kept in memory. */
  static class FakeDevice implements MtpDevice {
    final Map<String, byte[]> files = new ConcurrentHashMap<>();
    final AtomicReference<UsbException> failure = new AtomicReference<>();
    int applyCalls;

    @Override
    public Listing list(UsbSync target) {
      UsbException e = failure.get();
      if (e != null) throw e;
      return new Listing("ELEMNT-BOLT2", files);
    }

    @Override
    public String apply(UsbSync target, Map<String, byte[]> uploads, List<String> deletions) {
      applyCalls++;
      files.putAll(uploads);
      deletions.forEach(files::remove);
      return "ELEMNT-BOLT2";
    }

    @Override
    public FileList files(UsbSync target, String folder) {
      return new FileList("ELEMNT-BOLT2", List.of());
    }

    @Override
    public Map<String, byte[]> fetch(UsbSync target, String folder, List<String> names) {
      return Map.of();
    }
  }

  @TestConfiguration
  static class FakeDeviceConfig {
    @Bean
    @Primary
    FakeDevice fakeDevice() {
      return new FakeDevice();
    }
  }

  @DynamicPropertySource
  static void isolatedStorage(DynamicPropertyRegistry registry) throws IOException {
    Path dir = Files.createTempDirectory("trailplanner-sync-test");
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add(
        "trailplanner.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  @LocalServerPort int port;
  @Autowired RouteRepository routes;
  @Autowired FakeDevice device;

  @BeforeEach
  void emptyDevice() {
    device.files.clear();
    device.failure.set(null);
    device.applyCalls = 0;
  }

  private long route(long rider, String name) {
    return routes.insert(
        rider,
        new RouteDraft(
            name,
            List.of(Waypoint.of(49.61, 6.13), Waypoint.of(49.62, 6.14)),
            RoutingPreferences.defaults()),
        new PlannedRoute(
            List.of(new TrackPoint(6.13, 49.61, 300.0), new TrackPoint(6.14, 49.62, 320.0)),
            1500,
            20,
            0,
            LegBreakdown.empty()),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void mirrorsActiveRoutesAndDeletesOnlyConfirmedFiles() throws Exception {
    TestHttp http = new TestHttp(port);
    long rider = http.chooseNewRider("Sync rider");
    route(rider, "Wëllmillen");
    long twin = route(rider, "Wëllmillen"); // same file name: gets its id appended
    long archived = route(rider, "Old loop");
    routes.setArchived(rider, archived, Instant.now());
    route(rider, "Hirtenbrunnen");

    device.files.put("Hirtenbrunnen.gpx", bytes("an older version"));
    device.files.put("Komoot_tour.gpx", bytes("not from Trail Planner"));
    device.files.put(".index", bytes("the device's own"));

    var plan = http.get("/api/devices/wahoo-elemnt/sync");
    assertThat(plan.statusCode()).isEqualTo(200);
    assertThat(plan.body())
        .contains("\"device\":\"ELEMNT-BOLT2\"")
        .contains(
            "\"add\":[{\"fileName\":\"Wellmillen.gpx\",\"routeName\":\"Wëllmillen\"},"
                + "{\"fileName\":\"Wellmillen_"
                + twin
                + ".gpx\",\"routeName\":\"Wëllmillen\"}]")
        .contains(
            "\"update\":[{\"fileName\":\"Hirtenbrunnen.gpx\",\"routeName\":\"Hirtenbrunnen\"}]")
        .contains("\"remove\":[{\"fileName\":\"Komoot_tour.gpx\",\"routeName\":null}]")
        .contains("\"unchanged\":0")
        .doesNotContain("Old_loop");
    assertThat(device.applyCalls).isZero(); // the plan changes nothing

    // Without confirmation, files that are not active routes stay.
    var kept = http.send("POST", "/api/devices/wahoo-elemnt/sync", "{\"remove\":[]}");
    assertThat(kept.body())
        .isEqualTo(
            "{\"device\":\"ELEMNT-BOLT2\",\"added\":2,\"updated\":1,\"removed\":0,\"unchanged\":0}");
    assertThat(device.files)
        .containsOnlyKeys(
            "Wellmillen.gpx",
            "Wellmillen_" + twin + ".gpx",
            "Hirtenbrunnen.gpx",
            "Komoot_tour.gpx",
            ".index");
    assertThat(new String(device.files.get("Hirtenbrunnen.gpx"), StandardCharsets.UTF_8))
        .contains("<name>Hirtenbrunnen</name>");
    // The ELEMNT shows only plain ASCII names; Trail Planner keeps the accents.
    assertThat(new String(device.files.get("Wellmillen.gpx"), StandardCharsets.UTF_8))
        .contains("<name>Wellmillen</name>");

    // Confirmed deletions apply only to files that are not active routes (or the device's own).
    var removed =
        http.send(
            "POST",
            "/api/devices/wahoo-elemnt/sync",
            "{\"remove\":[\"Komoot_tour.gpx\",\"Hirtenbrunnen.gpx\",\".index\"]}");
    assertThat(removed.body())
        .isEqualTo(
            "{\"device\":\"ELEMNT-BOLT2\",\"added\":0,\"updated\":0,\"removed\":1,\"unchanged\":3}");
    assertThat(device.files)
        .containsOnlyKeys(
            "Wellmillen.gpx", "Wellmillen_" + twin + ".gpx", "Hirtenbrunnen.gpx", ".index");

    // Up to date: nothing is written.
    int calls = device.applyCalls;
    assertThat(http.send("POST", "/api/devices/wahoo-elemnt/sync", "{}").body())
        .contains("\"added\":0,\"updated\":0,\"removed\":0,\"unchanged\":3");
    assertThat(device.applyCalls).isEqualTo(calls);

    // Archiving a route offers its file for deletion.
    routes.setArchived(rider, twin, Instant.now());
    assertThat(http.get("/api/devices/wahoo-elemnt/sync").body())
        .contains("\"remove\":[{\"fileName\":\"Wellmillen_" + twin + ".gpx\"");
  }

  @Test
  void explainsDeviceProblemsAndUnsupportedDevices() throws Exception {
    TestHttp http = new TestHttp(port);
    http.chooseNewRider("Sync problems");

    device.failure.set(UsbException.noDevice("ELEMNT"));
    var none = http.get("/api/devices/wahoo-elemnt/sync");
    assertThat(none.statusCode()).isEqualTo(409);
    assertThat(none.body()).contains("\"reason\":\"NO_DEVICE\"").contains("No ELEMNT found.");

    device.failure.set(UsbException.busy("ELEMNT"));
    assertThat(http.send("POST", "/api/devices/wahoo-elemnt/sync", "{}").body())
        .contains("\"reason\":\"DEVICE_BUSY\"");

    assertThat(http.get("/api/devices/generic/sync").statusCode()).isEqualTo(404);
    assertThat(http.get("/api/devices/nope/sync").statusCode()).isEqualTo(404);
    assertThat(http.get("/api/devices").body())
        .contains(
            "\"usbSync\":{\"label\":\"ELEMNT\",\"folder\":\"routes\",\"afterSync\":\"On the ELEMNT,")
        .contains("\"removalNote\":\"The ELEMNT keeps routes it has already loaded")
        .contains("\"importsRides\":true")
        .contains("\"usbSync\":null");
  }
}
