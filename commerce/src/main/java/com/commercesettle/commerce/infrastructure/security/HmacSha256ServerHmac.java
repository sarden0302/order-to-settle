package com.commercesettle.commerce.infrastructure.security;

import com.commercesettle.commerce.application.port.ServerHmac;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 모든 커머스 인스턴스가 같은 키({@code AUTH_RATE_LIMIT_HMAC_KEY})를 쓴다. 키가 없거나 32바이트보다 짧으면 앱을 기동하지 않는다. 키를 바꾸면
 * 진행 중인 요청 제한 시간창과 24시간 안의 가입 재요청 비교가 초기화된다.
 */
@Component
class HmacSha256ServerHmac implements ServerHmac {
  private static final String ALGORITHM = "HmacSHA256";
  private static final int MIN_KEY_BYTES = 32;

  private final SecretKeySpec key;

  HmacSha256ServerHmac(@Value("${app.auth.rate-limit.identifier-hmac-key}") String key) {
    byte[] bytes = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_KEY_BYTES) {
      throw new IllegalStateException(
          "app.auth.rate-limit.identifier-hmac-key must be at least 32 bytes");
    }
    this.key = new SecretKeySpec(bytes, ALGORITHM);
  }

  @Override
  public String hex(String value) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is required by the JDK", e);
    }
  }
}
