package com.commercesettle.internalauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.ModelAndView;

/** 실제 명세(docs/openapi.json)의 작업으로 판정 순서를 확인한다. */
class InternalCallerFilterTest {
  static final String GATEWAY = "g".repeat(43);
  static final String SETTLEMENT = "s".repeat(43);
  static final String OPERATOR = "o".repeat(43);

  private SimpleMeterRegistry meters;
  private InternalCallerFilter filter;
  private Integer rejected;

  @BeforeEach
  void setUp() throws Exception {
    meters = new SimpleMeterRegistry();
    InternalOperations operations;
    try (InputStream specification =
        new ClassPathResource("contracts/openapi.json").getInputStream()) {
      operations = InternalOperations.load(specification, new ObjectMapper());
    }
    InternalCredentials credentials =
        new InternalCredentials(
            new InternalAuthProperties(
                "commerce",
                null,
                Map.of(
                    "gateway", new InternalAuthProperties.Credential(GATEWAY, null),
                    "service:settlement", new InternalAuthProperties.Credential(SETTLEMENT, null),
                    "operator:ops-1", new InternalAuthProperties.Credential(OPERATOR, null))));
    rejected = null;
    filter =
        new InternalCallerFilter(
            "commerce",
            credentials,
            operations,
            meters,
            (request, response, handler, error) -> {
              rejected = ((ErrorResponseException) error).getStatusCode().value();
              return new ModelAndView();
            });
  }

  @Test
  @DisplayName("Gateway가 판매자 신원과 함께 보낸 판매자 작업은 통과하고 발신자 ID를 넘긴다")
  void passesGatewaySellerRequest() throws Exception {
    MockHttpServletRequest request = request("POST", "/api/sellers/me/products", GATEWAY);
    request.addHeader(InternalCallerFilter.ACTOR_KIND, "SELLER");
    request.addHeader(InternalCallerFilter.ACTOR_ID, "8a5f1f9e-2f0b-4f53-9d0e-3c3f6a1f0a11");

    MockFilterChain chain = run(request);

    assertThat(rejected).isNull();
    assertThat(chain.getRequest()).isNotNull();
    assertThat(request.getAttribute(InternalCallerFilter.VERIFIED_CALLER)).isEqualTo("gateway");
  }

  @Test
  @DisplayName("공개 작업은 신원 헤더 없이도 Gateway 요청을 통과시킨다")
  void passesPublicRequestWithoutIdentity() throws Exception {
    MockFilterChain chain = run(request("GET", "/api/products", GATEWAY));

    assertThat(rejected).isNull();
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  @DisplayName("운영자·서비스 자격은 자기 x-actor 작업만 호출하고, 경로 변수가 있는 작업도 찾는다")
  void passesServiceAndOperatorOwnOperations() throws Exception {
    run(request("GET", "/internal/coupon-issue-runs", OPERATOR));
    assertThat(rejected).isNull();

    run(
        request(
            "GET",
            "/internal/products/8a5f1f9e-2f0b-4f53-9d0e-3c3f6a1f0a11/settlement-context",
            SETTLEMENT));
    assertThat(rejected).isNull();
  }

  @ParameterizedTest(name = "{0} {1} → {3}")
  @CsvSource({
    // 자격 없음·불일치
    "GET,/api/products,,401",
    "GET,/api/products,wrong-credential-wrong-credential-wrong-cred,401",
    // 명세에 없는 경로, 같은 경로의 다른 메서드
    "GET,/api/not-in-specification,GATEWAY,404",
    "DELETE,/api/products,GATEWAY,404",
    // 다른 서비스의 작업, 발신자 종류와 맞지 않는 작업
    "GET,/api/payments,GATEWAY,403",
    "GET,/internal/coupon-issue-runs,GATEWAY,403",
    "GET,/internal/coupon-issue-runs,SETTLEMENT,403",
    "GET,/api/products,OPERATOR,403"
  })
  @DisplayName("자격 → 명세 작업 → 소유 서비스·발신자 종류 순서로 거절한다")
  void rejectsInOrder(String method, String path, String credential, int status) throws Exception {
    MockFilterChain chain = run(request(method, path, credential(credential)));

    assertThat(rejected).isEqualTo(status);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  @DisplayName("Gateway가 아닌 발신자가 신원 헤더를 보내면 사칭으로 보고 403이다")
  void rejectsIdentityHeaderFromOtherSender() throws Exception {
    MockHttpServletRequest request = request("GET", "/internal/coupon-issue-runs", OPERATOR);
    request.addHeader(InternalCallerFilter.ACTOR_KIND, "BUYER");

    run(request);

    assertThat(rejected).isEqualTo(403);
  }

  @ParameterizedTest(name = "kind={0} id={1} → {2}")
  @CsvSource({
    ",,401",
    "SELLER,,401",
    "ADMIN,8a5f1f9e-2f0b-4f53-9d0e-3c3f6a1f0a11,401",
    "BUYER,8a5f1f9e-2f0b-4f53-9d0e-3c3f6a1f0a11,403"
  })
  @DisplayName("Gateway 보호 작업은 신원이 없으면 401, 계정 종류가 작업과 다르면 403이다")
  void checksGatewayIdentity(String kind, String id, int status) throws Exception {
    MockHttpServletRequest request = request("POST", "/api/sellers/me/products", GATEWAY);
    if (kind != null) request.addHeader(InternalCallerFilter.ACTOR_KIND, kind);
    if (id != null) request.addHeader(InternalCallerFilter.ACTOR_ID, id);

    run(request);

    assertThat(rejected).isEqualTo(status);
  }

  @Test
  @DisplayName("명세 문서 조회는 판정하지 않는다")
  void skipsSpecificationDocument() throws Exception {
    MockFilterChain chain = run(request("GET", "/v3/api-docs/commerce", null));

    assertThat(rejected).isNull();
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  @DisplayName("거절은 발신자 ID와 상태로 집계하고 확정 전 발신자는 unknown이다")
  void countsRejectionsBySender() throws Exception {
    run(request("GET", "/api/products", null));
    run(request("GET", "/api/payments", GATEWAY));

    assertThat(count("unknown", "401")).isEqualTo(1);
    assertThat(count("gateway", "403")).isEqualTo(1);
  }

  private double count(String sender, String status) {
    return meters.counter("internal.auth.rejections", "sender", sender, "status", status).count();
  }

  private MockFilterChain run(MockHttpServletRequest request) throws Exception {
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    return chain;
  }

  private static MockHttpServletRequest request(String method, String path, String credential) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setServletPath(path);
    if (credential != null) request.addHeader("Authorization", "Bearer " + credential);
    return request;
  }

  private static String credential(String name) {
    if (name == null) return null;
    return switch (name) {
      case "GATEWAY" -> GATEWAY;
      case "SETTLEMENT" -> SETTLEMENT;
      case "OPERATOR" -> OPERATOR;
      default -> name;
    };
  }
}
