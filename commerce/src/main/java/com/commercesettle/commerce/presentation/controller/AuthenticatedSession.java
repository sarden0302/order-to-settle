package com.commercesettle.commerce.presentation.controller;

import com.commercesettle.commerce.domain.model.ActorKind;
import java.util.UUID;

/**
 * Gateway가 검증해 전달한 현재 세션. 구매자·판매자 모두 받을 수 있고, 로그아웃처럼 현재 세션 자체를 다루는 작업만 쓴다.
 *
 * @param sessionHash 현재 세션 토큰의 SHA-256 소문자 16진수(X-Authenticated-Session-Hash)
 */
public record AuthenticatedSession(ActorKind kind, UUID id, String sessionHash) {
  @Override
  public String toString() {
    return "AuthenticatedSession[kind=%s, id=%s, sessionHash=<redacted>]".formatted(kind, id);
  }
}
