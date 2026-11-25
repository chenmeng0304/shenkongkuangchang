package com.deepmine.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileRepository extends JpaRepository<PlayerProfile, Long> {
    List<PlayerProfile> findTop20ByOrderByPowerDesc();

    List<PlayerProfile> findTop50ByOrderByPowerDesc();

    List<PlayerProfile> findTop20ByOrderByFacilitiesDesc();

    List<PlayerProfile> findByUsernameContainingIgnoreCase(String username);
}
