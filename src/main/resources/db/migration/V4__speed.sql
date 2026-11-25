-- v4：速度属性
ALTER TABLE player_profiles
  ADD COLUMN speed BIGINT NOT NULL DEFAULT 10;
