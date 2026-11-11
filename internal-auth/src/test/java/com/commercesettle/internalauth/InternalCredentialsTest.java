package com.commercesettle.internalauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InternalCredentialsTest {
  static final String GATEWAY = "g".repeat(43);
  static final String GATEWAY_PREVIOUS = "p".repeat(43);
  static final String SETTLEMENT = "s".repeat(43);

  @Test
  @DisplayName("받은 값과 일치하는 발신자를 찾고, 교체 중에는 이전 값도 같은 발신자로 받는다")
  void identifiesSenderByCurrentOrPreviousValue() {
    InternalCredentials credentials =
        new InternalCredentials(
            properties(
                Map.of(
                    "gateway", new InternalAuthProperties.Credential(GATEWAY, GATEWAY_PREVIOUS),
                    "service:settlement",
                        new InternalAuthProperties.Credential(SETTLEMENT, null))));

    assertThat(credentials.sender(GATEWAY)).contains("gateway");
    assertThat(credentials.sender(GATEWAY_PREVIOUS)).contains("gateway");
    assertThat(credentials.sender(SETTLEMENT)).contains("service:settlement");
    assertThat(credentials.sender("x".repeat(43))).isEmpty();
    assertThat(credentials.sender("")).isEmpty();
  }

  @Test
  @DisplayName("필수 발신자 자격이 없으면 기동하지 않는다")
  void requiresConfiguredSender() {
    var properties =
        properties(
            Map.of("service:settlement", new InternalAuthProperties.Credential(SETTLEMENT, null)));

    assertThatThrownBy(() -> new InternalCredentials(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("gateway");
  }

  @ParameterizedTest
  @ValueSource(strings = {"short", "has space in the value that is long enough to pass length"})
  @DisplayName("43자 미만이거나 URL-safe가 아닌 자격은 기동하지 않으며 오류에 원문을 넣지 않는다")
  void rejectsMalformedCredential(String value) {
    var properties =
        properties(Map.of("gateway", new InternalAuthProperties.Credential(value, null)));

    assertThatThrownBy(() -> new InternalCredentials(properties))
        .isInstanceOf(IllegalStateException.class)
        .message()
        .doesNotContain(value);
  }

  @Test
  @DisplayName("형식에 맞지 않는 발신자 ID와 여러 발신자가 함께 쓰는 값은 기동하지 않는다")
  void rejectsInvalidSenderAndSharedValue() {
    var invalidSender =
        properties(
            Map.of(
                "gateway", new InternalAuthProperties.Credential(GATEWAY, null),
                "service:Payment", new InternalAuthProperties.Credential(SETTLEMENT, null)));
    var shared =
        properties(
            Map.of(
                "gateway", new InternalAuthProperties.Credential(GATEWAY, null),
                "service:settlement", new InternalAuthProperties.Credential(GATEWAY, null)));

    assertThatThrownBy(() -> new InternalCredentials(invalidSender))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new InternalCredentials(shared))
        .isInstanceOf(IllegalStateException.class);
  }

  private static InternalAuthProperties properties(
      Map<String, InternalAuthProperties.Credential> senders) {
    return new InternalAuthProperties("commerce", null, senders);
  }
}
