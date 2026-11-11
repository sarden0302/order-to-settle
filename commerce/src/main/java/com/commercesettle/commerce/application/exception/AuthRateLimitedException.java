package com.commercesettle.commerce.application.exception;

/** 계정 식별자별 고정 시간창의 상한을 이미 사용했다. 응답의 Retry-After는 남은 시간창을 초 단위로 올림한 양의 정수다. */
public class AuthRateLimitedException extends RuntimeException {
  private final long retryAfterSeconds;

  public AuthRateLimitedException(long retryAfterSeconds) {
    super("authentication request rate limited");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
