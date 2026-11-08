package com.commercesettle.internalauth;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 컨트롤러 전에 호출자를 한 곳에서 판정한다(ADR 0010의 백엔드 판정 순서). 자격 일치 → 명세 작업 → 소유 서비스·발신자 종류 → 신원 헤더 순서로 보고, 통과하면
 * 발신자 ID를 {@link #VERIFIED_CALLER} 요청 속성으로 넘긴다. 거절 응답은 앱의 공통 오류 처리기가 만든다.
 */
public class InternalCallerFilter extends OncePerRequestFilter {
  /** 판정을 통과한 발신자 ID(예: {@code gateway}, {@code operator:ops-1}). */
  public static final String VERIFIED_CALLER = "verifiedInternalCaller";

  public static final String ACTOR_KIND = "X-Authenticated-Actor-Kind";
  public static final String ACTOR_ID = "X-Authenticated-Actor-Id";

  private static final Logger LOGGER = LoggerFactory.getLogger(InternalCallerFilter.class);
  private static final String BEARER = "Bearer ";
  private static final String GATEWAY = "gateway";
  private static final String IDENTITY_PREFIX = "x-authenticated-";
  private static final Set<String> USER_ACTORS = Set.of("PUBLIC", "BUYER", "SELLER");
  private static final String UNKNOWN = "unknown";

  private final String service;
  private final InternalCredentials credentials;
  private final InternalOperations operations;
  private final MeterRegistry meters;
  private final HandlerExceptionResolver errors;

  public InternalCallerFilter(
      String service,
      InternalCredentials credentials,
      InternalOperations operations,
      MeterRegistry meters,
      HandlerExceptionResolver errors) {
    this.service = service;
    this.credentials = credentials;
    this.operations = operations;
    this.meters = meters;
    this.errors = errors;
  }

  /** 명세 문서 조회만 판정하지 않는다. 관리 포트는 별도 웹 서버라 이 필터를 거치지 않는다. */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = path(request);
    return "GET".equals(request.getMethod())
        && (path.equals("/v3/api-docs") || path.startsWith("/v3/api-docs/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<String> sender = sender(request);
    if (sender.isEmpty()) {
      reject(request, response, UNKNOWN, HttpStatus.UNAUTHORIZED);
      return;
    }
    String caller = sender.get();
    Optional<InternalOperations.Operation> found =
        operations.find(request.getMethod(), path(request));
    if (found.isEmpty()) {
      reject(request, response, caller, HttpStatus.NOT_FOUND);
      return;
    }
    InternalOperations.Operation operation = found.get();
    if (!service.equals(operation.owner()) || !allows(caller, operation.actors())) {
      reject(request, response, caller, HttpStatus.FORBIDDEN);
      return;
    }
    boolean gateway = GATEWAY.equals(caller);
    if (!gateway && hasIdentityHeader(request)) {
      reject(request, response, caller, HttpStatus.FORBIDDEN);
      return;
    }
    if (gateway && !operation.isPublic()) {
      HttpStatus identity = identity(request, operation.actors());
      if (identity != null) {
        reject(request, response, caller, identity);
        return;
      }
    }
    request.setAttribute(VERIFIED_CALLER, caller);
    chain.doFilter(request, response);
  }

  /** Authorization이 정확히 하나이고 Bearer 값이 등록된 자격과 일치할 때의 발신자. */
  private Optional<String> sender(HttpServletRequest request) {
    List<String> values = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
    if (values.size() != 1) {
      return Optional.empty();
    }
    String value = values.get(0);
    if (!value.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
      return Optional.empty();
    }
    return credentials.sender(value.substring(BEARER.length()));
  }

  private static boolean allows(String sender, Set<String> actors) {
    if (GATEWAY.equals(sender)) {
      return actors.stream().anyMatch(USER_ACTORS::contains);
    }
    if (sender.startsWith("service:")) {
      return actors.contains("SERVICE:" + sender.substring("service:".length()));
    }
    return actors.contains("OPERATOR");
  }

  /** 보호 작업의 계정 종류·ID. 없거나 형식이 틀리면 401, 계정 종류가 작업과 다르면 403. 통과하면 null. */
  private static HttpStatus identity(HttpServletRequest request, Set<String> actors) {
    String kind = request.getHeader(ACTOR_KIND);
    String id = request.getHeader(ACTOR_ID);
    if (kind == null || id == null || !USER_ACTORS.contains(kind) || "PUBLIC".equals(kind)) {
      return HttpStatus.UNAUTHORIZED;
    }
    return actors.contains(kind) ? null : HttpStatus.FORBIDDEN;
  }

  private static boolean hasIdentityHeader(HttpServletRequest request) {
    return Collections.list(request.getHeaderNames()).stream()
        .anyMatch(name -> name.toLowerCase(Locale.ROOT).startsWith(IDENTITY_PREFIX));
  }

  /** 서블릿 컨테이너가 디코딩·정규화한 경로. 컨텍스트 경로는 명세에 없으므로 뺀다. */
  private static String path(HttpServletRequest request) {
    String servlet = request.getServletPath();
    String info = request.getPathInfo();
    String path = (servlet == null ? "" : servlet) + (info == null ? "" : info);
    return path.isEmpty() ? request.getRequestURI() : path;
  }

  /** 자격 원문 없이 발신자 ID와 결과만 기록한다. */
  private void reject(
      HttpServletRequest request, HttpServletResponse response, String sender, HttpStatus status)
      throws ServletException {
    meters
        .counter(
            "internal.auth.rejections",
            "sender",
            sender,
            "status",
            Integer.toString(status.value()))
        .increment();
    LOGGER
        .atWarn()
        .addKeyValue("event", service + ".internal-auth")
        .addKeyValue("sender", sender)
        .addKeyValue("method", request.getMethod())
        .addKeyValue("httpStatus", status.value())
        .log("Internal caller rejected");
    if (errors.resolveException(request, response, null, new ErrorResponseException(status))
        == null) {
      throw new ServletException("Internal caller rejection was not handled");
    }
  }
}
