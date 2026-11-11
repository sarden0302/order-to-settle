package com.commercesettle.commerce.application.port;

/**
 * 서버 키 기반 HMAC-SHA256. 원문 대신 저장·비교해야 하는 값(가입 요청 본문, 요청 제한의 이메일 식별자)에 쓴다. 키가 없으면 결과를 다시 만들 수 없어 저장값이
 * 노출돼도 원문을 추측해 맞춰 볼 수 없다.
 */
public interface ServerHmac {
  /** 소문자 16진수 64자. */
  String hex(String value);
}
