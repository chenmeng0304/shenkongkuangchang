package com.deepmine.profile;

public record CombatStats(long attack, long defense, long hp, double dodge, double crit, long speed) {
    public static CombatStats base() {
        return new CombatStats(50, 20, 1000, 0.05, 0.05, 10);
    }
}
