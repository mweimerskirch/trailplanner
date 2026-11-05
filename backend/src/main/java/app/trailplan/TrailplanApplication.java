package app.trailplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrailplanApplication {

  public static void main(String[] args) {
    SpringApplication.run(TrailplanApplication.class, args);
  }
}
