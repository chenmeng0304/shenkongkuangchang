-- 存档定时入库：完整客户端存档文本 + 更新时间
ALTER TABLE player_profiles ADD COLUMN save_text LONGTEXT NULL;
ALTER TABLE player_profiles ADD COLUMN save_at DATETIME NULL;
