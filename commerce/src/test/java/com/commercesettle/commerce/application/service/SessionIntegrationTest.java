package com.commercesettle.commerce.application.service;

import static com.commercesettle.commerce.support.ConcurrentTransactions.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commercesettle.commerce.application.exception.AuthRateLimitedException;
import com.commercesettle.commerce.application.exception.AuthenticationFailedException;
import com.commercesettle.commerce.application.exception.AuthenticationUnavailableException;
import com.commercesettle.commerce.application.port.AuthRateLimiter;
import com.commercesettle.commerce.application.service.SessionService.IssuedSession;
import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.commerce.domain.model.BuyerAccount;
import com.commercesettle.commerce.infrastructure.cache.AuthRedisGate;
import com.commercesettle.commerce.infrastructure.config.AuthProperties;
import io.lettuce.core.resource.ClientResources;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 실제 PostgreSQL·Redis에서 로그인 요청 제한, 세션·가드 원자 발급, 로그아웃을 확인한다. Lua 판정 분기는 실제 Redis에서만 확인할 수 있다. */
@SpringBootTest(
    properties = {
      "management.tracing.enabled=false",
      "app.auth.session-epoch=" + SessionIntegrationTest.EPOCH,
      "app.auth.rate-limit.identifier-hmac-key=session-test-hmac-key-32bytes-long",
      "app.internal-auth.senders.gateway.current=test-gateway-credential-0123456789abcdef0123"
    })
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SessionIntegrationTest {
  static final String EPOCH = "6f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f";
  private static final String PASSWORD = "correct-horse-battery";
  private static final RedisScript<Long> EXPIRE_TIME =
      RedisScript.of("return redis.call('PEXPIRETIME', KEYS[1])", Long.class);

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18.3").withInitScript("db/commerce.sql");

  @Container
  @ServiceConnection(name = "redis")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.10.2-alpine").withExposedPorts(6379);

  @Autowired SessionService sessions;
  @Autowired AccountRegistrationService registrations;
  @Autowired AuthRateLimiter rateLimiter;
  @Autowired StringRedisTemplate redis;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthProperties properties;
  @Autowired ClientResources clientResources;

  @DynamicPropertySource
  static void approvedRunId(DynamicPropertyRegistry registry) {
    registry.add("app.auth.redis-run-id", SessionIntegrationTest::redisRunId);
  }

  @Test
  @DisplayName("로그인은 토큰 해시 키에 고정 30분 세션을 저장하고 키 만료를 expiresAt에 맞추며 가드를 만든다")
  void issuesFixedSessionAndGuard() {
    BuyerAccount buyer = buyer();

    IssuedSession issued = sessions.login(ActorKind.BUYER, buyer.getEmailNormalized(), PASSWORD);

    String key = sessionKey(issued.token());
    Map<Object, Object> session = redis.opsForHash().entries(key);
    long issuedAt = Long.parseLong((String) session.get("issuedAt"));
    long expiresAt = Long.parseLong((String) session.get("expiresAt"));
    assertThat(issued.token()).hasSize(43);
    assertThat(session)
        .containsEntry("actorKind", "BUYER")
        .containsEntry("actorId", buyer.getId().toString())
        .containsEntry("purpose", "SESSION")
        .containsEntry("authVersion", "0");
    assertThat(expiresAt - issuedAt).isEqualTo(Duration.ofMinutes(30).toMillis());
    assertThat(issued.expiresAt().toEpochMilli()).isEqualTo(expiresAt);
    assertThat(redis.execute(EXPIRE_TIME, List.of(key))).isEqualTo(expiresAt);
    assertThat(redis.opsForHash().entries(guardKey(buyer.getId())))
        .containsEntry("authVersion", "0")
        .containsEntry("state", "ACTIVE")
        .doesNotContainKey("pendingChangeId");
  }

  @Test
  @DisplayName("틀린 비밀번호·없는 계정·정지 계정은 같은 인증 실패이며 가드를 만들지 않는다")
  void rejectsWrongPasswordUnknownAndSuspendedAlike() {
    BuyerAccount buyer = buyer();
    BuyerAccount suspended = buyer();
    jdbc.update(
        "update commerce.buyer_accounts set status = 'SUSPENDED' where id = ?", suspended.getId());

    assertThatThrownBy(
            () -> sessions.login(ActorKind.BUYER, buyer.getEmailNormalized(), PASSWORD + "!"))
        .isInstanceOf(AuthenticationFailedException.class);
    assertThatThrownBy(() -> sessions.login(ActorKind.BUYER, email(), PASSWORD))
        .isInstanceOf(AuthenticationFailedException.class);
    assertThatThrownBy(
            () -> sessions.login(ActorKind.BUYER, suspended.getEmailNormalized(), PASSWORD))
        .isInstanceOf(AuthenticationFailedException.class);

    assertThat(redis.hasKey(guardKey(buyer.getId()))).isFalse();
    assertThat(redis.hasKey(guardKey(suspended.getId()))).isFalse();
  }

  @Test
  @DisplayName("다섯 번 시도한 뒤에는 맞는 비밀번호도 429이고 재시도로 시간창을 늘리지 않는다")
  void limitsAttemptsAfterFiveWithoutExtendingWindow() {
    BuyerAccount buyer = buyer();
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(
              () -> sessions.login(ActorKind.BUYER, buyer.getEmailNormalized(), PASSWORD + "!"))
          .isInstanceOf(AuthenticationFailedException.class);
    }

    AuthRateLimitedException first = rateLimited(buyer.getEmailNormalized());
    AuthRateLimitedException second = rateLimited(buyer.getEmailNormalized());

    assertThat(first.retryAfterSeconds()).isBetween(1L, 900L);
    assertThat(second.retryAfterSeconds()).isLessThanOrEqualTo(first.retryAfterSeconds());
    assertThat(redis.hasKey(guardKey(buyer.getId()))).isFalse();
  }

  @Test
  @DisplayName("같은 계정의 동시 시도도 시간창 상한인 다섯 번만 통과한다")
  void allowsOnlyLimitUnderConcurrency() throws Exception {
    String identifier = UUID.randomUUID().toString();

    List<Object> results =
        runConcurrently(
            12,
            () -> {
              rateLimiter.acquireLogin(ActorKind.SELLER, identifier);
              return "allowed";
            });

    assertThat(results).filteredOn("allowed"::equals).hasSize(5);
    assertThat(results).filteredOn(AuthRateLimitedException.class::isInstance).hasSize(7);
  }

  @Test
  @DisplayName("정지로 확정된 가드나 다른 인증 버전이면 401, 변경 중인 가드면 503이며 세션을 만들지 않는다")
  void respectsGuardState() {
    BuyerAccount blocked = buyer();
    BuyerAccount stale = buyer();
    BuyerAccount pending = buyer();
    guard(blocked.getId(), Map.of("authVersion", "0", "state", "BLOCKED"));
    guard(stale.getId(), Map.of("authVersion", "1", "state", "ACTIVE"));
    guard(
        pending.getId(),
        Map.of("authVersion", "0", "state", "BLOCKED", "pendingChangeId", UUID.randomUUID() + ""));

    assertThatThrownBy(
            () -> sessions.login(ActorKind.BUYER, blocked.getEmailNormalized(), PASSWORD))
        .isInstanceOf(AuthenticationFailedException.class);
    assertThatThrownBy(() -> sessions.login(ActorKind.BUYER, stale.getEmailNormalized(), PASSWORD))
        .isInstanceOf(AuthenticationFailedException.class);
    assertThatThrownBy(
            () -> sessions.login(ActorKind.BUYER, pending.getEmailNormalized(), PASSWORD))
        .isInstanceOf(AuthenticationUnavailableException.class);

    assertThat(redis.keys("commerce-settle:auth:session:" + EPOCH + ":*"))
        .allSatisfy(
            key ->
                assertThat(redis.opsForHash().get(key, "actorId"))
                    .isNotIn(
                        blocked.getId().toString(),
                        stale.getId().toString(),
                        pending.getId().toString()));
  }

  @Test
  @DisplayName("로그아웃은 자기 세션만 지우고, 이미 지운 세션이나 다른 계정의 세션이면 인증 실패다")
  void logoutRemovesOnlyOwnSession() {
    BuyerAccount owner = buyer();
    BuyerAccount other = buyer();
    String hash =
        SessionService.tokenHash(
            sessions.login(ActorKind.BUYER, owner.getEmailNormalized(), PASSWORD).token());

    assertThatThrownBy(() -> sessions.logout(ActorKind.BUYER, other.getId(), hash))
        .isInstanceOf(AuthenticationFailedException.class);
    assertThat(redis.hasKey(sessionKeyOfHash(hash))).isTrue();

    sessions.logout(ActorKind.BUYER, owner.getId(), hash);

    assertThat(redis.hasKey(sessionKeyOfHash(hash))).isFalse();
    assertThatThrownBy(() -> sessions.logout(ActorKind.BUYER, owner.getId(), hash))
        .isInstanceOf(AuthenticationFailedException.class);
  }

  @Test
  @DisplayName("승인한 실행 식별자가 Redis와 다르면 인증 게이트를 열지 않는다")
  void keepsGateClosedForUnapprovedRunId() {
    AuthProperties unapproved =
        new AuthProperties(properties.sessionEpoch(), "0".repeat(40), properties.rateLimit());
    AuthRedisGate gate = new AuthRedisGate(redis, unapproved, clientResources);

    assertThatThrownBy(gate::requireOpen).isInstanceOf(AuthenticationUnavailableException.class);
    assertThatThrownBy(gate::requireOpen).isInstanceOf(AuthenticationUnavailableException.class);
  }

  private AuthRateLimitedException rateLimited(String email) {
    try {
      sessions.login(ActorKind.BUYER, email, PASSWORD);
    } catch (AuthRateLimitedException e) {
      return e;
    }
    throw new AssertionError("login was not rate limited");
  }

  private BuyerAccount buyer() {
    return registrations.registerBuyer(UUID.randomUUID().toString(), email(), PASSWORD, "구매자");
  }

  private void guard(UUID buyer, Map<String, String> fields) {
    redis.opsForHash().putAll(guardKey(buyer), fields);
  }

  private static String sessionKey(String token) {
    return sessionKeyOfHash(SessionService.tokenHash(token));
  }

  private static String sessionKeyOfHash(String hash) {
    return "commerce-settle:auth:session:" + EPOCH + ":" + hash;
  }

  private static String guardKey(UUID buyer) {
    return "commerce-settle:auth:subject:" + EPOCH + ":BUYER:" + buyer;
  }

  private static String email() {
    return "session-" + UUID.randomUUID() + "@example.com";
  }

  private static String redisRunId() {
    try {
      String info = REDIS.execInContainer("redis-cli", "INFO", "server").getStdout();
      return info.lines()
          .filter(line -> line.startsWith("run_id:"))
          .map(line -> line.substring("run_id:".length()).strip())
          .findFirst()
          .orElseThrow();
    } catch (Exception e) {
      throw new IllegalStateException("cannot read redis run_id", e);
    }
  }
}
