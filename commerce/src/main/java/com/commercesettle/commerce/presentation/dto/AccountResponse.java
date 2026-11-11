package com.commercesettle.commerce.presentation.dto;

import com.commercesettle.commerce.domain.model.Account;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 이메일·비밀번호 해시·인증 버전은 내보내지 않는다. */
@Schema(name = "Account")
public record AccountResponse(
    UUID id,
    @Schema(allowableValues = {"BUYER", "SELLER"}) String actorKind,
    String displayName,
    @Schema(allowableValues = {"ACTIVE", "SUSPENDED", "DELETED"}) String status,
    long version) {
  public static AccountResponse from(Account account) {
    return new AccountResponse(
        account.getId(),
        account.kind().name(),
        account.getDisplayName(),
        account.getStatus().name(),
        account.getVersion());
  }
}
