package com.commercesettle.commerce.application.service;

import static com.commercesettle.commerce.support.ConcurrentTransactions.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commercesettle.commerce.application.exception.IdempotencyConflictException;
import com.commercesettle.commerce.domain.exception.AccountEmailDuplicatedException;
import com.commercesettle.commerce.domain.model.AccountStatus;
import com.commercesettle.commerce.domain.model.BuyerAccount;
import com.commercesettle.commerce.domain.model.SellerAccount;
import java.util.List;
import java.util.Map;
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

/** 실제 PostgreSQL에서 가입의 계정·멱등 기록 원자 저장과 이메일 유일성을 확인한다. */
@SpringBootTest(
    properties = {
      "management.tracing.enabled=false",
      "app.auth.session-epoch=00000000-0000-4000-8000-000000000000",
      "app.auth.rate-limit.identifier-hmac-key=account-registration-test-hmac-key-32bytes",
      "app.internal-auth.senders.gateway.current=test-gateway-credential-0123456789abcdef0123"
    })
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountRegistrationIntegrationTest {
  private static final String PASSWORD = "correct-horse-battery";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18.3").withInitScript("db/commerce.sql");

  @Autowired AccountRegistrationService registrations;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("구매자 가입은 정규화한 이메일·Argon2id 해시로 활성 계정과 멱등 완료 기록을 함께 저장한다")
  void registersBuyerWithIdempotencyRecord() {
    String email = unique("Buyer");

    BuyerAccount buyer =
        registrations.registerBuyer(key(), "  " + email.toUpperCase() + " ", PASSWORD, "구매자");

    Map<String, Object> row =
        jdbc.queryForMap("select * from commerce.buyer_accounts where id = ?", buyer.getId());
    assertThat(row.get("email_normalized")).isEqualTo(email.toLowerCase());
    assertThat((String) row.get("password_hash"))
        .startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
        .doesNotContain(PASSWORD);
    assertThat(row.get("status")).isEqualTo(AccountStatus.ACTIVE.name());
    assertThat(row.get("auth_version")).isEqualTo(0L);
    assertThat(row.get("version")).isEqualTo(0L);
    Map<String, Object> record =
        jdbc.queryForMap(
            "select * from commerce.idempotency_records where resource_id = ?", buyer.getId());
    assertThat(record)
        .containsEntry("actor_kind", "PUBLIC")
        .containsEntry("actor_id", "BUYER_REGISTRATION")
        .containsEntry("operation", "REGISTER")
        .containsEntry("response_status", 201)
        .containsEntry("status", "COMPLETED");
  }

  @Test
  @DisplayName("같은 키·본문으로 다시 가입하면 처음 만든 계정을 돌려주고 새로 만들지 않는다")
  void replaysSameRegistration() {
    String email = unique("replay");
    String key = key();

    BuyerAccount first = registrations.registerBuyer(key, email, PASSWORD, "구매자");
    BuyerAccount replay = registrations.registerBuyer(key, email, PASSWORD, "구매자");

    assertThat(replay.getId()).isEqualTo(first.getId());
    assertThat(buyers(email)).isEqualTo(1);
  }

  @Test
  @DisplayName("같은 키에 비밀번호만 다른 본문은 멱등 충돌이며 계정을 바꾸지 않는다")
  void rejectsSameKeyWithDifferentPassword() {
    String email = unique("conflict");
    String key = key();
    BuyerAccount first = registrations.registerBuyer(key, email, PASSWORD, "구매자");
    String hash = passwordHash(first.getId());

    assertThatThrownBy(() -> registrations.registerBuyer(key, email, PASSWORD + "!", "구매자"))
        .isInstanceOf(IdempotencyConflictException.class);

    assertThat(buyers(email)).isEqualTo(1);
    assertThat(passwordHash(first.getId())).isEqualTo(hash);
  }

  @Test
  @DisplayName("다른 키로 같은 이메일을 가입하면 중복 이메일이며 그 키의 멱등 기록도 남기지 않는다")
  void rejectsDuplicateEmailWithAnotherKey() {
    String email = unique("duplicate");
    registrations.registerBuyer(key(), email, PASSWORD, "구매자");
    long records = count("idempotency_records where actor_id = 'BUYER_REGISTRATION'");

    assertThatThrownBy(() -> registrations.registerBuyer(key(), email, PASSWORD, "다른 구매자"))
        .isInstanceOf(AccountEmailDuplicatedException.class);

    assertThat(buyers(email)).isEqualTo(1);
    assertThat(count("idempotency_records where actor_id = 'BUYER_REGISTRATION'"))
        .isEqualTo(records);
  }

  @Test
  @DisplayName("다른 키로 같은 이메일을 동시에 가입하면 하나만 저장하고 나머지는 중복 이메일이다")
  void allowsOneOfConcurrentSameEmailRegistrations() throws Exception {
    String email = unique("race");

    List<Object> results =
        runConcurrently(
            2, () -> registrations.registerBuyer(key(), email, PASSWORD, "구매자").getId());

    assertThat(buyers(email)).isEqualTo(1);
    assertThat(results).filteredOn(UUID.class::isInstance).hasSize(1);
    assertThat(results).filteredOn(AccountEmailDuplicatedException.class::isInstance).hasSize(1);
  }

  @Test
  @DisplayName("판매자 가입은 구매자와 별도로 이메일 유일성을 보고 지급 계좌 참조를 저장한다")
  void registersSellerSeparatelyFromBuyer() {
    String email = unique("both");
    registrations.registerBuyer(key(), email, PASSWORD, "구매자");

    SellerAccount seller = registrations.registerSeller(email, PASSWORD, "판매자", "payout-1");

    assertThat(seller.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(
            jdbc.queryForObject(
                "select payout_account_ref from commerce.seller_accounts where id = ?",
                String.class,
                seller.getId()))
        .isEqualTo("payout-1");
  }

  @Test
  @DisplayName("삭제한 판매자의 이메일로도 다시 가입할 수 없다")
  void rejectsEmailOfDeletedSeller() {
    String email = unique("deleted");
    SellerAccount seller = registrations.registerSeller(email, PASSWORD, "판매자", "payout-1");
    jdbc.update(
        "update commerce.seller_accounts set status = 'DELETED', deleted_at = now() where id = ?",
        seller.getId());

    assertThatThrownBy(
            () -> registrations.registerSeller(email.toUpperCase(), PASSWORD, "판매자", "payout-2"))
        .isInstanceOf(AccountEmailDuplicatedException.class);

    assertThat(count("seller_accounts where email_normalized = '" + email + "'")).isEqualTo(1);
  }

  private long buyers(String email) {
    return count("buyer_accounts where email_normalized = '" + email + "'");
  }

  private String passwordHash(UUID buyer) {
    return jdbc.queryForObject(
        "select password_hash from commerce.buyer_accounts where id = ?", String.class, buyer);
  }

  private long count(String fromWhere) {
    return jdbc.queryForObject("select count(*) from commerce." + fromWhere, Long.class);
  }

  private static String unique(String name) {
    return (name + "-" + UUID.randomUUID() + "@example.com").toLowerCase();
  }

  private static String key() {
    return UUID.randomUUID().toString();
  }
}
