package com.commercesettle.commerce.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 실제 서블릿 필터 체인에서 내부 호출 판정이 컨트롤러 전에 걸리고, 거절이 공통 오류 계약으로 나가는지 확인한다. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.tracing.enabled=false",
      "app.internal-auth.senders.gateway.current=" + InternalCallerIntegrationTest.GATEWAY,
      "app.internal-auth.senders[operator\\:ops-1].current="
          + InternalCallerIntegrationTest.OPERATOR
    })
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InternalCallerIntegrationTest {
  static final String GATEWAY = "test-gateway-credential-0123456789abcdef0123";
  static final String OPERATOR = "test-operator-credential-0123456789abcdef012";
  private static final String PRODUCTS = "/api/sellers/me/products";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18.3").withInitScript("db/commerce.sql");

  @LocalServerPort int port;
  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("Gateway 자격과 판매자 신원이 있으면 컨트롤러까지 가서 본인 상품 목록을 돌려준다")
  void forwardsTrustedSellerToController() throws Exception {
    HttpResponse<String> response = get(PRODUCTS, GATEWAY, "SELLER", UUID.randomUUID().toString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(mapper.readTree(response.body()).path("items").isArray()).isTrue();
  }

  @ParameterizedTest(name = "{0} → {4}")
  @CsvSource({
    "자격 없음,/api/sellers/me/products,,SELLER,401,COMMON_AUTHENTICATION_REQUIRED",
    "명세에 없는 경로,/api/unknown,GATEWAY,SELLER,404,COMMON_RESOURCE_NOT_FOUND",
    "운영자의 사용자 작업,/api/sellers/me/products,OPERATOR,,403,COMMON_ROLE_FORBIDDEN",
    "구매자 신원의 판매자 작업,/api/sellers/me/products,GATEWAY,BUYER,403,COMMON_ROLE_FORBIDDEN",
    "신원 없는 보호 작업,/api/sellers/me/products,GATEWAY,,401,COMMON_AUTHENTICATION_REQUIRED"
  })
  @DisplayName("거절은 컨트롤러 전에 problem+json과 code·traceId로 응답하고 자격 원문을 넣지 않는다")
  void rejectsBeforeController(
      String ignored, String path, String credential, String kind, int status, String code)
      throws Exception {
    HttpResponse<String> response =
        get(path, credential(credential), kind, kind == null ? null : UUID.randomUUID().toString());

    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .startsWith("application/problem+json");
    JsonNode body = mapper.readTree(response.body());
    assertThat(body.path("code").asText()).isEqualTo(code);
    assertThat(body.path("instance").asText()).isEqualTo(path);
    assertThat(body.has("traceId")).isTrue();
    assertThat(response.body()).doesNotContain(GATEWAY, OPERATOR);
  }

  @Test
  @DisplayName("명세 문서는 자격 없이 조회한다")
  void servesSpecificationWithoutCredential() throws Exception {
    assertThat(get("/v3/api-docs", null, null, null).statusCode()).isEqualTo(200);
  }

  private static String credential(String name) {
    if ("GATEWAY".equals(name)) return GATEWAY;
    if ("OPERATOR".equals(name)) return OPERATOR;
    return null;
  }

  private HttpResponse<String> get(String path, String credential, String kind, String id)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
    if (credential != null) request.header("Authorization", "Bearer " + credential);
    if (kind != null) request.header(AuthenticatedActorResolver.ACTOR_KIND, kind);
    if (id != null) request.header(AuthenticatedActorResolver.ACTOR_ID, id);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
