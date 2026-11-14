package app.trailplanner.rides;

import app.trailplanner.imports.ImportResult;
import app.trailplanner.users.CurrentUser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rides")
public class RideController {

  public record RenameRequest(String name) {}

  private final RideService rides;

  public RideController(RideService rides) {
    this.rides = rides;
  }

  /** The rider's rides, newest first. */
  @GetMapping
  public List<RideSummary> list(CurrentUser user) {
    return rides.list(user);
  }

  /** A ride with its track. */
  @GetMapping("/{id}")
  public RideService.RideDetails get(CurrentUser user, @PathVariable long id) {
    return rides.get(user, id);
  }

  /** The ride's FIT file as imported. */
  @GetMapping("/{id}/file")
  public ResponseEntity<byte[]> file(CurrentUser user, @PathVariable long id) {
    RideRepository.StoredFile file = rides.file(user, id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(file.data());
  }

  @PatchMapping("/{id}")
  public RideSummary rename(
      CurrentUser user, @PathVariable long id, @RequestBody RenameRequest request) {
    return rides.rename(user, id, request.name());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(CurrentUser user, @PathVariable long id) {
    rides.delete(user, id);
  }

  /** Adds the ride's track to the library as a route. */
  @PostMapping("/{id}/route")
  public ImportResult saveAsRoute(CurrentUser user, @PathVariable long id) {
    return rides.saveAsRoute(user, id);
  }

  /** Imports FIT activity files (form field {@code files}); one result per file, in order. */
  @PostMapping("/files")
  public List<RideService.RideImportResult> importFiles(
      CurrentUser user, @RequestParam("files") List<MultipartFile> upload) {
    return upload.stream()
        .map(file -> {
          try {
            return rides.importFile(user, file.getOriginalFilename(), file.getBytes());
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        })
        .toList();
  }

  /** Imports the rides on the connected device that are not imported yet. */
  @PostMapping("/device/{deviceId}")
  public RideService.DeviceImportResult importFromDevice(
      CurrentUser user, @PathVariable String deviceId) {
    return rides.importFromDevice(user, deviceId);
  }
}
