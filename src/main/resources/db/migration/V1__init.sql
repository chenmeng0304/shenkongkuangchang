-- 深空矿场 · 初始表结构
CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_profiles (
  user_id BIGINT NOT NULL,
  username VARCHAR(32) NOT NULL,
  power BIGINT NOT NULL DEFAULT 0,
  cores BIGINT NOT NULL DEFAULT 0,
  prestiges INT NOT NULL DEFAULT 0,
  snapshot_json MEDIUMTEXT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id),
  KEY idx_profiles_power (power),
  CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS world_boss (
  id BIGINT NOT NULL AUTO_INCREMENT,
  level INT NOT NULL,
  name VARCHAR(64) NOT NULL,
  max_hp BIGINT NOT NULL,
  hp BIGINT NOT NULL,
  total_damage BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL,
  spawned_at DATETIME(6) NOT NULL,
  killed_at DATETIME(6) NULL,
  respawn_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_boss_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS boss_damage (
  id BIGINT NOT NULL AUTO_INCREMENT,
  boss_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  username VARCHAR(32) NOT NULL,
  damage BIGINT NOT NULL DEFAULT 0,
  attacks INT NOT NULL DEFAULT 0,
  rewarded TINYINT(1) NOT NULL DEFAULT 0,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_boss_user (boss_id, user_id),
  KEY idx_boss_damage (boss_id, damage),
  CONSTRAINT fk_damage_boss FOREIGN KEY (boss_id) REFERENCES world_boss (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pvp_profiles (
  user_id BIGINT NOT NULL,
  username VARCHAR(32) NOT NULL,
  rating INT NOT NULL DEFAULT 1000,
  wins INT NOT NULL DEFAULT 0,
  losses INT NOT NULL DEFAULT 0,
  streak INT NOT NULL DEFAULT 0,
  last_battle_at DATETIME(6) NULL,
  PRIMARY KEY (user_id),
  KEY idx_pvp_rating (rating),
  CONSTRAINT fk_pvp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS battles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  attacker_id BIGINT NOT NULL,
  attacker_name VARCHAR(32) NOT NULL,
  defender_id BIGINT NULL,
  defender_name VARCHAR(32) NOT NULL,
  attacker_power BIGINT NOT NULL,
  defender_power BIGINT NOT NULL,
  winner_id BIGINT NULL,
  rating_delta INT NOT NULL DEFAULT 0,
  log_json TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_battles_attacker (attacker_id, created_at),
  KEY idx_battles_defender (defender_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
