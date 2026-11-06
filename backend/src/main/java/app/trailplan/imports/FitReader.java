package app.trailplan.imports;

import app.trailplan.routing.TrackPoint;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads FIT files. Implements just enough of the FIT protocol for Trailplan: normal and
 * compressed-timestamp headers, both byte orders and developer fields (skipped). {@link #read}
 * gives the track of a course or activity; {@link #decode} hands every data message to a handler,
 * e.g. to read a recorded ride (see {@code rides.RideFileReader}).
 */
public final class FitReader {

  public static final int MESG_SESSION = 18;
  public static final int MESG_RECORD = 20;
  public static final int MESG_COURSE = 31;
  public static final int MESG_ACTIVITY = 34;

  /** Field number of the timestamp, the same in every message. */
  public static final int FIELD_TIMESTAMP = 253;

  /** FIT times count seconds from 1989-12-31T00:00:00Z. */
  public static final long FIT_EPOCH_SECONDS = 631_065_600L;

  private static final double SEMICIRCLES_TO_DEGREES = 180.0 / 2147483648.0;

  /** One field of a definition message. */
  private record Field(int number, int size) {}

  /** A definition message: how the data messages of one local type are laid out. */
  private record Definition(int globalNumber, boolean bigEndian, List<Field> fields, int devBytes) {

    int size() {
      int size = devBytes;
      for (Field f : fields) {
        size += f.size();
      }
      return size;
    }
  }

  /** Receives the data messages of a file, in order. */
  @FunctionalInterface
  public interface Handler {
    void message(int globalNumber, Message message);
  }

  /** A data message, valid only during the {@link Handler} call. */
  public static final class Message {
    private final byte[] data;
    private final int pos;
    private final Definition def;
    private final Long compressedTime;

    private Message(byte[] data, int pos, Definition def, Long compressedTime) {
      this.data = data;
      this.pos = pos;
      this.def = def;
      this.compressedTime = compressedTime;
    }

    private int offset(int number, int... sizes) {
      int offset = pos;
      for (Field f : def.fields()) {
        if (f.number() == number) {
          for (int size : sizes) {
            if (f.size() == size) return offset;
          }
          return -1;
        }
        offset += f.size();
      }
      return -1;
    }

    /** An unsigned 1, 2 or 4 byte field; null if absent or "invalid" (all bits set). */
    public Long uint(int number) {
      for (Field f : def.fields()) {
        if (f.number() != number) continue;
        int offset = offset(number, 1, 2, 4);
        if (offset < 0) return null;
        long value =
            switch (f.size()) {
              case 1 -> data[offset] & 0xFFL;
              case 2 -> u16(data, offset, def.bigEndian());
              default -> u32(data, offset, def.bigEndian());
            };
        long invalid = (1L << (8 * f.size())) - 1;
        return value == invalid ? null : value;
      }
      return null;
    }

    /** A signed 4 byte field; null if absent or invalid. */
    public Integer sint32(int number) {
      int offset = offset(number, 4);
      if (offset < 0) return null;
      int value = (int) u32(data, offset, def.bigEndian());
      return value == Integer.MAX_VALUE ? null : value;
    }

    /** A string field; null if absent or empty. */
    public String string(int number) {
      for (Field f : def.fields()) {
        if (f.number() != number) continue;
        int offset = offset(number, f.size());
        int length = 0;
        while (length < f.size() && data[offset + length] != 0) {
          length++;
        }
        String text = new String(data, offset, length, StandardCharsets.UTF_8).strip();
        return text.isEmpty() ? null : text;
      }
      return null;
    }

    /**
     * The message's time in FIT seconds: its timestamp field, or the time of a compressed timestamp
     * header. Null if it has neither.
     */
    public Long time() {
      Long stamp = uint(FIELD_TIMESTAMP);
      return stamp != null ? stamp : compressedTime;
    }

    /** The position of a record message, null if it has none. */
    public TrackPoint position(Double ele) {
      Integer lat = sint32(0);
      Integer lng = sint32(1);
      if (lat == null || lng == null) return null;
      double latDeg = lat * SEMICIRCLES_TO_DEGREES;
      double lngDeg = lng * SEMICIRCLES_TO_DEGREES;
      if (Math.abs(latDeg) > 90 || Math.abs(lngDeg) > 180) return null;
      return new TrackPoint(lngDeg, latDeg, ele);
    }

    /** The altitude of a record message (enhanced if present), null if it has none. */
    public Double altitude() {
      Long enhanced = uint(78);
      if (enhanced != null) return enhanced / 5.0 - 500;
      Long plain = uint(2);
      return plain == null ? null : plain / 5.0 - 500;
    }
  }

  public static boolean looksLikeFit(byte[] data) {
    return data.length >= 12
        && (data[0] == 12 || data[0] == 14)
        && data[8] == '.'
        && data[9] == 'F'
        && data[10] == 'I'
        && data[11] == 'T';
  }

  /** The track (record positions) and, for a course, its name. */
  public ParsedTrack read(byte[] data) {
    List<TrackPoint> points = new ArrayList<>();
    String[] name = {null};
    decode(
        data,
        (global, m) -> {
          if (global == MESG_RECORD) {
            TrackPoint point = m.position(m.altitude());
            if (point != null) points.add(point);
          } else if (global == MESG_COURSE && name[0] == null) {
            name[0] = m.string(5);
          }
        });
    return new ParsedTrack(name[0], points);
  }

  /**
   * Hands every data message to the handler.
   *
   * @throws ImportException if this is not a FIT file or it is damaged
   */
  public static void decode(byte[] data, Handler handler) {
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
    Long lastTime = null;

    int pos = headerSize;
    try {
      while (pos < end) {
        int header = data[pos++] & 0xFF;
        int localType;
        Long compressedTime = null;
        if ((header & 0x80) != 0) {
          // Compressed timestamp header: a data message, its time an offset from the last one.
          localType = (header >> 5) & 0x03;
          if (lastTime != null) {
            int offset = header & 0x1F;
            long time = (lastTime & ~0x1FL) + offset;
            if (offset < (lastTime & 0x1F)) time += 0x20;
            compressedTime = time;
            lastTime = time;
          }
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
        if (pos + def.size() > end) {
          throw new ImportException("The FIT file is truncated.");
        }
        Message message = new Message(data, pos, def, compressedTime);
        Long stamp = message.uint(FIELD_TIMESTAMP);
        if (stamp != null) lastTime = stamp;
        handler.message(def.globalNumber(), message);
        pos += def.size();
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new ImportException("The FIT file is truncated.", e);
    }
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
}
