package com.commercesettle.commerce.infrastructure.persistence;

import com.commercesettle.commerce.domain.model.SellerAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSellerAccountRepository extends JpaRepository<SellerAccount, UUID> {
  boolean existsByEmailNormalized(String emailNormalized);

  Optional<SellerAccount> findByEmailNormalized(String emailNormalized);
}
