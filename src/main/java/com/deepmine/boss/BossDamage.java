package com.deepmine.boss;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "boss_damage", uniqueConstraints = {
        @UniqueConstraint(name = "uk_boss_user", columnNames = {"boss_id", "user_id"})
})
public class BossDamage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "boss_id", nullable = false)
    private Long bossId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false)
    private long damage;

    @Column(nullable = false)
    private int attacks;

    @Column(nullable = false)
    private boolean rewarded;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public BossDamage() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBossId() {
        return bossId;
    }

    public void setBossId(Long bossId) {
        this.bossId = bossId;
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

    public long getDamage() {
        return damage;
    }

    public void setDamage(long damage) {
        this.damage = damage;
    }

    public int getAttacks() {
        return attacks;
    }

    public void setAttacks(int attacks) {
        this.attacks = attacks;
    }

    public boolean isRewarded() {
        return rewarded;
    }

    public void setRewarded(boolean rewarded) {
        this.rewarded = rewarded;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
