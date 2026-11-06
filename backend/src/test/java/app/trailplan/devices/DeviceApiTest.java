package app.trailplan.devices;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.TestHttp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The generic device API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceApiTest {

  @DynamicPropertySource
  static void isolatedStorage(DynamicPropertyRegistry registry) throws IOException {
    Path dir = Files.createTempDirectory("trailplan-device-test");
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("test.db"));
    registry.add("trailplan.router.custom-profile-dir", () -> dir.resolve("profiles").toString());
  }

  @LocalServerPort int port;

  @Test
  void listsDevicesInTheirOrderWithFormatsAndInstructions() throws Exception {
    TestHttp http = new TestHttp(port);
    http.chooseNewRider("Device rider");

    String devices = http.get("/api/devices").body();
    assertThat(devices.indexOf("\"id\":\"wahoo-elemnt\""))
        .isGreaterThanOrEqualTo(0)
        .isLessThan(devices.indexOf("\"id\":\"generic\""));
    assertThat(devices)
        .contains("{\"id\":\"gpx\",\"label\":\"GPX\",\"extension\":\"gpx\"}")
        .contains("\"title\":\"Copy over USB\"")
        .doesNotContain("cloud");
  }
}
