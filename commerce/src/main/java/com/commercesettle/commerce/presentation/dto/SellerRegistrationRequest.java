package com.commercesettle.commerce.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "SellerRegistration")
public record SellerRegistrationRequest(
    @NotBlank @Size(max = 254) @Email String email,
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) @NotNull @Size(min = 12, max = 128)
        String password,
    @NotBlank @Size(max = 80) String displayName,
    @NotBlank @Size(max = 100) String payoutAccountRef) {
  /** 비밀번호 원문이 로그·예외 메시지에 남지 않게 한다. */
  @Override
  public String toString() {
    return "SellerRegistrationRequest[email=<redacted>, password=<redacted>, displayName=%s]"
        .formatted(displayName);
  }
}
