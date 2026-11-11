package com.commercesettle.internalauth;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 받는 서비스의 내부 호출 자격 설정. 발신자 ID별로 현재 값과 교체 중의 이전 값을 둔다. 형식 검사는 {@link InternalCredentials}가 기동 때 한다.
 *
 * @param service 이 앱이 소유한 서비스. 명세 작업의 {@code x-owner-service}와 대조한다
 * @param requiredSender 반드시 설정해야 하는 발신자. 기본은 {@code gateway}
 * @param senders 발신자 ID별 자격
 */
@ConfigurationProperties("app.internal-auth")
public record InternalAuthProperties(
    String service, String requiredSender, Map<String, Credential> senders) {
  public InternalAuthProperties {
    requiredSender =
        requiredSender == null || requiredSender.isBlank() ? "gateway" : requiredSender;
    senders = senders == null ? Map.of() : Map.copyOf(senders);
  }

  /** 교체 중에만 {@code previous}를 둔다. 비어 있으면 이전 값을 허용하지 않는다. */
  public record Credential(String current, String previous) {}
}
