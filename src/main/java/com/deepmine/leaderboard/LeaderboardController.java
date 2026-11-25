package com.deepmine.leaderboard;

import com.deepmine.boss.BossDamage;
import com.deepmine.boss.BossDamageRepository;
import com.deepmine.boss.WorldBoss;
import com.deepmine.boss.WorldBossRepository;
import com.deepmine.profile.PlayerProfile;
import com.deepmine.profile.ProfileRepository;
import com.deepmine.pvp.PvpProfile;
import com.deepmine.pvp.PvpRepository;
import com.deepmine.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class LeaderboardController {

    private final ProfileRepository profiles;
    private final PvpRepository pvpRepository;
    private final BossDamageRepository bossDamageRepository;
    private final WorldBossRepository bossRepository;
    private final UserRepository users;

    public LeaderboardController(ProfileRepository profiles, PvpRepository pvpRepository,
                                 BossDamageRepository bossDamageRepository,
                                 WorldBossRepository bossRepository, UserRepository users) {
        this.profiles = profiles;
        this.pvpRepository = pvpRepository;
        this.bossDamageRepository = bossDamageRepository;
        this.bossRepository = bossRepository;
        this.users = users;
    }

    @GetMapping("/leaderboard")
    public Map<String, Object> leaderboard(@RequestParam(defaultValue = "power") String type) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if ("rating".equals(type)) {
            List<PvpProfile> list = pvpRepository.findTop20ByOrderByRatingDesc();
            for (int i = 0; i < list.size(); i++) {
                PvpProfile p = list.get(i);
                rows.add(row(i + 1, p.getUsername(),
                        "rating", p.getRating(),
                        "wins", p.getWins(),
                        "losses", p.getLosses()));
            }
        } else if ("facilities".equals(type)) {
            List<PlayerProfile> list = profiles.findTop20ByOrderByFacilitiesDesc();
            for (int i = 0; i < list.size(); i++) {
                PlayerProfile p = list.get(i);
                rows.add(row(i + 1, p.getUsername(),
                        "facilities", p.getFacilities(),
                        "power", p.getPower()));
            }
        } else if ("boss".equals(type)) {
            List<Object[]> totals = bossDamageRepository.findTopDamageTotals(PageRequest.of(0, 20));
            for (int i = 0; i < totals.size(); i++) {
                Object[] row = totals.get(i);
                rows.add(row(i + 1, String.valueOf(row[0]),
                        "damage", ((Number) row[1]).longValue(),
                        "attacks", ((Number) row[2]).longValue()));
            }
        } else {
            List<PlayerProfile> list = profiles.findTop20ByOrderByPowerDesc();
            for (int i = 0; i < list.size(); i++) {
                PlayerProfile p = list.get(i);
                rows.add(row(i + 1, p.getUsername(),
                        "power", p.getPower(),
                        "prestiges", p.getPrestiges()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("rows", rows);
        result.put("players", users.count());
        return result;
    }

    private Map<String, Object> row(int rank, String username, Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rank", rank);
        map.put("username", username);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
