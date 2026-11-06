package app.trailplan.rides;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

/** Builds small FIT activity files (little endian) for tests. CRCs are not written. */
final class TestFit {

  private final ByteArrayOutputStream body = new ByteArrayOutputStream();

  /**
   * Defines local type {@code local} as global message {@code global} with fields (number, size).
   */
  TestFit define(int local, int global, int... numberAndSize) {
    body.write(0x40 | local);
    body.write(0); // reserved
    body.write(0); // little endian
    body.write(global & 0xFF);
    body.write(global >> 8);
    body.write(numberAndSize.length / 2);
    for (int i = 0; i < numberAndSize.length; i += 2) {
      body.write(numberAndSize[i]);
      body.write(numberAndSize[i + 1]);
      body.write(0x86); // base type: irrelevant to the reader
    }
    sizes[local] = numberAndSize;
    return this;
  }

  private final int[][] sizes = new int[16][];

  /** A data message of local type {@code local} with these field values, in definition order. */
  TestFit data(int local, long... values) {
    body.write(local);
    return values(local, values);
  }

  /** A data message with a compressed timestamp header (local type 0–3, offset 0–31). */
  TestFit compressed(int local, int timeOffset, long... values) {
    body.write(0x80 | (local << 5) | (timeOffset & 0x1F));
    return values(local, values);
  }

  private TestFit values(int local, long... values) {
    int[] def = sizes[local];
    for (int i = 0; i < values.length; i++) {
      int size = def[2 * i + 1];
      for (int b = 0; b < size; b++) {
        body.write((int) (values[i] >>> (8 * b)) & 0xFF);
      }
    }
    return this;
  }

  byte[] bytes() {
    byte[] records = body.toByteArray();
    byte[] file = new byte[14 + records.length + 2];
    file[0] = 14;
    file[1] = 0x20;
    for (int i = 0; i < 4; i++) {
      file[4 + i] = (byte) (records.length >>> (8 * i));
    }
    file[8] = '.';
    file[9] = 'F';
    file[10] = 'I';
    file[11] = 'T';
    System.arraycopy(records, 0, file, 14, records.length);
    return file;
  }

  static long fitTime(Instant instant) {
    return instant.getEpochSecond() - 631_065_600L;
  }

  static long semicircles(double degrees) {
    return Math.round(degrees * 2147483648.0 / 180.0) & 0xFFFFFFFFL;
  }

  static long altitude(double metres) {
    return Math.round((metres + 500) * 5);
  }

  /** A two-minute recording without positions, as when a ride is started by accident. */
  static byte[] withoutTrack(Instant start) {
    long t0 = fitTime(start);
    TestFit fit = new TestFit();
    fit.define(0, 0, 0, 1).data(0, 4);
    fit.define(1, 20, 253, 4, 5, 4);
    for (int i = 0; i < 120; i++) fit.data(1, t0 + i, 0);
    fit.define(2, 18, 253, 4, 2, 4, 7, 4, 9, 4).data(2, t0 + 120, t0, 120_000, 0);
    return fit.bytes();
  }

  /**
   * A ride as a Wahoo ELEMNT records it: a record per second (the later ones with compressed
   * timestamps), a stop of 20 s, then a session and an activity message.
   *
   * @param start when the ride started (UTC)
   * @param utcOffset the device's local time minus UTC, in seconds
   * @param seed varies the track, so that rides differ
   */
  static byte[] ride(Instant start, int utcOffset, int seed) {
    long t0 = fitTime(start);
    TestFit fit = new TestFit();
    fit.define(0, 0, 0, 1).data(0, 4); // file_id: type activity
    fit.define(1, 20, 253, 4, 0, 4, 1, 4, 78, 4, 5, 4); // record with timestamp
    fit.define(2, 20, 0, 4, 1, 4, 78, 4, 5, 4); // record, compressed timestamp
    double lat = 49.60 + seed * 0.001;
    double distance = 0;
    int seconds = 0;
    fit.data(1, t0, semicircles(lat), semicircles(6.10), altitude(300), 0);
    // 40 s riding at 5 m/s (east), then 20 s standing still, then 40 s more riding.
    for (int i = 1; i <= 100; i++) {
      seconds++;
      boolean stopped = i > 40 && i <= 60;
      if (!stopped) distance += 5;
      double lng = 6.10 + distance / 72_000.0; // about 5 m per step at this latitude
      long time = t0 + seconds;
      long[] values = {
        semicircles(lat),
        semicircles(lng),
        altitude(300 + distance / 20),
        Math.round(distance * 100)
      };
      fit.compressed(2, (int) (time & 0x1F), values);
    }
    long end = t0 + seconds;
    fit.define(
        3, 18, 253, 4, 2, 4, 5, 1, 7, 4, 8, 4, 9, 4, 22, 2, 23, 2, 16, 1, 17, 1, 124, 4, 11, 2);
    fit.data(
        3,
        end,
        t0,
        2,
        seconds * 1000L,
        seconds * 1000L,
        Math.round(distance * 100),
        21,
        2,
        140,
        171,
        9_500,
        0xFFFF);
    fit.define(4, 34, 253, 4, 5, 4).data(4, end, end + utcOffset);
    return fit.bytes();
  }
}
