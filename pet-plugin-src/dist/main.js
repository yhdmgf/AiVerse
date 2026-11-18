'use strict';

const readline = require('readline');

const SETTINGS_PATH = 'data://pet-settings.json';
const DEFAULTS = { autoStart: false, form: 'deepy', width: 110, height: 130, visibilityPolicy: 'always', clickInterval: 5, autoEmote: true, autoEmoteInterval: 3, showBubble: true };
const IDLE_SLEEP_MS = 3 * 60 * 1000;
const TOUCH_WAKE_CHANCE = 0.3;
const AGENT_POLL_MS = 2500;

// agent 轮询唯一职责：检测“等待授权/确认”→ waking。
// thinking/working/success/error 交给 hooks 真实事件驱动，空闲交给宠物页自管；
// 轮询不再重复发这些状态，彻底消除 idle 消息风暴与两套状态驱动打架。
const AGENT_PET = {
  waking: { state: 'thinking', actions: { deepy: ['waking'], girl: ['waking'] }, lines: ['要你确认~', '看一下嘛', '授权一下？'] }
};
let agentPollTimer = null;
let prevAgentState = null;
let agentSessionId = null;

let hostSeq = 0;
const hostPending = new Map();
let overlayId = null;
let overlayGeneration = 0;
let idleTimer = null;
let sleepTimer = null;
let currentState = 'idle';

function send(message) {
  process.stdout.write(JSON.stringify(message) + '\n');
}

function hostCall(method, params) {
  const id = 'host-' + (++hostSeq);
  return new Promise((resolve, reject) => {
    hostPending.set(id, { resolve, reject });
    send({ jsonrpc: '2.0', id, method, params: params || {} });
  });
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function petLine(list) {
  return list[Math.floor(Math.random() * list.length)];
}

// 把 agent 状态推给悬浮窗；同状态只推一次。仅处理 waking（等待授权），
// 其余状态一律不从这里发（thinking/working/success/error 走 hooks，idle 走宠物页自管）。
async function forwardAgentState(state) {
  if (!overlayId || state === prevAgentState) return;
  const def = AGENT_PET[state];
  if (!def) return; // 不发送 idle / typing / happy / error。
  prevAgentState = state;
  try {
    await hostCall('host.overlay.postMessage', {
      surface: 'pet',
      payload: {
        type: 'agentState',
        agentState: state,
        actions: def.actions,
        resume: def.state,
        line: petLine(def.lines)
      }
    });
  } catch (e) {
    // 推送失败忽略，下轮再试。
  }
}

async function pollAgentOnce() {
  try {
    const listed = await hostCall('host.chat.listSessions', { limit: 1 });
    const session = ((listed && listed.sessions) || [])[0];
    if (!session || !session.id) return;
    agentSessionId = session.id;
    const runState = await hostCall('host.chat.getRunState', { sessionId: session.id });
    const run = (runState && runState.run) || {};
    // 只在 agent 正在运行时介入；运行结束交给 hooks.agentRunFinished，这里不发 happy/error/idle。
    if (run.running !== true) return;
    let approvals = null;
    try {
      approvals = await hostCall('host.chat.listApprovals', { sessionId: session.id });
    } catch (e) {
      approvals = null;
    }
    const pending = approvals && (approvals.tool || approvals.question || approvals.plan);
    // 仅等待授权/确认时发 waking；运行中的 thinking/working 交给 hooks，避免两套驱动打架。
    if (pending) await forwardAgentState('waking');
  } catch (e) {
    // 没权限/没有会话/宿主不支持：安静退化，不影响桌宠其他功能。
  }
}

function startAgentPolling() {
  stopAgentPolling();
  prevAgentState = null;
  pollAgentOnce();
  agentPollTimer = setInterval(pollAgentOnce, AGENT_POLL_MS);
}

function stopAgentPolling() {
  if (agentPollTimer) {
    clearInterval(agentPollTimer);
    agentPollTimer = null;
  }
  prevAgentState = null;
}

async function readSettings() {
  try {
    const file = await hostCall('host.fs.read', { path: SETTINGS_PATH });
    const parsed = JSON.parse(String(file.content || '{}'));
    return Object.assign({}, DEFAULTS, parsed);
  } catch (e) {
    return Object.assign({}, DEFAULTS);
  }
}

async function autoShowPet() {
  const settings = await readSettings();
  if (settings.autoStart !== true) {
    return { ok: true, skipped: true };
  }
  const width = Number(settings.width) || DEFAULTS.width;
  const height = Number(settings.height) || DEFAULTS.height;
  const visibilityPolicy = String(settings.visibilityPolicy || DEFAULTS.visibilityPolicy);
  let lastError = null;
  // 启动或启用插件时 App 可能还没进入前台；前台服务被系统挡掉时短暂重试。
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const info = await hostCall('host.overlay.create', {
        surface: 'pet',
        width,
        height,
        visibilityPolicy
      });
      overlayId = info.overlayId;
      overlayGeneration++;
      scheduleSleep();
      startAgentPolling();
      return { ok: true, overlayId: info.overlayId, width, height };
    } catch (error) {
      lastError = error;
      if (error.code === 'OVERLAY_SERVICE_FAILED' && attempt < 2) {
        await sleep(1200);
        continue;
      }
      break;
    }
  }
  return {
    ok: false,
    code: (lastError && lastError.code) || 'BOOT_FAILED',
    message: String((lastError && lastError.message) || lastError || 'unknown')
  };
}

