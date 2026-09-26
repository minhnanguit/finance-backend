package com.uit.finance;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.Modulithic;

/**
 * Modular monolith entry point.
 *
 * <p>Modules are detected by the {@code @ApplicationModule} annotation on their package (see {@code
 * application.properties}: {@code spring.modulith.detection-strategy=explicitly-annotated}), which
 * lets business modules live under {@code modules.*} while {@code shared} is an open, shared
 * module.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic(systemName = "Finance", sharedModules = "shared")
public class FinanceApplication {

  public static void main(String[] args) {
    SpringApplication.run(FinanceApplication.class, args);
  }

  /** Single clock for the whole system so time can be controlled in tests. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
