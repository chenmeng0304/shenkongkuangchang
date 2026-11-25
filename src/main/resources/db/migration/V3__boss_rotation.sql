-- v3：世界Boss 30分钟轮换与12期循环
ALTER TABLE world_boss
  ADD COLUMN ends_at DATETIME NULL,
  ADD COLUMN cycle_index INT NOT NULL DEFAULT 0;
