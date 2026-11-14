package app.trailplanner.export;

import java.time.Clock;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** FIT course file, the native route format of Wahoo and Garmin devices. */
@Component
public class FitFormat implements RouteFormat {

  private final FitCourseWriter writer;
  private final Clock clock;

  public FitFormat(FitCourseWriter writer, Clock clock) {
    this.writer = writer;
    this.clock = clock;
  }

  @Override
  public String id() {
    return "fit";
  }

  @Override
  public String label() {
    return "FIT course";
  }

  @Override
  public String extension() {
    return "fit";
  }

  @Override
  public MediaType mediaType() {
    return MediaType.parseMediaType("application/vnd.ant.fit");
  }

  @Override
  public byte[] write(ExportedRoute route) {
    return writer.write(
        route.name(), route.track(), route.ascent(), route.descent(), clock.instant());
  }
}
