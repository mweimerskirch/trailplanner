package app.trailplan.config;

import app.trailplan.export.FitCourseWriter;
import app.trailplan.export.GpxWriter;
import app.trailplan.routing.BRouterClient;
import app.trailplan.routing.BRouterProfileGenerator;
import app.trailplan.routing.BRouterResponseParser;
import app.trailplan.routing.LegCache;
import app.trailplan.routing.ProfileStore;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  ProfileStore profileStore(TrailplanProperties props) {
    return new ProfileStore(props.router().customProfileDir(), new BRouterProfileGenerator());
  }

  @Bean
  BRouterClient bRouterClient(
      TrailplanProperties props, RestClient.Builder builder, ObjectMapper mapper) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(props.router().timeout());
    RestClient http =
        builder
            .clone()
            .baseUrl(props.router().baseUrl().toString())
            .requestFactory(factory)
            .build();
    return new BRouterClient(http, new BRouterResponseParser(mapper));
  }

  @Bean
  LegCache legCache(TrailplanProperties props) {
    return new LegCache(props.router().legCacheSize());
  }

  @Bean
  GpxWriter gpxWriter() {
    return new GpxWriter();
  }

  @Bean
  FitCourseWriter fitCourseWriter() {
    return new FitCourseWriter();
  }
}
