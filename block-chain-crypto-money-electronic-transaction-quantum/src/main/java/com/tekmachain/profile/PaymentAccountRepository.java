package com.tekmachain.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, Long> {

    List<PaymentAccount> findByOwnerOrderByCreatedAtDesc(String owner);

    List<PaymentAccount> findByOwnerAndKind(String owner, String kind);

    Optional<PaymentAccount> findByIdAndOwner(Long id, String owner);

    long countByOwnerAndKind(String owner, String kind);
}
