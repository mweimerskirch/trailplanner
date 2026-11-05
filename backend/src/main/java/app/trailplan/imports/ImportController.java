package app.trailplan.imports;

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
  public List<ImportResult> importFiles(@RequestParam("files") List<MultipartFile> upload) {
    return upload.stream().map(this::importOne).toList();
  }

  private ImportResult importOne(MultipartFile file) {
    try {
      return files.importFile(file.getOriginalFilename(), file.getBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
