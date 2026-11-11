package com.commercesettle.commerce.domain.model;

/** 정지·삭제 계정은 로그인하거나 새 혜택을 받을 수 없다. 삭제는 논리 삭제다. */
public enum AccountStatus {
  ACTIVE,
  SUSPENDED,
  DELETED
}
