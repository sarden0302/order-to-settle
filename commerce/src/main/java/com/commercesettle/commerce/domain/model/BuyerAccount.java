package com.commercesettle.commerce.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "buyer_accounts")
public class BuyerAccount extends Account {
  protected BuyerAccount() {}

  private BuyerAccount(
      UUID id, String emailNormalized, String passwordHash, String displayName, Instant now) {
    super(id, emailNormalized, passwordHash, displayName, now);
  }

  public static BuyerAccount register(
      UUID id, String emailNormalized, String passwordHash, String displayName, Instant now) {
    return new BuyerAccount(id, emailNormalized, passwordHash, displayName, now);
  }

  @Override
  public ActorKind kind() {
    return ActorKind.BUYER;
  }
}
