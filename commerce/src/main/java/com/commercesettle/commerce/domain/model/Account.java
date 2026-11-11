package com.commercesettle.commerce.domain.model;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 구매자·판매자 계정의 공통 상태. 인증 버전은 계정 전체 세션을 폐기할 때 올리며, 세션은 발급 때의 버전과 같아야 유효하다. 비밀번호 해시는 응답·로그에 넣지 않는다.
 */
@MappedSuperclass
public abstract class Account extends AuditableEntity {
  @Id private UUID id;

  private String emailNormalized;
  private String passwordHash;
  private String displayName;

  @Enumerated(EnumType.STRING)
  private AccountStatus status;

  private long authVersion;
  private Instant deletedAt;

  @Version private Long version;

  protected Account() {}

  /** 새 계정은 활성 상태, 인증 버전 0으로 시작한다. 이메일은 {@link #normalizeEmail}을 거친 값을 받는다. */
  protected Account(
      UUID id, String emailNormalized, String passwordHash, String displayName, Instant now) {
    super(now);
    this.id = Objects.requireNonNull(id, "id");
    this.emailNormalized = Objects.requireNonNull(emailNormalized, "emailNormalized");
    this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.status = AccountStatus.ACTIVE;
    this.authVersion = 0;
  }

  /** 앞뒤 공백을 지우고 소문자로 맞춘다. 가입·로그인·요청 제한 키가 같은 값을 쓴다. */
  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  public abstract ActorKind kind();

  /** 정지·삭제 계정은 로그인과 새 세션 발급, 혜택 수령을 할 수 없다. */
  public boolean canLogIn() {
    return status == AccountStatus.ACTIVE;
  }

  public UUID getId() {
    return id;
  }

  public String getEmailNormalized() {
    return emailNormalized;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public long getAuthVersion() {
    return authVersion;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public Long getVersion() {
    return version;
  }
}
