import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowDown,
  ArrowUp,
  Brain,
  ChevronRight,
  CircleAlert,
  Globe,
  Sparkles,
  UserRound,
  Wrench,
} from "lucide-react";
import dayjs from "dayjs";
import {
  activateSession,
  getSession,
  listFiles,
  listMessages,
  listModels,
  listRoles,
  listWorkspaces,
  sendMessage,
  setReasoningEffort,
  setSessionModel,
  setSessionRole,
  setWebSearch,
  getWebSearch,
  subscribeSessionStream,
} from "~/services/api";
import { REASONING_EFFORTS, type MessageDto } from "~/types/core";
import { Shell } from "~/components/Shell";
import { Dropdown, DropdownItem } from "~/components/Dropdown";
import { McpMenu } from "~/components/McpMenu";
import { Markdown, CopyButton } from "~/components/Markdown";

function Avatar() {
  return (
    <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-accent text-accent-fg">
      <Sparkles className="h-4 w-4" />
    </span>
  );
}

function Reasoning({ text }: { text: string }) {
  return (
    <details className="group mb-2 rounded-xl border border-line bg-surface2/60 text-xs text-muted">
      <summary className="flex cursor-pointer list-none items-center gap-1.5 px-3 py-2 select-none [&::-webkit-details-marker]:hidden">
        <Brain className="h-3.5 w-3.5" />
        思考过程
        <ChevronRight className="ml-auto h-3.5 w-3.5 transition-transform group-open:rotate-90" />
      </summary>
      <div className="max-h-64 overflow-auto whitespace-pre-wrap break-words border-t border-line px-3 py-2 leading-relaxed">
        {text}
      </div>
    </details>
  );
}

function ToolCard({ m }: { m: MessageDto }) {
  return (
    <details className="group anim-in ml-11 max-w-[min(100%,46rem)] rounded-xl border border-line bg-surface text-xs">
      <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 select-none [&::-webkit-details-marker]:hidden">
        <span
          className={`flex h-5 w-5 items-center justify-center rounded-md ${
            m.isError ? "bg-danger/15 text-danger" : "bg-accent-soft text-accent"
          }`}
        >
          {m.isError ? <CircleAlert className="h-3 w-3" /> : <Wrench className="h-3 w-3" />}
        </span>
        <span className="min-w-0 flex-1 truncate font-medium">
          {m.toolName ?? "工具"}
          {m.isError ? "（失败）" : ""}
        </span>
        <ChevronRight className="h-3.5 w-3.5 text-faint transition-transform group-open:rotate-90" />
      </summary>
      {m.content ? (
        <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words border-t border-line px-3 py-2 font-mono text-[11.5px] leading-relaxed text-muted">
          {m.content}
        </pre>
      ) : (
        <div className="border-t border-line px-3 py-2 text-faint">无输出</div>
      )}
    </details>
  );
}

function MessageItem({ m }: { m: MessageDto }) {
  if (m.role === "TOOL") return <ToolCard m={m} />;

  if (m.role === "USER") {
    return (
      <div className="anim-in group flex flex-col items-end">
        <div className="max-w-[85%] md:max-w-[75%]">
          <div className="whitespace-pre-wrap break-words rounded-2xl rounded-br-md border border-line bg-surface2 px-4 py-2.5 text-[15px] leading-relaxed text-fg">
            {m.content}
          </div>
          <div className="mt-1 flex items-center justify-end gap-2 pr-1 text-[11px] text-faint">
            <CopyButton text={m.content} className="md:opacity-0 md:transition-opacity md:group-hover:opacity-100" />
            <span>{dayjs(m.timestamp).format("HH:mm")}</span>
          </div>
        </div>
      </div>
    );
  }

  // ASSISTANT / SYSTEM：仅带工具调用的空消息不再渲染成「（空）」
  if (!m.content && !m.reasoning) {
    if (m.toolCallsJson) return null;
  }

  return (
    <div className="anim-in group flex gap-3">
      <Avatar />
      <div className="min-w-0 flex-1">
        {m.reasoning && <Reasoning text={m.reasoning} />}
        {m.content ? (
          <Markdown>{m.content}</Markdown>
        ) : (
          !m.reasoning && <span className="text-sm text-faint">（空）</span>
        )}
        {m.content && (
          <div className="mt-1.5 flex items-center gap-2 text-[11px] text-faint opacity-100 md:opacity-0 md:transition-opacity md:group-hover:opacity-100">
            <span>{dayjs(m.timestamp).format("HH:mm")}</span>
            <CopyButton text={m.content} />
          </div>
        )}
      </div>
    </div>
  );
}

function Thinking() {
  return (
    <div className="anim-in flex gap-3">
      <Avatar />
      <div className="dots flex h-8 items-center">
        <span />
        <span />
        <span />
      </div>
    </div>
  );
}

