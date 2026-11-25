package com.deepmine.profile;

import java.util.List;
import java.util.Map;

/** 客户端上传的进度快照（战力 / 设施 / 装备 / 防线） */
public record Snapshot(
        Map<String, Integer> buildings,
        List<String> upgrades,
        List<String> achievements,
        Map<String, Integer> coreUpgrades,
        Map<String, Double> materials,
        List<String> unlocked,
        Map<String, Object> gear,
        Map<String, Object> defense,
        CombatStats combat,
        long cores,
        long prestiges,
        double ore,
        double runOre,
        double totalOre,
        double bestRun
) {
    public static Snapshot empty() {
        return new Snapshot(Map.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(),
                Map.of(), Map.of(), CombatStats.base(), 0, 0, 0, 0, 0, 0);
    }
}
