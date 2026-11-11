package com.commercesettle.commerce.application.exception;

/** 로그인 자격 오류·정지/삭제 계정·발급 중 인증 버전 변경, 이미 없는 세션의 로그아웃. 계정 존재 여부를 구분하지 않도록 사유를 밖으로 내보내지 않는다. */
public class AuthenticationFailedException extends RuntimeException {
  public AuthenticationFailedException() {
    super("authentication failed");
  }
}
