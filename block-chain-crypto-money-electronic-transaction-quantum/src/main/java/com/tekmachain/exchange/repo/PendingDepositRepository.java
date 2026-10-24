package com.tekmachain.exchange.repo;

import com.tekmachain.exchange.domain.PendingDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingDepositRepository extends JpaRepository<PendingDeposit, Long> {

    List<PendingDeposit> findByCreditedFalse();
}
