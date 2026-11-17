-- 「换一批」次数计数：防止刷成本（API.md §2.3 规定最多 2 次，第 3 次 429 REGEN_LIMIT）
ALTER TABLE discussions ADD COLUMN regen_count INTEGER NOT NULL DEFAULT 0;
