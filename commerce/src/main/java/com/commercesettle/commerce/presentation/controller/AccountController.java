package com.commercesettle.commerce.presentation.controller;

import com.commercesettle.commerce.application.service.AccountRegistrationService;
import com.commercesettle.commerce.domain.model.Account;
import com.commercesettle.commerce.presentation.dto.AccountResponse;
import com.commercesettle.commerce.presentation.dto.BuyerRegistrationRequest;
import com.commercesettle.commerce.presentation.dto.SellerRegistrationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts", description = "구매자·판매자 가입")
@RestController
public class AccountController {
  private final AccountRegistrationService registrations;

  public AccountController(AccountRegistrationService registrations) {
    this.registrations = registrations;
  }

  @Operation(
      operationId = "post_api_buyers",
      summary = "구매자 가입",
      description = "같은 Idempotency-Key·본문의 24시간 안 재요청은 처음 만든 계정을 201로 다시 반환한다.")
  @PostMapping("/api/buyers")
  public ResponseEntity<AccountResponse> registerBuyer(
      @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128) String idempotencyKey,
      @Valid @RequestBody BuyerRegistrationRequest request) {
    Account account =
        registrations.registerBuyer(
            idempotencyKey, request.email(), request.password(), request.displayName());
    return created("/api/buyers/", account);
  }

  @Operation(operationId = "post_api_sellers", summary = "판매자 가입")
  @PostMapping("/api/sellers")
  public ResponseEntity<AccountResponse> registerSeller(
      @Valid @RequestBody SellerRegistrationRequest request) {
    Account account =
        registrations.registerSeller(
            request.email(), request.password(), request.displayName(), request.payoutAccountRef());
    return created("/api/sellers/", account);
  }

  private static ResponseEntity<AccountResponse> created(String prefix, Account account) {
    return ResponseEntity.created(URI.create(prefix + account.getId()))
        .body(AccountResponse.from(account));
  }
}
