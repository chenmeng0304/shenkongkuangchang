package com.deepmine;

import com.deepmine.boss.BossDamage;
import com.deepmine.boss.BossDamageRepository;
import com.deepmine.boss.WorldBoss;
import com.deepmine.boss.WorldBossRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    WorldBossRepository bossRepository;

    @Autowired
    BossDamageRepository damageRepository;

    @Test
    void accountBossAndPvpFlow() {
        // 1. 注册
        Map<String, Object> register = Map.of("username", "tester01", "password", "secret123");
        ResponseEntity<Map> reg = rest.postForEntity("/api/auth/register", json(register), Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reg.getBody()).containsKeys("token", "username", "userId");
        String token = (String) reg.getBody().get("token");
        assertThat(token).isNotBlank();
        long userId = toLong(reg.getBody().get("userId"));

        // 2. 未登录访问被拒绝
        ResponseEntity<Map> unauthorized = rest.getForEntity("/api/me", Map.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3. 登录
        ResponseEntity<Map> login = rest.postForEntity("/api/auth/login", json(register), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().get("username")).isEqualTo("tester01");

        // 4. 同步进度并计算战力
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("buildings", Map.of("cart", 30, "drill", 20, "belt", 12));
        snapshot.put("upgrades", List.of("u_pick", "u_arm", "u_cart1"));
        snapshot.put("achievements", List.of("a_tap1", "a_tap100"));
        snapshot.put("coreUpgrades", Map.of("resonance", 2));
        snapshot.put("materials", Map.of("iron", 20.0, "gold", 2.0, "copper", 100.0));
        snapshot.put("cores", 10);
        snapshot.put("prestiges", 1);
        snapshot.put("runOre", 1_000_000);
        snapshot.put("totalOre", 2_000_000);
        snapshot.put("bestRun", 1_500_000);
        ResponseEntity<Map> sync = exchange("/api/me/sync", HttpMethod.POST, snapshot, token, Map.class);
        assertThat(sync.getStatusCode()).isEqualTo(HttpStatus.OK);
        long powerBeforeGear = toLong(sync.getBody().get("power"));
        assertThat(powerBeforeGear).isGreaterThan(1000);

        snapshot.put("combat", Map.of(
                "attack", 800, "defense", 400, "hp", 9000, "dodge", 0.2, "crit", 0.25, "speed", 120));
        snapshot.put("gear", Map.of(
                "blueprints", List.of("bp_iron_blade"),
                "crafted", List.of("bp_iron_blade"),
                "equipped", Map.of("weapon", "bp_iron_blade")));
        snapshot.put("defense", Map.of(
                "gatling", Map.of("level", 5, "hp", 6000),
                "shield", Map.of("level", 2, "hp", 9000)));
        ResponseEntity<Map> syncGear = exchange("/api/me/sync", HttpMethod.POST, snapshot, token, Map.class);
        assertThat(syncGear.getStatusCode()).isEqualTo(HttpStatus.OK);
        long power = toLong(syncGear.getBody().get("power"));
        assertThat(power).isGreaterThan(powerBeforeGear);
        assertThat(toLong(syncGear.getBody().get("facilities"))).isEqualTo(62);

        ResponseEntity<Map> me = exchange("/api/me", HttpMethod.GET, null, token, Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toLong(me.getBody().get("power"))).isEqualTo(power);
        assertThat(me.getBody().get("rating")).isEqualTo(1000);

        // 5. 世界Boss：读取、防线出击、反击、冷却
        ResponseEntity<Map> boss = exchange("/api/boss", HttpMethod.GET, null, token, Map.class);
        assertThat(boss.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(boss.getBody().get("status")).isEqualTo("ACTIVE");
        long bossId = toLong(boss.getBody().get("id"));

        Map<String, Object> turrets = Map.of(
                "gatling", Map.of("level", 5, "hp", 10000),
                "cannon", Map.of("level", 3, "hp", 12000),
                "shield", Map.of("level", 2, "hp", 9000));
        Map<String, Object> attackBody = Map.of("turrets", turrets);

        ResponseEntity<Map> attack = exchange("/api/boss/attack", HttpMethod.POST, attackBody, token, Map.class);
        assertThat(attack.getStatusCode()).isEqualTo(HttpStatus.OK);
        long damage = toLong(attack.getBody().get("damage"));
        assertThat(damage).isGreaterThan(1000);
        assertThat(toLong(attack.getBody().get("myDamage"))).isEqualTo(damage);
        assertThat((List<?>) attack.getBody().get("hits")).isNotEmpty();

        ResponseEntity<Map> cooling = exchange("/api/boss/attack", HttpMethod.POST, attackBody, token, Map.class);
        assertThat(cooling.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // 6. 击杀与结算（测试里直接把 Boss 血量调低并清冷却）
        WorldBoss current = bossRepository.findById(bossId).orElseThrow();
        current.setHp(1);
        bossRepository.save(current);
        BossDamage row = damageRepository.findByBossIdAndUserId(bossId, userId).orElseThrow();
        row.setUpdatedAt(LocalDateTime.now().minusSeconds(60));
        damageRepository.save(row);

        ResponseEntity<Map> kill = exchange("/api/boss/attack", HttpMethod.POST, attackBody, token, Map.class);
        assertThat(kill.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(kill.getBody().get("killed")).isEqualTo(true);

        // 6.1 到 30 分钟轮换点：关闭本期并生成下一位，上一期可结算
        current.setEndsAt(LocalDateTime.now().minusSeconds(1));
        bossRepository.save(current);
        ResponseEntity<Map> rotated = exchange("/api/boss", HttpMethod.GET, null, token, Map.class);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rotated.getBody().get("canClaim")).isEqualTo(true);
        assertThat(toLong(rotated.getBody().get("cycleIndex"))).isEqualTo(1);
        assertThat(toLong(rotated.getBody().get("bossCount"))).isEqualTo(20);

        ResponseEntity<Map> claim = exchange("/api/boss/claim", HttpMethod.POST, null, token, Map.class);
        assertThat(claim.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toLong(claim.getBody().get("rank"))).isEqualTo(1);
        // 新规则：前 5 名发神话/传说图纸（不发光晶），6-10 名发晶核，11 名起发贵重金属
        assertThat(claim.getBody().get("blueprintQuality")).isIn("mythic", "legend");
        assertThat(toLong(claim.getBody().get("cores"))).isEqualTo(0);
        assertThat(claim.getBody()).containsKey("materials");
        ResponseEntity<Map> claimAgain = exchange("/api/boss/claim", HttpMethod.POST, null, token, Map.class);
        assertThat(claimAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 7. 竞技场匹配与战斗（逐次攻击）
        ResponseEntity<Map> opponent = exchange("/api/pvp/opponent", HttpMethod.GET, null, token, Map.class);
        assertThat(opponent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(opponent.getBody()).containsKeys("username", "power", "rating", "bot");

        java.util.Map<String, Object> battleRequest = new java.util.HashMap<>();
        if (opponent.getBody().get("userId") != null) {
            battleRequest.put("opponentId", opponent.getBody().get("userId"));
        }
        battleRequest.put("opponentName", opponent.getBody().get("username"));
        battleRequest.put("opponentPower", opponent.getBody().get("power"));
        battleRequest.put("opponentRating", opponent.getBody().get("rating"));

        ResponseEntity<Map> battle = exchange("/api/pvp/battle", HttpMethod.POST, battleRequest, token, Map.class);
        assertThat(battle.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(battle.getBody()).containsKeys("win", "exchanges", "myStats", "oppStats",
                "ratingDelta", "newRating", "rewardOre");
        List<?> exchanges = (List<?>) battle.getBody().get("exchanges");
        assertThat(exchanges).isNotEmpty();
        Map<String, Object> myBattleStats = (Map<String, Object>) battle.getBody().get("myStats");
        assertThat(toLong(myBattleStats.get("speed"))).isEqualTo(120);
        assertThat(Math.abs(toLong(battle.getBody().get("ratingDelta")))).isGreaterThanOrEqualTo(1);

        ResponseEntity<List> history = exchange("/api/pvp/history", HttpMethod.GET, null, token, List.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> battles = history.getBody();
        assertThat(battles).isNotEmpty();

        // 8. 排行榜
        ResponseEntity<Map> leaderboard = rest.getForEntity("/api/public/leaderboard?type=power", Map.class);
        assertThat(leaderboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> rows = (List<?>) leaderboard.getBody().get("rows");
        assertThat(rows).isNotEmpty();
        assertThat(leaderboard.getBody().get("players")).isEqualTo(1);

        ResponseEntity<Map> facilityBoard = rest.getForEntity("/api/public/leaderboard?type=facilities", Map.class);
        assertThat(facilityBoard.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> facilityRows = (List<Map<String, Object>>) facilityBoard.getBody().get("rows");
        assertThat(facilityRows).isNotEmpty();
        assertThat(toLong(facilityRows.get(0).get("facilities"))).isEqualTo(62);

        ResponseEntity<Map> bossBoard = rest.getForEntity("/api/public/leaderboard?type=boss", Map.class);
        assertThat(bossBoard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) bossBoard.getBody().get("rows")).isNotEmpty();

        // 9. 健康检查
        ResponseEntity<Map> health = rest.getForEntity("/api/health", Map.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody().get("ok")).isEqualTo(true);

        // 10. 微信登录：云托管会注入 X-WX-OPENID，没配 AppSecret 也能建号
        HttpHeaders wxHeaders = new HttpHeaders();
        wxHeaders.setContentType(MediaType.APPLICATION_JSON);
        wxHeaders.set("X-WX-OPENID", "test-openid-0001");
        ResponseEntity<Map> wxLogin = rest.exchange("/api/auth/wx", HttpMethod.POST,
                new HttpEntity<>(Map.of(), wxHeaders), Map.class);
        assertThat(wxLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wxLogin.getBody()).containsKeys("token", "userId", "username");
        String wxToken = (String) wxLogin.getBody().get("token");
        long wxUserId = toLong(wxLogin.getBody().get("userId"));
        assertThat(wxToken).isNotBlank();

        // 同一个 openid 再登一次 → 还是同一个人
        ResponseEntity<Map> wxAgain = rest.exchange("/api/auth/wx", HttpMethod.POST,
                new HttpEntity<>(Map.of(), wxHeaders), Map.class);
        assertThat(wxAgain.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toLong(wxAgain.getBody().get("userId"))).isEqualTo(wxUserId);

        // 微信账号的档案可以用 token 正常访问
        ResponseEntity<Map> wxMe = exchange("/api/me", HttpMethod.GET, null, wxToken, Map.class);
        assertThat(wxMe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toLong(wxMe.getBody().get("userId"))).isEqualTo(wxUserId);

        // 既没有 code 也没有平台 openid → 400
        ResponseEntity<Map> wxNoCode = rest.exchange("/api/auth/wx", HttpMethod.POST,
                json(Map.of()), Map.class);
        assertThat(wxNoCode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, Object body, String token, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), type);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
