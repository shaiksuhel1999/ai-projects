package com.tekmachain.repo;

import com.tekmachain.domain.Utxo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UtxoRepository extends JpaRepository<Utxo, Long> {

    List<Utxo> findByAddress(String address);

    Optional<Utxo> findByTxIdAndOutIndex(String txId, int outIndex);

    @Transactional
    void deleteByTxIdAndOutIndex(String txId, int outIndex);
}
