package com.commercesettle.internalauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 받은 Bearer 값이 어느 발신자의 자격인지 확정한다. 원문은 보관하지 않고 SHA-256 결과만 상수 시간으로 비교한다. 설정이 형식에 맞지 않으면 앱을 기동하지 않는다.
 */
public final class InternalCredentials {
  private static final Pattern CREDENTIAL = Pattern.compile("[A-Za-z0-9_-]{43,}");
  private static final Pattern SENDER =
      Pattern.compile("gateway|service:[a-z][a-z0-9-]*|operator:[A-Za-z0-9._-]+");

  private final List<Entry> entries;

  public InternalCredentials(InternalAuthProperties properties) {
    if (properties.service() == null || properties.service().isBlank()) {
      throw new IllegalStateException("app.internal-auth.service is required");
    }
    Map<String, InternalAuthProperties.Credential> senders = properties.senders();
    if (!senders.containsKey(properties.requiredSender())) {
      throw new IllegalStateException(
          "Internal credential is required for sender " + properties.requiredSender());
    }
    List<Entry> loaded = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    senders.forEach(
        (sender, credential) -> {
          if (!SENDER.matcher(sender).matches()) {
            throw new IllegalStateException("Invalid internal sender ID " + sender);
          }
          String current = credential == null ? null : credential.current();
          if (current == null || !CREDENTIAL.matcher(current).matches()) {
            throw new IllegalStateException("Invalid internal credential for sender " + sender);
          }
          add(loaded, seen, sender, current);
          String previous = credential.previous();
          if (previous != null && !previous.isBlank()) {
            if (!CREDENTIAL.matcher(previous).matches()) {
              throw new IllegalStateException(
                  "Invalid previous internal credential for sender " + sender);
            }
            add(loaded, seen, sender, previous);
          }
        });
    this.entries = List.copyOf(loaded);
  }

  /** 같은 값이 두 발신자에 걸리면 받은 값으로 발신자를 하나로 정할 수 없다. */
  private static void add(List<Entry> loaded, Set<String> seen, String sender, String value) {
    if (!seen.add(value)) {
      throw new IllegalStateException("Internal credential is reused across senders");
    }
    loaded.add(new Entry(sender, digest(value)));
  }

  /** 일치하는 발신자 ID. 항목을 모두 비교해 일치 위치에 따라 걸리는 시간이 달라지지 않게 한다. */
  public Optional<String> sender(String presented) {
    if (presented == null || presented.isEmpty()) {
      return Optional.empty();
    }
    byte[] received = digest(presented);
    String matched = null;
    for (Entry entry : entries) {
      if (MessageDigest.isEqual(entry.digest(), received)) {
        matched = entry.sender();
      }
    }
    return Optional.ofNullable(matched);
  }

  private static byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private record Entry(String sender, byte[] digest) {}
}
