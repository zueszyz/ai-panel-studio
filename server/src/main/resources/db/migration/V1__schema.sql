-- AI Panel Studio · 初始结构（SQLite）
-- PRAGMA（WAL / busy_timeout / foreign_keys）由连接 URL 与 Hikari init-sql 设置，此处只建结构。
-- 时间统一 TEXT ISO-8601 UTC；枚举 TEXT + CHECK；JSON 存 TEXT。

CREATE TABLE users (
  id            TEXT PRIMARY KEY,
  device_id     TEXT NOT NULL UNIQUE,
  display_name  TEXT NOT NULL DEFAULT '旁听者',
  email         TEXT,
  created_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE discussions (
  id             TEXT PRIMARY KEY,
  topic          TEXT NOT NULL CHECK (length(topic) BETWEEN 8 AND 200),
  goal           TEXT,
  status         TEXT NOT NULL DEFAULT 'DRAFT'
                 CHECK (status IN ('DRAFT','PANEL_READY','LIVE','SUMMARIZING','CLOSED','INTERRUPTED','FAILED')),
  panel_size     INTEGER NOT NULL DEFAULT 4 CHECK (panel_size BETWEEN 3 AND 6),
  turn_index     INTEGER NOT NULL DEFAULT 0,
  topic_analysis TEXT,
  created_by     TEXT REFERENCES users(id) ON DELETE SET NULL,
  started_at     TEXT,
  ended_at       TEXT,
  created_at     TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  updated_at     TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  deleted_at     TEXT
);

CREATE TABLE agents (
  id                     TEXT PRIMARY KEY,
  discussion_id          TEXT NOT NULL REFERENCES discussions(id) ON DELETE CASCADE,
  role_type              TEXT NOT NULL
                         CHECK (role_type IN ('HOST','STRATEGIST','ANALYST','TECH','FINANCE','RESEARCHER')),
  name                   TEXT NOT NULL,
  title                  TEXT NOT NULL,
  stance                 TEXT NOT NULL,
  focus                  TEXT NOT NULL DEFAULT '[]',
  voice                  TEXT NOT NULL,
  color_token            TEXT NOT NULL,
  system_prompt_digest   TEXT,
  order_index            INTEGER NOT NULL,
  status                 TEXT NOT NULL DEFAULT 'ACTIVE'
                         CHECK (status IN ('ACTIVE','REPLACED_BY_USER','RETIRED')),
  created_at             TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE messages (
  id                TEXT PRIMARY KEY,
  discussion_id     TEXT NOT NULL REFERENCES discussions(id) ON DELETE CASCADE,
  agent_id          TEXT REFERENCES agents(id) ON DELETE SET NULL,
  user_id           TEXT REFERENCES users(id) ON DELETE SET NULL,
  role              TEXT NOT NULL CHECK (role IN ('HOST','EXPERT','USER')),
  kind              TEXT NOT NULL CHECK (kind IN ('OPENING','SPEECH','DIGEST','INTERRUPT','SYSTEM')),
  status            TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','STREAMING','DONE','FAILED','PARTIAL')),
  round_no          INTEGER NOT NULL DEFAULT 0,
  seq               INTEGER NOT NULL DEFAULT 0,
  content           TEXT NOT NULL DEFAULT '',
  quote_message_id  TEXT REFERENCES messages(id) ON DELETE SET NULL,
  is_interrupt      INTEGER NOT NULL DEFAULT 0 CHECK (is_interrupt IN (0,1)),
  tokens_in         INTEGER NOT NULL DEFAULT 0,
  tokens_out        INTEGER NOT NULL DEFAULT 0,
  latency_ms        INTEGER,
  attempt           INTEGER NOT NULL DEFAULT 1,
  client_msg_id     TEXT NOT NULL,
  created_at        TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  updated_at        TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE minutes (
  id              TEXT PRIMARY KEY,
  discussion_id   TEXT NOT NULL REFERENCES discussions(id) ON DELETE CASCADE,
  version         INTEGER NOT NULL,
  content_md      TEXT NOT NULL DEFAULT '',
  structured_json TEXT NOT NULL DEFAULT '{}',
  model           TEXT,
  tokens_used     INTEGER NOT NULL DEFAULT 0,
  status          TEXT NOT NULL DEFAULT 'GENERATING'
                  CHECK (status IN ('GENERATING','READY','FAILED')),
  created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE TABLE agent_runs (
  id             TEXT PRIMARY KEY,
  discussion_id  TEXT NOT NULL REFERENCES discussions(id) ON DELETE CASCADE,
  agent_id       TEXT REFERENCES agents(id) ON DELETE SET NULL,
  message_id     TEXT REFERENCES messages(id) ON DELETE SET NULL,
  phase          TEXT NOT NULL CHECK (phase IN ('THINKING','GENERATING','DONE','FAILED')),
  trigger_type   TEXT NOT NULL
                 CHECK (trigger_type IN ('ROUND_START','HOST_ASSIGN','USER_INTERRUPT','PEER_QUOTE','DIGEST')),
  round_no       INTEGER NOT NULL DEFAULT 0,
  status         TEXT,
  error_code     TEXT,
  duration_ms    INTEGER,
  tokens         INTEGER NOT NULL DEFAULT 0,
  meta           TEXT NOT NULL DEFAULT '{}',
  started_at     TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  ended_at       TEXT
);

CREATE TABLE discussion_participants (
  id             TEXT PRIMARY KEY,
  discussion_id  TEXT NOT NULL REFERENCES discussions(id) ON DELETE CASCADE,
  user_id        TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  seat_id        TEXT REFERENCES agents(id) ON DELETE SET NULL,
  role           TEXT NOT NULL CHECK (role IN ('PLAYER','VIEWER')),
  joined_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  left_at        TEXT
);
