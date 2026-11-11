package com.commercesettle.commerce.infrastructure.cache;

import com.commercesettle.commerce.application.exception.AuthenticationUnavailableException;
import com.commercesettle.commerce.infrastructure.config.AuthProperties;
import io.lettuce.core.event.connection.DisconnectedEvent;
import io.lettuce.core.resource.ClientResources;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 사용자 인증의 Redis 게이트. 앱 기동과 인증 허용은 별개라, 첫 인증 요청에서 Redis의 실행 식별자가 승인한 값과 같을 때만 연다. 열린 뒤 연결이 끊기거나 식별자가
 * 다르면 닫고, 재접속만으로는 다시 열지 않는다. AOF로 복원된 이전 세션·가드를 그대로 믿지 않기 위해서이며, 다시 열려면 인증 세대를 바꾸고 앱을 재기동한다
 * (docs/development.md#redis-세션-검증과-복구).
 */
@Component
public class AuthRedisGate {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthRedisGate.class);
  private static final Pattern RUN_ID = Pattern.compile("[0-9a-f]{40}");

  private enum State {
    UNVERIFIED,
    OPEN,
    CLOSED
  }

  private final StringRedisTemplate redis;
  private final String approvedRunId;
  private final AtomicReference<State> state = new AtomicReference<>(State.UNVERIFIED);

  public AuthRedisGate(
      StringRedisTemplate redis, AuthProperties properties, ClientResources clientResources) {
    this.redis = redis;
    this.approvedRunId = properties.redisRunId();
    clientResources
        .eventBus()
        .get()
        .filter(DisconnectedEvent.class::isInstance)
        .subscribe(event -> closeIfOpen());
  }

  /** 확인 전 단절은 다음 요청의 실행 식별자 확인이 대신하므로, 연 뒤의 단절만 닫는다. */
  private void closeIfOpen() {
    if (state.compareAndSet(State.OPEN, State.CLOSED)) {
      log("DISCONNECTED");
    }
  }

  /** 열려 있지 않으면 {@link AuthenticationUnavailableException}. 처음 한 번은 Redis의 실행 식별자를 확인해 연다. */
  public void requireOpen() {
    State current = state.get();
    if (current == State.OPEN) {
      return;
    }
    if (current == State.UNVERIFIED && verify()) {
      return;
    }
    throw new AuthenticationUnavailableException("user authentication gate is closed");
  }

  private boolean verify() {
    if (!RUN_ID.matcher(approvedRunId).matches()) {
      close("RUN_ID_NOT_CONFIGURED");
      return false;
    }
    String runId;
    try {
      RedisCallback<Properties> info = connection -> connection.serverCommands().info("server");
      Properties server = redis.execute(info);
      runId = server == null ? null : server.getProperty("run_id");
    } catch (DataAccessException e) {
      // 확인하지 못했을 뿐 불일치가 확정된 것은 아니므로 닫지 않고 다음 요청에서 다시 확인한다.
      throw new AuthenticationUnavailableException("redis run_id is not verifiable", e);
    }
    if (!approvedRunId.equals(runId)) {
      close("RUN_ID_MISMATCH");
      return false;
    }
    return state.compareAndSet(State.UNVERIFIED, State.OPEN) || state.get() == State.OPEN;
  }

  private void close(String reason) {
    if (state.getAndSet(State.CLOSED) != State.CLOSED) {
      log(reason);
    }
  }

  private static void log(String reason) {
    LOGGER
        .atError()
        .addKeyValue("event", "auth.redis-gate")
        .addKeyValue("reason", reason)
        .log("User authentication gate closed");
  }
}
