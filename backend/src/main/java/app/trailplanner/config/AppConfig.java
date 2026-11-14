package app.trailplanner.config;

import app.trailplanner.export.FitCourseWriter;
import app.trailplanner.export.GpxWriter;
import app.trailplanner.routing.BRouterClient;
import app.trailplanner.routing.BRouterProfileGenerator;
import app.trailplanner.routing.BRouterResponseParser;
import app.trailplanner.routing.LegCache;
import app.trailplanner.routing.ProfileStore;
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
  ProfileStore profileStore(TrailPlannerProperties props) {
    return new ProfileStore(props.router().customProfileDir(), new BRouterProfileGenerator());
  }

  @Bean
  BRouterClient bRouterClient(
      TrailPlannerProperties props, RestClient.Builder builder, ObjectMapper mapper) {
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
  LegCache legCache(TrailPlannerProperties props) {
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
