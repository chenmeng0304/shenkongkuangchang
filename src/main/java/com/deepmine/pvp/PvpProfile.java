package com.deepmine.pvp;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pvp_profiles")
public class PvpProfile {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false)
    private int rating = 1000;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(nullable = false)
    private int streak;

    @Column(name = "last_battle_at")
    private LocalDateTime lastBattleAt;

    public PvpProfile() {
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

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public LocalDateTime getLastBattleAt() {
        return lastBattleAt;
    }

    public void setLastBattleAt(LocalDateTime lastBattleAt) {
        this.lastBattleAt = lastBattleAt;
    }
}
