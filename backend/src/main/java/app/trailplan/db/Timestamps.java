package app.trailplan.db;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * How timestamps are stored: ISO-8601 UTC with exactly three fraction digits, so that they sort
 * correctly as text ({@code Instant.toString()} varies the number of digits).
 */
public final class Timestamps {

  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private Timestamps() {}

  public static String format(Instant instant) {
    return instant == null ? null : FORMAT.format(instant);
  }

  public static Instant parse(String text) {
    return text == null ? null : Instant.parse(text);
  }
}
