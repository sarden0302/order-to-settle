package com.commercesettle.commerce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.commercesettle.commerce.domain.model.Account;
import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.commerce.domain.model.SellerAccount;
import com.commercesettle.commerce.domain.repository.AccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 초기 SQL의 계정 테이블과 엔티티 매핑이 맞고, 계정 종류별 테이블을 섞어 찾지 않는지 확인한다. */
@SpringBootTest(
    properties = {
      "management.tracing.enabled=false",
      "app.auth.session-epoch=00000000-0000-4000-8000-000000000000",
      "app.auth.rate-limit.identifier-hmac-key=account-repository-test-hmac-key-32bytes",
      "app.internal-auth.senders.gateway.current=test-gateway-credential-0123456789abcdef0123"
    })
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountRepositoryIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18.3").withInitScript("db/commerce.sql");

  @Autowired AccountRepository accounts;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("구매자·판매자 계정을 종류별로 찾고, 다른 종류의 ID로는 찾지 않는다")
  void findsAccountByKind() {
    UUID buyer = buyer("ACTIVE");
    UUID seller = UUID.randomUUID();
    jdbc.update(
        "insert into commerce.seller_accounts (id, email_normalized, password_hash, display_name,"
            + " status, payout_account_ref, created_at, updated_at, version)"
            + " values (?, ?, 'hash', 'name', 'ACTIVE', 'payout-1', now(), now(), 0)",
        seller,
        seller + "@test.example");

    Account foundBuyer = accounts.findById(ActorKind.BUYER, buyer).orElseThrow();
    Account foundSeller = accounts.findById(ActorKind.SELLER, seller).orElseThrow();

    assertThat(foundBuyer.kind()).isEqualTo(ActorKind.BUYER);
    assertThat(foundBuyer.getEmailNormalized()).isEqualTo(buyer + "@test.example");
    assertThat(foundSeller.kind()).isEqualTo(ActorKind.SELLER);
    assertThat(((SellerAccount) foundSeller).getPayoutAccountRef()).isEqualTo("payout-1");
    assertThat(accounts.findById(ActorKind.SELLER, buyer)).isEmpty();
    assertThat(accounts.findById(ActorKind.BUYER, seller)).isEmpty();
  }

  @Test
  @DisplayName("정지·삭제 계정은 로그인할 수 없는 계정으로 읽는다")
  void readsLoginEligibilityFromStatus() {
    assertThat(accounts.findById(ActorKind.BUYER, buyer("ACTIVE")).orElseThrow().canLogIn())
        .isTrue();
    assertThat(accounts.findById(ActorKind.BUYER, buyer("SUSPENDED")).orElseThrow().canLogIn())
        .isFalse();
    assertThat(accounts.findById(ActorKind.BUYER, buyer("DELETED")).orElseThrow().canLogIn())
        .isFalse();
  }

  private UUID buyer(String status) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into commerce.buyer_accounts (id, email_normalized, password_hash, display_name,"
            + " status, created_at, updated_at, version, deleted_at)"
            + " values (?, ?, 'hash', 'name', ?, now(), now(), 0,"
            + " case when ? = 'DELETED' then now() end)",
        id,
        id + "@test.example",
        status,
        status);
    return id;
  }
}
