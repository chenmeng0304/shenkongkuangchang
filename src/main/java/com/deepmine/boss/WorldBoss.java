package com.deepmine.boss;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "world_boss")
public class WorldBoss {
    public static final String ACTIVE = "ACTIVE";
    public static final String DEAD = "DEAD";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "max_hp", nullable = false)
    private long maxHp;

    @Column(nullable = false)
    private long hp;

    @Column(name = "total_damage", nullable = false)
    private long totalDamage;

    @Column(nullable = false, length = 16)
    private String status = ACTIVE;

    @Column(name = "spawned_at", nullable = false)
    private LocalDateTime spawnedAt;

    @Column(name = "killed_at")
    private LocalDateTime killedAt;

    @Column(name = "respawn_at")
    private LocalDateTime respawnAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "cycle_index", nullable = false)
    private int cycleIndex;

    public WorldBoss() {
    }

    public boolean isDead() {
        return DEAD.equals(status);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(long maxHp) {
        this.maxHp = maxHp;
    }

    public long getHp() {
        return hp;
    }

    public void setHp(long hp) {
        this.hp = hp;
    }

    public long getTotalDamage() {
        return totalDamage;
    }

    public void setTotalDamage(long totalDamage) {
        this.totalDamage = totalDamage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSpawnedAt() {
        return spawnedAt;
    }

    public void setSpawnedAt(LocalDateTime spawnedAt) {
        this.spawnedAt = spawnedAt;
    }

    public LocalDateTime getKilledAt() {
        return killedAt;
    }

    public void setKilledAt(LocalDateTime killedAt) {
        this.killedAt = killedAt;
    }

    public LocalDateTime getRespawnAt() {
        return respawnAt;
    }

    public void setRespawnAt(LocalDateTime respawnAt) {
        this.respawnAt = respawnAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(LocalDateTime endsAt) {
        this.endsAt = endsAt;
    }

    public int getCycleIndex() {
        return cycleIndex;
    }

    public void setCycleIndex(int cycleIndex) {
        this.cycleIndex = cycleIndex;
    }
}
