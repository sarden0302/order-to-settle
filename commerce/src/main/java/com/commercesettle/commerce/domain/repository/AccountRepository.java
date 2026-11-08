package com.commercesettle.commerce.domain.repository;

import com.commercesettle.commerce.domain.model.Account;
import com.commercesettle.commerce.domain.model.ActorKind;
import java.util.Optional;
import java.util.UUID;

/** 계정 종류별 테이블을 하나의 조회로 감싼다. 구매자 ID로 판매자 계정을 찾지 않는다. */
public interface AccountRepository {
  Optional<Account> findById(ActorKind kind, UUID id);
}
