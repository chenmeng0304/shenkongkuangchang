package com.deepmine.pvp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BattleRepository extends JpaRepository<Battle, Long> {
    List<Battle> findTop10ByAttackerIdOrDefenderIdOrderByCreatedAtDesc(Long attackerId, Long defenderId);

    long countByAttackerIdOrDefenderId(Long attackerId, Long defenderId);
}
