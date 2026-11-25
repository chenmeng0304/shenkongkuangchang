package com.deepmine.pvp;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "battles")
public class Battle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attacker_id", nullable = false)
    private Long attackerId;

    @Column(name = "attacker_name", nullable = false, length = 32)
    private String attackerName;

    @Column(name = "defender_id")
    private Long defenderId;

    @Column(name = "defender_name", nullable = false, length = 32)
    private String defenderName;

    @Column(name = "attacker_power", nullable = false)
    private long attackerPower;

    @Column(name = "defender_power", nullable = false)
    private long defenderPower;

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "rating_delta", nullable = false)
    private int ratingDelta;

    @Lob
    @Column(name = "log_json")
    private String logJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAttackerId() {
        return attackerId;
    }

    public void setAttackerId(Long attackerId) {
        this.attackerId = attackerId;
    }

    public String getAttackerName() {
        return attackerName;
    }

    public void setAttackerName(String attackerName) {
        this.attackerName = attackerName;
    }

    public Long getDefenderId() {
        return defenderId;
    }

    public void setDefenderId(Long defenderId) {
        this.defenderId = defenderId;
    }

    public String getDefenderName() {
        return defenderName;
    }

    public void setDefenderName(String defenderName) {
        this.defenderName = defenderName;
    }

    public long getAttackerPower() {
        return attackerPower;
    }

    public void setAttackerPower(long attackerPower) {
        this.attackerPower = attackerPower;
    }

    public long getDefenderPower() {
        return defenderPower;
    }

    public void setDefenderPower(long defenderPower) {
        this.defenderPower = defenderPower;
    }

    public Long getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(Long winnerId) {
        this.winnerId = winnerId;
    }

    public int getRatingDelta() {
        return ratingDelta;
    }

    public void setRatingDelta(int ratingDelta) {
        this.ratingDelta = ratingDelta;
    }

    public String getLogJson() {
        return logJson;
    }

    public void setLogJson(String logJson) {
        this.logJson = logJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