function EmptyChat() {
  return (
    <div className="anim-in flex flex-col items-center py-24 text-center">
      <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-accent-soft text-accent">
        <Sparkles className="h-7 w-7" />
      </span>
      <p className="mt-4 text-sm font-medium">还没有消息</p>
      <p className="mt-1 text-xs text-faint">在下方输入内容，开始对话</p>
    </div>
  );
}

/** 头部：模型选择器 */
function ModelMenu({ sessionId, current }: { sessionId: string; current?: string | null }) {
  const queryClient = useQueryClient();
  const { data } = useQuery({ queryKey: ["models"], queryFn: listModels });
  const providers = useMemo(
    () => (data ?? []).filter((p) => p.isEnabled && p.models.length > 0),
    [data],
  );
  // 展开成「provider×model」扁平列表
  const options = useMemo(
    () =>
      providers.flatMap((p) =>
        p.models.map((m) => ({ providerId: p.id, providerName: p.name, model: m })),
      ),
    [providers],
  );

  async function pick(close: () => void, providerId: string, model: string) {
    close();
    try {
      await setSessionModel(sessionId, providerId, model);
      queryClient.invalidateQueries({ queryKey: ["session", sessionId] });
    } catch (e) {
      console.error(e);
    }
  }

  return (
    <Dropdown
      icon={<Sparkles className="h-[18px] w-[18px]" />}
      label="模型"
      value={current}
    >
      {(close) =>
        options.length === 0 ? (
          <p className="px-2.5 py-3 text-center text-xs text-faint">暂无可用模型</p>
        ) : (
          options.map((o) => {
            const active = current === o.model;
            return (
              <DropdownItem
                key={`${o.providerId}:${o.model}`}
                active={active}
                onClick={() => pick(close, o.providerId, o.model)}
              >
                <span className="block truncate">{o.model}</span>
                <span className="block truncate text-[11px] text-faint">{o.providerName}</span>
              </DropdownItem>
            );
          })
        )
      }
    </Dropdown>
  );
}

/** 头部：思考强度选择器 */
function ReasoningMenu({
  sessionId,
  current,
}: {
  sessionId: string;
  current?: string | null;
}) {
  const queryClient = useQueryClient();
  async function pick(close: () => void, effort: string) {
    close();
    try {
      await setReasoningEffort(sessionId, effort);
      queryClient.invalidateQueries({ queryKey: ["session", sessionId] });
    } catch (e) {
      console.error(e);
    }
  }

  return (
    <Dropdown
      icon={<Brain className="h-[18px] w-[18px]" />}
      label="思考强度"
      value={current}
    >
      {(close) =>
        REASONING_EFFORTS.map((e) => (
          <DropdownItem
            key={e}
            active={current === e}
            onClick={() => pick(close, e)}
          >
            {e}
          </DropdownItem>
        ))
      }
    </Dropdown>
  );
}

/** 头部：角色选择器 */
function RoleMenu({ sessionId }: { sessionId: string }) {
  const queryClient = useQueryClient();
  const { data } = useQuery({ queryKey: ["roles"], queryFn: listRoles });
  const roles = data ?? [];

  async function pick(close: () => void, roleId: string) {
    close();
    try {
      await setSessionRole(sessionId, roleId);
      queryClient.invalidateQueries({ queryKey: ["session", sessionId] });
    } catch (e) {
      console.error(e);
    }
  }

  return (
    <Dropdown icon={<UserRound className="h-[18px] w-[18px]" />} label="角色">
      {(close) =>
        roles.length === 0 ? (
          <p className="px-2.5 py-3 text-center text-xs text-faint">暂无角色</p>
        ) : (
          roles.map((r) => (
            <DropdownItem
              key={r.id}
              onClick={() => pick(close, r.id)}
            >
              <span className="block truncate">{r.name}</span>
              {r.description && (
                <span className="block truncate text-[11px] text-faint">{r.description}</span>
              )}
            </DropdownItem>
          ))
        )
      }
    </Dropdown>
  );
}

