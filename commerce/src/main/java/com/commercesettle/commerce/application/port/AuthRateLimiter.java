package com.commercesettle.commerce.application.port;

import com.commercesettle.commerce.application.exception.AuthRateLimitedException;
import com.commercesettle.commerce.application.exception.AuthenticationUnavailableException;
import com.commercesettle.commerce.domain.model.ActorKind;

/** 계정 종류·이메일 식별자별 로그인 시도 수를 여러 인스턴스가 공유하는 고정 시간창으로 센다. */
public interface AuthRateLimiter {
  /**
   * 이번 시도를 세고, 상한을 이미 사용했으면 {@link AuthRateLimitedException}을 던진다. 확인·증가를 확정할 수 없으면 {@link
   * AuthenticationUnavailableException}이다.
   *
   * @param identifierHmac 정규화한 이메일의 서버 키 HMAC. 원문 이메일을 넘기지 않는다
   */
  void acquireLogin(ActorKind kind, String identifierHmac);
}
