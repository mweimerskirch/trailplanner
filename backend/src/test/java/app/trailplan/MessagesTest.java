package app.trailplan;

import static org.assertj.core.api.Assertions.assertThat;

import app.trailplan.presets.DefaultPresets;
import app.trailplan.presets.PresetService;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** The server's translations (messages_fr/de.properties) are complete and consistent. */
class MessagesTest {

  private static Properties load(String name) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = MessagesTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as(name).isNotNull();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }

  @Test
  void frenchAndGermanHaveTheSameKeys() throws IOException {
    assertThat(load("messages_de.properties").stringPropertyNames())
        .isEqualTo(load("messages_fr.properties").stringPropertyNames());
  }

  @Test
  void everyBuiltInPresetIsTranslated() throws IOException {
    Properties french = load("messages_fr.properties");
    for (DefaultPresets.Preset preset : DefaultPresets.ALL) {
      String key = PresetService.messageKey(preset.name());
      assertThat(french.getProperty(key)).as(key).isNotBlank();
    }
  }
}
