package com.tekmachain.repo;

import com.tekmachain.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByAddress(String address);

    Optional<Wallet> findFirstByLabel(String label);

    boolean existsByAddress(String address);

    List<Wallet> findByOwner(String owner);

    List<Wallet> findByOwnerIsNull();
}
