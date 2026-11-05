package app.trailplan.routing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Writes generated profiles into BRouter's custom profile directory (a volume shared with the
 * BRouter container). File names are content hashes, so identical preferences reuse one file and
 * files never need to be updated in place.
 */
public final class ProfileStore {

  private static final String BROUTER_CUSTOM_PREFIX = "custom_";

  private final Path directory;
  private final BRouterProfileGenerator generator;

  public ProfileStore(Path directory, BRouterProfileGenerator generator) {
    this.directory = directory;
    this.generator = generator;
  }

  /** Returns the BRouter profile id for these preferences, writing the profile if needed. */
  public String profileIdFor(RoutingPreferences preferences) {
    String content = generator.generate(preferences);
    String name = "tp_" + sha256(content).substring(0, 16);
    Path target = directory.resolve(name + ".brf");
    if (!Files.exists(target)) {
      write(target, content);
    }
    return BROUTER_CUSTOM_PREFIX + name;
  }

  private void write(Path target, String content) {
    try {
      Files.createDirectories(directory);
      Path temp = Files.createTempFile(directory, ".tmp-", ".brf");
      Files.writeString(temp, content, StandardCharsets.UTF_8);
      try {
        Files.move(
            temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write routing profile to " + directory, e);
    }
  }

  private static String sha256(String content) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
