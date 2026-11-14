package app.trailplanner.export;

import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class GpxFormat implements RouteFormat {

  private final GpxWriter writer;

  public GpxFormat(GpxWriter writer) {
    this.writer = writer;
  }

  @Override
  public String id() {
    return "gpx";
  }

  @Override
  public String label() {
    return "GPX";
  }

  @Override
  public String extension() {
    return "gpx";
  }

  @Override
  public MediaType mediaType() {
    return MediaType.parseMediaType("application/gpx+xml");
  }

  @Override
  public byte[] write(ExportedRoute route) {
    return writer.write(route.name(), route.track()).getBytes(StandardCharsets.UTF_8);
  }
}
