package com.deepmine.profile;

import com.deepmine.common.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProfileService {

    public static final String[] BUILDING_ORDER = {
            "cart", "drill", "belt", "crusher", "ironMine", "ironSmelter",
            "smelter", "factory", "copperMine", "goldMine", "goldSmelter",
            "elevator", "mithrilMine", "starMine", "starSmelter",
            "obsidianMine", "starCoreMine", "voidMine",
            "rig", "forge", "dyson", "chrono", "singularity"
    };

    private static final Set<String> BUILDING_IDS = Set.of(
            "cart", "drill", "belt", "crusher", "ironMine", "ironSmelter",
            "smelter", "factory", "copperMine", "goldMine", "goldSmelter",
            "elevator", "mithrilMine", "starMine", "starSmelter",
            "obsidianMine", "starCoreMine", "voidMine",
            "rig", "forge", "dyson", "chrono", "singularity");

    private static final Set<String> KNOWN_UPGRADES = Set.of(
            "u_cart1", "u_pick", "u_arm", "u_drill1", "u_luck", "u_belt1",
            "u_sonic", "u_team", "u_crush1", "u_offline1", "u_smelter1", "u_reso",
            "u_factory1", "u_night", "u_shift", "u_crit2", "u_elev1", "u_market",
            "u_rig1", "u_pure", "u_forge1", "u_cable", "u_dyson1", "u_auto",
            "u_chrono1", "u_sing1", "u_iron1", "u_smelt1", "u_goldmine",
            "u_goldsmelt", "u_starmine", "u_starsmelt");

    private static final Set<String> KNOWN_ACHIEVEMENTS = Set.of(
            "a_tap1", "a_tap100", "a_tap1000", "a_tap10000", "a_ore1k", "a_ore1m",
            "a_ore1b", "a_ore1t", "a_build50", "a_build200", "a_all", "a_speed1m",
            "a_crit50", "a_prestige1", "a_prestige5", "a_prestige20", "a_offline",
            "a_cores100", "a_time1h", "a_upgrades20", "a_iron1", "a_iron100",
            "a_gold10", "a_star1");

    private static final Set<String> KNOWN_CORE_UPGRADES = Set.of(
            "resonance", "tapfocus", "autotap", "deepoffline", "capital", "critcore");

    private static final long MAX_POWER = 1_000_000_000_000_000L;
    private static final long MAX_CORES = 1_000_000_000L;
    private static final long MAX_PRESTIGES = 1_000_000L;
    private static final int MAX_LEVEL = 1_000_000;

    private final ProfileRepository profiles;
    private final ObjectMapper objectMapper;

    public ProfileService(ProfileRepository profiles, ObjectMapper objectMapper) {
        this.profiles = profiles;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PlayerProfile getOrCreate(Long userId, String username) {
        return profiles.findById(userId).orElseGet(() -> {
            PlayerProfile p = new PlayerProfile();
            p.setUserId(userId);
            p.setUsername(username);
            p.setPower(1);
            p.setCores(0);
            p.setPrestiges(0);
            p.setFacilities(0);
            p.setAttack(50);
            p.setDefense(20);
            p.setHp(1000);
            p.setDodge(0.05);
            p.setCrit(0.05);
            p.setSpeed(10);
            p.setSnapshotJson("{}");
            p.setUpdatedAt(LocalDateTime.now());
            return profiles.save(p);
        });
    }

    @Transactional
    public PlayerProfile sync(Long userId, String username, Snapshot raw) {
        Snapshot snapshot = normalize(raw);
        PlayerProfile p = profiles.findById(userId).orElseGet(PlayerProfile::new);
        p.setUserId(userId);
        p.setUsername(username);
        p.setPower(computePower(snapshot));
        p.setCores(snapshot.cores());
        p.setPrestiges((int) snapshot.prestiges());
        p.setFacilities(totalFacilities(snapshot));
        CombatStats combat = snapshot.combat();
        p.setAttack(combat.attack());
        p.setDefense(combat.defense());
        p.setHp(combat.hp());
        p.setDodge(combat.dodge());
        p.setCrit(combat.crit());
        p.setSpeed(combat.speed());
        p.setSnapshotJson(writeJson(snapshot));
        p.setUpdatedAt(LocalDateTime.now());
        return profiles.save(p);
    }

    public static int totalFacilities(Snapshot s) {
        if (s.buildings() == null) {
            return 0;
        }
        long sum = 0;
        for (String id : BUILDING_ORDER) {
            sum += Math.max(0, s.buildings().getOrDefault(id, 0));
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    public PlayerProfile require(Long userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> ApiException.notFound("玩家档案不存在，请先同步进度"));
    }

    /** 服务端计算战力，数值做了上限保护，避免恶意快照把排行榜刷爆 */
    public static long computePower(Snapshot s) {
        if (s == null) {
            return 1;
        }
        double base = 0;
        Map<String, Integer> buildings = s.buildings() == null ? Map.of() : s.buildings();
        for (int i = 0; i < BUILDING_ORDER.length; i++) {
            Integer lv = buildings.get(BUILDING_ORDER[i]);
            int level = lv == null ? 0 : Math.min(MAX_LEVEL, Math.max(0, lv));
            base += level * (i + 1) * 12.0;
        }
        int upgradeCount = s.upgrades() == null ? 0 : Math.min(100, s.upgrades().size());
        int achievementCount = s.achievements() == null ? 0 : Math.min(100, s.achievements().size());
        long cores = Math.min(MAX_CORES, Math.max(0, s.cores()));
        long prestiges = Math.min(MAX_PRESTIGES, Math.max(0, s.prestiges()));

        base += upgradeCount * 70.0;
        base += achievementCount * 25.0;
        base += cores * 30.0;
        base += prestiges * 250.0;

        Map<String, Double> mats = s.materials() == null ? Map.of() : s.materials();
        base += material(mats, "ironOre", 3);
        base += material(mats, "goldOre", 8);
        base += material(mats, "starOre", 20);
        base += material(mats, "iron", 150);
        base += material(mats, "gold", 500);
        base += material(mats, "star", 1500);
        base += material(mats, "copper", 4);
        base += material(mats, "mithril", 40);
        base += material(mats, "obsidian", 120);
        base += material(mats, "starCore", 600);
        base += material(mats, "void", 2000);

        if (s.combat() != null) {
            CombatStats c = s.combat();
            base += Math.max(0, c.attack()) * 2.0;
            base += Math.max(0, c.defense()) * 3.0;
            base += Math.max(0, c.hp()) * 0.2;
            base += Math.max(0, c.dodge()) * 5000;
            base += Math.max(0, c.crit()) * 5000;
            base += Math.max(0, c.speed()) * 40;
        }

        double multiplier = 1.0
                + Math.min(cores, 10_000) * 0.03
                + achievementCount * 0.02
                + Math.min(prestiges, 100) * 0.05;
        long power = (long) Math.min(MAX_POWER, Math.max(1.0, base * multiplier));
        return Math.max(1, power);
    }

    private static double material(Map<String, Double> materials, String key, double weight) {
        Double value = materials.get(key);
        if (value == null || value.isNaN() || value.isInfinite() || value <= 0) {
            return 0;
        }
        return Math.min(1e12, value) * weight;
    }

    public static CombatStats clampCombat(CombatStats raw) {
        if (raw == null) {
            return CombatStats.base();
        }
        double dodge = Double.isFinite(raw.dodge()) ? Math.max(0, Math.min(0.75, raw.dodge())) : 0.05;
        double crit = Double.isFinite(raw.crit()) ? Math.max(0, Math.min(0.75, raw.crit())) : 0.05;
        return new CombatStats(
                Math.max(50, Math.min(1_000_000_000L, raw.attack())),
                Math.max(20, Math.min(1_000_000_000L, raw.defense())),
                Math.max(1000, Math.min(1_000_000_000L, raw.hp())),
                dodge,
                crit,
                Math.max(10, Math.min(10_000, raw.speed())));
    }

    public Snapshot normalize(Snapshot raw) {
        if (raw == null) {
            return Snapshot.empty();
        }
        Map<String, Integer> buildings = new LinkedHashMap<>();
        if (raw.buildings() != null) {
            for (String id : BUILDING_ORDER) {
                Integer value = raw.buildings().get(id);
                if (value != null && value > 0) {
                    buildings.put(id, Math.min(MAX_LEVEL, value));
                }
            }
        }

        List<String> upgrades = sanitizeIds(raw.upgrades(), KNOWN_UPGRADES);
        List<String> achievements = sanitizeIds(raw.achievements(), KNOWN_ACHIEVEMENTS);
        Map<String, Integer> coreUpgrades = new LinkedHashMap<>();
        if (raw.coreUpgrades() != null) {
            raw.coreUpgrades().forEach((key, value) -> {
                if (key != null && KNOWN_CORE_UPGRADES.contains(key) && value != null && value > 0) {
                    coreUpgrades.put(key, Math.min(100, value));
                }
            });
        }

        long cores = Math.min(MAX_CORES, Math.max(0, raw.cores()));
        long prestiges = Math.min(MAX_PRESTIGES, Math.max(0, raw.prestiges()));
        double ore = safeDouble(raw.ore());
        double runOre = safeDouble(raw.runOre());
        double totalOre = safeDouble(raw.totalOre());
        double bestRun = safeDouble(raw.bestRun());

        Map<String, Double> materials = new LinkedHashMap<>();
        if (raw.materials() != null) {
            for (String key : List.of("ironOre", "goldOre", "starOre", "iron", "gold", "star",
                    "copper", "mithril", "obsidian", "starCore", "void")) {
                Double value = raw.materials().get(key);
                if (value != null && !value.isNaN() && !value.isInfinite() && value > 0) {
                    materials.put(key, Math.min(1e12, value));
                }
            }
        }
        List<String> unlocked = sanitizeIds(raw.unlocked(), BUILDING_IDS);
        Map<String, Object> gear = raw.gear() == null ? Map.of() : raw.gear();
        Map<String, Object> defense = raw.defense() == null ? Map.of() : raw.defense();
        CombatStats combat = clampCombat(raw.combat());

        return new Snapshot(buildings, upgrades, achievements, coreUpgrades,
                materials, unlocked, gear, defense, combat,
                cores, prestiges, ore, runOre, totalOre, bestRun);
    }

    private List<String> sanitizeIds(List<String> ids, Set<String> known) {
        if (ids == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (id != null && known.contains(id) && !out.contains(id) && out.size() < 100) {
                out.add(id);
            }
        }
        return out;
    }

    private double safeDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            return 0;
        }
        return Math.min(1e300, value);
    }

    /**
     * 读取某个玩家当前穿着的装备（快照里的 gear.equipped: 部位 → 图纸 id）
     * 用于竞技场查看对手装备。
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, String> equippedOf(Long userId) {
        PlayerProfile profile = profiles.findById(userId).orElse(null);
        if (profile == null) return java.util.Map.of();
        String json = profile.getSnapshotJson();
        if (json == null || json.isBlank()) return java.util.Map.of();
        try {
            java.util.Map<String, Object> root = objectMapper.readValue(json, java.util.Map.class);
            Object gear = root.get("gear");
            if (!(gear instanceof java.util.Map)) return java.util.Map.of();
            Object equipped = ((java.util.Map<String, Object>) gear).get("equipped");
            if (!(equipped instanceof java.util.Map)) return java.util.Map.of();
            java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<String, Object> e : ((java.util.Map<String, Object>) equipped).entrySet()) {
                if (e.getValue() != null) out.put(e.getKey(), String.valueOf(e.getValue()));
            }
            return out;
        } catch (Exception e) {
            return java.util.Map.of();
        }
    }

    private String writeJson(Snapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new ApiException(500, "进度序列化失败");
        }
    }
}
