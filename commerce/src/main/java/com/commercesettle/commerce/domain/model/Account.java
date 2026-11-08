package com.commercesettle.commerce.domain.model;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
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
