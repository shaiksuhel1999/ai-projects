package com.tekmachain.repo;

import com.tekmachain.domain.Block;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findTopByOrderByHeightDesc();

    List<Block> findAllByOrderByHeightAsc();

    List<Block> findAllByOrderByHeightDesc();
}
