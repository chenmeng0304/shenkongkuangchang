package com.deepmine.profile;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "player_profiles")
public class PlayerProfile {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false)
    private long power;

    @Column(nullable = false)
    private long cores;

    @Column(nullable = false)
    private int prestiges;

    @Column(nullable = false)
    private int facilities;

    @Column(name = "atk", nullable = false)
    private long attack = 50;

    @Column(name = "def", nullable = false)
    private long defense = 20;

    @Column(nullable = false)
    private long hp = 1000;

    @Column(nullable = false)
    private double dodge = 0.05;

    @Column(nullable = false)
    private double crit = 0.05;

    @Column(nullable = false)
    private long speed = 10;

    @Lob
    @Column(name = "snapshot_json")
    private String snapshotJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PlayerProfile() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getPower() {
        return power;
    }

    public void setPower(long power) {
        this.power = power;
    }

    public long getCores() {
        return cores;
    }

    public void setCores(long cores) {
        this.cores = cores;
    }

    public int getPrestiges() {
        return prestiges;
    }

    public void setPrestiges(int prestiges) {
        this.prestiges = prestiges;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getFacilities() {
        return facilities;
    }

    public void setFacilities(int facilities) {
        this.facilities = facilities;
    }

    public long getAttack() {
        return attack;
    }

    public void setAttack(long attack) {
        this.attack = attack;
    }

    public long getDefense() {
        return defense;
    }

    public void setDefense(long defense) {
        this.defense = defense;
    }

    public long getHp() {
        return hp;
    }

    public void setHp(long hp) {
        this.hp = hp;
    }

    public double getDodge() {
        return dodge;
    }

    public void setDodge(double dodge) {
        this.dodge = dodge;
    }

    public double getCrit() {
        return crit;
    }

    public void setCrit(double crit) {
        this.crit = crit;
    }

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public CombatStats combat() {
        return new CombatStats(attack, defense, hp, dodge, crit, speed);
    }
}
