package com.tekmachain.exchange.repo;

import com.tekmachain.exchange.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findTop50ByPairOrderByCreatedAtDesc(String pair);

    List<Trade> findByPairAndCreatedAtGreaterThanEqual(String pair, long since);
}