function clearOverlayState() {
  overlayGeneration++;
  overlayId = null;
  stopAgentPolling();
  clearTimeout(idleTimer);
  clearTimeout(sleepTimer);
  if (['feeding', 'success', 'error'].includes(currentState)) currentState = 'idle';
}

function scheduleSleep() {
  clearTimeout(sleepTimer);
  sleepTimer = null;
  if (!overlayId || currentState !== 'idle') return;
  sleepTimer = setTimeout(() => {
    postState('sleep');
  }, IDLE_SLEEP_MS);
}

async function postState(state) {
  currentState = state;
  // hooks 真实状态变化后清空去重标记，使下一轮 approval 检测能重新触发 waking。
  prevAgentState = null;
  if (!overlayId) {
    return { ok: false, code: 'OVERLAY_NOT_FOUND', state };
  }
  const generation = overlayGeneration;
  clearTimeout(idleTimer);
  scheduleSleep();
  try {
    await hostCall('host.overlay.postMessage', {
      surface: 'pet',
      payload: { type: 'state', state }
    });
    return { ok: true, state, logs: ['pet state -> ' + state] };
  } catch (error) {
    if (error.code === 'OVERLAY_NOT_FOUND' && generation === overlayGeneration) {
      clearOverlayState();
    }
    return {
      ok: false,
      code: error.code || 'POST_FAILED',
      state,
      logs: ['pet state failed -> ' + (error.code || error.message)]
    };
  }
}

function scheduleIdle() {
  clearTimeout(idleTimer);
  idleTimer = setTimeout(() => {
    postState('idle');
  }, 3000);
}

async function postHint(text) {
  try {
    await hostCall('host.overlay.postMessage', {
      surface: 'pet',
      payload: { type: 'hint', text }
    });
  } catch (e) {
    // 浮层已关闭时忽略。
  }
}

async function handleMenuAction(action) {
  if (action === 'feed') {
    const result = postState('feeding');
    scheduleIdle();
    await result;
    await postHint('好吃！');
  } else if (action === 'sleep') {
    await postState('sleep');
    await postHint('晚安…');
  } else if (action === 'hide') {
    clearOverlayState();
    try {
      await hostCall('host.overlay.close', { surface: 'pet' });
    } catch (e) {
      // 已关闭。
    }
  }
  return { ok: true };
}

