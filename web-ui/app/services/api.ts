import ky from "ky";
import type {
  FavoriteDto,
  FileEntryDto,
  McpServerDto,
  MessageDto,
  ModelInfoDto,
  RoleDto,
  SessionDto,
  WorkspaceListDto,
} from "~/types/core";

const TOKEN_KEY = "aiverse-token";

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* 隐私模式下可能不可用 */
  }
}

function gotoLogin() {
  setToken(null);
  if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
    window.location.assign("/login");
  }
}

const api = ky.create({
  prefixUrl: "/api",
  hooks: {
    beforeRequest: [
      (request) => {
        const token = getToken();
        if (token) request.headers.set("Authorization", `Bearer ${token}`);
      },
    ],
    afterResponse: [
      (_request, _options, response) => {
        if (response.status === 401) gotoLogin();
      },
    ],
  },
});

export async function login(password: string): Promise<string> {
  const { token } = await api.post("auth/login", { json: { password } }).json<{ token: string }>();
  setToken(token);
  return token;
}

export async function listSessions(workspacePath?: string | null): Promise<SessionDto[]> {
  return api
    .get("sessions", {
      searchParams: workspacePath ? { workspace: workspacePath } : undefined,
    })
    .json();
}

export async function createSession(workspaceName?: string | null): Promise<SessionDto> {
  return api.post("sessions", { json: workspaceName ? { workspaceName } : {} }).json();
}

export async function getSession(id: string): Promise<SessionDto> {
  return api.get(`sessions/${id}`).json();
}

/** 打开会话时把 App 全局工作区切到会话所属项目。 */
export async function activateSession(id: string): Promise<SessionDto> {
  return api.post(`sessions/${id}/activate`).json();
}

export async function listWorkspaces(): Promise<WorkspaceListDto> {
  return api.get("workspaces").json();
}

export async function selectWorkspace(name: string): Promise<void> {
  await api.post("workspaces/select", { json: { name } });
}

export async function listMessages(sessionId: string): Promise<MessageDto[]> {
  return api.get(`sessions/${sessionId}/messages`).json();
}

export async function sendMessage(sessionId: string, content: string): Promise<void> {
  await api.post(`sessions/${sessionId}/messages`, { json: { content } });
}

export async function listModels(): Promise<ModelInfoDto[]> {
  return api.get("models").json();
}

export async function setSessionModel(
  sessionId: string,
  providerId: string,
  model: string,
): Promise<void> {
  await api.put(`sessions/${sessionId}/model`, { json: { providerId, model } });
}

export async function setReasoningEffort(sessionId: string, effort: string): Promise<void> {
  await api.put(`sessions/${sessionId}/reasoning-effort`, { json: { effort } });
}

export async function getWebSearch(): Promise<{ enabled: boolean }> {
  return api.get("settings/web-search").json();
}

export async function setWebSearch(enabled: boolean): Promise<void> {
  await api.put("settings/web-search", { json: { enabled } });
}

export async function listRoles(): Promise<RoleDto[]> {
  return api.get("roles").json();
}

export async function setSessionRole(sessionId: string, roleId: string): Promise<void> {
  await api.put(`sessions/${sessionId}/role`, { json: { roleId } });
}

export async function listMcp(): Promise<McpServerDto[]> {
  return api.get("mcp").json();
}

export async function listFiles(): Promise<FileEntryDto[]> {
  return api.get("files").json();
}

export async function listFavorites(): Promise<FavoriteDto[]> {
  return api.get("favorites").json();
}

export async function deleteFavorite(id: string): Promise<void> {
  await api.delete(`favorites/${id}`);
}

/** SSE 订阅会话流式事件。返回取消函数。 */
export function subscribeSessionStream(
  sessionId: string,
  onEvent: (event: { type: string; data?: unknown }) => void,
  onError: (error: Error) => void,
): () => void {
  const controller = new AbortController();
  let closed = false;
  const url = `/api/sessions/${sessionId}/stream`;

  (async () => {
    try {
      const token = getToken();
      const response = await fetch(url, {
        signal: controller.signal,
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      if (!response.ok) throw new Error(`SSE ${response.status}`);
      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (!closed) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split("\n\n");
        buffer = parts.pop() ?? "";
        for (const part of parts) {
          const dataLines = part
            .split("\n")
            .filter((l) => l.startsWith("data:"));
          if (dataLines.length === 0) continue;
          const data = dataLines.map((l) => l.slice(5).trim()).join("\n");
          try {
            onEvent(JSON.parse(data));
          } catch {
            onEvent({ type: "raw", data });
          }
        }
      }
    } catch (e) {
      if (!closed) onError(e instanceof Error ? e : new Error(String(e)));
    }
  })();

  return () => {
    closed = true;
    controller.abort();
  };
}
