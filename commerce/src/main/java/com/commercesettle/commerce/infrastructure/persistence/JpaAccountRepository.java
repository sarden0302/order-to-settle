package com.commercesettle.commerce.infrastructure.persistence;

import com.commercesettle.commerce.domain.model.Account;
import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.commerce.domain.repository.AccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaAccountRepository implements AccountRepository {
  private final SpringDataBuyerAccountRepository buyers;
  private final SpringDataSellerAccountRepository sellers;

  JpaAccountRepository(
      SpringDataBuyerAccountRepository buyers, SpringDataSellerAccountRepository sellers) {
    this.buyers = buyers;
    this.sellers = sellers;
  }

  @Override
  public Optional<Account> findById(ActorKind kind, UUID id) {
    return switch (kind) {
      case BUYER -> buyers.findById(id).map(Account.class::cast);
      case SELLER -> sellers.findById(id).map(Account.class::cast);
    };
  }
}