const handlers = {
  'hooks.boot': () => autoShowPet(),
  'hooks.pluginEnabled': () => autoShowPet(),
  'hooks.agentRunStarted': () => postState('thinking'),
  'hooks.agentToolStarted': () => postState('working'),
  'hooks.agentToolFinished': () => postState('working'),
  'hooks.agentRunFinished': (args) => {
    const state = args.status === 'success'
      ? 'success'
      : (args.status === 'cancelled' ? 'idle' : 'error');
    const result = postState(state);
    if (state !== 'idle') scheduleIdle();
    return result;
  },
  // 配置页读取当前浮层，用于展示状态与关闭按钮。
  'overlay.status': () => ({ ok: true, overlayId, state: currentState }),
  // 配置页手动创建/关闭浮层时同步给常驻进程。
  'overlay.created': (args) => {
    if (args.overlayId) overlayId = String(args.overlayId);
    overlayGeneration++;
    scheduleSleep();
    // 登记存活浮层后主动同步形态，尽早推送；pet 页未就绪时可能丢失，由 petReady 回推 + 收敛循环兜底。
    void (async () => {
      try {
        const settings = await readSettings();
        // await readSettings 期间浮层可能已被关闭并清空 overlayId，为空则不再推送。
        if (!overlayId) return;
        await hostCall('host.overlay.postMessage', {
          surface: 'pet',
          payload: { type: 'form', form: settings.form, clickInterval: Number(settings.clickInterval) || 5 }
        });
      } catch (error) {
        // 投递失败（多半是 overlayId 已失效）时清空登记；其它错误静默，pet 页 petReady 回推 + 收敛循环会兜底。
        if (error.code === 'OVERLAY_NOT_FOUND') clearOverlayState();
      }
    })();
    return { ok: true };
  },
  'overlay.closed': () => {
    clearOverlayState();
    return { ok: true };
  },
  // 配置页切换形态/点击频率后，转发给悬浮窗使其即时生效（overlay.update 不会派发页面消息）。
  'overlay.setForm': async (args) => {
    const form = String((args && args.form) || 'deepy');
    const settings = await readSettings();
    // overlayId 为空说明常驻进程还没登记到存活浮层（浮层未开 / 刚重建但尚未收到 petReady）。
    // 此时不送消息必然没效果，必须如实返回 skipped，让配置页弹「未生效」而不是假成功。
    if (!overlayId) return { ok: false, code: 'OVERLAY_NOT_FOUND', skipped: true, form };
    try {
      await hostCall('host.overlay.postMessage', {
        surface: 'pet',
        payload: { type: 'form', form, clickInterval: Number(settings.clickInterval) || 5 }
      });
      return { ok: true, form };
    } catch (error) {
      // 投递失败（多半是 overlayId 已失效）时清空登记，下次 petReady/created 重新登记；
      // 并把错误如实返回，配置页不再误弹「已切换」。
      if (error.code === 'OVERLAY_NOT_FOUND') clearOverlayState();
      return { ok: false, code: error.code || 'POST_FAILED', form };
    }
  },
  'overlay.setClickInterval': async (args) => {
    const sec = Math.min(10, Math.max(1, Number(args && args.seconds) || 5));
    if (!overlayId) return { ok: true, skipped: true };
    try {
      await hostCall('host.overlay.postMessage', {
        surface: 'pet',
        payload: { type: 'clickInterval', seconds: sec }
      });
      return { ok: true, seconds: sec };
    } catch (error) {
      return { ok: false, code: error.code || 'POST_FAILED' };
    }
  },
  // 配置页切换自动表情开关/间隔后，转发给悬浮窗使其即时生效。
  'overlay.setAutoEmote': async (args) => {
    const enabled = (args && args.enabled) !== false;
    const raw = Number(args && args.interval);
    const interval = Number.isFinite(raw) ? Math.min(10, Math.max(2, Math.round(raw))) : 3;
    if (!overlayId) return { ok: true, skipped: true };
    try {
      await hostCall('host.overlay.postMessage', {
        surface: 'pet',
        payload: { type: 'autoEmote', enabled, interval }
      });
      return { ok: true, enabled, interval };
    } catch (error) {
      return { ok: false, code: error.code || 'POST_FAILED' };
    }
  },
  // 配置页切换「显示聊天气泡」后，转发给悬浮窗使其即时生效。
  'overlay.setBubble': async (args) => {
    const enabled = (args && args.enabled) !== false;
    if (!overlayId) return { ok: true, skipped: true };
    try {
      await hostCall('host.overlay.postMessage', {
        surface: 'pet',
        payload: { type: 'bubble', enabled }
      });
      return { ok: true, enabled };
    } catch (error) {
      return { ok: false, code: error.code || 'POST_FAILED' };
    }
  },
  'overlay.message': async (args) => {
    const payload = args.payload || {};
    if (payload.type === 'menuAction') return handleMenuAction(payload.action);
    if (payload.type === 'petReady') {
      if (args.overlayId) overlayId = String(args.overlayId);
      overlayGeneration++;
      scheduleSleep();
      const settings = await readSettings();
      return hostCall('host.overlay.postMessage', {
        surface: 'pet',
        payload: { type: 'state', state: currentState, form: settings.form }
      });
    }
    if (payload.type === 'activity') {
      scheduleSleep();
    } else if (payload.type === 'pet') {
      if (currentState !== 'sleep') {
        scheduleSleep();
      } else if (Math.random() < TOUCH_WAKE_CHANCE) {
        await postState('idle');
        await postHint('醒啦，陪你一会儿');
      } else {
        await postHint('呼噜…');
      }
    }
    return { ok: true };
  }
};

function handleHostRequest(id, method, params) {
  if (method !== 'tool.invoke' && method !== 'hook.invoke' && method !== 'ui.invoke') {
    send({ jsonrpc: '2.0', id, error: { code: 'METHOD_UNKNOWN', message: method } });
    return;
  }
  const handler = handlers[params.handler];
  if (typeof handler !== 'function') {
    send({ jsonrpc: '2.0', id, error: { code: 'HANDLER_NOT_FOUND', message: params.handler } });
    return;
  }
  Promise.resolve()
    .then(() => handler(params.args || {}))
    .then((result) => send({ jsonrpc: '2.0', id, result: result === undefined ? null : result }))
    .catch((error) => send({
      jsonrpc: '2.0',
      id,
      error: { code: 'PLUGIN_ERROR', message: String((error && error.message) || error) }
    }));
}

function handleHostResponse(id, message) {
  const pending = hostPending.get(id);
  if (!pending) return;
  hostPending.delete(id);
  if (message.error) {
    const error = new Error(message.error.message || 'host error');
    error.code = message.error.code || 'HOST_ERROR';
    pending.reject(error);
  } else {
    pending.resolve(message.result === undefined ? null : message.result);
  }
}

readline.createInterface({ input: process.stdin }).on('line', (line) => {
  if (!line.trim()) return;
  let message;
  try {
    message = JSON.parse(line);
  } catch (e) {
    return;
  }
  if (message.method && message.id !== undefined) {
    handleHostRequest(message.id, message.method, message.params || {});
  } else if (message.method) {
    return;
  } else if (message.id !== undefined) {
    handleHostResponse(String(message.id), message);
  }
});
