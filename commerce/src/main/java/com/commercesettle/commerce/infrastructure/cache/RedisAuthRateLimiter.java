package com.commercesettle.commerce.infrastructure.cache;

import com.commercesettle.commerce.application.exception.AuthRateLimitedException;
import com.commercesettle.commerce.application.exception.AuthenticationUnavailableException;
import com.commercesettle.commerce.application.port.AuthRateLimiter;
import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.commerce.infrastructure.config.AuthProperties;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** 로그인 시도 수를 Redis 고정 시간창으로 센다. 키에는 원문 이메일 대신 서버 키 HMAC을 쓴다. */
@Component
class RedisAuthRateLimiter implements AuthRateLimiter {
  private static final String PREFIX = "commerce-settle:auth:rate:login:";

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> SCRIPT =
      RedisScript.of(new ClassPathResource("redis/rate-limit.lua"), List.class);

  private final StringRedisTemplate redis;
  private final AuthRedisGate gate;
  private final String maxRequests;
  private final String windowMillis;

  RedisAuthRateLimiter(StringRedisTemplate redis, AuthRedisGate gate, AuthProperties properties) {
    this.redis = redis;
    this.gate = gate;
    this.maxRequests = Integer.toString(properties.rateLimit().loginMaxRequests());
    this.windowMillis = Long.toString(properties.rateLimit().loginWindowSeconds() * 1000L);
  }

  @Override
  public void acquireLogin(ActorKind kind, String identifierHmac) {
    gate.requireOpen();
    List<?> result;
    try {
      result =
          redis.execute(
              SCRIPT,
              List.of(PREFIX + kind.name() + ":" + identifierHmac),
              maxRequests,
              windowMillis);
    } catch (DataAccessException e) {
      throw new AuthenticationUnavailableException("login rate limit is not verifiable", e);
    }
    if (result == null || result.size() != 2) {
      throw new AuthenticationUnavailableException("unexpected rate limit result");
    }
    if (((Number) result.get(0)).longValue() == 1) {
      return;
    }
    long remainingMillis = ((Number) result.get(1)).longValue();
    throw new AuthRateLimitedException(Math.max(1, Math.ceilDiv(remainingMillis, 1000)));
  }
}
