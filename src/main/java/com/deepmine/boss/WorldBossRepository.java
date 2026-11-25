package com.deepmine.boss;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorldBossRepository extends JpaRepository<WorldBoss, Long> {
    Optional<WorldBoss> findTopByOrderByIdDesc();

    Optional<WorldBoss> findTopByEndsAtBeforeOrderByEndsAtDesc(LocalDateTime now);

    List<WorldBoss> findTop12ByEndsAtBeforeOrderByEndsAtDesc(LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from WorldBoss b where b.id = :id")
    Optional<WorldBoss> findByIdForUpdate(@Param("id") Long id);
}
