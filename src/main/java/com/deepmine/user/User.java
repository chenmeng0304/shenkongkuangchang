package com.deepmine.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 微信 openid（微信登录的账号靠它识别；账号密码登录的老账号为空） */
    @Column(name = "openid", unique = true, length = 64)
    private String openid;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    /** 微信昵称（授权后才有；为空时展示 username） */
    @Column(length = 32)
    private String nickname;

    /** 微信头像地址（授权后才有） */
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    /** 密码哈希（微信登录的账号为 null） */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public User() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
