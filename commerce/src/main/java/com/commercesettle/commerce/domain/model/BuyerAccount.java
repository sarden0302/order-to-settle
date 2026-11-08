package com.commercesettle.commerce.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "buyer_accounts")
public class BuyerAccount extends Account {
  protected BuyerAccount() {}

  @Override
  public ActorKind kind() {
    return ActorKind.BUYER;
  }
}
