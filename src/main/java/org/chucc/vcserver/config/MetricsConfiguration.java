package org.chucc.vcserver.config;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration for annotation-based metrics collection.
 * Enables {@code @Timed} and {@code @Counted} annotations via AOP.
 */
@Configuration
@EnableAspectJAutoProxy
public class MetricsConfiguration {

  /**
   * Enables {@code @Timed} annotation support.
   *
   * @param registry the meter registry
   * @return the timed aspect
   */
  @Bean
  public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }

  /**
   * Enables {@code @Counted} annotation support.
   *
   * @param registry the meter registry
   * @return the counted aspect
   */
  @Bean
  public CountedAspect countedAspect(MeterRegistry registry) {
    return new CountedAspect(registry);
  }
}
