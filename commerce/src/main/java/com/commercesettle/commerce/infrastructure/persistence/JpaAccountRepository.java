package com.commercesettle.commerce.infrastructure.persistence;

import com.commercesettle.commerce.domain.exception.AccountEmailDuplicatedException;
import com.commercesettle.commerce.domain.model.Account;
import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.commerce.domain.model.BuyerAccount;
import com.commercesettle.commerce.domain.model.SellerAccount;
import com.commercesettle.commerce.domain.repository.AccountRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Repository;

@Repository
class JpaAccountRepository implements AccountRepository {
  private static final Set<String> EMAIL_CONSTRAINTS =
      Set.of("uk_buyer_accounts_email", "uk_seller_accounts_email");

  private final SpringDataBuyerAccountRepository buyers;
  private final SpringDataSellerAccountRepository sellers;
  private final JdbcOperations jdbc;

  JpaAccountRepository(
      SpringDataBuyerAccountRepository buyers,
      SpringDataSellerAccountRepository sellers,
      JdbcOperations jdbc) {
    this.buyers = buyers;
    this.sellers = sellers;
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Account> findById(ActorKind kind, UUID id) {
    return switch (kind) {
      case BUYER -> buyers.findById(id).map(Account.class::cast);
      case SELLER -> sellers.findById(id).map(Account.class::cast);
    };
  }

  @Override
  public boolean emailExists(ActorKind kind, String emailNormalized) {
    return switch (kind) {
      case BUYER -> buyers.existsByEmailNormalized(emailNormalized);
      case SELLER -> sellers.existsByEmailNormalized(emailNormalized);
    };
  }

  @Override
  public Optional<Account> findByEmail(ActorKind kind, String emailNormalized) {
    return switch (kind) {
      case BUYER -> buyers.findByEmailNormalized(emailNormalized).map(Account.class::cast);
      case SELLER -> sellers.findByEmailNormalized(emailNormalized).map(Account.class::cast);
    };
  }

  @Override
  public <T extends Account> T add(T account) {
    TransactionLimits.apply(jdbc);
    try {
      return switch (account) {
        case BuyerAccount buyer -> cast(buyers.saveAndFlush(buyer));
        case SellerAccount seller -> cast(sellers.saveAndFlush(seller));
        default -> throw new IllegalArgumentException("unknown account kind " + account.kind());
      };
    } catch (DataIntegrityViolationException e) {
      if (EMAIL_CONSTRAINTS.contains(constraintName(e))) {
        throw new AccountEmailDuplicatedException();
      }
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Account> T cast(Account account) {
    return (T) account;
  }

  private static String constraintName(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        return violation.getConstraintName();
      }
    }
    return null;
  }
}
