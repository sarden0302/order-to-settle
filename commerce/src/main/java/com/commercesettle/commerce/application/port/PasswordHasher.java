package com.commercesettle.commerce.application.port;

/** 비밀번호 원문을 저장용 해시로 바꾼다. 해시에는 알고리즘·설정·사용자별 salt가 함께 들어간다. */
public interface PasswordHasher {
  String hash(String rawPassword);
}
