package com.commercesettle.commerce.presentation.dto;

import com.commercesettle.commerce.application.service.SessionService.IssuedSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 토큰 원문은 이 발급 응답에서만 나간다. Authorization: Bearer로 보낸다. */
@Schema(name = "Session")
public record SessionResponse(
    String token,
    @Schema(allowableValues = {"Bearer"}) String tokenType,
    OffsetDateTime expiresAt,
    AccountResponse account) {
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  public static SessionResponse from(IssuedSession session) {
    return new SessionResponse(
        session.token(),
        "Bearer",
        session.expiresAt().atZone(KST).toOffsetDateTime(),
        AccountResponse.from(session.account()));
  }

  @Override
  public String toString() {
    return "SessionResponse[token=<redacted>, expiresAt=%s]".formatted(expiresAt);
  }
}
