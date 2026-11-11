package com.commercesettle.internalauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 서블릿 앱의 모든 요청에 내부 호출 판정을 건다. 앱은 {@code contracts/openapi.json} 리소스와 {@code app.internal-auth} 설정을
 * 둔다. 자격·명세가 맞지 않으면 기동하지 않는다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthAutoConfiguration {
  static final String SPECIFICATION = "contracts/openapi.json";

  /** 추적 관측 필터 다음, 다른 앱 필터보다 먼저 판정해 거절 응답에도 실제 traceId가 남게 한다. */
  static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

  @Bean
  InternalCredentials internalCredentials(InternalAuthProperties properties) {
    return new InternalCredentials(properties);
  }

  @Bean
  InternalOperations internalOperations(ObjectProvider<ObjectMapper> mapper) throws IOException {
    try (InputStream specification = new ClassPathResource(SPECIFICATION).getInputStream()) {
      return InternalOperations.load(specification, mapper.getIfAvailable(ObjectMapper::new));
    }
  }

  @Bean
  FilterRegistrationBean<InternalCallerFilter> internalCallerFilter(
      InternalAuthProperties properties,
      InternalCredentials credentials,
      InternalOperations operations,
      ObjectProvider<MeterRegistry> meters,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) {
    FilterRegistrationBean<InternalCallerFilter> registration =
        new FilterRegistrationBean<>(
            new InternalCallerFilter(
                properties.service(),
                credentials,
                operations,
                meters.getIfAvailable(() -> Metrics.globalRegistry),
                errors));
    registration.setOrder(ORDER);
    return registration;
  }
}
