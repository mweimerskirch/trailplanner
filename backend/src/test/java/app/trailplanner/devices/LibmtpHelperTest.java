package app.trailplanner.devices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.trailplanner.config.TrailPlannerProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The process handling of {@link LibmtpHelper}, against a shell script posing as the helper. */
class LibmtpHelperTest {

  private static final UsbSync TARGET =
      new UsbSync("ELEMNT", "Wahoo", "routes", "gpx", null, false, null, "exports");

  @TempDir Path dir;

  /** A fake trailplanner-mtp: logs its arguments, then runs {@code body}. */
  private LibmtpHelper helper(String body) throws IOException {
    Path script = dir.resolve("trailplanner-mtp");
    Files.writeString(
        script,
        "#!/bin/sh\necho \"$@\" >> \"" + dir.resolve("calls") + "\"\n" + body + "\n",
        StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
    return new LibmtpHelper(
        new TrailPlannerProperties(
            new TrailPlannerProperties.Router(URI.create("http://brouter"), null, Duration.ZERO, 1),
            new TrailPlannerProperties.Komoot(URI.create("https://api.komoot.de")),
            new TrailPlannerProperties.Usb(script.toString(), Duration.ofSeconds(10)),
            List.of(),
            ""));
  }

  @Test
  void readsTheFolderAndIgnoresLibmtpChatter() throws Exception {
    LibmtpHelper mtp =
        helper(
            """
            echo 'Device 0 (VID=05c6 and PID=9039) is a Qualcomm (for PhiComm) C230w (MTP).'
            printf 'DEVICE\\tWahooFitness\\tELEMNT-BOLT2\\t4324\\n'
            printf 'one' > "$5/A.gpx"
            printf 'FILE\\t3\\tA.gpx\\n'
            """);

    MtpDevice.Listing listing = mtp.list(TARGET);

    assertThat(listing.device()).isEqualTo("ELEMNT-BOLT2");
    assertThat(listing.files()).containsOnlyKeys("A.gpx");
    assertThat(new String(listing.files().get("A.gpx"), StandardCharsets.UTF_8)).isEqualTo("one");
    assertThat(Files.readString(dir.resolve("calls"))).startsWith("--match Wahoo list routes /");
  }

  @Test
  void passesUploadsAndDeletions() throws Exception {
    LibmtpHelper mtp =
        helper(
            """
            printf 'DEVICE\\tWahooFitness\\tELEMNT-BOLT2\\t4324\\n'
            cat "$5/New.gpx" > "%s"
            """
                .formatted(dir.resolve("uploaded")));

    String device = mtp.apply(TARGET, Map.of("New.gpx", "xml".getBytes()), List.of("Old.gpx"));

    assertThat(device).isEqualTo("ELEMNT-BOLT2");
    assertThat(Files.readString(dir.resolve("uploaded"))).isEqualTo("xml");
    assertThat(Files.readString(dir.resolve("calls")).strip()).endsWith(" Old.gpx");
  }

  @Test
  void turnsExitCodesIntoMessages() throws Exception {
    assertThatThrownBy(() -> helper("exit 2").list(TARGET))
        .isInstanceOf(UsbException.class)
        .hasMessageContaining("No ELEMNT found");
    assertThatThrownBy(() -> helper("exit 3").list(TARGET))
        .hasMessageContaining("could not be opened");
    assertThatThrownBy(() -> helper("exit 4").list(TARGET))
        .hasMessageContaining("has no “routes” folder");
    assertThatThrownBy(() -> helper("echo boom >&2; exit 1").list(TARGET))
        .hasMessageContaining("Talking to the ELEMNT failed");
  }

  @Test
  void aMissingHelperMeansUsbSyncIsUnavailable() {
    LibmtpHelper mtp =
        new LibmtpHelper(
            new TrailPlannerProperties(
                new TrailPlannerProperties.Router(
                    URI.create("http://brouter"), null, Duration.ZERO, 1),
                new TrailPlannerProperties.Komoot(URI.create("https://api.komoot.de")),
                new TrailPlannerProperties.Usb(
                    dir.resolve("missing").toString(), Duration.ofSeconds(1)),
                List.of(),
                ""));
    assertThatThrownBy(() -> mtp.list(TARGET))
        .isInstanceOf(UsbException.class)
        .hasMessageContaining("not available");
  }

  @Test
  void refusesPathsAsFileNames() throws Exception {
    LibmtpHelper mtp = helper("exit 0");
    assertThatThrownBy(() -> mtp.apply(TARGET, Map.of(), List.of("../x")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
