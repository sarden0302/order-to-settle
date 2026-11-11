package com.commercesettle.commerce.application.exception;

/** 인증 저장소를 확인할 수 없거나 인증 게이트가 닫혔다. 요청 제한·세션 발급·삭제를 확정하지 못했으므로 토큰을 반환하거나 성공으로 안내하지 않는다. */
public class AuthenticationUnavailableException extends RuntimeException {
  public AuthenticationUnavailableException(String message) {
    super(message);
  }

  public AuthenticationUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
