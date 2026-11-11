package com.commercesettle.commerce.infrastructure.security;

import com.commercesettle.commerce.application.port.PasswordHasher;
import java.util.UUID;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/** Argon2id. 시작 설정은 메모리 19MiB·반복 2·병렬 1이다(docs/policies.md#비밀번호와-공통-요청). */
@Component
class Argon2PasswordHasher implements PasswordHasher {
  private static final int SALT_BYTES = 16;
  private static final int HASH_BYTES = 32;
  private static final int PARALLELISM = 1;
  private static final int MEMORY_KIB = 19 * 1024;
  private static final int ITERATIONS = 2;

  private final Argon2PasswordEncoder encoder =
      new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);

  /** 계정이 없을 때 대신 검사할 해시. 같은 설정으로 만들어 검사 비용을 맞춘다. 어떤 원문과도 맞지 않게 임의 값으로 만든다. */
  private final String absentAccountHash = encoder.encode(UUID.randomUUID().toString());

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String storedHash) {
    if (storedHash == null) {
      encoder.matches(rawPassword, absentAccountHash);
      return false;
    }
    return encoder.matches(rawPassword, storedHash);
  }
}
