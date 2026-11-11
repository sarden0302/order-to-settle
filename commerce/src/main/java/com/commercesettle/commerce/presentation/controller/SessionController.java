package com.commercesettle.commerce.presentation.controller;

import com.commercesettle.commerce.application.service.SessionService;
import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.commerce.presentation.dto.LoginRequest;
import com.commercesettle.commerce.presentation.dto.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sessions", description = "구매자·판매자 로그인과 로그아웃")
@RestController
public class SessionController {
  private final SessionService sessions;

  public SessionController(SessionService sessions) {
    this.sessions = sessions;
  }

  @Operation(operationId = "post_api_buyer_sessions", summary = "buyer 로그인")
  @PostMapping("/api/buyer-sessions")
  public SessionResponse buyerLogin(@Valid @RequestBody LoginRequest request) {
    return login(ActorKind.BUYER, request);
  }

  @Operation(operationId = "post_api_seller_sessions", summary = "seller 로그인")
  @PostMapping("/api/seller-sessions")
  public SessionResponse sellerLogin(@Valid @RequestBody LoginRequest request) {
    return login(ActorKind.SELLER, request);
  }

  @Operation(
      operationId = "delete_api_sessions_current",
      summary = "로그아웃",
      description = "Gateway가 검증한 현재 세션만 Redis에서 삭제한 뒤 204를 반환한다.")
  @DeleteMapping("/api/sessions/current")
  public ResponseEntity<Void> logout(@Parameter(hidden = true) AuthenticatedSession session) {
    sessions.logout(session.kind(), session.id(), session.sessionHash());
    return ResponseEntity.noContent().build();
  }

  private SessionResponse login(ActorKind kind, LoginRequest request) {
    return SessionResponse.from(sessions.login(kind, request.email(), request.password()));
  }
}
