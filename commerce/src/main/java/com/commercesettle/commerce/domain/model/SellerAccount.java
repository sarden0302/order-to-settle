package com.commercesettle.commerce.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 판매자 계정. 지급 계좌 참조는 정산 지급에 쓰는 대역 계좌 식별자다. */
@Entity
@Table(name = "seller_accounts")
public class SellerAccount extends Account {
  private String payoutAccountRef;

  protected SellerAccount() {}

  @Override
  public ActorKind kind() {
    return ActorKind.SELLER;
  }

  public String getPayoutAccountRef() {
    return payoutAccountRef;
  }
}
