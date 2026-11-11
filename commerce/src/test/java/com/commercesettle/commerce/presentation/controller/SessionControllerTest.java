package com.commercesettle.commerce.presentation.controller;

import static com.commercesettle.commerce.support.OpenApiContracts.matchesContract;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercesettle.commerce.application.exception.AuthRateLimitedException;
import com.commercesettle.commerce.application.exception.AuthenticationFailedException;
import com.commercesettle.commerce.application.exception.AuthenticationUnavailableException;
import com.commercesettle.commerce.application.service.SessionService;
import com.commercesettle.commerce.application.service.SessionService.IssuedSession;
import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.commerce.domain.model.SellerAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(SessionController.class)
class SessionControllerTest {
  private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final String TOKEN = "q3mJ8m0lC1b0yQ4u2bW9nqv6cY3eH7xg8dK1sP5tR2w";
  private static final String HASH = "a".repeat(64);
  private static final String LOGIN =
      """
      {"email":"seller@example.com","password":"correct-horse-battery"}
      """;

  @Autowired MockMvc mvc;
  @MockitoBean SessionService sessions;

  @Test
  @DisplayName("판매자 로그인은 200과 정식 Session 계약의 Bearer 토큰·KST 만료 시각을 반환한다")
  void issuesSession() throws Exception {
    SellerAccount seller =
        SellerAccount.register(
            ACCOUNT,
            "seller@example.com",
            "hash",
            "판매자",
            "payout-1",
            Instant.parse("2026-11-11T00:00:00Z"));
    ReflectionTestUtils.setField(seller, "version", 0L);
    given(sessions.login(ActorKind.SELLER, "seller@example.com", "correct-horse-battery"))
        .willReturn(new IssuedSession(TOKEN, Instant.parse("2026-11-11T01:30:00Z"), seller));

    String body =
        mvc.perform(login("/api/seller-sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value(TOKEN))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresAt").value("2026-11-11T10:30:00+09:00"))
            .andExpect(jsonPath("$.account.actorKind").value("SELLER"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    matchesContract("Session", body);
    matchesContract("LoginRequest", LOGIN);
  }

  @Test
  @DisplayName("자격 오류는 계정 존재 여부와 관계없이 같은 401 공통 오류다")
  void rejectsInvalidCredentials() throws Exception {
    given(sessions.login(any(), anyString(), anyString()))
        .willThrow(new AuthenticationFailedException());

    mvc.perform(login("/api/buyer-sessions"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Content-Type", "application/problem+json"))
        .andExpect(jsonPath("$.code").value("COMMON_AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.instance").value("/api/buyer-sessions"));
  }

  @Test
  @DisplayName("요청 제한에 걸리면 429와 남은 시간창의 Retry-After를 반환한다")
  void returnsRetryAfterWhenRateLimited() throws Exception {
    given(sessions.login(any(), anyString(), anyString()))
        .willThrow(new AuthRateLimitedException(842));

    mvc.perform(login("/api/buyer-sessions"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "842"))
        .andExpect(jsonPath("$.code").value("COMMON_RATE_LIMITED"));
  }

  @Test
  @DisplayName("인증 저장소를 확인할 수 없으면 503이며 토큰을 반환하지 않는다")
  void returnsUnavailableWhenStoreFails() throws Exception {
    given(sessions.login(any(), anyString(), anyString()))
        .willThrow(new AuthenticationUnavailableException("auth store is not available"));

    mvc.perform(login("/api/buyer-sessions"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("COMMON_TEMPORARILY_UNAVAILABLE"))
        .andExpect(jsonPath("$.token").doesNotExist());
  }

  @Test
  @DisplayName("비밀번호가 12자 미만이면 요청 제한을 세기 전에 400으로 거절한다")
  void rejectsShortPasswordBeforeLogin() throws Exception {
    mvc.perform(
            post("/api/buyer-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"b@example.com\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"));
    verifyNoInteractions(sessions);
  }

  @Test
  @DisplayName("로그아웃은 Gateway가 전달한 계정·세션 해시로 현재 세션을 지우고 204를 반환한다")
  void logsOut() throws Exception {
    mvc.perform(logout().header(AuthenticatedActorResolver.SESSION_HASH, HASH))
        .andExpect(status().isNoContent());

    verify(sessions).logout(ActorKind.BUYER, ACCOUNT, HASH);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {"ABC", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
  @DisplayName("세션 해시가 없거나 64자리 소문자 16진수가 아니면 401이며 세션을 지우지 않는다")
  void rejectsMissingOrMalformedSessionHash(String hash) throws Exception {
    MockHttpServletRequestBuilder request = logout();
    if (hash != null) {
      request.header(AuthenticatedActorResolver.SESSION_HASH, hash);
    }

    mvc.perform(request)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_AUTHENTICATION_REQUIRED"));
    verifyNoInteractions(sessions);
  }

  @Test
  @DisplayName("이미 지운 세션의 로그아웃은 401이다")
  void rejectsLogoutOfRemovedSession() throws Exception {
    willThrow(new AuthenticationFailedException()).given(sessions).logout(any(), any(), any());

    mvc.perform(logout().header(AuthenticatedActorResolver.SESSION_HASH, HASH))
        .andExpect(status().isUnauthorized());
  }

  private static MockHttpServletRequestBuilder login(String path) {
    return post(path).contentType(MediaType.APPLICATION_JSON).content(LOGIN);
  }

  private static MockHttpServletRequestBuilder logout() {
    return delete("/api/sessions/current")
        .header(AuthenticatedActorResolver.ACTOR_KIND, "BUYER")
        .header(AuthenticatedActorResolver.ACTOR_ID, ACCOUNT.toString());
  }
}
