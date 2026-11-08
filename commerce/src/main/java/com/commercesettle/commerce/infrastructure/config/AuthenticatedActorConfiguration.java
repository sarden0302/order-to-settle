package com.commercesettle.commerce.infrastructure.config;

import com.commercesettle.commerce.presentation.controller.AuthenticatedActorResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 컨트롤러가 구매자·판매자 신원을 인자로 받게 한다. */
@Configuration
public class AuthenticatedActorConfiguration implements WebMvcConfigurer {
  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new AuthenticatedActorResolver());
  }
}
