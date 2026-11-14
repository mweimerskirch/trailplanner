package app.trailplanner.export;

import app.trailplanner.routing.TrackPoint;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Writes a route as a FIT course file, which Garmin and Wahoo devices take as a course.
 *
 * <p>Implements just the slice of the FIT protocol a course needs (file_id, course, lap, timer
 * events and records), so no SDK dependency is required. Timestamps are synthetic, based on a
 * nominal riding speed, because courses only need them to be increasing.
 */
public final class FitCourseWriter {

  private static final long FIT_EPOCH_OFFSET = 631_065_600L; // 1989-12-31T00:00:00Z
  private static final double NOMINAL_SPEED_MPS = 15 / 3.6;
  private static final int MAX_NAME_BYTES = 64;

  // FIT base types
  private static final int ENUM = 0x00;
  private static final int UINT8 = 0x02;
  private static final int STRING = 0x07;
  private static final int UINT16 = 0x84;
  private static final int SINT32 = 0x85;
  private static final int UINT32 = 0x86;
  private static final int UINT32Z = 0x8C;

  // Global message numbers
  private static final int MESG_FILE_ID = 0;
  private static final int MESG_LAP = 19;
  private static final int MESG_RECORD = 20;
  private static final int MESG_EVENT = 21;
  private static final int MESG_COURSE = 31;

  /**
   * @param ascent total climbing in metres (use the router's filtered value; summing raw elevation
   *     differences overstates it)
   * @param descent total descent in metres
   */
  public byte[] write(
      String name, List<TrackPoint> points, double ascent, double descent, Instant start) {
    if (points.size() < 2) {
      throw new IllegalArgumentException("A course needs at least two points.");
    }
    Body body = new Body();
    long startTime = fitTime(start);

    double[] cumulative = new double[points.size()];
    for (int i = 1; i < points.size(); i++) {
      cumulative[i] = cumulative[i - 1] + points.get(i - 1).distanceTo(points.get(i));
    }
    double total = cumulative[points.size() - 1];
    long endTime = startTime + Math.round(total / NOMINAL_SPEED_MPS);
    TrackPoint first = points.get(0);
    TrackPoint last = points.get(points.size() - 1);

    // file_id
    body.define(
        0,
        MESG_FILE_ID,
        new int[][] {
          {0, 1, ENUM}, {1, 2, UINT16}, {2, 2, UINT16}, {3, 4, UINT32Z}, {4, 4, UINT32}
        });
    body.header(0);
    body.u8(6); // type = course
    body.u16(255); // manufacturer = development
    body.u16(0); // product
    body.u32(1); // serial number
    body.u32(startTime); // time_created

    // course
    byte[] nameBytes = truncatedUtf8(name);
    body.define(1, MESG_COURSE, new int[][] {{4, 1, ENUM}, {5, MAX_NAME_BYTES, STRING}});
    body.header(1);
    body.u8(2); // sport = cycling
    body.fixedString(nameBytes, MAX_NAME_BYTES);

    // lap covering the whole course
    body.define(
        2,
        MESG_LAP,
        new int[][] {
          {253, 4, UINT32}, {2, 4, UINT32}, {3, 4, SINT32}, {4, 4, SINT32}, {5, 4, SINT32},
          {6, 4, SINT32}, {7, 4, UINT32}, {8, 4, UINT32}, {9, 4, UINT32}, {21, 2, UINT16},
          {22, 2, UINT16}
        });
    body.header(2);
    body.u32(endTime);
    body.u32(startTime);
    body.s32(semicircles(first.lat()));
    body.s32(semicircles(first.lng()));
    body.s32(semicircles(last.lat()));
    body.s32(semicircles(last.lng()));
    body.u32((endTime - startTime) * 1000); // total_elapsed_time, ms
    body.u32((endTime - startTime) * 1000); // total_timer_time, ms
    body.u32(Math.round(total * 100)); // total_distance, cm
    body.u16(clampU16(Math.round(ascent)));
    body.u16(clampU16(Math.round(descent)));

    // timer start / stop events
    body.define(
        3, MESG_EVENT, new int[][] {{253, 4, UINT32}, {0, 1, ENUM}, {1, 1, ENUM}, {4, 1, UINT8}});
    body.header(3);
    body.u32(startTime);
    body.u8(0); // event = timer
    body.u8(0); // event_type = start
    body.u8(0);

    // records
    body.define(
        4,
        MESG_RECORD,
        new int[][] {
          {253, 4, UINT32}, {0, 4, SINT32}, {1, 4, SINT32}, {2, 2, UINT16}, {5, 4, UINT32}
        });
    for (int i = 0; i < points.size(); i++) {
      TrackPoint p = points.get(i);
      body.header(4);
      body.u32(startTime + Math.round(cumulative[i] / NOMINAL_SPEED_MPS));
      body.s32(semicircles(p.lat()));
      body.s32(semicircles(p.lng()));
      body.u16(p.ele() == null ? 0xFFFF : clampU16(Math.round((p.ele() + 500) * 5)));
      body.u32(Math.round(cumulative[i] * 100));
    }

    body.header(3);
    body.u32(endTime);
    body.u8(0); // event = timer
    body.u8(4); // event_type = stop_all
    body.u8(0);

    return body.toFile();
  }

