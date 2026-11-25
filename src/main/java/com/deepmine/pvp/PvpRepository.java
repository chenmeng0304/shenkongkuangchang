package com.deepmine.pvp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PvpRepository extends JpaRepository<PvpProfile, Long> {
    List<PvpProfile> findTop20ByOrderByRatingDesc();

    List<PvpProfile> findTop40ByRatingBetweenAndUserIdNotOrderByRatingDesc(int low, int high, Long userId);

    Optional<PvpProfile> findByUsername(String username);

    /** 积分比我高的人数（算我的排名用） */
    long countByRatingGreaterThan(int rating);
}
