package com.commercesettle.commerce.application.service;

import com.commercesettle.commerce.application.exception.AuthenticationFailedException;
import com.commercesettle.commerce.application.port.AuthRateLimiter;
import com.commercesettle.commerce.application.port.PasswordHasher;
import com.commercesettle.commerce.application.port.ServerHmac;
import com.commercesettle.commerce.application.port.SessionStore;
import com.commercesettle.commerce.domain.model.Account;
import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.commerce.domain.repository.AccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 로그인과 로그아웃. 로그인은 요청 제한 → 계정 조회·비밀번호 확인 → Redis 세션 발급 순서다. 자격 오류·정지/삭제 계정·발급 중 인증 버전 변경은 같은 {@link
 * AuthenticationFailedException}이라 계정 존재 여부가 드러나지 않는다(docs/policies.md#세션-발급과-검증).
 */
@Service
public class SessionService {
  private static final int TOKEN_BYTES = 32;

  private final AccountRepository accounts;
  private final PasswordHasher passwords;
  private final ServerHmac hmac;
  private final AuthRateLimiter rateLimiter;
  private final SessionStore sessions;
  private final SecureRandom random = new SecureRandom();

  public SessionService(
      AccountRepository accounts,
      PasswordHasher passwords,
      ServerHmac hmac,
      AuthRateLimiter rateLimiter,
      SessionStore sessions) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.hmac = hmac;
    this.rateLimiter = rateLimiter;
    this.sessions = sessions;
  }

  /** 계정이 없어도 요청 제한을 세고 같은 비용의 비밀번호 검사를 거친다. 토큰 원문은 응답에만 담고 저장하지 않는다. */
  public IssuedSession login(ActorKind kind, String email, String password) {
    String normalized = Account.normalizeEmail(email);
    rateLimiter.acquireLogin(kind, hmac.hex(normalized));
    Optional<Account> found = accounts.findByEmail(kind, normalized);
    boolean passwordMatches =
        passwords.matches(password, found.map(Account::getPasswordHash).orElse(null));
    if (!passwordMatches || found.isEmpty() || !found.get().canLogIn()) {
      throw new AuthenticationFailedException();
    }
    Account account = found.get();
    String token = newToken();
    Instant expiresAt =
        sessions
            .issue(kind, account.getId(), account.getAuthVersion(), tokenHash(token))
            .orElseThrow(AuthenticationFailedException::new);
    return new IssuedSession(token, expiresAt, account);
  }

  /** Gateway가 검증한 현재 세션만 지운다. 이미 없는 세션이면 {@link AuthenticationFailedException}이며 복원하지 않는다. */
  public void logout(ActorKind kind, UUID accountId, String sessionHash) {
    if (!sessions.revoke(kind, accountId, sessionHash)) {
      throw new AuthenticationFailedException();
    }
  }

  /** 256비트 임의값의 URL-safe Base64(패딩 없음, 43자). */
  private String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 세션 키와 Gateway의 X-Authenticated-Session-Hash가 쓰는 SHA-256 소문자 16진수. */
  static String tokenHash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the JDK", e);
    }
  }

  /** 발급한 세션. {@code token}은 이 응답에서만 원문으로 나간다. */
  public record IssuedSession(String token, Instant expiresAt, Account account) {
    @Override
    public String toString() {
      return "IssuedSession[token=<redacted>, expiresAt=%s, account=%s]"
          .formatted(expiresAt, account.getId());
    }
  }
}