  private static long fitTime(Instant instant) {
    return instant.getEpochSecond() - FIT_EPOCH_OFFSET;
  }

  private static int semicircles(double degrees) {
    return (int) Math.round(degrees * (2147483648.0 / 180.0));
  }

  private static int clampU16(long value) {
    return (int) Math.max(0, Math.min(0xFFFE, value));
  }

  private static byte[] truncatedUtf8(String name) {
    byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
    int limit = MAX_NAME_BYTES - 1; // keep room for the terminating zero
    if (bytes.length <= limit) {
      return bytes;
    }
    // Cut on a character boundary: skip back over UTF-8 continuation bytes.
    int end = limit;
    while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
      end--;
    }
    byte[] cut = new byte[end];
    System.arraycopy(bytes, 0, cut, 0, end);
    return cut;
  }

  /** Accumulates FIT records (little endian) and wraps them with header and CRCs. */
  private static final class Body {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    void define(int localType, int globalNumber, int[][] fields) {
      out.write(0x40 | localType); // definition message header
      out.write(0); // reserved
      out.write(0); // little endian
      u16(globalNumber);
      out.write(fields.length);
      for (int[] f : fields) {
        out.write(f[0]); // field number
        out.write(f[1]); // size in bytes
        out.write(f[2]); // base type
      }
    }

    void header(int localType) {
      out.write(localType & 0x0F);
    }

    void u8(int v) {
      out.write(v & 0xFF);
    }

    void u16(int v) {
      out.write(v & 0xFF);
      out.write((v >>> 8) & 0xFF);
    }

    void u32(long v) {
      for (int i = 0; i < 4; i++) {
        out.write((int) ((v >>> (8 * i)) & 0xFF));
      }
    }

    void s32(int v) {
      u32(v & 0xFFFFFFFFL);
    }

    void fixedString(byte[] bytes, int size) {
      out.write(bytes, 0, bytes.length);
      for (int i = bytes.length; i < size; i++) {
        out.write(0);
      }
    }

    byte[] toFile() {
      byte[] data = out.toByteArray();
      byte[] header = new byte[14];
      header[0] = 14; // header size
      header[1] = 0x20; // protocol version 2.0
      header[2] = (byte) (2132 & 0xFF); // profile version 21.32
      header[3] = (byte) (2132 >>> 8);
      for (int i = 0; i < 4; i++) {
        header[4 + i] = (byte) ((data.length >>> (8 * i)) & 0xFF);
      }
      header[8] = '.';
      header[9] = 'F';
      header[10] = 'I';
      header[11] = 'T';
      int headerCrc = crc(0, header, 0, 12);
      header[12] = (byte) (headerCrc & 0xFF);
      header[13] = (byte) (headerCrc >>> 8);

      byte[] file = new byte[header.length + data.length + 2];
      System.arraycopy(header, 0, file, 0, header.length);
      System.arraycopy(data, 0, file, header.length, data.length);
      int fileCrc = crc(0, file, 0, header.length + data.length);
      file[file.length - 2] = (byte) (fileCrc & 0xFF);
      file[file.length - 1] = (byte) (fileCrc >>> 8);
      return file;
    }
  }

  private static final int[] CRC_TABLE = {
    0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
    0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400
  };

  static int crc(int crc, byte[] bytes, int offset, int length) {
    for (int i = offset; i < offset + length; i++) {
      int b = bytes[i] & 0xFF;
      int tmp = CRC_TABLE[crc & 0xF];
      crc = (crc >>> 4) & 0x0FFF;
      crc = crc ^ tmp ^ CRC_TABLE[b & 0xF];
      tmp = CRC_TABLE[crc & 0xF];
      crc = (crc >>> 4) & 0x0FFF;
      crc = crc ^ tmp ^ CRC_TABLE[(b >>> 4) & 0xF];
    }
    return crc;
  }
}
