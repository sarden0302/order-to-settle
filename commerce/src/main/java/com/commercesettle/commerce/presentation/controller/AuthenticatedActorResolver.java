package com.commercesettle.commerce.presentation.controller;

import com.commercesettle.commerce.domain.model.ActorKind;
import com.commercesettle.internalauth.InternalCallerFilter;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 내부 호출 필터가 신뢰한 Gateway 신원 헤더를 {@link AuthenticatedBuyer}·{@link AuthenticatedSeller} 인자로 바꾼다. 발신자
 * 확인은 필터가 맡고, 여기서는 계정 종류를 다시 확인해 Gateway 작업 매핑 오류를 마지막으로 막는다. 신원이 없거나 형식이 틀리면 401, 종류가 다르면 403이다.
 */
public class AuthenticatedActorResolver implements HandlerMethodArgumentResolver {
  public static final String ACTOR_KIND = InternalCallerFilter.ACTOR_KIND;
  public static final String ACTOR_ID = InternalCallerFilter.ACTOR_ID;

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    Class<?> type = parameter.getParameterType();
    return type == AuthenticatedBuyer.class || type == AuthenticatedSeller.class;
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer container,
      NativeWebRequest request,
      WebDataBinderFactory binders) {
    ActorKind kind = kind(request.getHeader(ACTOR_KIND));
    UUID id = id(request.getHeader(ACTOR_ID));
    ActorKind expected =
        parameter.getParameterType() == AuthenticatedBuyer.class
            ? ActorKind.BUYER
            : ActorKind.SELLER;
    if (kind != expected) {
      throw new ErrorResponseException(HttpStatus.FORBIDDEN);
    }
    return kind == ActorKind.BUYER ? new AuthenticatedBuyer(id) : new AuthenticatedSeller(id);
  }

  private static ActorKind kind(String value) {
    if (value != null) {
      for (ActorKind kind : ActorKind.values()) {
        if (kind.name().equals(value)) return kind;
      }
    }
    throw new ErrorResponseException(HttpStatus.UNAUTHORIZED);
  }

  private static UUID id(String value) {
    try {
      // 축약 표기를 받아 주지 않도록 정규 36자 표기만 허용한다.
      if (value != null && value.length() == 36) return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      // 아래에서 인증 실패로 응답한다.
    }
    throw new ErrorResponseException(HttpStatus.UNAUTHORIZED);
  }
}
