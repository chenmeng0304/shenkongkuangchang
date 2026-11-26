package com.deepmine.pvp;

import com.deepmine.common.ApiException;
import com.deepmine.profile.CombatStats;
import com.deepmine.profile.PlayerProfile;
import com.deepmine.profile.ProfileRepository;
import com.deepmine.profile.ProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PvpService {

    private static final String[] BOT_NAMES = {
            "星尘游侠", "熔岩矿工", "虚空拾荒者", "轨道技工", "深渊勘探员",
            "量子搬运工", "陨石猎人", "霓虹监工", "铁壳工程师", "极夜看守"
    };

    private final PvpRepository pvpRepository;
    private final BattleRepository battleRepository;
    private final ProfileRepository profileRepository;
    private final com.deepmine.profile.ProfileService profileService;
    private final ObjectMapper objectMapper;
    private final int cooldownSeconds;

    public PvpService(PvpRepository pvpRepository,
                      BattleRepository battleRepository,
                      ProfileRepository profileRepository,
                      com.deepmine.profile.ProfileService profileService,
                      ObjectMapper objectMapper,
                      @Value("${app.pvp.battle-cooldown-seconds:10}") int cooldownSeconds) {
        this.pvpRepository = pvpRepository;
        this.battleRepository = battleRepository;
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
        this.cooldownSeconds = cooldownSeconds;
    }

    @Transactional
    public PvpProfile getOrCreate(Long userId, String username) {
        return pvpRepository.findById(userId).map(p -> {
            if (username != null && !username.isBlank() && !username.equals(p.getUsername())) {
                p.setUsername(username);
                return pvpRepository.save(p);
            }
            return p;
        }).orElseGet(() -> {
            PvpProfile p = new PvpProfile();
            p.setUserId(userId);
            p.setUsername(username);
            p.setRating(1000);
            return pvpRepository.save(p);
        });
    }

    @Transactional(readOnly = true)
    public PvpProfile require(Long userId) {
        return pvpRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("竞技场档案不存在"));
    }

    @Transactional
    public OpponentDto opponent(Long userId) {        PvpProfile me = getOrCreate(userId, "矿工" + userId);
        PlayerProfile mine = profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("请先同步一次游戏进度"));

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<PvpProfile> candidates = pvpRepository
                .findTop40ByRatingBetweenAndUserIdNotOrderByRatingDesc(
                        me.getRating() - 250, me.getRating() + 250, userId);
        candidates.removeIf(candidate -> candidate.getRating() <= 0);

        List<Candidate> pool = new ArrayList<>();
        for (PvpProfile candidate : candidates) {
            PlayerProfile profile = profileRepository.findById(candidate.getUserId()).orElse(null);
            if (profile != null) {
                pool.add(new Candidate(candidate, profile));
            }
        }
        if (!pool.isEmpty()) {
            long myPower = Math.max(1, mine.getPower());
            List<Candidate> fair = pool.stream()
                    .filter(c -> c.profile.getPower() >= Math.max(1, myPower / 3)
                            && c.profile.getPower() <= myPower * 3 + 500)
                    .toList();
            if (!fair.isEmpty()) {
                Candidate pick = fair.get(random.nextInt(fair.size()));
                return new OpponentDto(pick.profile.getUserId(), pick.profile.getUsername(),
                        pick.profile.getPower(), pick.pvp.getRating(),
                        pick.pvp.getWins(), pick.pvp.getLosses(), false);
            }
        }

        long basePower = Math.max(120, mine.getPower());
        long power = Math.max(100, Math.round(basePower * (0.7 + random.nextDouble() * 0.5)));
        int rating = Math.max(100, me.getRating() + random.nextInt(-80, 81));
        String name = BOT_NAMES[random.nextInt(BOT_NAMES.length)] + "·" + (100 + random.nextInt(900));
        return new OpponentDto(null, name, power, rating,
                random.nextInt(3, 40), random.nextInt(3, 30), true);
    }

    @Transactional
    public BattleResult battle(Long userId, BattleRequest request) {
        PvpProfile me = getOrCreate(userId, "矿工" + userId);
        PlayerProfile mine = profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("请先同步一次游戏进度"));

        LocalDateTime now = LocalDateTime.now();
        if (me.getLastBattleAt() != null) {
            LocalDateTime next = me.getLastBattleAt().plusSeconds(cooldownSeconds);
            if (next.isAfter(now)) {
                long remaining = Math.max(1, Duration.between(now, next).getSeconds());
                throw ApiException.tooMany("竞技场冷却中，还需 " + remaining + " 秒");
            }
        }

        long oppPower;
        int oppRating;
        String oppName;
        Long oppId = null;
        PvpProfile oppProfile = null;
        PlayerProfile oppPlayer = null;

        if (request != null && request.opponentId() != null) {
            if (request.opponentId().equals(userId)) {
                throw ApiException.badRequest("不能挑战自己");
            }
            oppProfile = pvpRepository.findById(request.opponentId())
                    .orElseThrow(() -> ApiException.notFound("对手不存在"));
            oppPlayer = profileRepository.findById(request.opponentId())
                    .orElseThrow(() -> ApiException.notFound("对手档案不存在"));
            oppId = oppPlayer.getUserId();
            oppPower = Math.max(1, oppPlayer.getPower());
            oppRating = Math.max(100, oppProfile.getRating());
            oppName = oppPlayer.getUsername();
        } else {
            long basePower = Math.max(120, mine.getPower());
            long maxPower = Math.max(500, basePower * 2);
            long minPower = Math.max(100, basePower / 2);
            oppPower = request != null && request.opponentPower() != null
                    ? clamp(request.opponentPower(), minPower, maxPower)
                    : Math.round(basePower * (0.75 + ThreadLocalRandom.current().nextDouble() * 0.45));
            oppRating = request != null && request.opponentRating() != null
                    ? clamp(request.opponentRating(), me.getRating() - 200, me.getRating() + 200)
                    : me.getRating();
            oppName = cleanName(request != null ? request.opponentName() : null);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        CombatStats myStats = ProfileService.clampCombat(mine.combat());

        CombatStats oppStats;
        if (oppPlayer != null) {
            oppStats = ProfileService.clampCombat(oppPlayer.combat());
        } else {
            oppStats = new CombatStats(
                    Math.max(50, Math.round(myStats.attack() * (0.75 + random.nextDouble() * 0.5))),
                    Math.max(20, Math.round(myStats.defense() * (0.75 + random.nextDouble() * 0.5))),
                    Math.max(1000, Math.round(myStats.hp() * (0.75 + random.nextDouble() * 0.5))),
                    Math.min(0.6, myStats.dodge() * (0.8 + random.nextDouble() * 0.4)),
                    Math.min(0.6, myStats.crit() * (0.8 + random.nextDouble() * 0.4)),
                    Math.max(1, Math.round(myStats.speed() * (0.8 + random.nextDouble() * 0.4))));
        }
        oppPower = Math.max(1, oppPlayer != null ? oppPlayer.getPower() : oppPower);
        // 血量池放大 2.2 倍：回合数变多，运气成分被摊平，强者更稳
        final long myMaxHp = Math.round(myStats.hp() * 2.2);
        final long oppMaxHp = Math.round(oppStats.hp() * 2.2);
        final long myPower = Math.max(1, mine.getPower());
        final long oppPowerF = Math.max(1, oppPower);

        long myHp = myMaxHp;
        long oppHp = oppMaxHp;
        List<Exchange> exchanges = new ArrayList<>();
        boolean win = false;
        boolean decided = false;
        boolean meFirst = myStats.speed() >= oppStats.speed();
        for (int round = 0; round < 60 && !decided; round++) {
            if (meFirst) {
                Exchange mineAttack = strike("me", myPower, oppPowerF, oppStats.hp(), myStats, oppStats, myHp, oppHp, random);
                exchanges.add(mineAttack);
                myHp = mineAttack.myHp();
                oppHp = mineAttack.oppHp();
                if (oppHp <= 0) { win = true; decided = true; break; }
                Exchange oppAttack = strike("opp", oppPowerF, myPower, myStats.hp(), oppStats, myStats, myHp, oppHp, random);
                exchanges.add(oppAttack);
                myHp = oppAttack.myHp();
                oppHp = oppAttack.oppHp();
                if (myHp <= 0) { win = false; decided = true; break; }
            } else {
                Exchange oppAttack = strike("opp", oppPowerF, myPower, myStats.hp(), oppStats, myStats, myHp, oppHp, random);
                exchanges.add(oppAttack);
                myHp = oppAttack.myHp();
                oppHp = oppAttack.oppHp();
                if (myHp <= 0) { win = false; decided = true; break; }
                Exchange mineAttack = strike("me", myPower, oppPowerF, oppStats.hp(), myStats, oppStats, myHp, oppHp, random);
                exchanges.add(mineAttack);
                myHp = mineAttack.myHp();
                oppHp = mineAttack.oppHp();
                if (oppHp <= 0) { win = true; decided = true; break; }
            }
        }
        if (!decided) {
            win = (myHp / (double) Math.max(1, myStats.hp()))
                    >= (oppHp / (double) Math.max(1, oppStats.hp()));
        }

        double expected = 1.0 / (1.0 + Math.pow(10.0, (oppRating - me.getRating()) / 400.0));
        int delta = (int) Math.round(32 * ((win ? 1 : 0) - expected));
        delta = win ? Math.max(1, delta) : Math.min(-1, delta);

        me.setRating(Math.max(100, me.getRating() + delta));
        me.setStreak(win ? me.getStreak() + 1 : 0);
        if (win) {
            me.setWins(me.getWins() + 1);
        } else {
            me.setLosses(me.getLosses() + 1);
        }
        me.setLastBattleAt(now);
        pvpRepository.save(me);

        int oppNewRating = oppRating;
        if (oppProfile != null) {
            oppProfile.setRating(Math.max(100, oppRating - delta));
            if (win) {
                oppProfile.setLosses(oppProfile.getLosses() + 1);
                oppProfile.setStreak(0);
            } else {
                oppProfile.setWins(oppProfile.getWins() + 1);
                oppProfile.setStreak(oppProfile.getStreak() + 1);
            }
            pvpRepository.save(oppProfile);
            oppNewRating = oppProfile.getRating();
        }

        Battle battle = new Battle();
        battle.setAttackerId(userId);
        battle.setAttackerName(mine.getUsername());
        battle.setDefenderId(oppId);
        battle.setDefenderName(oppName);
        battle.setAttackerPower(mine.getPower());
        battle.setDefenderPower(oppPower);
        battle.setWinnerId(win ? userId : oppId);
        battle.setRatingDelta(delta);
        battle.setLogJson(writeLog(exchanges, win, myHp, oppHp));
        battle.setCreatedAt(now);
        battleRepository.save(battle);

        long rewardOre = win ? 4000 + mine.getPower() * 6 : 800 + mine.getPower() * 2;
        long rewardCores = win ? 1 : 0;
        return new BattleResult(win, mine.getPower(), oppPower, oppName, oppId,
                mine.getUsername(), myStats, oppStats, myHp, oppHp, myMaxHp, oppMaxHp, exchanges,
                delta, me.getRating(), oppNewRating, rewardOre, rewardCores, me.getStreak(),
                battle.getId(), now.plusSeconds(cooldownSeconds), cooldownSeconds);
    }

    /** 一次出手：伤害由双方属性（攻击力 / 防御力 / 暴击 / 闪避）决定，战力只做小幅修正 */
    private Exchange strike(String actor, long atkPower, long defPower, long defenderMaxHp,
                            CombatStats attacker, CombatStats defender,
                            long myHp, long oppHp, ThreadLocalRandom random) {
        if (random.nextDouble() < Math.min(0.4, defender.dodge())) {
            return new Exchange(actor, 0, false, true, myHp, oppHp);
        }
        // 防御减伤（防御越高挨打越少，有上限）
        double mitigation = defender.defense() / (defender.defense() + 400.0);
        // 战力微调：占比 0.5 → 1.0；0.75 → 1.1；0.25 → 0.9
        double share = (double) atkPower / (double) (atkPower + defPower);
        double powerBias = 0.9 + 0.2 * (share * 2.0 - 1.0);
        // 核心：伤害由攻击力决定，随机幅度收窄（±6%）
        double base = attacker.attack() * (1 - mitigation) * powerBias;
        long damage = Math.max(1, Math.round(base * (0.94 + random.nextDouble() * 0.12)));
        boolean crit = random.nextDouble() < attacker.crit();
        if (crit) {
            damage = Math.round(damage * 1.6);
        }
        if ("me".equals(actor)) {
            return new Exchange(actor, damage, crit, false, myHp, Math.max(0, oppHp - damage));
        }
        return new Exchange(actor, damage, crit, false, Math.max(0, myHp - damage), oppHp);
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(Long userId) {
        return battleRepository.findTop10ByAttackerIdOrDefenderIdOrderByCreatedAtDesc(userId, userId)
                .stream()
                .map(battle -> {
                    boolean attacker = battle.getAttackerId().equals(userId);
                    boolean won = battle.getWinnerId() != null && battle.getWinnerId().equals(userId);
                    return new HistoryItem(
                            battle.getId(),
                            attacker ? battle.getDefenderName() : battle.getAttackerName(),
                            won,
                            attacker ? battle.getAttackerPower() : battle.getDefenderPower(),
                            attacker ? battle.getDefenderPower() : battle.getAttackerPower(),
                            attacker ? battle.getRatingDelta() : -battle.getRatingDelta(),
                            battle.getCreatedAt());
                })
                .toList();
    }

    private String writeLog(List<Exchange> exchanges, boolean win, long myHp, long oppHp) {
        try {
            return objectMapper.writeValueAsString(
                    new BattleLog(exchanges, win, myHp, oppHp));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            return "神秘矿工";
        }
        String trimmed = name.strip();
        return trimmed.length() > 16 ? trimmed.substring(0, 16) : trimmed;
    }

    private record Candidate(PvpProfile pvp, PlayerProfile profile) {
    }

    /**
     * 竞技场玩家列表：返回所有玩家（不含自己），按战力降序，附带竞技积分与战绩。
     * 同时返回「我」的积分排名，用于页面顶部展示自己的名次。
     */
    @Transactional
    public PlayersPage players(Long userId) {
        List<PlayerProfile> profiles = profileRepository.findTop50ByOrderByPowerDesc();
        List<Long> ids = profiles.stream().map(PlayerProfile::getUserId).toList();
        java.util.Map<Long, PvpProfile> pvp = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            for (PvpProfile p : pvpRepository.findAllById(ids)) {
                pvp.put(p.getUserId(), p);
            }
        }
        long myPower = profiles.stream()
                .filter(p -> p.getUserId().equals(userId))
                .mapToLong(PlayerProfile::getPower)
                .findFirst().orElse(0L);
        PvpProfile me = pvpRepository.findById(userId).orElse(null);
        int myRating = me == null ? 1000 : Math.max(100, me.getRating());
        int myRank = (int) pvpRepository.countByRatingGreaterThan(myRating) + 1;
        int total = (int) pvpRepository.count();

        List<PlayerDto> rows = profiles.stream()
                .filter(p -> !p.getUserId().equals(userId))
                .map(p -> {
                    PvpProfile row = pvp.get(p.getUserId());
                    return new PlayerDto(
                            p.getUserId(),
                            p.getUsername(),
                            Math.max(1, p.getPower()),
                            row == null ? 1000 : Math.max(100, row.getRating()),
                            row == null ? 0 : row.getWins(),
                            row == null ? 0 : row.getLosses(),
                            p.getPrestiges(),
                            p.getFacilities(),
                            myPower > 0 ? (int) Math.round(p.getPower() * 100.0 / myPower) : 100);
                })
                .toList();
        return new PlayersPage(rows, myRank, myRating, total);
    }

    /** 竞技场列表 + 我的排名 */
    public record PlayersPage(List<PlayerDto> players, int myRank, int myRating, int total) {
    }

    public record PlayerDto(Long userId, String username, long power, int rating,
                            int wins, int losses, int prestiges, int facilities, int powerPct) {
    }

    /** 竞技场点头像：对方的战力 / 积分 / 战绩 / 当前装备（部位 → 图纸 id） */
    public record PlayerGearDto(Long userId, String username, long power, int rating,
                                int wins, int losses, java.util.Map<String, String> equipped) {
    }

    @Transactional(readOnly = true)
    public PlayerGearDto gearOf(Long userId) {
        PlayerProfile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null) {
            throw com.deepmine.common.ApiException.notFound("玩家不存在");
        }
        PvpProfile pvp = pvpRepository.findById(userId).orElse(null);
        return new PlayerGearDto(
                profile.getUserId(),
                profile.getUsername(),
                profile.getPower(),
                pvp == null ? 1000 : pvp.getRating(),
                pvp == null ? 0 : pvp.getWins(),
                pvp == null ? 0 : pvp.getLosses(),
                profileService.equippedOf(userId));
    }

    public record OpponentDto(Long userId, String username, long power, int rating,
                              int wins, int losses, boolean bot) {
    }

    public record BattleRequest(Long opponentId, String opponentName,
                                Long opponentPower, Integer opponentRating) {
    }

    public record Exchange(String actor, long damage, boolean crit, boolean dodge,
                           long myHp, long oppHp) {
    }

    public record BattleLog(List<Exchange> exchanges, boolean win, long myHp, long oppHp) {
    }

    public record BattleResult(boolean win, long myPower, long oppPower, String oppName, Long oppId,
                               String myName, CombatStats myStats, CombatStats oppStats,
                               long myHp, long oppHp, long myHpMax, long oppHpMax, List<Exchange> exchanges,
                               int ratingDelta, int newRating, int oppNewRating,
                               long rewardOre, long rewardCores, int streak, long battleId,
                               LocalDateTime nextBattleAt, int cooldown) {
    }

    public record HistoryItem(long battleId, String opponentName, boolean win,
                              long myPower, long oppPower, int ratingDelta, LocalDateTime at) {
    }
}
