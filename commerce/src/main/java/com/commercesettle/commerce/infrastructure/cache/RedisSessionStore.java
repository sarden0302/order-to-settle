package com.commercesettle.commerce.infrastructure.cache;

import com.commercesettle.commerce.application.exception.AuthenticationUnavailableException;
import com.commercesettle.commerce.application.port.SessionStore;
import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.commerce.infrastructure.config.AuthProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 로그인 세션과 계정별 인증 가드. 판단과 쓰기를 스크립트 하나로 실행해 동시 로그인·폐기와 경합하지 않는다. 스크립트 캐시가 비면 Spring이 본문으로 다시
 * 실행한다(docs/decisions/0003-redis-sessions.md).
 */
@Component
class RedisSessionStore implements SessionStore {
  /** 고정 만료. 요청·조회로 연장하지 않는다(docs/policies.md#세션-발급과-검증). */
  static final Duration SESSION_LIFETIME = Duration.ofMinutes(30);

  private static final String ISSUED = "ISSUED";
  private static final String REJECTED = "REJECTED";

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> ISSUE =
      RedisScript.of(new ClassPathResource("redis/issue-session.lua"), List.class);

  private static final RedisScript<Long> REVOKE =
      RedisScript.of(new ClassPathResource("redis/revoke-session.lua"), Long.class);

  private final StringRedisTemplate redis;
  private final AuthRedisGate gate;
  private final String epoch;

  RedisSessionStore(StringRedisTemplate redis, AuthRedisGate gate, AuthProperties properties) {
    this.redis = redis;
    this.gate = gate;
    this.epoch = properties.epoch();
  }

  @Override
  public Optional<Instant> issue(
      ActorKind kind, UUID accountId, long authVersion, String tokenHash) {
    gate.requireOpen();
    List<?> result =
        run(
            () ->
                redis.execute(
                    ISSUE,
                    List.of(sessionKey(tokenHash), guardKey(kind, accountId)),
                    kind.name(),
                    accountId.toString(),
                    Long.toString(authVersion),
                    Long.toString(SESSION_LIFETIME.toMillis())));
    String outcome = result == null || result.isEmpty() ? "" : String.valueOf(result.get(0));
    if (ISSUED.equals(outcome) && result.size() == 3) {
      return Optional.of(Instant.ofEpochMilli(Long.parseLong(String.valueOf(result.get(2)))));
    }
    if (REJECTED.equals(outcome)) {
      return Optional.empty();
    }
    throw new AuthenticationUnavailableException("session was not issued: " + reason(result));
  }

  @Override
  public boolean revoke(ActorKind kind, UUID accountId, String tokenHash) {
    gate.requireOpen();
    Long deleted =
        run(
            () ->
                redis.execute(
                    REVOKE, List.of(sessionKey(tokenHash)), kind.name(), accountId.toString()));
    return deleted != null && deleted == 1L;
  }

  String sessionKey(String tokenHash) {
    return "commerce-settle:auth:session:" + epoch + ":" + tokenHash;
  }

  String guardKey(ActorKind kind, UUID accountId) {
    return "commerce-settle:auth:subject:" + epoch + ":" + kind.name() + ":" + accountId;
  }

  private static <T> T run(Supplier<T> call) {
    try {
      return call.get();
    } catch (DataAccessException e) {
      throw new AuthenticationUnavailableException("auth store is not available", e);
    }
  }

  /** 스크립트가 돌려준 사유 코드만 남긴다. 키·토큰 해시는 넣지 않는다. */
  private static String reason(List<?> result) {
    return result == null || result.size() < 2 ? "UNKNOWN" : String.valueOf(result.get(1));
  }
}
