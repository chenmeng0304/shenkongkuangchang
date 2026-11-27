package com.deepmine.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileRepository extends JpaRepository<PlayerProfile, Long> {
    List<PlayerProfile> findTop20ByOrderByPowerDesc();

    List<PlayerProfile> findTop50ByOrderByPowerDesc();

    List<PlayerProfile> findTop20ByOrderByFacilitiesDesc();

    List<PlayerProfile> findByUsernameContainingIgnoreCase(String username);

    /** 全服设施总和：世界 Boss 血量依据 */
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(p.facilities), 0) from PlayerProfile p")
    long sumFacilities();
}
