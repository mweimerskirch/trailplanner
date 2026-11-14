package app.trailplanner.devices;

import app.trailplanner.config.TrailPlannerProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Talks to USB devices through the {@code trailplanner-mtp} helper (C, libmtp; see {@code
 * backend/mtp}). The helper prints tab-separated lines (DEVICE, FILE, SENT, DELETED); libmtp's own
 * output is ignored. Its exit code says why it failed.
 */
@Component
public class LibmtpHelper implements MtpDevice {

  private static final Logger log = LoggerFactory.getLogger(LibmtpHelper.class);

  private static final int NO_DEVICE = 2;
  private static final int BUSY = 3;
  private static final int NO_FOLDER = 4;

  private final String helper;
  private final Duration timeout;

  public LibmtpHelper(TrailPlannerProperties props) {
    this.helper = props.usb().helper();
    this.timeout = props.usb().timeout();
  }

  @Override
  public Listing list(UsbSync target) {
    Path dir = tempDir();
    try {
      Output out = run(target, target.folder(), List.of("list", target.folder(), dir.toString()));
      Map<String, byte[]> files = new LinkedHashMap<>();
      for (String[] line : out.lines("FILE")) {
        String name = line[2];
        files.put(name, Files.readAllBytes(dir.resolve(name)));
      }
      return new Listing(out.device(), files);
    } catch (IOException e) {
      throw UsbException.failed(target.label(), e);
    } finally {
      deleteRecursively(dir);
    }
  }

  @Override
  public String apply(UsbSync target, Map<String, byte[]> uploads, List<String> deletions) {
    Path dir = tempDir();
    try {
      for (Map.Entry<String, byte[]> upload : uploads.entrySet()) {
        Files.write(dir.resolve(safeName(upload.getKey())), upload.getValue());
      }
      List<String> args = new ArrayList<>(List.of("apply", target.folder(), dir.toString()));
      deletions.stream().map(LibmtpHelper::safeName).forEach(args::add);
      return run(target, target.folder(), args).device();
    } catch (IOException e) {
      throw UsbException.failed(target.label(), e);
    } finally {
      deleteRecursively(dir);
    }
  }

  @Override
  public FileList files(UsbSync target, String folder) {
    try {
      Output out = run(target, folder, List.of("files", safeName(folder)));
      List<DeviceFile> files =
          out.lines("FILE").stream()
              .map(line -> new DeviceFile(line[2], Long.parseLong(line[1])))
              .toList();
      return new FileList(out.device(), files);
    } catch (IOException | NumberFormatException e) {
      throw UsbException.failed(target.label(), e);
    }
  }

  @Override
  public Map<String, byte[]> fetch(UsbSync target, String folder, List<String> names) {
    if (names.isEmpty()) return Map.of();
    Path dir = tempDir();
    try {
      List<String> args = new ArrayList<>(List.of("get", safeName(folder), dir.toString()));
      names.stream().map(LibmtpHelper::safeName).forEach(args::add);
      Output out = run(target, folder, args);
      Map<String, byte[]> files = new LinkedHashMap<>();
      for (String[] line : out.lines("FILE")) {
        String name = safeName(line[2]);
        files.put(name, Files.readAllBytes(dir.resolve(name)));
      }
      return files;
    } catch (IOException e) {
      throw UsbException.failed(target.label(), e);
    } finally {
      deleteRecursively(dir);
    }
  }

  private record Output(List<String> lines) {

    List<String[]> lines(String tag) {
      return lines.stream()
          .filter(l -> l.startsWith(tag + "\t"))
          .map(l -> l.split("\t", -1))
          .toList();
    }

    /** The model the device reports, or its manufacturer if it reports none. */
    String device() {
      return lines("DEVICE").stream()
          .filter(l -> l.length >= 3)
          .map(l -> l[2].isBlank() ? l[1] : l[2])
          .findFirst()
          .orElse("device");
    }
  }

  private Output run(UsbSync target, String folder, List<String> args) throws IOException {
    String label = target.label();
    List<String> command = new ArrayList<>(List.of(helper, "--match", target.match()));
    command.addAll(args);
    Path stdout = Files.createTempFile("trailplanner-mtp", ".out");
    Path stderr = Files.createTempFile("trailplanner-mtp", ".err");
    try {
      Process process;
      try {
        process =
            new ProcessBuilder(command)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
      } catch (IOException e) {
        throw UsbException.unavailable(e);
      }
      boolean finished;
      try {
        finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
        throw UsbException.failed(label, e);
      }
      if (!finished) {
        process.destroyForcibly();
        throw UsbException.failed(label, new IOException("trailplanner-mtp timed out"));
      }
      String errors = Files.readString(stderr, StandardCharsets.UTF_8).strip();
      int exit = process.exitValue();
      if (exit != 0) {
        log.info("trailplanner-mtp {} exited with {}: {}", args.getFirst(), exit, errors);
      }
      switch (exit) {
        case 0 -> {
          return new Output(Files.readAllLines(stdout, StandardCharsets.UTF_8));
        }
        case NO_DEVICE -> throw UsbException.noDevice(label);
        case BUSY -> throw UsbException.busy(label);
        case NO_FOLDER -> throw UsbException.noFolder(label, folder);
        default ->
            throw UsbException.failed(
                label, new IOException("trailplanner-mtp exited with " + exit + ": " + errors));
      }
    } finally {
      Files.deleteIfExists(stdout);
      Files.deleteIfExists(stderr);
    }
  }

  /** File names come from route names (see FileNames) or from the device; never paths. */
  private static String safeName(String name) {
    if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")) {
      throw new IllegalArgumentException("Not a plain file name: " + name);
    }
    return name;
  }

  private static Path tempDir() {
    try {
      return Files.createTempDirectory("trailplanner-usb");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void deleteRecursively(Path dir) {
    try (Stream<Path> paths = Files.walk(dir)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    } catch (IOException e) {
      log.warn("Could not delete {}", dir, e);
    }
  }
}
