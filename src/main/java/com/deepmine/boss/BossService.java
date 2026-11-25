package com.deepmine.boss;

import com.deepmine.common.ApiException;
import com.deepmine.profile.PlayerProfile;
import com.deepmine.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BossService {

    /** 20 位世界Boss，每天轮换一位 */
    private static final String[] BOSS_NAMES = {
            "虚空吞噬者", "熔核巨像", "深渊母巢", "星骸利维坦",
            "反物质暴君", "坍缩星虫", "黑曜执政官", "熵变之眼",
            "永夜方舟", "星核暴君", "虚空织网者", "终焉棱镜",
            "猩红守望者", "冰封行者", "雷暴矩阵", "血肉花园",
            "机械先知", "暗月祭司", "虚空鲸", "终末圣像"
    };

    /**
     * 每位 Boss 的血量系数（与 BOSS_NAMES 一一对应）：
     * 越靠后的 Boss 越硬，同一天里玩家会明显感到「今天这只特别肉」。
     */
    private static final double[] BOSS_HP_FACTOR = {
            0.70, 0.78, 0.86, 0.94, 1.02, 1.10, 1.16, 1.22, 1.28, 1.34,
            1.40, 1.48, 1.56, 1.64, 1.72, 1.80, 1.88, 1.96, 2.06, 2.20
    };

    private static final int BOSS_COUNT = BOSS_NAMES.length;

    /** 世界Boss 血量：原版 ×3000（v1.5 先 ×100，再 ×30），再乘每位 Boss 的系数 */
    private static final long BOSS_BASE_HP_MIN = 600_000_000L;
    private static final long BOSS_HP_PER_FACILITY = 6_000_000L;
    private static final Set<String> DEFENSE_IDS = Set.of("gatling", "cannon", "shield", "repair");

    /** 防线建筑等级上限（与客户端 DEFENSE_MAX_LEVEL 一致，防止单点堆到离谱火力） */
    private static final int DEFENSE_MAX_LEVEL = 30;

    private final WorldBossRepository bossRepository;
    private final BossDamageRepository damageRepository;
    private final ProfileRepository profileRepository;
    private final int cooldownSeconds;
    private final int periodSeconds;

    public BossService(WorldBossRepository bossRepository,
                       BossDamageRepository damageRepository,
                       ProfileRepository profileRepository,
                       @Value("${app.boss.attack-cooldown-seconds:12}") int cooldownSeconds,
                       @Value("${app.boss.period-seconds:86400}") int periodSeconds) {
        this.bossRepository = bossRepository;
        this.damageRepository = damageRepository;
        this.profileRepository = profileRepository;
        this.cooldownSeconds = cooldownSeconds;
        this.periodSeconds = periodSeconds;
    }

    @Transactional
    public BossView current(Long userId) {
        WorldBoss boss = ensureBoss();
        LocalDateTime now = LocalDateTime.now();
        BossDamage mine = damageRepository.findByBossIdAndUserId(boss.getId(), userId).orElse(null);
        List<BossDamage> topRows = damageRepository.findTop20ByBossIdOrderByDamageDesc(boss.getId());
        List<TopEntry> top = new ArrayList<>();
        int myRank = 0;
        for (int i = 0; i < topRows.size(); i++) {
            BossDamage row = topRows.get(i);
            top.add(new TopEntry(i + 1, row.getUsername(), row.getDamage(), row.getAttacks()));
            if (row.getUserId().equals(userId)) {
                myRank = i + 1;
            }
        }

        long myDamage = mine == null ? 0 : mine.getDamage();
        int myAttacks = mine == null ? 0 : mine.getAttacks();
        Claimable claimable = claimable(userId, now);
        boolean canClaim = claimable != null;

        int nextAttackIn = 0;
        if (mine != null && mine.getUpdatedAt() != null) {
            LocalDateTime next = mine.getUpdatedAt().plusSeconds(cooldownSeconds);
            if (next.isAfter(now)) {
                nextAttackIn = (int) Math.max(1, Duration.between(now, next).getSeconds());
            }
        }
        long remaining = boss.getEndsAt() == null
                ? 0
                : Math.max(0, Duration.between(now, boss.getEndsAt()).getSeconds());
        int nextIndex = (boss.getCycleIndex() + 1) % BOSS_COUNT;

        return new BossView(boss.getId(), boss.getLevel(), boss.getName(), boss.getHp(), boss.getMaxHp(),
                boss.getTotalDamage(), boss.getStatus(), myDamage, myAttacks, myRank, canClaim,
                nextAttackIn, remaining, boss.getEndsAt(), boss.getCycleIndex(), BOSS_COUNT,
                BOSS_NAMES[nextIndex], top, damageRepository.countByBossId(boss.getId()));
    }

    @Transactional
    public AttackResult attack(Long userId, AttackRequest request) {
        WorldBoss current = ensureBoss();
        LocalDateTime now = LocalDateTime.now();
        if (current.getEndsAt() != null && !now.isBefore(current.getEndsAt())) {
            throw ApiException.conflict("本期世界Boss已到 30 分钟轮换时间，请刷新");
        }
        if (current.isDead()) {
            throw ApiException.conflict("本期世界Boss已被击破，30 分钟后自动刷新");
        }
        WorldBoss boss = bossRepository.findByIdForUpdate(current.getId())
                .orElseThrow(() -> ApiException.conflict("世界Boss状态异常，请刷新"));
        if (boss.isDead()) {
            throw ApiException.conflict("本期世界Boss已被击破，30 分钟后自动刷新");
        }
        PlayerProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("请先同步一次游戏进度"));

        Map<String, Integer> levels = new HashMap<>();
        Map<String, Long> hps = new HashMap<>();
        if (request != null && request.turrets() != null) {
            for (String id : DEFENSE_IDS) {
                TurretState state = request.turrets().get(id);
                if (state == null) continue;
                int level = Math.max(0, Math.min(DEFENSE_MAX_LEVEL, state.level()));
                if (level <= 0) continue;
                long maxHp = defenseMaxHp(id, level);
                long hp = Math.max(0, Math.min(maxHp, state.hp()));
                levels.put(id, level);
                hps.put(id, hp);
            }
        }
        if (levels.isEmpty()) {
            throw ApiException.badRequest("请先在基地防线建造炮塔");
        }
        boolean anyAlive = hps.values().stream().anyMatch(hp -> hp > 0);
        if (!anyAlive) {
            throw ApiException.badRequest("炮塔全部损毁，请先用矿石修理");
        }

        BossDamage row = damageRepository.findByBossIdAndUserId(boss.getId(), userId)
                .orElseGet(() -> {
                    BossDamage created = new BossDamage();
                    created.setBossId(boss.getId());
                    created.setUserId(userId);
                    created.setUsername(profile.getUsername());
                    return created;
                });

        if (row.getUpdatedAt() != null) {
            LocalDateTime next = row.getUpdatedAt().plusSeconds(cooldownSeconds);
            if (next.isAfter(now)) {
                long remaining = Math.max(1, Duration.between(now, next).getSeconds());
                throw ApiException.tooMany("防线冷却中，还需 " + remaining + " 秒");
            }
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double raw = 0;
        for (String id : DEFENSE_IDS) {
            Integer level = levels.get(id);
            if (level == null || level <= 0) continue;
            if (hps.getOrDefault(id, 0L) <= 0) continue;
            double damage = defenseDamage(id);
            if (damage <= 0) continue;
            long maxHp = defenseMaxHp(id, level);
            double ratio = maxHp > 0 ? Math.max(0, Math.min(1, hps.get(id) / (double) maxHp)) : 0;
            raw += damage * level * (0.4 + 0.6 * ratio);
        }
        boolean crit = random.nextDouble() < 0.1;
        if (raw <= 0) {
            throw ApiException.badRequest("没有可输出的炮塔，请先建造或修理机枪塔 / 榴弹炮");
        }
        long damage = Math.max(1, Math.round(raw * (0.9 + random.nextDouble() * 0.2) * (crit ? 1.5 : 1)));
        damage = Math.min(damage, boss.getHp());

        int shieldLevel = levels.getOrDefault("shield", 0);
        if (hps.getOrDefault("shield", 0L) <= 0) shieldLevel = 0;
        double reduction = Math.min(0.6, shieldLevel * 0.03);
        long counterBase = 2500 + boss.getLevel() * 900L;
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<String> alive = new ArrayList<>();
            for (String id : DEFENSE_IDS) {
                if (levels.getOrDefault(id, 0) > 0 && hps.getOrDefault(id, 0L) > 0) {
                    alive.add(id);
                }
            }
            if (alive.isEmpty()) break;
            String target = alive.get(random.nextInt(alive.size()));
            long hitDamage = Math.round(counterBase * (1 - reduction) * (0.8 + random.nextDouble() * 0.4));
            long after = Math.max(0, hps.get(target) - hitDamage);
            hps.put(target, after);
            hits.add(new Hit(target, hitDamage, after, after <= 0));
        }

        Map<String, Long> repaired = new LinkedHashMap<>();
        long repairPool = 800L * levels.getOrDefault("repair", 0);
        if (hps.getOrDefault("repair", 0L) <= 0) repairPool = 0;
        if (repairPool > 0) {
            for (String id : DEFENSE_IDS) {
                Integer level = levels.get(id);
                if (level == null || level <= 0) continue;
                long maxHp = defenseMaxHp(id, level);
                long hp = hps.getOrDefault(id, 0L);
                long missing = maxHp - hp;
                if (missing <= 0 || repairPool <= 0) continue;
                long heal = Math.min(missing, repairPool);
                hps.put(id, hp + heal);
                repaired.put(id, hp + heal);
                repairPool -= heal;
            }
        }

        boss.setHp(Math.max(0, boss.getHp() - damage));
        boss.setTotalDamage(boss.getTotalDamage() + damage);
        boolean killed = boss.getHp() <= 0;
        if (killed) {
            boss.setStatus(WorldBoss.DEAD);
            boss.setKilledAt(now);
        }
        bossRepository.save(boss);

        row.setDamage(row.getDamage() + damage);
        row.setAttacks(row.getAttacks() + 1);
        row.setUpdatedAt(now);
        row.setUsername(profile.getUsername());
        damageRepository.save(row);

        return new AttackResult(damage, crit, boss.getHp(), boss.getMaxHp(), boss.getTotalDamage(),
                row.getDamage(), row.getAttacks(), killed, boss.getLevel(), cooldownSeconds,
                now.plusSeconds(cooldownSeconds), hits, repaired);
    }

    /**
     * 结算最近一期已结束的 Boss：晶核按伤害占比 1~3 个，图纸按排名 / 伤害概率掉落。
     */
    @Transactional
    public ClaimResult claim(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Claimable claimable = claimable(userId, now);
        if (claimable == null) {
            throw ApiException.conflict("本期尚未结束，或没有可领取的伤害奖励");
        }
        WorldBoss boss = claimable.boss();
        BossDamage row = claimable.row();
        List<BossDamage> all = damageRepository.findByBossIdOrderByDamageDesc(boss.getId());
        int rank = 1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(row.getId())) {
                rank = i + 1;
                break;
            }
        }
        double share = boss.getTotalDamage() > 0
                ? (double) row.getDamage() / boss.getTotalDamage()
                : 0;

        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 奖励规则：
        //   第 1-5 名  → 随机神话 / 传说图纸（名次越前神话概率越高）
        //   第 6-10 名 → 晶核 10 / 5 / 3 / 2 / 1
        //   第 11 名起 → 贵重金属（按伤害占比）
        String blueprintQuality = null;
        int cores = 0;
        Map<String, Long> materials = new HashMap<>();
        if (rank <= 5) {
            double mythicChance = rank <= 2 ? 0.6 : 0.35;
            blueprintQuality = random.nextDouble() < mythicChance ? "mythic" : "legend";
        } else if (rank <= 10) {
            int[] coreTable = {10, 5, 3, 2, 1}; // 第 6 / 7 / 8 / 9 / 10 名
            cores = coreTable[rank - 6];
        } else {
            materials.put("iron", 40L + Math.round(share * 600));
            materials.put("copper", 10L + Math.round(share * 150));
            if (share >= 0.05) materials.put("gold", 2L + Math.round(share * 40));
            if (share >= 0.10) materials.put("mithril", 1L + Math.round(share * 12));
        }

        row.setRewarded(true);
        damageRepository.save(row);
        return new ClaimResult(0, cores, share, boss.getLevel(), blueprintQuality, rank, boss.getName(), materials);
    }

    private Claimable claimable(Long userId, LocalDateTime now) {
        for (WorldBoss ended : bossRepository.findTop12ByEndsAtBeforeOrderByEndsAtDesc(now)) {
            BossDamage row = damageRepository.findByBossIdAndUserId(ended.getId(), userId).orElse(null);
            if (row == null || row.isRewarded() || row.getDamage() <= 0) continue;
            return new Claimable(ended, row);
        }
        return null;
    }

    /** 后台定时轮换：即使没有客户端请求，30 分钟到点也会关闭本期并生成下一位 */
    @Transactional
    @Scheduled(initialDelayString = "${app.boss.rotate-initial-ms:30000}",
               fixedDelayString = "${app.boss.rotate-check-ms:15000}")
    public void rotateIfDue() {
        ensureBoss();
    }

    /** 懒刷新：30 分钟一到就关闭本期并生成下一位，不关心剩余血量 */
    private synchronized WorldBoss ensureBoss() {
        WorldBoss latest = bossRepository.findTopByOrderByIdDesc().orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (latest == null) {
            return spawnBoss(null, now);
        }
        if (latest.getEndsAt() == null) {
            // 兼容旧数据：升级后从当前时刻起算完整 30 分钟
            latest.setEndsAt(now.plusSeconds(periodSeconds));
            latest.setRespawnAt(latest.getEndsAt());
            latest.setCycleIndex(Math.max(0, latest.getCycleIndex()));
            return bossRepository.save(latest);
        }
        if (!now.isBefore(latest.getEndsAt())) {
            if (WorldBoss.ACTIVE.equals(latest.getStatus())) {
                latest.setStatus(WorldBoss.CLOSED);
                bossRepository.save(latest);
            }
            return spawnBoss(latest, now);
        }
        return latest;
    }

    private WorldBoss spawnBoss(WorldBoss previous, LocalDateTime now) {
        int topFacilities = profileRepository.findTop20ByOrderByFacilitiesDesc().stream()
                .mapToInt(PlayerProfile::getFacilities)
                .max()
                .orElse(0);
        int cycle = previous == null ? 0 : (previous.getCycleIndex() + 1) % BOSS_COUNT;
        int level = previous == null ? 1 : previous.getLevel() + 1;
        double factor = cycle >= 0 && cycle < BOSS_HP_FACTOR.length ? BOSS_HP_FACTOR[cycle] : 1.0;
        long baseHp = Math.max(BOSS_BASE_HP_MIN, topFacilities * BOSS_HP_PER_FACILITY);
        long maxHp = Math.max(1L, Math.round(baseHp * factor));

        WorldBoss boss = new WorldBoss();
        boss.setLevel(level);
        boss.setName(BOSS_NAMES[cycle] + " · Lv" + level);
        boss.setMaxHp(maxHp);
        boss.setHp(maxHp);
        boss.setTotalDamage(0);
        boss.setStatus(WorldBoss.ACTIVE);
        boss.setSpawnedAt(now);
        boss.setEndsAt(now.plusSeconds(periodSeconds));
        boss.setRespawnAt(boss.getEndsAt());
        boss.setCycleIndex(cycle);
        return bossRepository.save(boss);
    }

    private static long defenseMaxHp(String id, int level) {
        long baseHp = switch (id) {
            case "gatling" -> 8_000L;
            case "cannon" -> 14_000L;
            case "shield" -> 10_000L;
            case "repair" -> 8_000L;
            default -> 0L;
        };
        return Math.round(baseHp * (1 + (level - 1) * 0.22));
    }

    private static long defenseDamage(String id) {
        return switch (id) {
            case "gatling" -> 9_000L;
            case "cannon" -> 30_000L;
            default -> 0L;
        };
    }

    private record Claimable(WorldBoss boss, BossDamage row) {
    }

    public record TopEntry(int rank, String username, long damage, int attacks) {
    }

    public record BossView(Long id, int level, String name, long hp, long maxHp, long totalDamage,
                           String status, long myDamage, int myAttacks, int myRank, boolean canClaim,
                           int nextAttackIn, long remaining, LocalDateTime endsAt,
                           int cycleIndex, int bossCount, String nextName,
                           List<TopEntry> top, long participants) {
    }

    public record AttackResult(long damage, boolean crit, long bossHp, long bossMaxHp,
                               long bossTotalDamage, long myDamage, int myAttacks, boolean killed,
                               int level, int cooldown, LocalDateTime nextAttackAt,
                               List<Hit> hits, Map<String, Long> repaired) {
    }

    public record TurretState(int level, long hp) {
    }

    public record AttackRequest(Map<String, TurretState> turrets) {
    }

    public record Hit(String id, long damage, long hpAfter, boolean destroyed) {
    }

    public record ClaimResult(long ore, long cores, double share, int level, String blueprintQuality,
                              int rank, String bossName, Map<String, Long> materials) {
    }
}