export default function Chat() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [streaming, setStreaming] = useState<string | null>(null);
  const [waiting, setWaiting] = useState(false);
  const [input, setInput] = useState("");
  const [pending, setPending] = useState(false);
  const [showJump, setShowJump] = useState(false);
  const [webSearch, setWebSearchOn] = useState<boolean | null>(null);
  const [fileMenuOpen, setFileMenuOpen] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const stickRef = useRef(true);
  const taRef = useRef<HTMLTextAreaElement>(null);

  const { data: session } = useQuery({
    queryKey: ["session", id],
    queryFn: () => getSession(id!),
    enabled: !!id,
    retry: false,
  });

  const { data: workspaceData } = useQuery({
    queryKey: ["workspaces"],
    queryFn: listWorkspaces,
  });
  const workspaceLabel = workspaceData?.workspaces.find(
    (w) => w.path === session?.workspacePath,
  )?.label;

  // 直接打开 URL 或从别的项目点进来时，把服务端当前工作区切到本会话的目录；
  // 否则 @ 文件列表和 Bash/文件工具会继续用上一个项目。
  useEffect(() => {
    if (!id) return;
    activateSession(id)
      .then(() => {
        queryClient.invalidateQueries({ queryKey: ["workspaces"] });
        queryClient.invalidateQueries({ queryKey: ["files"] });
      })
      .catch((e) => console.error(e));
  }, [id, queryClient]);

  const { data: messages, isLoading, refetch } = useQuery({
    queryKey: ["messages", id],
    queryFn: () => listMessages(id!),
    enabled: !!id,
  });

  const { data: files } = useQuery({
    queryKey: ["files"],
    queryFn: listFiles,
    enabled: fileMenuOpen,
  });

  const { data: ws } = useQuery({
    queryKey: ["web-search"],
    queryFn: getWebSearch,
  });
  useEffect(() => {
    if (ws) setWebSearchOn(ws.enabled);
  }, [ws]);

  useEffect(() => {
    if (!id) return;
    const unsub = subscribeSessionStream(
      id,
      (event) => {
        if (event.type === "streaming") {
          setWaiting(false);
          setStreaming(String((event.data as { content?: string } | undefined)?.content ?? ""));
        } else if (event.type === "message" || event.type === "completed" || event.type === "error") {
          refetch();
          setStreaming(null);
          if (event.type !== "message") setWaiting(false);
        }
      },
      () => refetch(),
    );
    return unsub;
  }, [id]);

  const scrollToBottom = useCallback((smooth = true) => {
    bottomRef.current?.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "end" });
  }, []);

  // 仅当用户本来就在底部附近时才自动跟随，避免阅读历史时被拉走
  useEffect(() => {
    if (stickRef.current) scrollToBottom(streaming === null);
  }, [messages, streaming, waiting, scrollToBottom]);

  function onScroll() {
    const el = scrollRef.current;
    if (!el) return;
    const near = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    stickRef.current = near;
    setShowJump(!near);
  }

  // 输入框自适应高度
  useEffect(() => {
    const ta = taRef.current;
    if (!ta) return;
    ta.style.height = "auto";
    ta.style.height = `${Math.min(ta.scrollHeight, 200)}px`;
  }, [input]);

  async function onSend() {
    const content = input.trim();
    if (!content || !id || pending) return;
    setInput("");
    setPending(true);
    setWaiting(true);
    stickRef.current = true;
    try {
      await sendMessage(id, content);
      refetch();
    } catch (e) {
      console.error(e);
      setWaiting(false);
    } finally {
      setPending(false);
    }
  }

  async function toggleWebSearch() {
    const next = !(webSearch ?? false);
    setWebSearchOn(next);
    try {
      await setWebSearch(next);
    } catch (e) {
      console.error(e);
      setWebSearchOn(!next);
    }
  }

  function onInputChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    const v = e.target.value;
    setInput(v);
    // 输入 @ 触发文件引用菜单（仅末尾字符为 @ 时）
    if (v.endsWith("@")) setFileMenuOpen(true);
  }

  // 失焦时延迟收起，给菜单项点击留出时间
  function onInputBlur() {
    setTimeout(() => setFileMenuOpen(false), 150);
  }

  function pickFile(path: string) {
    setFileMenuOpen(false);
    setInput((cur) => (cur.endsWith("@") ? `${cur.slice(0, -1)}@${path} ` : `${cur}@${path} `));
    taRef.current?.focus();
  }

  const filteredFiles = useMemo(() => {
    const all = files ?? [];
    if (!fileMenuOpen) return [];
    // 用 @ 之后的文字过滤
    const m = /@([^\s@]*)$/.exec(input);
    const kw = (m?.[1] ?? "").toLowerCase();
    return all
      .filter((f) => !f.isDir && (!kw || f.path.toLowerCase().includes(kw)))
      .slice(0, 30);
  }, [files, fileMenuOpen, input]);

  const isTouch =
    typeof window !== "undefined" && window.matchMedia?.("(pointer: coarse)").matches;

  const subtitle = [
    workspaceLabel,
    session?.model,
    session?.mode,
    id ? `#${id.slice(0, 8)}` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <Shell
      activeId={id}
      title={session?.title || "会话"}
      subtitle={subtitle}
      actions={
        id ? (
          <div className="flex items-center gap-0.5">
            <ModelMenu sessionId={id} current={session?.model} />
            <ReasoningMenu sessionId={id} current={session?.reasoningEffort} />
            <RoleMenu sessionId={id} />
            <McpMenu />
          </div>
        ) : null
      }
    >
      <div className="relative flex min-h-0 flex-1 flex-col">
        <main ref={scrollRef} onScroll={onScroll} className="min-h-0 flex-1 overflow-y-auto">
          <div className="mx-auto w-full max-w-3xl space-y-5 px-4 py-6 md:px-6">
            {isLoading && (
              <div className="space-y-6">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="flex gap-3">
                    <div className="skeleton h-8 w-8 rounded-xl" />
                    <div className="flex-1 space-y-2">
                      <div className="skeleton h-3 w-2/3 rounded" />
                      <div className="skeleton h-3 w-full rounded" />
                      <div className="skeleton h-3 w-5/6 rounded" />
                    </div>
                  </div>
                ))}
              </div>
            )}

            {messages && messages.length === 0 && streaming === null && <EmptyChat />}

            {messages?.map((m) => <MessageItem key={m.id} m={m} />)}

            {streaming !== null && (
              <div className="flex gap-3">
                <Avatar />
                <div className={`min-w-0 flex-1`}>
                  {streaming ? (
                    <div className="caret">
                      <Markdown>{streaming}</Markdown>
                    </div>
                  ) : (
                    <div className="dots flex h-8 items-center">
                      <span />
                      <span />
                      <span />
                    </div>
                  )}
                </div>
              </div>
            )}

            {waiting && streaming === null && <Thinking />}
            <div ref={bottomRef} className="h-px" />
          </div>
        </main>

        {showJump && (
          <button
            onClick={() => {
              stickRef.current = true;
              scrollToBottom();
            }}
            aria-label="回到底部"
            className="anim-in absolute bottom-28 left-1/2 z-10 flex h-9 w-9 -translate-x-1/2 items-center justify-center rounded-full border border-line bg-surface text-muted shadow-card hover:text-fg"
          >
            <ArrowDown className="h-4 w-4" />
          </button>
        )}

        <footer className="safe-b shrink-0 bg-linear-to-t from-bg from-70% to-transparent px-3 pb-3 pt-2 md:px-6">
          <div className="mx-auto max-w-3xl">
            <div className="flex items-end gap-2 rounded-[26px] border border-line bg-surface p-2.5 pl-3 shadow-card transition-colors focus-within:border-accent/50">
              {/* 快捷栏：联网开关 */}
              <button
                type="button"
                onClick={toggleWebSearch}
                aria-pressed={webSearch ?? false}
                aria-label="联网搜索"
                title="联网搜索"
                className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition-colors active:scale-90 ${
                  webSearch
                    ? "bg-accent-soft text-accent"
                    : "text-muted hover:bg-surface2 hover:text-fg"
                }`}
              >
                <Globe className="h-[18px] w-[18px]" />
              </button>

              <textarea
                ref={taRef}
                value={input}
                onChange={onInputChange}
                onBlur={onInputBlur}
                onKeyDown={(e) => {
                  if (e.key !== "Enter" || e.shiftKey) return;
                  if (e.nativeEvent.isComposing || isTouch) return; // 输入法选词 / 手机上回车换行
                  e.preventDefault();
                  onSend();
                }}
                rows={1}
                enterKeyHint={isTouch ? "enter" : "send"}
                placeholder={isTouch ? "输入消息…" : "输入消息，@ 引用文件，Enter 发送"}
                className="max-h-[200px] min-h-[36px] flex-1 resize-none bg-transparent py-1.5 text-[15px] leading-relaxed text-fg outline-none placeholder:text-faint"
              />
              <button
                onClick={onSend}
                disabled={pending || !input.trim()}
                aria-label="发送"
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-accent text-accent-fg transition-all hover:brightness-110 active:scale-90 disabled:bg-surface2 disabled:text-faint"
              >
                <ArrowUp className="h-[18px] w-[18px]" strokeWidth={2.4} />
              </button>
            </div>

            {/* @ 文件引用菜单 */}
            {fileMenuOpen && filteredFiles.length > 0 && (
              <div className="anim-in mx-auto mb-2 max-h-56 w-full max-w-3xl overflow-y-auto rounded-xl border border-line bg-surface p-1 shadow-card">
                {filteredFiles.map((f) => (
                  <button
                    key={f.path}
                    type="button"
                    onClick={() => pickFile(f.path)}
                    className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-fg/90 transition-colors hover:bg-surface2"
                  >
                    <span className="min-w-0 flex-1 truncate">{f.name}</span>
                    <span className="shrink-0 text-[11px] text-faint">{f.path}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </footer>
      </div>
    </Shell>
  );
}
