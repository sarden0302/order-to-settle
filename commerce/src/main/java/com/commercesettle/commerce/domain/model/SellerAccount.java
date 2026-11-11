package com.commercesettle.commerce.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 판매자 계정. 지급 계좌 참조는 정산 지급에 쓰는 대역 계좌 식별자다. */
@Entity
@Table(name = "seller_accounts")
public class SellerAccount extends Account {
  private String payoutAccountRef;

  protected SellerAccount() {}

  private SellerAccount(
      UUID id,
      String emailNormalized,
      String passwordHash,
      String displayName,
      String payoutAccountRef,
      Instant now) {
    super(id, emailNormalized, passwordHash, displayName, now);
    this.payoutAccountRef = Objects.requireNonNull(payoutAccountRef, "payoutAccountRef");
  }

  public static SellerAccount register(
      UUID id,
      String emailNormalized,
      String passwordHash,
      String displayName,
      String payoutAccountRef,
      Instant now) {
    return new SellerAccount(id, emailNormalized, passwordHash, displayName, payoutAccountRef, now);
  }

  @Override
  public ActorKind kind() {
    return ActorKind.SELLER;
  }

  public String getPayoutAccountRef() {
    return payoutAccountRef;
  }
}
