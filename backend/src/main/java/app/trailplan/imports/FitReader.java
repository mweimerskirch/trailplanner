package app.trailplan.imports;

import app.trailplan.routing.TrackPoint;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the track of a FIT file (course or activity): the positions and altitudes of all record
 * messages, and the course name if there is one. Implements just enough of the FIT protocol for
 * that: normal and compressed-timestamp headers, both byte orders and developer fields (skipped).
 */
public final class FitReader {

  private static final int MESG_RECORD = 20;
  private static final int MESG_COURSE = 31;
  private static final double SEMICIRCLES_TO_DEGREES = 180.0 / 2147483648.0;

  /** One field of a definition message. */
  private record Field(int number, int size) {}

  /** A definition message: how the data messages of one local type are laid out. */
  private record Definition(
      int globalNumber, boolean bigEndian, List<Field> fields, int devBytes) {}

  public static boolean looksLikeFit(byte[] data) {
    return data.length >= 12
        && (data[0] == 12 || data[0] == 14)
        && data[8] == '.'
        && data[9] == 'F'
        && data[10] == 'I'
        && data[11] == 'T';
  }

  public ParsedTrack read(byte[] data) {
    if (!looksLikeFit(data)) {
      throw new ImportException("This is not a FIT file.");
    }
    int headerSize = data[0];
    long dataSize = u32(data, 4, false);
    if (data.length < headerSize + dataSize) {
      throw new ImportException("The FIT file is truncated.");
    }
    int end = (int) (headerSize + dataSize);
    Definition[] definitions = new Definition[16];
    List<TrackPoint> points = new ArrayList<>();
    String name = null;

    int pos = headerSize;
    try {
      while (pos < end) {
        int header = data[pos++] & 0xFF;
        int localType;
        if ((header & 0x80) != 0) {
          localType = (header >> 5) & 0x03; // compressed timestamp header: always a data message
        } else if ((header & 0x40) != 0) {
          localType = header & 0x0F;
          boolean hasDevFields = (header & 0x20) != 0;
          boolean bigEndian = data[pos + 1] == 1;
          int global = u16(data, pos + 2, bigEndian);
          int count = data[pos + 4] & 0xFF;
          pos += 5;
          List<Field> fields = new ArrayList<>(count);
          for (int i = 0; i < count; i++) {
            fields.add(new Field(data[pos] & 0xFF, data[pos + 1] & 0xFF));
            pos += 3;
          }
          int devBytes = 0;
          if (hasDevFields) {
            int devCount = data[pos++] & 0xFF;
            for (int i = 0; i < devCount; i++) {
              devBytes += data[pos + 1] & 0xFF;
              pos += 3;
            }
          }
          definitions[localType] = new Definition(global, bigEndian, fields, devBytes);
          continue;
        } else {
          localType = header & 0x0F;
        }

        Definition def = definitions[localType];
        if (def == null) {
          throw new ImportException("The FIT file is damaged (data before its definition).");
        }
        if (pos + messageSize(def) > end) {
          throw new ImportException("The FIT file is truncated.");
        }
        if (def.globalNumber() == MESG_RECORD) {
          TrackPoint point = record(data, pos, def);
          if (point != null) {
            points.add(point);
          }
        } else if (def.globalNumber() == MESG_COURSE && name == null) {
          name = courseName(data, pos, def);
        }
        pos += messageSize(def);
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new ImportException("The FIT file is truncated.", e);
    }
    return new ParsedTrack(name, points);
  }

  private static TrackPoint record(byte[] data, int pos, Definition def) {
    Integer lat = null;
    Integer lng = null;
    Double altitude = null;
    Double enhancedAltitude = null;
    int offset = pos;
    for (Field f : def.fields()) {
      switch (f.number()) {
        case 0 -> lat = f.size() == 4 ? sint32(data, offset, def.bigEndian()) : null;
        case 1 -> lng = f.size() == 4 ? sint32(data, offset, def.bigEndian()) : null;
        case 2 -> {
          if (f.size() == 2) {
            int raw = u16(data, offset, def.bigEndian());
            altitude = raw == 0xFFFF ? null : raw / 5.0 - 500;
          }
        }
        case 78 -> {
          if (f.size() == 4) {
            long raw = u32(data, offset, def.bigEndian());
            enhancedAltitude = raw == 0xFFFFFFFFL ? null : raw / 5.0 - 500;
          }
        }
        default -> {}
      }
      offset += f.size();
    }
    if (lat == null || lng == null || lat == Integer.MAX_VALUE || lng == Integer.MAX_VALUE) {
      return null; // records without a position (e.g. sensor-only samples)
    }
    double latDeg = lat * SEMICIRCLES_TO_DEGREES;
    double lngDeg = lng * SEMICIRCLES_TO_DEGREES;
    if (Math.abs(latDeg) > 90 || Math.abs(lngDeg) > 180) {
      return null;
    }
    return new TrackPoint(lngDeg, latDeg, enhancedAltitude != null ? enhancedAltitude : altitude);
  }

  private static String courseName(byte[] data, int pos, Definition def) {
    int offset = pos;
    for (Field f : def.fields()) {
      if (f.number() == 5) {
        int length = 0;
        while (length < f.size() && data[offset + length] != 0) {
          length++;
        }
        String text = new String(data, offset, length, StandardCharsets.UTF_8).strip();
        return text.isEmpty() ? null : text;
      }
      offset += f.size();
    }
    return null;
  }

  private static int messageSize(Definition def) {
    int size = def.devBytes();
    for (Field f : def.fields()) {
      size += f.size();
    }
    return size;
  }

  private static int u16(byte[] data, int pos, boolean bigEndian) {
    int a = data[pos] & 0xFF;
    int b = data[pos + 1] & 0xFF;
    return bigEndian ? (a << 8) | b : (b << 8) | a;
  }

  private static long u32(byte[] data, int pos, boolean bigEndian) {
    long value = 0;
    for (int i = 0; i < 4; i++) {
      int b = data[pos + (bigEndian ? i : 3 - i)] & 0xFF;
      value = (value << 8) | b;
    }
    return value;
  }

  private static int sint32(byte[] data, int pos, boolean bigEndian) {
    return (int) u32(data, pos, bigEndian);
  }
}
