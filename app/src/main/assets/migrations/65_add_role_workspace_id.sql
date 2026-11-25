-- 角色绑定工作区：新增 workspaceId 列（存 Workspace.name），null 表示不绑定
ALTER TABLE roles ADD COLUMN workspaceId TEXT;
CREATE INDEX IF NOT EXISTS index_roles_workspaceId ON roles(workspaceId);
