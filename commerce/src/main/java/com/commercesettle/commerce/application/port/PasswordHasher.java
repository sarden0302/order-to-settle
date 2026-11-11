package com.commercesettle.commerce.application.port;

/** 비밀번호 원문을 저장용 해시로 바꾸고 확인한다. 해시에는 알고리즘·설정·사용자별 salt가 함께 들어간다. */
public interface PasswordHasher {
  String hash(String rawPassword);

  /**
   * 원문이 저장된 해시와 맞는지 확인한다. 해시가 null(계정 없음)이어도 같은 비용의 검사를 수행하고 false를 돌려줘, 응답 시간으로 계정 존재 여부가 드러나지 않게
   * 한다.
   */
  boolean matches(String rawPassword, String storedHash);
}
