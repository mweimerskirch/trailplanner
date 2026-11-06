package app.trailplan.rides;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.TestHttp;
import app.trailplan.devices.MtpDevice;
import app.trailplan.devices.UsbSync;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

/** Importing, showing and managing recorded rides, against an in-memory device. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RideApiTest {

  /** A device whose rides folder is kept in memory; records which files were downloaded. */
  static class FakeDevice implements MtpDevice {
    final Map<String, byte[]> rides = new ConcurrentHashMap<>();
    final List<String> fetched = new ArrayList<>();

    @Override
    public Listing list(UsbSync target) {
      return new Listing("ELEMNT-BOLT2", Map.of());
    }

    @Override
    public String apply(UsbSync target, Map<String, byte[]> uploads, List<String> deletions) {
      return "ELEMNT-BOLT2";
    }

    @Override
    public FileList files(UsbSync target, String folder) {
      assertThat(folder).isEqualTo("exports");
      List<DeviceFile> files = new ArrayList<>();
      rides.forEach((name, data) -> files.add(new DeviceFile(name, data.length)));
      return new FileList("ELEMNT-BOLT2", files);
    }

    @Override
    public Map<String, byte[]> fetch(UsbSync target, String folder, List<String> names) {
      fetched.addAll(names);
      Map<String, byte[]> result = new LinkedHashMap<>();
      for (String name : names) {
        if (rides.containsKey(name)) result.put(name, rides.get(name));
      }
      return result;
    }
  }

  @TestConfiguration
  static class FakeDeviceConfig {
    @Bean
    @Primary
    FakeDevice fakeRideDevice() {
      return new FakeDevice();
    }
  }

  @DynamicPropertySource
  static void isolatedStorage(DynamicPropertyRegistry registry) throws IOException {
    Path dir = Files.createTempDirectory("trailplan-ride-test");
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  @LocalServerPort int port;
  @Autowired FakeDevice device;

  @BeforeEach
  void emptyDevice() {
    device.rides.clear();
    device.fetched.clear();
  }

  private static byte[] ride(String start, int seed) {
    return TestFit.ride(Instant.parse(start), 3600, seed);
  }

  private static long id(String body, String field) {
    return Long.parseLong(body.replaceAll("(?s).*?\"" + field + "\":(\\d+).*", "$1"));
  }

  @Test
  void importsRidesFromTheDeviceOnce() throws Exception {
    TestHttp http = new TestHttp(port);
    http.chooseNewRider("Device rider");
    device.rides.put(
        "2026-05-02-133000-ELEMNT BOLT A1B2-38-0.fit", ride("2026-05-02T13:30:00Z", 1));
    device.rides.put(
        "2026-10-26-064500-ELEMNT BOLT A1B2-39-0.fit", ride("2026-10-26T06:45:00Z", 2));
    device.rides.put("broken.fit", "not a fit file".getBytes());
    device.rides.put("notes.txt", "ignored".getBytes());
    device.rides.put("accident.fit", TestFit.withoutTrack(Instant.parse("2026-10-20T10:00:00Z")));

    var first = http.send("POST", "/api/rides/device/wahoo-elemnt", null);
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(first.body())
        .startsWith(
            "{\"device\":\"ELEMNT-BOLT2\",\"imported\":2,\"skipped\":0,\"withoutTrack\":1,"
                + "\"failed\":[")
        .contains("\"fileName\":\"broken.fit\",\"status\":\"FAILED\"");

    String list = http.get("/api/rides").body();
    // Newest first, named by the local start time.
    assertThat(list)
        .containsSubsequence("\"name\":\"Morning ride\"", "\"name\":\"Afternoon ride\"");
    assertThat(list)
        .contains("\"sport\":\"cycling\"")
        .contains("\"startedAt\":\"2026-05-02T13:30:00Z\"")
        .contains("\"utcOffset\":3600")
        .contains("\"moving\":80.0,\"distance\":400.0")
        .contains("\"avgSpeed\":5.0")
        .contains("\"avgPower\":null");

    // Known files are not downloaded again; a new one is.
    device.fetched.clear();
    device.rides.put(
        "2026-10-27-170000-ELEMNT BOLT A1B2-40-0.fit", ride("2026-10-27T17:00:00Z", 3));
    var second = http.send("POST", "/api/rides/device/wahoo-elemnt", null);
    assertThat(second.body()).contains("\"imported\":1,\"skipped\":2");
    assertThat(device.fetched)
        .containsExactlyInAnyOrder(
            "2026-10-27-170000-ELEMNT BOLT A1B2-40-0.fit", "broken.fit", "accident.fit");

    // A deleted ride does not come back from the device.
    long rideId = id(http.get("/api/rides").body(), "id");
    assertThat(http.send("DELETE", "/api/rides/" + rideId, null).statusCode()).isEqualTo(204);
    assertThat(http.get("/api/rides/" + rideId).statusCode()).isEqualTo(404);
    assertThat(http.send("POST", "/api/rides/device/wahoo-elemnt", null).body())
        .contains("\"imported\":0,\"skipped\":3");
    assertThat(http.get("/api/rides").body().split("\"id\":", -1)).hasSize(3);

    // Devices without a rides folder cannot import.
    assertThat(http.send("POST", "/api/rides/device/generic", null).statusCode()).isEqualTo(404);
  }

  @Test
  void uploadsShowsRenamesAndSavesRidesAsRoutes() throws Exception {
    TestHttp http = new TestHttp(port);
    http.chooseNewRider("Upload rider");
    byte[] fit = ride("2026-09-07T08:13:27Z", 4);

    var uploaded = http.upload("/api/rides/files", Map.of("morning.fit", fit));
    assertThat(uploaded.body())
        .contains("\"fileName\":\"morning.fit\",\"status\":\"IMPORTED\"")
        .contains("\"name\":\"Morning ride\"");
    long rideId = id(uploaded.body(), "rideId");
    assertThat(http.upload("/api/rides/files", Map.of("copy.fit", fit)).body())
        .contains("\"status\":\"ALREADY_IMPORTED\"")
        .contains("already imported");

    var details = http.get("/api/rides/" + rideId);
    assertThat(details.statusCode()).isEqualTo(200);
    assertThat(details.body())
        .contains("\"ride\":{\"id\":" + rideId)
        .contains("\"track\":[[6.1000")
        .endsWith("\"routeId\":null}");

    var renamed = http.send("PATCH", "/api/rides/" + rideId, "{\"name\":\"Grunewiese loop\"}");
    assertThat(renamed.body()).contains("\"name\":\"Grunewiese loop\"");
    assertThat(http.send("PATCH", "/api/rides/" + rideId, "{\"name\":\" \"}").statusCode())
        .isEqualTo(400);

    // Saving as a route: once; the ride then points to it.
    var saved = http.send("POST", "/api/rides/" + rideId + "/route", null);
    assertThat(saved.body())
        .contains("\"status\":\"IMPORTED\"")
        .contains("\"name\":\"Grunewiese loop\"");
    long routeId = id(saved.body(), "routeId");
    assertThat(http.get("/api/routes/" + routeId).body()).contains("\"source\":\"RIDE\"");
    assertThat(http.send("POST", "/api/rides/" + rideId + "/route", null).body())
        .contains("\"status\":\"ALREADY_IMPORTED\"");
    assertThat(http.get("/api/rides/" + rideId).body()).endsWith("\"routeId\":" + routeId + "}");

    // The original file can be downloaded.
    var file = http.get("/api/rides/" + rideId + "/file");
    assertThat(file.headers().firstValue("Content-Disposition").orElseThrow())
        .contains("morning.fit");

    // Deleted, then uploaded again: restored.
    http.send("DELETE", "/api/rides/" + rideId, null);
    assertThat(http.upload("/api/rides/files", Map.of("morning.fit", fit)).body())
        .contains("deleted and is restored");
    assertThat(http.get("/api/rides/" + rideId).statusCode()).isEqualTo(200);

    // Other riders do not see it.
    TestHttp other = new TestHttp(port);
    other.chooseNewRider("Someone else");
    assertThat(other.get("/api/rides/" + rideId).statusCode()).isEqualTo(404);
    assertThat(other.get("/api/rides").body()).isEqualTo("[]");
  }
}
