-- 微信登录：用 openid 唯一标识微信用户；密码改为可空（微信登录的账号没有密码）
ALTER TABLE users ADD COLUMN openid VARCHAR(64) NULL AFTER id;
ALTER TABLE users ADD UNIQUE KEY uk_users_openid (openid);
ALTER TABLE users MODIFY COLUMN password_hash VARCHAR(100) NULL;
