package com.commercesettle.commerce.domain.repository;

import com.commercesettle.commerce.domain.exception.AccountEmailDuplicatedException;
import com.commercesettle.commerce.domain.model.Account;
import com.commercesettle.commerce.domain.model.ActorKind;
import java.util.Optional;
import java.util.UUID;

/** 계정 종류별 테이블을 하나의 조회로 감싼다. 구매자 ID로 판매자 계정을 찾지 않는다. */
public interface AccountRepository {
  Optional<Account> findById(ActorKind kind, UUID id);

  /** 삭제한 계정도 포함한다. 이메일은 정규화한 값이다. */
  boolean emailExists(ActorKind kind, String emailNormalized);

  /** 삭제·정지한 계정도 돌려준다. 로그인 가능 여부는 호출자가 계정 상태로 판단한다. */
  Optional<Account> findByEmail(ActorKind kind, String emailNormalized);

  /**
   * 새 계정을 즉시 DB에 반영한다. 같은 종류의 이메일을 다른 트랜잭션이 먼저 저장했다면 그 트랜잭션이 끝나기를 잠금 대기 상한까지 기다린 뒤 {@link
   * AccountEmailDuplicatedException}을 던진다.
   */
  <T extends Account> T add(T account);
}
