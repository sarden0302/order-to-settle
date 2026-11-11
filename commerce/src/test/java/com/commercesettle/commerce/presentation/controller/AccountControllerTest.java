package com.commercesettle.commerce.presentation.controller;

import static com.commercesettle.commerce.support.OpenApiContracts.matchesContract;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercesettle.commerce.application.service.AccountRegistrationService;
import com.commercesettle.commerce.domain.exception.AccountEmailDuplicatedException;
import com.commercesettle.commerce.domain.model.Account;
import com.commercesettle.commerce.domain.model.BuyerAccount;
import com.commercesettle.commerce.domain.model.SellerAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
class AccountControllerTest {
  private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final String KEY = "7b94c07e-a3cb-4b80-96cc-bfb299ebc31b";
  private static final String PASSWORD = "correct-horse-battery";
  private static final Instant NOW = Instant.parse("2026-11-11T01:00:00Z");
  private static final String BUYER =
      """
      {"email":"buyer@example.com","password":"correct-horse-battery","displayName":"구매자"}
      """;

  @Autowired MockMvc mvc;
  @MockitoBean AccountRegistrationService registrations;

  @Test
  @DisplayName("구매자 가입은 201과 Location, 이메일·비밀번호 없는 계정 응답을 반환한다")
  void registersBuyer() throws Exception {
    given(registrations.registerBuyer(KEY, "buyer@example.com", PASSWORD, "구매자"))
        .willReturn(saved(BuyerAccount.register(ACCOUNT, "buyer@example.com", "hash", "구매자", NOW)));

    String body =
        mvc.perform(
                post("/api/buyers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", KEY)
                    .content(BUYER))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/buyers/" + ACCOUNT))
            .andExpect(jsonPath("$.id").value(ACCOUNT.toString()))
            .andExpect(jsonPath("$.actorKind").value("BUYER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    matchesContract("Account", body);
    matchesContract("BuyerRegistration", BUYER);
  }

  @Test
  @DisplayName("판매자 가입은 멱등키 없이 201과 판매자 Location을 반환한다")
  void registersSeller() throws Exception {
    given(registrations.registerSeller("seller@example.com", PASSWORD, "판매자", "payout-1"))
        .willReturn(
            saved(
                SellerAccount.register(
                    ACCOUNT, "seller@example.com", "hash", "판매자", "payout-1", NOW)));

    mvc.perform(
            post("/api/sellers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"seller@example.com","password":"correct-horse-battery",
                     "displayName":"판매자","payoutAccountRef":"payout-1"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/sellers/" + ACCOUNT))
        .andExpect(jsonPath("$.actorKind").value("SELLER"));
  }

  @Test
  @DisplayName("구매자 가입에 멱등키가 없으면 400이며 가입을 시작하지 않는다")
  void rejectsBuyerWithoutIdempotencyKey() throws Exception {
    mvc.perform(post("/api/buyers").contentType(MediaType.APPLICATION_JSON).content(BUYER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
    verifyNoInteractions(registrations);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "password|{\"email\":\"b@example.com\",\"password\":\"short-pass1\",\"displayName\":\"구매자\"}",
        "email|{\"email\":\"not-an-email\",\"password\":\"correct-horse-battery\",\"displayName\":\"구매자\"}",
        "displayName|{\"email\":\"b@example.com\",\"password\":\"correct-horse-battery\",\"displayName\":\" \"}"
      })
  @DisplayName("비밀번호 12자 미만·이메일 형식·빈 표시명은 400이며 거절 값을 응답에 넣지 않는다")
  void rejectsInvalidRegistration(String field, String body) throws Exception {
    mvc.perform(
            post("/api/buyers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", KEY)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
    verifyNoInteractions(registrations);
  }

  @Test
  @DisplayName("이미 가입한 이메일은 409 ACCOUNT_EMAIL_DUPLICATED다")
  void rejectsDuplicatedEmail() throws Exception {
    given(registrations.registerBuyer(anyString(), anyString(), anyString(), anyString()))
        .willThrow(new AccountEmailDuplicatedException());

    mvc.perform(
            post("/api/buyers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", KEY)
                .content(BUYER))
        .andExpect(status().isConflict())
        .andExpect(header().string("Content-Type", "application/problem+json"))
        .andExpect(jsonPath("$.code").value("ACCOUNT_EMAIL_DUPLICATED"));
  }

  @Test
  @DisplayName("명세에 없는 필드를 보내면 400이다")
  void rejectsUnknownField() throws Exception {
    mvc.perform(
            post("/api/sellers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"s@example.com","password":"correct-horse-battery",
                     "displayName":"판매자","payoutAccountRef":"p","status":"ACTIVE"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("status"));
    verifyNoInteractions(registrations);
  }

  /** 저장소가 처음 저장할 때 붙이는 낙관적 버전 0을 채운다. */
  private static <T extends Account> T saved(T account) {
    ReflectionTestUtils.setField(account, "version", 0L);
    return account;
  }
}
