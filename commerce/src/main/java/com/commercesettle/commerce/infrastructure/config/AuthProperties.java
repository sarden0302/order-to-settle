package com.commercesettle.commerce.infrastructure.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사용자 인증 설정. 세션 세대와 요청 제한 값은 형식이 틀리면 앱을 기동하지 않는다. Redis 실행 식별자는 기동과 별개로 인증 게이트가 확인한다.
 *
 * @param sessionEpoch 세션·가드 키의 인증 세대. Gateway와 같은 값이며 바꾸면 기존 세션이 모두 무효가 된다
 * @param redisRunId 승인한 Redis 실행 식별자(INFO server의 run_id). 비어 있으면 인증을 열지 않는다
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(UUID sessionEpoch, String redisRunId, RateLimit rateLimit) {
  public AuthProperties {
    if (sessionEpoch == null) {
      throw new IllegalStateException("app.auth.session-epoch is required");
    }
    if (rateLimit == null) {
      throw new IllegalStateException("app.auth.rate-limit is required");
    }
    redisRunId = redisRunId == null ? "" : redisRunId.strip();
  }

  /** 소문자 UUID 표기. 키에 그대로 넣는다. */
  public String epoch() {
    return sessionEpoch.toString();
  }

  /**
   * 로그인·비밀번호 복구 요청 제한. 네 값 모두 양의 정수여야 한다(docs/policies.md#로그인복구-요청-제한). 식별자 HMAC 키는 {@code
   * ServerHmac} 구현이 검사한다.
   */
  public record RateLimit(
      int loginMaxRequests,
      int loginWindowSeconds,
      int passwordResetMaxRequests,
      int passwordResetWindowSeconds) {
    public RateLimit {
      requirePositive("login-max-requests", loginMaxRequests);
      requirePositive("login-window-seconds", loginWindowSeconds);
      requirePositive("password-reset-max-requests", passwordResetMaxRequests);
      requirePositive("password-reset-window-seconds", passwordResetWindowSeconds);
    }

    private static void requirePositive(String name, int value) {
      if (value <= 0) {
        throw new IllegalStateException("app.auth.rate-limit." + name + " must be positive");
      }
    }
  }
}
