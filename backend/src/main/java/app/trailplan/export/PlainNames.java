package app.trailplan.export;

import java.text.Normalizer;
import java.util.Map;

/**
 * Route names in plain ASCII, for devices that cannot show anything else: the ELEMNT drops accented
 * letters and shows XML escapes such as {@code &lt;} literally. Accents are removed ("Ardèche" →
 * "Ardeche"), a few letters and punctuation are spelled out, and characters that XML must escape
 * are replaced.
 */
public final class PlainNames {

  private static final Map<String, String> REPLACEMENTS =
      Map.ofEntries(
          Map.entry("ß", "ss"),
          Map.entry("æ", "ae"),
          Map.entry("Æ", "AE"),
          Map.entry("œ", "oe"),
          Map.entry("Œ", "OE"),
          Map.entry("ø", "o"),
          Map.entry("Ø", "O"),
          Map.entry("ł", "l"),
          Map.entry("Ł", "L"),
          Map.entry("đ", "d"),
          Map.entry("Đ", "D"),
          Map.entry("–", "-"),
          Map.entry("—", "-"),
          Map.entry("‘", "'"),
          Map.entry("’", "'"),
          Map.entry("“", "\""),
          Map.entry("”", "\""),
          Map.entry("„", "\""),
          Map.entry("…", "..."),
          Map.entry("&", "+"));

  private PlainNames() {}

  /** Removes accents only, e.g. for file names. */
  static String withoutAccents(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
  }

  public static String of(String name) {
    String text = name;
    for (Map.Entry<String, String> r : REPLACEMENTS.entrySet()) {
      text = text.replace(r.getKey(), r.getValue());
    }
    text = withoutAccents(text);
    text = text.replaceAll("<-+>|<-+|-+>", "-").replaceAll("[<>]", "");
    text = text.replaceAll("[^\\x20-\\x7E]", "").replaceAll("\\s+", " ").strip();
    return text.isEmpty() ? "Route" : text;
  }
}
