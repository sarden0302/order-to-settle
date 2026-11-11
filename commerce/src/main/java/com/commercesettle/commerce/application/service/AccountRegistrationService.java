package com.commercesettle.commerce.application.service;

import com.commercesettle.commerce.application.port.PasswordHasher;
import com.commercesettle.commerce.application.port.ServerHmac;
import com.commercesettle.commerce.domain.exception.AccountEmailDuplicatedException;
import com.commercesettle.commerce.domain.model.Account;
import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.commerce.domain.model.BuyerAccount;
import com.commercesettle.commerce.domain.model.SellerAccount;
import com.commercesettle.commerce.domain.repository.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구매자·판매자 가입. 비밀번호 해시는 계산 시간 동안 DB 커넥션을 잡지 않도록 트랜잭션 전에 만든다. 웰컴 쿠폰 발급은 쿠폰 기능을 빌드에 되돌릴 때 구매자 가입 트랜잭션에
 * 붙인다(docs/policies.md#구매자-가입-재시도).
 */
@Service
public class AccountRegistrationService {
  /** 계정 ID가 생기기 전이라 서버가 고정한 멱등 범위(docs/data-model.md#idempotency_records). */
  static final String SIGNUP_ACTOR_KIND = "PUBLIC";

  static final String SIGNUP_ACTOR_ID = "BUYER_REGISTRATION";
  static final String SIGNUP_OPERATION = "REGISTER";
  private static final int CREATED = 201;

  private final AccountRepository accounts;
  private final IdempotentRequests idempotentRequests;
  private final PasswordHasher passwords;
  private final ServerHmac hmac;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public AccountRegistrationService(
      AccountRepository accounts,
      IdempotentRequests idempotentRequests,
      PasswordHasher passwords,
      ServerHmac hmac,
      TransactionTemplate transactions,
      Clock clock) {
    this.accounts = accounts;
    this.idempotentRequests = idempotentRequests;
    this.passwords = passwords;
    this.hmac = hmac;
    this.transactions = transactions;
    this.clock = clock;
  }

  /**
   * 같은 키·본문의 24시간 안 재요청은 처음 만든 계정을 돌려준다. 본문 비교값은 비밀번호 원문을 포함하므로 SHA-256 대신 서버 키 HMAC으로 만든다. 다른 키의
   * 같은 이메일은 {@link AccountEmailDuplicatedException}이다.
   */
  public BuyerAccount registerBuyer(
      String idempotencyKey, String email, String password, String displayName) {
    String normalized = Account.normalizeEmail(email);
    String requestHmac =
        hmac.hex(IdempotentRequests.fingerprint(List.of(normalized, password, displayName)));
    IdempotentRequests.Request request =
        new IdempotentRequests.Request(
            SIGNUP_ACTOR_KIND,
            SIGNUP_ACTOR_ID,
            SIGNUP_OPERATION,
            idempotencyKey,
            List.of(requestHmac));
    String passwordHash = passwords.hash(password);
    UUID id = UUID.randomUUID();
    return transactions.execute(
        status ->
            idempotentRequests.execute(
                request,
                id,
                CREATED,
                recordId ->
                    add(BuyerAccount.register(id, normalized, passwordHash, displayName, now())),
                existingId -> buyer(existingId)));
  }

  /** 판매자 가입은 멱등키를 받지 않는다. 같은 이메일은 삭제한 계정이어도 {@link AccountEmailDuplicatedException}이다. */
  public SellerAccount registerSeller(
      String email, String password, String displayName, String payoutAccountRef) {
    String normalized = Account.normalizeEmail(email);
    String passwordHash = passwords.hash(password);
    UUID id = UUID.randomUUID();
    return transactions.execute(
        status ->
            add(
                SellerAccount.register(
                    id, normalized, passwordHash, displayName, payoutAccountRef, now())));
  }

  private <T extends Account> T add(T account) {
    if (accounts.emailExists(account.kind(), account.getEmailNormalized())) {
      throw new AccountEmailDuplicatedException();
    }
    return accounts.add(account);
  }

  /** 완료 기록과 계정은 같은 트랜잭션에 저장했고 계정은 물리 삭제하지 않으므로 항상 있다. */
  private BuyerAccount buyer(UUID id) {
    return (BuyerAccount)
        accounts
            .findById(ActorKind.BUYER, id)
            .orElseThrow(() -> new IllegalStateException("registered buyer is missing"));
  }

  private Instant now() {
    return clock.instant();
  }
}
