package com.commercesettle.commerce.domain.exception;

/** 같은 계정 종류에 정규화한 이메일이 이미 있다. 삭제한 계정의 이메일도 다시 쓰지 않는다. 메시지에 이메일을 넣지 않는다. */
public class AccountEmailDuplicatedException extends RuntimeException {
  public AccountEmailDuplicatedException() {
    super("account email is already registered");
  }
}
