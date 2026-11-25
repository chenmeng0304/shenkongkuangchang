package com.deepmine.boss;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BossDamageRepository extends JpaRepository<BossDamage, Long> {
    Optional<BossDamage> findByBossIdAndUserId(Long bossId, Long userId);

    List<BossDamage> findTop20ByBossIdOrderByDamageDesc(Long bossId);

    List<BossDamage> findByBossIdOrderByDamageDesc(Long bossId);

    @Query("select d.username, sum(d.damage), sum(d.attacks) from BossDamage d " +
           "group by d.username order by sum(d.damage) desc")
    List<Object[]> findTopDamageTotals(Pageable pageable);

    long countByBossId(Long bossId);
}
