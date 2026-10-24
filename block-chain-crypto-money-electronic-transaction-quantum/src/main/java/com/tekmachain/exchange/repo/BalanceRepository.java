package com.tekmachain.exchange.repo;

import com.tekmachain.exchange.domain.Balance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BalanceRepository extends JpaRepository<Balance, Long> {

    Optional<Balance> findByAddressAndAsset(String address, String asset);

    List<Balance> findByAddress(String address);
}
