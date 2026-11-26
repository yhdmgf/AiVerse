-- 提供商置顶：新增 pinned 列（0/1），默认 0 表示不置顶，旧数据零感知升级
ALTER TABLE ai_providers ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0;
