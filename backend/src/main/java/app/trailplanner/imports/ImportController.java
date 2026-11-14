package app.trailplanner.imports;

import app.trailplanner.users.CurrentUser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
public class ImportController {

  private final FileImportService files;

  public ImportController(FileImportService files) {
    this.files = files;
  }

  /** Imports GPX/FIT files (form field {@code files}); one result per file, in order. */
  @PostMapping("/files")
  public List<ImportResult> importFiles(
      CurrentUser user, @RequestParam("files") List<MultipartFile> upload) {
    return upload.stream().map(file -> importOne(user, file)).toList();
  }

  private ImportResult importOne(CurrentUser user, MultipartFile file) {
    try {
      return files.importFile(user, file.getOriginalFilename(), file.getBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
