package com.commercesettle.commerce.application.port;

import com.commercesettle.commerce.application.exception.AuthenticationUnavailableException;
import com.commercesettle.commerce.domain.model.ActorKind;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis 로그인 세션. 토큰 원문은 받지 않고 SHA-256 해시만 다룬다. 저장소를 확인할 수 없거나 계정 인증 변경이 진행 중이면 {@link
 * AuthenticationUnavailableException}이다.
 */
public interface SessionStore {
  /**
   * 계정의 인증 가드가 ACTIVE이고 인증 버전이 같을 때만 고정 30분 세션을 저장한다. 가드가 없으면 DB에서 확인한 버전으로 만든다.
   *
   * @return 만료 시각. 정지·삭제로 확정된 가드나 다른 인증 버전이면 비어 있다
   */
  Optional<Instant> issue(ActorKind kind, UUID accountId, long authVersion, String tokenHash);

  /** 해당 계정의 세션이면 삭제하고 true. 이미 없거나 다른 계정의 세션이면 false다. */
  boolean revoke(ActorKind kind, UUID accountId, String tokenHash);
}
