-- 微信昵称 / 头像（wx.createUserInfoButton 授权后拿到，存起来用于展示）
ALTER TABLE users ADD COLUMN nickname VARCHAR(32) NULL AFTER username;
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(512) NULL AFTER nickname;
