package app.trailplan.export;

import java.text.Normalizer;

public final class FileNames {

  private FileNames() {}

  /** A safe ASCII file name derived from a route name. */
  public static String forRoute(String routeName, String extension) {
    String ascii = Normalizer.normalize(routeName, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    String slug = ascii.replaceAll("[^A-Za-z0-9-]+", "_").replaceAll("^_+|_+$", "");
    return (slug.isEmpty() ? "route" : slug) + "." + extension;
  }
}
